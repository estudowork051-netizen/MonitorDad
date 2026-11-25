import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const PAIRING_ALPHABET = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
export const DEFAULT_PAIRING_TTL_MS = 10 * 60 * 1000; // 10 minutes

export function generatePairingCode(length = 6) {
  const bytes = crypto.randomBytes(length);
  let code = '';
  for (let i = 0; i < length; i += 1) {
    code += PAIRING_ALPHABET[bytes[i] % PAIRING_ALPHABET.length];
  }
  return code;
}

export function hmacSha256Hex(message, secret) {
  return crypto.createHmac('sha256', secret).update(message, 'utf8').digest('hex');
}

export function timingSafeEqualHex(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  if (a.length !== b.length || a.length === 0) return false;
  const bufA = Buffer.from(a, 'utf8');
  const bufB = Buffer.from(b, 'utf8');
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

export class RelayStore {
  constructor(dataDir) {
    this.dataDir = dataDir;
    this.stateFile = path.join(dataDir, 'relay-state.json');
    this.state = {
      pairings: {},
      devices: {}
    };
    this.load();
  }

  load() {
    fs.mkdirSync(this.dataDir, { recursive: true });
    if (!fs.existsSync(this.stateFile)) {
      this.save();
      return;
    }
    try {
      const raw = fs.readFileSync(this.stateFile, 'utf8');
      const parsed = JSON.parse(raw);
      this.state = {
        pairings: parsed?.pairings && typeof parsed.pairings === 'object' ? parsed.pairings : {},
        devices: parsed?.devices && typeof parsed.devices === 'object' ? parsed.devices : {}
      };
    } catch {
      this.state = { pairings: {}, devices: {} };
    }
  }

  save() {
    fs.mkdirSync(this.dataDir, { recursive: true });
    const tmpFile = `${this.stateFile}.tmp.${process.pid}`;
    fs.writeFileSync(tmpFile, JSON.stringify(this.state, null, 2), { encoding: 'utf8', mode: 0o600 });
    fs.renameSync(tmpFile, this.stateFile);
  }

  sweepExpiredPairings(nowMillis = Date.now()) {
    let changed = false;
    for (const [code, entry] of Object.entries(this.state.pairings)) {
      if (!entry || nowMillis > entry.expiresAtMillis) {
        delete this.state.pairings[code];
        changed = true;
      }
    }
    if (changed) this.save();
  }

  createPairing({ deviceId, deviceName, ttlMillis = DEFAULT_PAIRING_TTL_MS, nowMillis = Date.now() }) {
    this.sweepExpiredPairings(nowMillis);
    const cleanDeviceId = String(deviceId || '').trim();
    const cleanDeviceName = String(deviceName || 'Tablet MonitorDad').trim();
    if (!cleanDeviceId) {
      throw new Error('deviceId is required');
    }

    let code = generatePairingCode(6);
    while (this.state.pairings[code]) {
      code = generatePairingCode(6);
    }

    const claimToken = crypto.randomBytes(24).toString('hex');
    const expiresAtMillis = nowMillis + ttlMillis;

    this.state.pairings[code] = {
      code,
      deviceId: cleanDeviceId,
      deviceName: cleanDeviceName,
      claimToken,
      createdAtMillis: nowMillis,
      expiresAtMillis,
      claimed: false,
      parentId: null,
      sharedSecret: null,
      claimedAtMillis: null
    };
    this.save();

    return {
      pairingCode: code,
      claimToken,
      expiresAtMillis,
      deviceId: cleanDeviceId,
      deviceName: cleanDeviceName
    };
  }

  claimPairing({ pairingCode, parentId, deviceName, nowMillis = Date.now() }) {
    this.sweepExpiredPairings(nowMillis);
    const normalizedCode = String(pairingCode || '').trim().toUpperCase().replace(/[^A-Z0-9]/g, '');
    const cleanParentId = String(parentId || '').trim();
    if (!normalizedCode) {
      return { error: 'Código de pareamento obrigatório.', status: 400 };
    }
    if (!cleanParentId) {
      return { error: 'parentId obrigatório.', status: 400 };
    }

    const entry = this.state.pairings[normalizedCode];
    if (!entry) {
      return { error: 'Código inválido ou expirado.', status: 404 };
    }
    if (nowMillis > entry.expiresAtMillis) {
      delete this.state.pairings[normalizedCode];
      this.save();
      return { error: 'Código expirado. Gere um novo código no tablet.', status: 410 };
    }
    if (entry.claimed) {
      return { error: 'Este código já foi utilizado.', status: 409 };
    }

    const sharedSecret = crypto.randomBytes(32).toString('hex');
    const finalDeviceName = String(deviceName || '').trim() || entry.deviceName;

    entry.claimed = true;
    entry.parentId = cleanParentId;
    entry.deviceName = finalDeviceName;
    entry.sharedSecret = sharedSecret;
    entry.claimedAtMillis = nowMillis;

    const existingDevice = this.state.devices[entry.deviceId] || {};
    this.state.devices[entry.deviceId] = {
      deviceId: entry.deviceId,
      parentId: cleanParentId,
      deviceName: finalDeviceName,
      sharedSecret,
      pairedAtMillis: nowMillis,
      lastHeartbeatParent: existingDevice.lastHeartbeatParent || 0,
      lastHeartbeatKids: existingDevice.lastHeartbeatKids || 0,
      commands: [],
      acks: [],
      rulesEnvelope: existingDevice.rulesEnvelope || null,
      telemetry: existingDevice.telemetry || null
    };

    this.save();

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

  completePairing({ pairingCode, claimToken, nowMillis = Date.now() }) {
    this.sweepExpiredPairings(nowMillis);
    const normalizedCode = String(pairingCode || '').trim().toUpperCase().replace(/[^A-Z0-9]/g, '');
    const token = String(claimToken || '').trim();

    const entry = this.state.pairings[normalizedCode];
    if (!entry) {
      return { error: 'Sessão de pareamento expirada ou já concluída.', status: 404 };
    }
    if (!timingSafeEqualHex(entry.claimToken, token)) {
      return { error: 'claimToken inválido.', status: 403 };
    }
    if (nowMillis > entry.expiresAtMillis) {
      delete this.state.pairings[normalizedCode];
      this.save();
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

    // Single-use: remove pairing record immediately after Kids retrieves the secret
    delete this.state.pairings[normalizedCode];
    this.save();

    return { status: 200, data: result };
  }

  getDevice(deviceId) {
    return this.state.devices[deviceId] || null;
  }

  provisionDevice({ deviceId, parentId, deviceName, sharedSecret, nowMillis = Date.now() }) {
    const cleanDeviceId = String(deviceId || '').trim();
    const cleanParentId = String(parentId || '').trim();
    const cleanSecret = String(sharedSecret || '').trim();
    if (!cleanDeviceId || !cleanParentId || cleanSecret.length < 16) {
      return { error: 'deviceId, parentId e sharedSecret válido são obrigatórios.', status: 400 };
    }

    const existing = this.state.devices[cleanDeviceId];
    if (existing) {
      if (
        existing.parentId !== cleanParentId ||
        !timingSafeEqualHex(existing.sharedSecret, cleanSecret)
      ) {
        return { error: 'Dispositivo já vinculado com outra credencial neste servidor.', status: 403 };
      }
      if (deviceName && String(deviceName).trim()) {
        existing.deviceName = String(deviceName).trim();
        this.save();
      }
      return {
        status: 200,
        data: { ok: true, status: 'ALREADY_PROVISIONED', deviceId: cleanDeviceId }
      };
    }

    this.state.devices[cleanDeviceId] = {
      deviceId: cleanDeviceId,
      parentId: cleanParentId,
      deviceName: String(deviceName || 'Tablet MonitorDad').trim(),
      sharedSecret: cleanSecret,
      pairedAtMillis: nowMillis,
      lastHeartbeatParent: null,
      lastHeartbeatKids: null,
      commands: [],
      acks: [],
      rulesEnvelope: null,
      telemetry: null
    };
    this.save();
    return {
      status: 201,
      data: { ok: true, status: 'PROVISIONED', deviceId: cleanDeviceId }
    };
  }

  revokeDevice(deviceId) {
    const cleanDeviceId = String(deviceId || '').trim();
    if (!cleanDeviceId || !this.state.devices[cleanDeviceId]) {
      return false;
    }
    delete this.state.devices[cleanDeviceId];
    this.save();
    return true;
  }

  recordHeartbeat(deviceId, role, nowMillis = Date.now()) {
    const device = this.state.devices[deviceId];
    if (!device) return null;
    if (role === 'parent') {
      device.lastHeartbeatParent = nowMillis;
    } else {
      device.lastHeartbeatKids = nowMillis;
    }
    this.save();
    return nowMillis;
  }

  upsertCommand(deviceId, command) {
    const device = this.state.devices[deviceId];
    if (!device) return null;
    if (!Array.isArray(device.commands)) device.commands = [];
    const idx = device.commands.findIndex((c) => c.commandId === command.commandId);
    if (idx >= 0) {
      device.commands[idx] = { ...device.commands[idx], ...command };
    } else {
      device.commands.push(command);
      if (device.commands.length > 200) {
        device.commands = device.commands.slice(-200);
      }
    }
    this.save();
    return command;
  }

  getPendingCommands(deviceId, nowMillis = Date.now()) {
    const device = this.state.devices[deviceId];
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

  recordAck(deviceId, ack) {
    const device = this.state.devices[deviceId];
    if (!device) return null;
    if (!Array.isArray(device.acks)) device.acks = [];
    const idx = device.acks.findIndex((a) => a.commandId === ack.commandId);
    if (idx >= 0) {
      device.acks[idx] = ack;
    } else {
      device.acks.push(ack);
      if (device.acks.length > 200) {
        device.acks = device.acks.slice(-200);
      }
    }

    const cmdIdx = (device.commands || []).findIndex((c) => c.commandId === ack.commandId);
    if (cmdIdx >= 0) {
      device.commands[cmdIdx].status = ack.status;
    }

    this.save();
    return ack;
  }

  getAcks(deviceId) {
    const device = this.state.devices[deviceId];
    return device?.acks || [];
  }

  setRulesEnvelope(deviceId, envelope) {
    const device = this.state.devices[deviceId];
    if (!device) return null;
    const currentVersion = Number(device.rulesEnvelope?.version || 0);
    const nextVersion = Number(envelope?.version || 0);
    if (!device.rulesEnvelope || nextVersion >= currentVersion) {
      device.rulesEnvelope = envelope;
      this.save();
    }
    return device.rulesEnvelope;
  }

  getRulesEnvelope(deviceId) {
    return this.state.devices[deviceId]?.rulesEnvelope || null;
  }

  setTelemetry(deviceId, telemetry) {
    const device = this.state.devices[deviceId];
    if (!device) return null;
    const currentTs = Number(device.telemetry?.timestamp || 0);
    const nextTs = Number(telemetry?.timestamp || 0);
    if (!device.telemetry || nextTs >= currentTs) {
      device.telemetry = telemetry;
      this.save();
    }
    return device.telemetry;
  }

  getTelemetry(deviceId) {
    return this.state.devices[deviceId]?.telemetry || null;
  }
}
