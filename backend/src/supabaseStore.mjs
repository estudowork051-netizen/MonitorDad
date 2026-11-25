import crypto from 'node:crypto';
import { DEFAULT_PAIRING_TTL_MS, generatePairingCode, timingSafeEqualHex } from './store.mjs';

function mapDeviceRow(row) {
  if (!row) return null;
  return {
    deviceId: row.device_id,
    parentId: row.parent_id,
    deviceName: row.device_name,
    sharedSecret: row.shared_secret,
    pairedAtMillis: Number(row.paired_at_millis),
    lastHeartbeatParent: row.last_heartbeat_parent != null ? Number(row.last_heartbeat_parent) : null,
    lastHeartbeatKids: row.last_heartbeat_kids != null ? Number(row.last_heartbeat_kids) : null,
    commands: Array.isArray(row.commands) ? row.commands : [],
    acks: Array.isArray(row.acks) ? row.acks : [],
    rulesEnvelope: row.rules_envelope ?? null,
    telemetry: row.telemetry ?? null
  };
}

function mapPairingRow(row) {
  if (!row) return null;
  return {
    code: row.code,
    deviceId: row.device_id,
    deviceName: row.device_name,
    claimToken: row.claim_token,
    createdAtMillis: Number(row.created_at_millis),
    expiresAtMillis: Number(row.expires_at_millis),
    claimed: Boolean(row.claimed),
    parentId: row.parent_id ?? null,
    sharedSecret: row.shared_secret ?? null,
    claimedAtMillis: row.claimed_at_millis != null ? Number(row.claimed_at_millis) : null
  };
}

function detectSupabaseKeyWarning(cleanKey) {
  if (cleanKey.startsWith('sb_publishable_')) {
    return 'SUPABASE_SERVICE_ROLE_KEY está usando uma chave pública (sb_publishable_). Configure a chave secreta service_role ou sb_secret_ no Render.';
  }
  const parts = cleanKey.split('.');
  if (parts.length === 3) {
    try {
      const payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
      if (payload?.role && payload.role !== 'service_role') {
        return `SUPABASE_SERVICE_ROLE_KEY possui papel '${payload.role}' em vez de 'service_role'. Substitua pela chave service_role no Render.`;
      }
    } catch {
      // ignore non-JSON payload
    }
  }
  return null;
}

