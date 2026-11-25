import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { RelayStore, hmacSha256Hex } from '../src/store.mjs';
import { SupabaseRelayStore } from '../src/supabaseStore.mjs';
import { createRelayServer } from '../src/server.mjs';

async function startTestServer(options = {}) {
  const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'monitordad-relay-test-'));
  const store = new RelayStore(dataDir);
  const server = createRelayServer(store, options);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const addr = server.address();
  const baseUrl = `http://127.0.0.1:${addr.port}`;
  return {
    baseUrl,
    dataDir,
    store,
    close: async () => {
      await new Promise((resolve) => server.close(resolve));
      fs.rmSync(dataDir, { recursive: true, force: true });
    }
  };
}

function authHeaders(deviceId, parentId, sharedSecret) {
  const digest = hmacSha256Hex(`${deviceId}:${parentId}`, sharedSecret);
  return {
    'Content-Type': 'application/json',
    'X-MonitorDad-Device': deviceId,
    'X-MonitorDad-Parent': parentId,
    Authorization: `Bearer ${digest}`
  };
}

test('health endpoint returns ok', async () => {
  const ctx = await startTestServer();
  try {
    const res = await fetch(`${ctx.baseUrl}/health`);
    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.status, 'ok');
    assert.equal(body.service, 'monitordad-relay');
  } finally {
    await ctx.close();
  }
});

test('full temporary pairing lifecycle is single-use and rejects replay', async () => {
  const ctx = await startTestServer();
  try {
    // 1. Kids initiates pairing
    const initRes = await fetch(`${ctx.baseUrl}/api/v1/pairing/initiate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ deviceId: 'tablet-lucas-01', deviceName: 'Tablet do Lucas' })
    });
    assert.equal(initRes.status, 201);
    const initData = await initRes.json();
    assert.equal(initData.pairingCode.length, 6);
    assert.ok(initData.claimToken.length >= 32);

    // 2. Kids polls before parent claims -> PENDING
    const pendingRes = await fetch(`${ctx.baseUrl}/api/v1/pairing/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        pairingCode: initData.pairingCode,
        claimToken: initData.claimToken
      })
    });
    assert.equal(pendingRes.status, 200);
    const pendingData = await pendingRes.json();
    assert.equal(pendingData.status, 'PENDING');

    // 3. Parent claims the pairing code
    const claimRes = await fetch(`${ctx.baseUrl}/api/v1/pairing/claim`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        pairingCode: initData.pairingCode,
        parentId: 'parent-hod-01'
      })
    });
    assert.equal(claimRes.status, 200);
    const claimData = await claimRes.json();
    assert.equal(claimData.deviceId, 'tablet-lucas-01');
    assert.equal(claimData.parentId, 'parent-hod-01');
    assert.equal(claimData.sharedSecret.length, 64);

    // 4. Second claim attempt with same code must fail (409)
    const secondClaimRes = await fetch(`${ctx.baseUrl}/api/v1/pairing/claim`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        pairingCode: initData.pairingCode,
        parentId: 'attacker-parent'
      })
    });
    assert.equal(secondClaimRes.status, 409);

    // 5. Kids completes pairing -> receives sharedSecret once
    const completeRes = await fetch(`${ctx.baseUrl}/api/v1/pairing/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        pairingCode: initData.pairingCode,
        claimToken: initData.claimToken
      })
    });
    assert.equal(completeRes.status, 200);
    const completeData = await completeRes.json();
    assert.equal(completeData.status, 'PAIRED');
    assert.equal(completeData.sharedSecret, claimData.sharedSecret);

    // 6. Re-completing the same pairing code must fail (single-use)
    const replayCompleteRes = await fetch(`${ctx.baseUrl}/api/v1/pairing/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        pairingCode: initData.pairingCode,
        claimToken: initData.claimToken
      })
    });
    assert.equal(replayCompleteRes.status, 404);
  } finally {
    await ctx.close();
  }
});

