import http from 'node:http';
import { URL } from 'node:url';
import { hmacSha256Hex, timingSafeEqualHex } from './store.mjs';

const MAX_BODY_BYTES = 256 * 1024;

function sendJson(res, statusCode, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    'X-Content-Type-Options': 'nosniff'
  });
  res.end(body);
}

async function readJsonBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let totalBytes = 0;

    req.on('data', (chunk) => {
      totalBytes += chunk.length;
      if (totalBytes > MAX_BODY_BYTES) {
        reject(Object.assign(new Error('Payload too large'), { statusCode: 413 }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });

    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8').trim();
      if (!raw) {
        resolve({});
        return;
      }
      try {
        resolve(JSON.parse(raw));
      } catch {
        reject(Object.assign(new Error('Invalid JSON body'), { statusCode: 400 }));
      }
    });

    req.on('error', (err) => reject(err));
  });
}

async function authenticateDeviceRequest(req, store, deviceId) {
  const device = await store.getDevice(deviceId);
  if (!device) {
    return { ok: false, status: 404, error: 'Dispositivo não pareado neste servidor.' };
  }

  const headerDevice = String(req.headers['x-monitordad-device'] || '').trim();
  const headerParent = String(req.headers['x-monitordad-parent'] || '').trim();
  const authHeader = String(req.headers['authorization'] || '').trim();

  if (headerDevice !== deviceId || headerParent !== device.parentId) {
    return { ok: false, status: 403, error: 'Cabeçalhos de identificação inválidos.' };
  }

  if (!authHeader.startsWith('Bearer ')) {
    return { ok: false, status: 401, error: 'Authorization Bearer obrigatório.' };
  }

  const token = authHeader.slice('Bearer '.length).trim();
  const expected = hmacSha256Hex(`${deviceId}:${device.parentId}`, device.sharedSecret);
  if (!timingSafeEqualHex(token, expected)) {
    return { ok: false, status: 401, error: 'Assinatura HMAC inválida.' };
  }

  return { ok: true, device };
}