export class SupabaseRelayStore {
  constructor({ supabaseUrl, serviceRoleKey, fetchImpl = globalThis.fetch }) {
    const cleanUrl = String(supabaseUrl || '')
      .trim()
      .replace(/^["']|["']$/g, '')
      .replace(/\/rest\/v1\/?$/i, '')
      .replace(/\/+$/, '');
    const cleanKey = String(serviceRoleKey || '')
      .trim()
      .replace(/^["']|["']$/g, '');
    if (!cleanUrl.startsWith('https://') && !cleanUrl.startsWith('http://127.0.0.1') && !cleanUrl.startsWith('http://localhost')) {
      throw new Error('SUPABASE_URL inválida: deve iniciar com https://');
    }
    if (!cleanKey) {
      throw new Error('SUPABASE_SERVICE_ROLE_KEY obrigatória.');
    }
    this.restBaseUrl = `${cleanUrl}/rest/v1`;
    this.serviceRoleKey = cleanKey;
    this.isJwtKey = cleanKey.split('.').length === 3;
    this.keyWarning = detectSupabaseKeyWarning(cleanKey);
    this.fetchImpl = fetchImpl;
    this.storageType = 'supabase-postgres';
  }

  async request(pathWithQuery, { method = 'GET', body, prefer } = {}) {
    if (this.keyWarning) {
      const err = new Error(this.keyWarning);
      err.statusCode = 502;
      throw err;
    }

    const headers = {
      apikey: this.serviceRoleKey,
      'Content-Type': 'application/json'
    };
    // Legacy service_role keys are 3-part JWTs and accept Authorization: Bearer.
    // New Supabase sb_secret_... keys are NOT JWTs and must be sent via apikey only.
    if (this.isJwtKey) {
      headers.Authorization = `Bearer ${this.serviceRoleKey}`;
    }
    if (prefer) {
      headers.Prefer = prefer;
    }

    let res;
    try {
      res = await this.fetchImpl(`${this.restBaseUrl}${pathWithQuery}`, {
        method,
        headers,
        body: body !== undefined ? JSON.stringify(body) : undefined
      });
    } catch (networkErr) {
      const err = new Error(`Falha de rede ao conectar no Supabase: ${networkErr?.message || 'erro desconhecido'}`);
      err.statusCode = 502;
      throw err;
    }

    const text = await res.text();
    if (!res.ok) {
      let pgCode = '';
      let detail = text || res.statusText;
      try {
        const parsed = JSON.parse(text);
        pgCode = String(parsed?.code || '');
        const codePrefix = pgCode ? `[${pgCode}] ` : '';
        detail = `${codePrefix}${parsed?.message || parsed?.error || detail}`;
      } catch {
        // keep plain text
      }

      let hint = '';
      if (pgCode === '42501' || res.status === 401) {
        hint = ' — Verifique se SUPABASE_SERVICE_ROLE_KEY é a chave service_role/sb_secret_ e execute backend/supabase/migrations/001_grant_service_role.sql no Supabase.';
      } else if (pgCode === 'PGRST205') {
        hint = ' — Execute backend/supabase/schema.sql no SQL Editor do Supabase.';
      }

      const err = new Error(`Supabase REST ${res.status}: ${detail}${hint}`);
      err.statusCode = 502;
      throw err;
    }
    if (!text) return null;
    return JSON.parse(text);
  }

  async checkHealth() {
    if (this.keyWarning) {
      return { ok: false, error: this.keyWarning };
    }
    try {
      await this.request('/relay_devices?select=device_id&limit=1');
      return { ok: true };
    } catch (err) {
      return { ok: false, error: err?.message || 'Erro ao consultar Supabase' };
    }
  }

  async sweepExpiredPairings(nowMillis = Date.now()) {
    await this.request(`/relay_pairings?expires_at_millis=lt.${Math.floor(nowMillis)}`, {
      method: 'DELETE'
    });
  }

  async createPairing({ deviceId, deviceName, ttlMillis = DEFAULT_PAIRING_TTL_MS, nowMillis = Date.now() }) {
    await this.sweepExpiredPairings(nowMillis);
    const cleanDeviceId = String(deviceId || '').trim();
    const cleanDeviceName = String(deviceName || 'Tablet MonitorDad').trim();
    if (!cleanDeviceId) {
      throw new Error('deviceId is required');
    }

    const claimToken = crypto.randomBytes(24).toString('hex');
    const expiresAtMillis = nowMillis + ttlMillis;

    for (let attempt = 0; attempt < 5; attempt += 1) {
      const code = generatePairingCode(6);
      const existing = await this.request(`/relay_pairings?code=eq.${encodeURIComponent(code)}&select=code`);
      if (Array.isArray(existing) && existing.length > 0) {
        continue;
      }

      await this.request('/relay_pairings', {
        method: 'POST',
        prefer: 'return=minimal',
        body: {
          code,
          device_id: cleanDeviceId,
          device_name: cleanDeviceName,
          claim_token: claimToken,
          created_at_millis: nowMillis,
          expires_at_millis: expiresAtMillis,
          claimed: false,
          parent_id: null,
          shared_secret: null,
          claimed_at_millis: null
        }
      });

      return {
        pairingCode: code,
        claimToken,
        expiresAtMillis,
        deviceId: cleanDeviceId,
        deviceName: cleanDeviceName
      };
    }

    throw new Error('Não foi possível gerar um código de pareamento único.');
  }

  async claimPairing({ pairingCode, parentId, deviceName, nowMillis = Date.now() }) {
    await this.sweepExpiredPairings(nowMillis);
    const normalizedCode = String(pairingCode || '').trim().toUpperCase().replace(/[^A-Z0-9]/g, '');
    const cleanParentId = String(parentId || '').trim();
    if (!normalizedCode) {
      return { error: 'Código de pareamento obrigatório.', status: 400 };
    }
    if (!cleanParentId) {
      return { error: 'parentId obrigatório.', status: 400 };
    }

    const rows = await this.request(`/relay_pairings?code=eq.${encodeURIComponent(normalizedCode)}&select=*`);
    const entry = Array.isArray(rows) && rows.length > 0 ? mapPairingRow(rows[0]) : null;
    if (!entry) {
      return { error: 'Código inválido ou expirado.', status: 404 };
    }
    if (nowMillis > entry.expiresAtMillis) {
      await this.request(`/relay_pairings?code=eq.${encodeURIComponent(normalizedCode)}`, { method: 'DELETE' });
      return { error: 'Código expirado. Gere um novo código no tablet.', status: 410 };
    }
    if (entry.claimed) {
      return { error: 'Este código já foi utilizado.', status: 409 };
    }

    const sharedSecret = crypto.randomBytes(32).toString('hex');
    const finalDeviceName = String(deviceName || '').trim() || entry.deviceName;

    const updatedRows = await this.request(
      `/relay_pairings?code=eq.${encodeURIComponent(normalizedCode)}&claimed=eq.false`,
      {
        method: 'PATCH',
        prefer: 'return=representation',
        body: {
          claimed: true,
          parent_id: cleanParentId,
          device_name: finalDeviceName,
          shared_secret: sharedSecret,
          claimed_at_millis: nowMillis
        }
      }
    );

    if (!Array.isArray(updatedRows) || updatedRows.length === 0) {
      return { error: 'Este código já foi utilizado.', status: 409 };
    }

    const existingDevice = await this.getDevice(entry.deviceId);
    await this.request('/relay_devices?on_conflict=device_id', {
      method: 'POST',
      prefer: 'resolution=merge-duplicates,return=minimal',
      body: {
        device_id: entry.deviceId,
        parent_id: cleanParentId,
        device_name: finalDeviceName,
        shared_secret: sharedSecret,
        paired_at_millis: nowMillis,
        last_heartbeat_parent: existingDevice?.lastHeartbeatParent ?? 0,
        last_heartbeat_kids: existingDevice?.lastHeartbeatKids ?? 0,
        commands: [],
        acks: [],
        rules_envelope: existingDevice?.rulesEnvelope ?? null,
        telemetry: existingDevice?.telemetry ?? null,
        updated_at: new Date(nowMillis).toISOString()
      }
    });

    return {
      status: 200,
      data: {
        deviceId: entry.deviceId,
        parentId: cleanParentId,
        deviceName: finalDeviceName,
        sharedSecret,
        pairedAtMillis: nowMillis
      }
    };
  }

  async completePairing({ pairingCode, claimToken, nowMillis = Date.now() }) {
    await this.sweepExpiredPairings(nowMillis);
    const normalizedCode = String(pairingCode || '').trim().toUpperCase().replace(/[^A-Z0-9]/g, '');
    const token = String(claimToken || '').trim();

    const rows = await this.request(`/relay_pairings?code=eq.${encodeURIComponent(normalizedCode)}&select=*`);
    const entry = Array.isArray(rows) && rows.length > 0 ? mapPairingRow(rows[0]) : null;
    if (!entry) {
      return { error: 'Sessão de pareamento expirada ou já concluída.', status: 404 };
    }
    if (!timingSafeEqualHex(entry.claimToken, token)) {
      return { error: 'claimToken inválido.', status: 403 };
    }
    if (nowMillis > entry.expiresAtMillis) {
      await this.request(`/relay_pairings?code=eq.${encodeURIComponent(normalizedCode)}`, { method: 'DELETE' });
      return { error: 'Código de pareamento expirado.', status: 410 };
    }

    if (!entry.claimed) {
      return {
        status: 200,
        data: {
          status: 'PENDING',
          expiresAtMillis: entry.expiresAtMillis
        }
      };
    }

    const result = {
      status: 'PAIRED',
      deviceId: entry.deviceId,
      parentId: entry.parentId,
      deviceName: entry.deviceName,
      sharedSecret: entry.sharedSecret,
      pairedAtMillis: entry.claimedAtMillis
    };

    await this.request(`/relay_pairings?code=eq.${encodeURIComponent(normalizedCode)}`, {
      method: 'DELETE'
    });

    return { status: 200, data: result };
  }

  async getDevice(deviceId) {
    const cleanDeviceId = String(deviceId || '').trim();
    if (!cleanDeviceId) return null;
    const rows = await this.request(`/relay_devices?device_id=eq.${encodeURIComponent(cleanDeviceId)}&select=*`);
    return Array.isArray(rows) && rows.length > 0 ? mapDeviceRow(rows[0]) : null;
  }

  async patchDevice(deviceId, fields) {
    await this.request(`/relay_devices?device_id=eq.${encodeURIComponent(deviceId)}`, {
      method: 'PATCH',
      prefer: 'return=minimal',
      body: {
        ...fields,
        updated_at: new Date().toISOString()
      }
    });
  }

  async provisionDevice({ deviceId, parentId, deviceName, sharedSecret, nowMillis = Date.now() }) {
    const cleanDeviceId = String(deviceId || '').trim();
    const cleanParentId = String(parentId || '').trim();
    const cleanSecret = String(sharedSecret || '').trim();
    if (!cleanDeviceId || !cleanParentId || cleanSecret.length < 16) {
      return { error: 'deviceId, parentId e sharedSecret válido são obrigatórios.', status: 400 };
    }

    const existing = await this.getDevice(cleanDeviceId);
    if (existing) {
      if (
        existing.parentId !== cleanParentId ||
        !timingSafeEqualHex(existing.sharedSecret, cleanSecret)
      ) {
        return { error: 'Dispositivo já vinculado com outra credencial neste servidor.', status: 403 };
      }
      if (deviceName && String(deviceName).trim()) {
        await this.patchDevice(cleanDeviceId, { device_name: String(deviceName).trim() });
      }
      return {
        status: 200,
        data: { ok: true, status: 'ALREADY_PROVISIONED', deviceId: cleanDeviceId }
      };
    }

    await this.request('/relay_devices', {
      method: 'POST',
      prefer: 'return=minimal',
      body: {
        device_id: cleanDeviceId,
        parent_id: cleanParentId,
        device_name: String(deviceName || 'Tablet MonitorDad').trim(),
        shared_secret: cleanSecret,
        paired_at_millis: nowMillis,
        last_heartbeat_parent: null,
        last_heartbeat_kids: null,
        commands: [],
        acks: [],
        rules_envelope: null,
        telemetry: null,
        updated_at: new Date(nowMillis).toISOString()
      }
    });

    return {
      status: 201,
      data: { ok: true, status: 'PROVISIONED', deviceId: cleanDeviceId }
    };
  }

  async revokeDevice(deviceId) {
    const cleanDeviceId = String(deviceId || '').trim();
    if (!cleanDeviceId) return false;
    const deleted = await this.request(`/relay_devices?device_id=eq.${encodeURIComponent(cleanDeviceId)}`, {
      method: 'DELETE',
      prefer: 'return=representation'
    });
    return Array.isArray(deleted) && deleted.length > 0;
  }

  async recordHeartbeat(deviceId, role, nowMillis = Date.now()) {
    const device = await this.getDevice(deviceId);
    if (!device) return null;
    const patch = role === 'parent'
      ? { last_heartbeat_parent: nowMillis }
      : { last_heartbeat_kids: nowMillis };
    await this.patchDevice(deviceId, patch);
    return nowMillis;
  }

  async upsertCommand(deviceId, command) {
    const device = await this.getDevice(deviceId);
    if (!device) return null;
    const commands = Array.isArray(device.commands) ? [...device.commands] : [];
    const idx = commands.findIndex((c) => c.commandId === command.commandId);
    if (idx >= 0) {
      commands[idx] = { ...commands[idx], ...command };
    } else {
      commands.push(command);
    }
    const trimmed = commands.length > 200 ? commands.slice(-200) : commands;
    await this.patchDevice(deviceId, { commands: trimmed });
    return command;
  }

  async getPendingCommands(deviceId, nowMillis = Date.now()) {
    const device = await this.getDevice(deviceId);
    if (!device) return [];
    const ackedIds = new Set(
      (device.acks || [])
        .filter((a) => a.status === 'EXECUTED' || a.status === 'FAILED' || a.status === 'EXPIRED')
        .map((a) => a.commandId)
    );
    return (device.commands || []).filter((cmd) => {
      if (ackedIds.has(cmd.commandId)) return false;
      if (typeof cmd.expiresAtMillis === 'number' && nowMillis > cmd.expiresAtMillis) return false;
      return true;
    });
  }

  async recordAck(deviceId, ack) {
    const device = await this.getDevice(deviceId);
    if (!device) return null;
    const acks = Array.isArray(device.acks) ? [...device.acks] : [];
    const idx = acks.findIndex((a) => a.commandId === ack.commandId);
    if (idx >= 0) {
      acks[idx] = ack;
    } else {
      acks.push(ack);
    }
    const trimmedAcks = acks.length > 200 ? acks.slice(-200) : acks;

    const commands = Array.isArray(device.commands) ? [...device.commands] : [];
    const cmdIdx = commands.findIndex((c) => c.commandId === ack.commandId);
    if (cmdIdx >= 0) {
      commands[cmdIdx] = { ...commands[cmdIdx], status: ack.status };
    }

    await this.patchDevice(deviceId, { acks: trimmedAcks, commands });
    return ack;
  }

  async getAcks(deviceId) {
    const device = await this.getDevice(deviceId);
    return device?.acks || [];
  }

  async setRulesEnvelope(deviceId, envelope) {
    const device = await this.getDevice(deviceId);
    if (!device) return null;
    const currentVersion = Number(device.rulesEnvelope?.version || 0);
    const nextVersion = Number(envelope?.version || 0);
    if (!device.rulesEnvelope || nextVersion >= currentVersion) {
      await this.patchDevice(deviceId, { rules_envelope: envelope });
      return envelope;
    }
    return device.rulesEnvelope;
  }

  async getRulesEnvelope(deviceId) {
    const device = await this.getDevice(deviceId);
    return device?.rulesEnvelope || null;
  }

  async setTelemetry(deviceId, telemetry) {
    const device = await this.getDevice(deviceId);
    if (!device) return null;
    const currentTs = Number(device.telemetry?.timestamp || 0);
    const nextTs = Number(telemetry?.timestamp || 0);
    if (!device.telemetry || nextTs >= currentTs) {
      await this.patchDevice(deviceId, { telemetry });
      return telemetry;
    }
    return device.telemetry;
  }

  async getTelemetry(deviceId) {
    const device = await this.getDevice(deviceId);
    return device?.telemetry || null;
  }
}