test('expired pairing code is rejected', async () => {
  const ctx = await startTestServer({ pairingTtlMs: -10 });
  try {
    const initRes = await fetch(`${ctx.baseUrl}/api/v1/pairing/initiate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ deviceId: 'tablet-exp-01', deviceName: 'Tablet' })
    });
    const initData = await initRes.json();

    const claimRes = await fetch(`${ctx.baseUrl}/api/v1/pairing/claim`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pairingCode: initData.pairingCode, parentId: 'parent-1' })
    });
    assert.equal(claimRes.status, 404);
  } finally {
    await ctx.close();
  }
});

test('authenticated commands, ACKs, rules, telemetry and persistence across store reload', async () => {
  const ctx = await startTestServer();
  try {
    const initRes = await fetch(`${ctx.baseUrl}/api/v1/pairing/initiate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ deviceId: 'tablet-02', deviceName: 'Tablet Sala' })
    });
    const initData = await initRes.json();

    const claimRes = await fetch(`${ctx.baseUrl}/api/v1/pairing/claim`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pairingCode: initData.pairingCode, parentId: 'parent-02' })
    });
    const { deviceId, parentId, sharedSecret } = await claimRes.json();

    // Unauthorized request must fail
    const unauthRes = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}/commands/pending`, {
      headers: authHeaders(deviceId, parentId, 'wrong-secret')
    });
    assert.equal(unauthRes.status, 401);

    // Heartbeat
    const hbRes = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}/heartbeat`, {
      method: 'POST',
      headers: authHeaders(deviceId, parentId, sharedSecret),
      body: JSON.stringify({ role: 'parent' })
    });
    assert.equal(hbRes.status, 200);

    // Parent sends signed command
    const now = Date.now();
    const cmd = {
      commandId: 'cmd-101',
      targetDeviceId: deviceId,
      issuedByParentId: parentId,
      type: 'PAUSE_DEVICE',
      payload: {},
      createdAtMillis: now,
      expiresAtMillis: now + 60_000,
      sequenceNumber: 1,
      nonce: 'nonce-101'
    };
    cmd.signature = hmacSha256Hex(
      `${cmd.commandId}:${cmd.targetDeviceId}:${cmd.type}:${cmd.sequenceNumber}:${cmd.nonce}:${cmd.expiresAtMillis}`,
      sharedSecret
    );

    const sendCmdRes = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}/commands`, {
      method: 'POST',
      headers: authHeaders(deviceId, parentId, sharedSecret),
      body: JSON.stringify(cmd)
    });
    assert.equal(sendCmdRes.status, 201);

    // Kids polls pending commands
    const pendingRes = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}/commands/pending`, {
      headers: authHeaders(deviceId, parentId, sharedSecret)
    });
    assert.equal(pendingRes.status, 200);
    const pendingBody = await pendingRes.json();
    assert.equal(pendingBody.commands.length, 1);
    assert.equal(pendingBody.commands[0].commandId, 'cmd-101');

    // Kids sends ACK
    const ackRes = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}/commands/cmd-101/ack`, {
      method: 'POST',
      headers: authHeaders(deviceId, parentId, sharedSecret),
      body: JSON.stringify({
        commandId: 'cmd-101',
        deviceId,
        status: 'EXECUTED',
        timestampMillis: Date.now(),
        resultMessage: 'Tablet pausado'
      })
    });
    assert.equal(ackRes.status, 200);

    // Pending commands should now be empty
    const pendingAfter = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}/commands/pending`, {
      headers: authHeaders(deviceId, parentId, sharedSecret)
    });
    const pendingAfterBody = await pendingAfter.json();
    assert.equal(pendingAfterBody.commands.length, 0);

    // Parent polls ACKs
    const acksRes = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}/acks`, {
      headers: authHeaders(deviceId, parentId, sharedSecret)
    });
    const acksBody = await acksRes.json();
    assert.equal(acksBody.acks.length, 1);
    assert.equal(acksBody.acks[0].status, 'EXECUTED');

    // Rules PUT / GET
    const putRules = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}/rules`, {
      method: 'PUT',
      headers: authHeaders(deviceId, parentId, sharedSecret),
      body: JSON.stringify({ version: 2, dailyDeviceLimitMinutes: 90 })
    });
    assert.equal(putRules.status, 200);

    const getRules = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}/rules`, {
      headers: authHeaders(deviceId, parentId, sharedSecret)
    });
    const rulesBody = await getRules.json();
    assert.equal(rulesBody.dailyDeviceLimitMinutes, 90);

    // Telemetry PUT / GET
    const putTel = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}/telemetry`, {
      method: 'PUT',
      headers: authHeaders(deviceId, parentId, sharedSecret),
      body: JSON.stringify({ timestamp: 123456, summary: { totalUsedMinutesToday: 42, isDemoData: false } })
    });
    assert.equal(putTel.status, 200);

    const getTel = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}/telemetry`, {
      headers: authHeaders(deviceId, parentId, sharedSecret)
    });
    const telBody = await getTel.json();
    assert.equal(telBody.summary.totalUsedMinutesToday, 42);

    // Verify persistence across RelayStore reload from disk
    const reloadedStore = new RelayStore(ctx.dataDir);
    assert.equal(reloadedStore.getDevice(deviceId).sharedSecret, sharedSecret);
    assert.equal(reloadedStore.getRulesEnvelope(deviceId).dailyDeviceLimitMinutes, 90);
    assert.equal(reloadedStore.getTelemetry(deviceId).summary.totalUsedMinutesToday, 42);
  } finally {
    await ctx.close();
  }
});

test('pairing claim enforces brute-force rate limiting after repeated failures', async () => {
  const ctx = await startTestServer();
  try {
    for (let i = 0; i < 5; i += 1) {
      const res = await fetch(`${ctx.baseUrl}/api/v1/pairing/claim`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ pairingCode: 'WRONG9', parentId: 'parent-1' })
      });
      assert.equal(res.status, 404);
    }

    const blocked = await fetch(`${ctx.baseUrl}/api/v1/pairing/claim`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pairingCode: 'WRONG9', parentId: 'parent-1' })
    });
    assert.equal(blocked.status, 429);
  } finally {
    await ctx.close();
  }
});

test('local-paired device can provision onto relay without re-pairing and blocks credential hijack', async () => {
  const ctx = await startTestServer();
  try {
    const deviceId = 'tablet-local-01';
    const parentId = 'parent-local-01';
    const sharedSecret = 'a1b2c3d4e5f60718293a4b5c6d7e8f90';

    // 1. Provision locally-paired credentials onto relay
    const provRes = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}/provision`, {
      method: 'POST',
      headers: authHeaders(deviceId, parentId, sharedSecret),
      body: JSON.stringify({ parentId, deviceName: 'Tablet Local', sharedSecret })
    });
    assert.equal(provRes.status, 201);

    // 2. Idempotent re-provision with same secret returns 200
    const reProvRes = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}/provision`, {
      method: 'POST',
      headers: authHeaders(deviceId, parentId, sharedSecret),
      body: JSON.stringify({ parentId, deviceName: 'Tablet Atualizado', sharedSecret })
    });
    assert.equal(reProvRes.status, 200);

    // 3. Attacker trying to hijack deviceId with different secret must get 403
    const hijackSecret = 'ffffffffffffffffffffffffffffffff';
    const hijackRes = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}/provision`, {
      method: 'POST',
      headers: authHeaders(deviceId, 'attacker-parent', hijackSecret),
      body: JSON.stringify({ parentId: 'attacker-parent', deviceName: 'Evil', sharedSecret: hijackSecret })
    });
    assert.equal(hijackRes.status, 403);

    // 4. Legitimate device can immediately send heartbeat after provisioning
    const hbRes = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}/heartbeat`, {
      method: 'POST',
      headers: authHeaders(deviceId, parentId, sharedSecret),
      body: JSON.stringify({ role: 'parent' })
    });
    assert.equal(hbRes.status, 200);

    // 5. Revoking device deletes it from relay
    const delRes = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}`, {
      method: 'DELETE',
      headers: authHeaders(deviceId, parentId, sharedSecret)
    });
    assert.equal(delRes.status, 200);

    const afterDelHb = await fetch(`${ctx.baseUrl}/api/v1/devices/${deviceId}/heartbeat`, {
      method: 'POST',
      headers: authHeaders(deviceId, parentId, sharedSecret),
      body: JSON.stringify({ role: 'parent' })
    });
    assert.equal(afterDelHb.status, 404);
  } finally {
    await ctx.close();
  }
});

