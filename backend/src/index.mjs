import path from 'node:path';
import { RelayStore } from './store.mjs';
import { SupabaseRelayStore } from './supabaseStore.mjs';
import { createRelayServer } from './server.mjs';

const PORT = Number(process.env.PORT || 8787);
const HOST = process.env.HOST || '0.0.0.0';
const DATA_DIR = process.env.DATA_DIR || path.resolve(process.cwd(), 'data');
const SUPABASE_URL = (process.env.SUPABASE_URL || '').trim();
const SUPABASE_SERVICE_ROLE_KEY = (process.env.SUPABASE_SERVICE_ROLE_KEY || '').trim();

let store;
if (SUPABASE_URL && SUPABASE_SERVICE_ROLE_KEY) {
  store = new SupabaseRelayStore({
    supabaseUrl: SUPABASE_URL,
    serviceRoleKey: SUPABASE_SERVICE_ROLE_KEY
  });
} else {
  if (process.env.RENDER) {
    console.warn(
      '[MonitorDad Relay] AVISO: SUPABASE_URL/SUPABASE_SERVICE_ROLE_KEY não configurados. ' +
      'Usando armazenamento local temporário (dados serão perdidos ao reiniciar no Render Free).'
    );
  }
  store = new RelayStore(DATA_DIR);
}

const server = createRelayServer(store);

server.listen(PORT, HOST, () => {
  const storageLabel = store.storageType || `local-file (${DATA_DIR})`;
  console.log(`[MonitorDad Relay] Listening on http://${HOST}:${PORT} (storage=${storageLabel})`);
});

process.on('SIGTERM', () => {
  server.close(() => process.exit(0));
});
process.on('SIGINT', () => {
  server.close(() => process.exit(0));
});