export function createRelayServer(store, options = {}) {
  const startedAt = Date.now();
  const pairingTtlMs = options.pairingTtlMs;
  const maxClaimFailures = options.maxClaimFailures ?? 5;
  const claimWindowMs = options.claimWindowMs ?? 5 * 60 * 1000;
  const claimFailuresByIp = new Map();

  function getClientKey(req) {
    const fwd = req.headers['x-forwarded-for'];
    if (typeof fwd === 'string' && fwd.trim()) {
      return fwd.split(',')[0].trim();
    }
    return req.socket?.remoteAddress || 'unknown';
  }

  function isClaimRateLimited(ip, now = Date.now()) {
    const entry = claimFailuresByIp.get(ip);
    if (!entry) return false;
    if (now - entry.windowStart > claimWindowMs) {
      claimFailuresByIp.delete(ip);
      return false;
    }
    return entry.count >= maxClaimFailures;
  }

  function recordClaimFailure(ip, now = Date.now()) {
    const entry = claimFailuresByIp.get(ip);
    if (!entry || now - entry.windowStart > claimWindowMs) {
      claimFailuresByIp.set(ip, { count: 1, windowStart: now });
    } else {
      entry.count += 1;
    }
  }

  return http.createServer(async (req, res) => {
    try {
      const parsedUrl = new URL(req.url || '/', 'http://localhost');
      const pathname = parsedUrl.pathname;
      const method = (req.method || 'GET').toUpperCase();

      if (method === 'GET' && pathname === '/health') {
        const dbHealth = typeof store.checkHealth === 'function'
          ? await store.checkHealth()
          : { ok: true };
        return sendJson(res, 200, {
          status: 'ok',
          service: 'monitordad-relay',
          storage: store.storageType || 'local-file',
          databaseConnected: dbHealth.ok,
          ...(dbHealth.ok ? {} : { databaseError: dbHealth.error }),
          uptimeSeconds: Math.floor((Date.now() - startedAt) / 1000)
        });
      }

      if (method === 'POST' && pathname === '/api/v1/pairing/initiate') {
        const body = await readJsonBody(req);
        if (!body.deviceId || !String(body.deviceId).trim()) {
          return sendJson(res, 400, { error: 'deviceId é obrigatório.' });
        }
        const created = await store.createPairing({
          deviceId: body.deviceId,
          deviceName: body.deviceName || 'Tablet MonitorDad',
          ttlMillis: pairingTtlMs
        });
        return sendJson(res, 201, created);
      }

      if (method === 'POST' && pathname === '/api/v1/pairing/claim') {
        const clientIp = getClientKey(req);
        if (isClaimRateLimited(clientIp)) {
          return sendJson(res, 429, {
            error: 'Muitas tentativas incorretas de pareamento. Aguarde alguns minutos.'
          });
        }
        const body = await readJsonBody(req);
        const claimed = await store.claimPairing({
          pairingCode: body.pairingCode,
          parentId: body.parentId,
          deviceName: body.deviceName
        });
        if (claimed.error) {
          recordClaimFailure(clientIp);
          return sendJson(res, claimed.status, { error: claimed.error });
        }
        claimFailuresByIp.delete(clientIp);
        return sendJson(res, 200, claimed.data);
      }

      if (method === 'POST' && pathname === '/api/v1/pairing/complete') {
        const body = await readJsonBody(req);
        const completed = await store.completePairing({
          pairingCode: body.pairingCode,
          claimToken: body.claimToken
        });
        if (completed.error) {
          return sendJson(res, completed.status, { error: completed.error });
        }
        return sendJson(res, 200, completed.data);
      }

      const provisionMatch = pathname.match(/^\/api\/v1\/devices\/([^/]+)\/provision$/);
      if (method === 'POST' && provisionMatch) {
        const deviceId = decodeURIComponent(provisionMatch[1]);
        const body = await readJsonBody(req);
        const parentId = req.headers['x-monitordad-parent'] || body.parentId;
        const authHeader = String(req.headers.authorization || '');
        const token = authHeader.startsWith('Bearer ') ? authHeader.slice(7).trim() : '';
        const providedSecret = String(body.sharedSecret || '').trim();
        if (!parentId || !providedSecret || !token) {
          return sendJson(res, 401, { error: 'Cabeçalhos de autenticação e sharedSecret são obrigatórios.' });
        }
        const expectedDigest = hmacSha256Hex(`${deviceId}:${parentId}`, providedSecret);
        if (!timingSafeEqualHex(token, expectedDigest)) {
          return sendJson(res, 401, { error: 'Assinatura HMAC inválida para provisionamento.' });
        }
        const provisioned = await store.provisionDevice({
          deviceId,
          parentId,
          deviceName: body.deviceName,
          sharedSecret: providedSecret
        });
        if (provisioned.error) {
          return sendJson(res, provisioned.status, { error: provisioned.error });
        }
        return sendJson(res, provisioned.status, provisioned.data);
      }

      const deviceRootMatch = pathname.match(/^\/api\/v1\/devices\/([^/]+)$/);
      if (method === 'DELETE' && deviceRootMatch) {
        const deviceId = decodeURIComponent(deviceRootMatch[1]);
        const auth = await authenticateDeviceRequest(req, store, deviceId);
        if (!auth.ok) {
          return sendJson(res, auth.status, { error: auth.error });
        }
        await store.revokeDevice(deviceId);
        return sendJson(res, 200, { ok: true, revoked: true, deviceId });
      }

      const deviceMatch = pathname.match(/^\/api\/v1\/devices\/([^/]+)(\/.*)$/);
      if (deviceMatch) {
        const deviceId = decodeURIComponent(deviceMatch[1]);
        const subPath = deviceMatch[2];
        const auth = await authenticateDeviceRequest(req, store, deviceId);
        if (!auth.ok) {
          return sendJson(res, auth.status, { error: auth.error });
        }

        if (method === 'POST' && subPath === '/heartbeat') {
          const body = await readJsonBody(req);
          const role = body.role === 'parent' ? 'parent' : 'kids';
          const serverTimeMillis = await store.recordHeartbeat(deviceId, role);
          return sendJson(res, 200, { ok: true, serverTimeMillis });
        }

        if (method === 'POST' && subPath === '/commands') {
          const cmd = await readJsonBody(req);
          if (!cmd.commandId || !cmd.type) {
            return sendJson(res, 400, { error: 'Comando inválido: commandId e type são obrigatórios.' });
          }
          if (cmd.signature) {
            const canonical = `${cmd.commandId}:${cmd.targetDeviceId}:${cmd.type}:${cmd.sequenceNumber}:${cmd.nonce}:${cmd.expiresAtMillis}`;
            const expectedSig = hmacSha256Hex(canonical, auth.device.sharedSecret);
            if (!timingSafeEqualHex(cmd.signature, expectedSig)) {
              return sendJson(res, 403, { error: 'Assinatura do comando inválida.' });
            }
          }
          await store.upsertCommand(deviceId, cmd);
          return sendJson(res, 201, { ok: true, commandId: cmd.commandId });
        }

        if (method === 'GET' && subPath === '/commands/pending') {
          const commands = await store.getPendingCommands(deviceId);
          return sendJson(res, 200, { commands });
        }

        const ackMatch = subPath.match(/^\/commands\/([^/]+)\/ack$/);
        if (method === 'POST' && ackMatch) {
          const commandId = decodeURIComponent(ackMatch[1]);
          const ack = await readJsonBody(req);
          if (!ack.status) {
            return sendJson(res, 400, { error: 'Status do ACK é obrigatório.' });
          }
          await store.recordAck(deviceId, { ...ack, commandId });
          return sendJson(res, 200, { ok: true, commandId });
        }

        if (method === 'GET' && subPath === '/acks') {
          return sendJson(res, 200, { acks: await store.getAcks(deviceId) });
        }

        if (method === 'PUT' && subPath === '/rules') {
          const envelope = await readJsonBody(req);
          await store.setRulesEnvelope(deviceId, envelope);
          return sendJson(res, 200, { ok: true });
        }

        if (method === 'GET' && subPath === '/rules') {
          const envelope = await store.getRulesEnvelope(deviceId);
          return sendJson(res, 200, envelope || {});
        }

        if (method === 'PUT' && subPath === '/telemetry') {
          const telemetry = await readJsonBody(req);
          await store.setTelemetry(deviceId, telemetry);
          return sendJson(res, 200, { ok: true });
        }

        if (method === 'GET' && subPath === '/telemetry') {
          const telemetry = await store.getTelemetry(deviceId);
          return sendJson(res, 200, telemetry || {});
        }
      }

      return sendJson(res, 404, { error: 'Endpoint não encontrado.' });
    } catch (err) {
      const status = err?.statusCode || 500;
      if (status >= 500) {
        console.error(`[MonitorDad Relay] ${req.method} ${req.url?.split('?')[0]} -> HTTP ${status}: ${err?.message || 'Erro interno'}`);
      }
      return sendJson(res, status, { error: err?.message || 'Erro interno do servidor.' });
    }
  });
}