function createInMemorySupabaseFetch() {
  const tables = {
    relay_pairings: new Map(),
    relay_devices: new Map()
  };

  return async (urlStr, options = {}) => {
    const url = new URL(urlStr);
    const tableName = url.pathname.split('/rest/v1/')[1];
    const table = tables[tableName];
    if (!table) {
      return new Response(JSON.stringify({ message: 'Table not found' }), { status: 404 });
    }

    const method = (options.method || 'GET').toUpperCase();
    const prefer = String(options.headers?.Prefer || '');
    const body = options.body ? JSON.parse(options.body) : null;

    const matchRow = (row) => {
      for (const [key, val] of url.searchParams.entries()) {
        if (key === 'select' || key === 'on_conflict') continue;
        if (val.startsWith('eq.')) {
          const expected = val.slice(3);
          const actual = row[key];
          if (String(actual) !== expected) return false;
        } else if (val.startsWith('lt.')) {
          const bound = Number(val.slice(3));
          if (!(Number(row[key]) < bound)) return false;
        }
      }
      return true;
    };

    const pkField = tableName === 'relay_pairings' ? 'code' : 'device_id';

    if (method === 'GET') {
      const matched = [...table.values()].filter(matchRow);
      return new Response(JSON.stringify(matched), { status: 200 });
    }

    if (method === 'POST') {
      const pk = body[pkField];
      if (table.has(pk) && !prefer.includes('resolution=merge-duplicates')) {
        return new Response(JSON.stringify({ message: 'Duplicate key' }), { status: 409 });
      }
      table.set(pk, structuredClone(body));
      return new Response(prefer.includes('return=representation') ? JSON.stringify([body]) : '', {
        status: 201
      });
    }

    if (method === 'PATCH') {
      const updated = [];
      for (const [pk, row] of table.entries()) {
        if (matchRow(row)) {
          const next = { ...row, ...structuredClone(body) };
          table.set(pk, next);
          updated.push(next);
        }
      }
      return new Response(prefer.includes('return=representation') ? JSON.stringify(updated) : '', {
        status: 200
      });
    }

    if (method === 'DELETE') {
      const deleted = [];
      for (const [pk, row] of table.entries()) {
        if (matchRow(row)) {
          deleted.push(row);
          table.delete(pk);
        }
      }
      return new Response(prefer.includes('return=representation') ? JSON.stringify(deleted) : '', {
        status: 200
      });
    }

    return new Response('Method not allowed', { status: 405 });
  };
}

test('SupabaseRelayStore persists pairings, provisioning, commands, rules, telemetry and survives server restart', async () => {
  const mockFetch = createInMemorySupabaseFetch();
  const store1 = new SupabaseRelayStore({
    supabaseUrl: 'https://example.supabase.co',
    serviceRoleKey: 'test-service-role-key',
    fetchImpl: mockFetch
  });

  const server1 = createRelayServer(store1);
  await new Promise((resolve) => server1.listen(0, '127.0.0.1', resolve));
  const baseUrl1 = `http://127.0.0.1:${server1.address().port}`;

  let deviceId;
  let parentId;
  let sharedSecret;

  try {
    const health = await (await fetch(`${baseUrl1}/health`)).json();
    assert.equal(health.storage, 'supabase-postgres');

    const init = await (await fetch(`${baseUrl1}/api/v1/pairing/initiate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ deviceId: 'tablet-supa-01', deviceName: 'Tablet Supabase' })
    })).json();

    const claim = await (await fetch(`${baseUrl1}/api/v1/pairing/claim`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pairingCode: init.pairingCode, parentId: 'parent-supa-01' })
    })).json();

    deviceId = claim.deviceId;
    parentId = claim.parentId;
    sharedSecret = claim.sharedSecret;

    const complete = await (await fetch(`${baseUrl1}/api/v1/pairing/complete`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pairingCode: init.pairingCode, claimToken: init.claimToken })
    })).json();
    assert.equal(complete.status, 'PAIRED');

    const putRules = await fetch(`${baseUrl1}/api/v1/devices/${deviceId}/rules`, {
      method: 'PUT',
      headers: authHeaders(deviceId, parentId, sharedSecret),
      body: JSON.stringify({ version: 5, dailyDeviceLimitMinutes: 120 })
    });
    assert.equal(putRules.status, 200);

    const putTel = await fetch(`${baseUrl1}/api/v1/devices/${deviceId}/telemetry`, {
      method: 'PUT',
      headers: authHeaders(deviceId, parentId, sharedSecret),
      body: JSON.stringify({ timestamp: 999999, summary: { totalUsedMinutesToday: 30, isDemoData: false } })
    });
    assert.equal(putTel.status, 200);
  } finally {
    await new Promise((resolve) => server1.close(resolve));
  }

  // Simulate Render Free spin-down / restart with a fresh server & store instance
  const store2 = new SupabaseRelayStore({
    supabaseUrl: 'https://example.supabase.co',
    serviceRoleKey: 'test-service-role-key',
    fetchImpl: mockFetch
  });
  const server2 = createRelayServer(store2);
  await new Promise((resolve) => server2.listen(0, '127.0.0.1', resolve));
  const baseUrl2 = `http://127.0.0.1:${server2.address().port}`;

  try {
    const rulesAfterRestart = await (await fetch(`${baseUrl2}/api/v1/devices/${deviceId}/rules`, {
      headers: authHeaders(deviceId, parentId, sharedSecret)
    })).json();
    assert.equal(rulesAfterRestart.dailyDeviceLimitMinutes, 120);

    const telAfterRestart = await (await fetch(`${baseUrl2}/api/v1/devices/${deviceId}/telemetry`, {
      headers: authHeaders(deviceId, parentId, sharedSecret)
    })).json();
    assert.equal(telAfterRestart.summary.totalUsedMinutesToday, 30);

    const delRes = await fetch(`${baseUrl2}/api/v1/devices/${deviceId}`, {
      method: 'DELETE',
      headers: authHeaders(deviceId, parentId, sharedSecret)
    });
    assert.equal(delRes.status, 200);
  } finally {
    await new Promise((resolve) => server2.close(resolve));
  }
});

test('SupabaseRelayStore handles sb_secret_ keys without Bearer JWT header and reports health diagnostics', async () => {
  const seenHeaders = [];
  const baseMock = createInMemorySupabaseFetch();
  const spyFetch = async (url, options = {}) => {
    seenHeaders.push(options.headers || {});
    return baseMock(url, options);
  };

  // 1. New sb_secret_... key: must NOT send Authorization: Bearer
  const secretKeyStore = new SupabaseRelayStore({
    supabaseUrl: 'https://example.supabase.co/rest/v1/',
    serviceRoleKey: 'sb_secret_test_key_12345',
    fetchImpl: spyFetch
  });
  const serverSecret = createRelayServer(secretKeyStore);
  await new Promise((resolve) => serverSecret.listen(0, '127.0.0.1', resolve));
  try {
    const port = serverSecret.address().port;
    const healthRes = await fetch(`http://127.0.0.1:${port}/health`);
    const health = await healthRes.json();
    assert.equal(health.databaseConnected, true);
    assert.equal(seenHeaders[0].apikey, 'sb_secret_test_key_12345');
    assert.equal(seenHeaders[0].Authorization, undefined);
  } finally {
    await new Promise((resolve) => serverSecret.close(resolve));
  }

  // 2. Failing Supabase query: /health reports databaseConnected=false with safe error detail
  const failingStore = new SupabaseRelayStore({
    supabaseUrl: 'https://example.supabase.co',
    serviceRoleKey: 'header.payload.sig',
    fetchImpl: async () => new Response(
      JSON.stringify({ code: 'PGRST205', message: "Could not find the table 'public.relay_devices'" }),
      { status: 404 }
    )
  });
  const serverFail = createRelayServer(failingStore);
  await new Promise((resolve) => serverFail.listen(0, '127.0.0.1', resolve));
  try {
    const port = serverFail.address().port;
    const health = await (await fetch(`http://127.0.0.1:${port}/health`)).json();
    assert.equal(health.databaseConnected, false);
    assert.match(health.databaseError, /PGRST205/);
  } finally {
    await new Promise((resolve) => serverFail.close(resolve));
  }
});

test('SupabaseRelayStore rejects anon/publishable keys and surfaces 42501 GRANT hint', async () => {
  // 1. Public sb_publishable_ key is rejected with clear message
  const pubStore = new SupabaseRelayStore({
    supabaseUrl: 'https://example.supabase.co',
    serviceRoleKey: 'sb_publishable_fake_key'
  });
  const pubHealth = await pubStore.checkHealth();
  assert.equal(pubHealth.ok, false);
  assert.match(pubHealth.error, /sb_publishable_/);

  // 2. JWT with role=anon is rejected without exposing the token
  const anonPayload = Buffer.from(JSON.stringify({ role: 'anon' })).toString('base64url');
  const anonStore = new SupabaseRelayStore({
    supabaseUrl: 'https://example.supabase.co',
    serviceRoleKey: `header.${anonPayload}.signature`
  });
  const anonHealth = await anonStore.checkHealth();
  assert.equal(anonHealth.ok, false);
  assert.match(anonHealth.error, /anon/);
  assert.doesNotMatch(anonHealth.error, /signature/);

  // 3. 42501 permission denied includes migration hint
  const permStore = new SupabaseRelayStore({
    supabaseUrl: 'https://example.supabase.co',
    serviceRoleKey: 'sb_secret_valid_format',
    fetchImpl: async () => new Response(
      JSON.stringify({ code: '42501', message: 'permission denied for table relay_devices' }),
      { status: 401 }
    )
  });
  const permHealth = await permStore.checkHealth();
  assert.equal(permHealth.ok, false);
  assert.match(permHealth.error, /42501/);
  assert.match(permHealth.error, /001_grant_service_role\.sql/);
});





