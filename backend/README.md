# MonitorDad Relay Backend

Servidor relay HTTPS leve, sem dependências externas de runtime (Node.js 20+), responsável por:
- **Pareamento temporário por código de uso único** (`POST /api/v1/pairing/initiate`, `POST /api/v1/pairing/claim`, `POST /api/v1/pairing/complete`).
- **Provisionamento local → remoto sem re-pareamento** (`POST /api/v1/devices/:id/provision`) e **revogação** (`DELETE /api/v1/devices/:id`).
- **Autenticação HMAC-SHA256** (`Authorization: Bearer <hmac>`, `X-MonitorDad-Device`, `X-MonitorDad-Parent`).
- **Fila de comandos remotos e confirmações (ACK)** (`commands`, `commands/pending`, `commands/:id/ack`, `acks`).
- **Sincronização de regras e telemetria real** (`PUT/GET /rules`, `PUT/GET /telemetry`, `POST /heartbeat`).
- **Persistência**:
  - **Produção (Render Free + Supabase Free)**: PostgreSQL persistente via Supabase REST API (`SUPABASE_URL` + `SUPABASE_SERVICE_ROLE_KEY`).
  - **Desenvolvimento / testes locais**: arquivo atômico em disco (`DATA_DIR/relay-state.json`).

## Executar localmente

```bash
cd backend
npm test
PORT=8787 DATA_DIR=./data npm start
```

Para testar localmente conectado ao Supabase, copie `.env.example` para `.env`, preencha suas variáveis e execute com `--env-file=.env`:

```bash
node --env-file=.env src/index.mjs
```

## Hospedagem 100% gratuita com HTTPS (Render Free + Supabase Free)

Veja o passo a passo completo em [`documentation/REMOTE_DEPLOY_GUIDE.md`](../documentation/REMOTE_DEPLOY_GUIDE.md):
1. Crie um projeto gratuito no **Supabase** e execute `backend/supabase/schema.sql` no SQL Editor.
2. Publique o serviço gratuito no **Render** usando `backend/render.yaml` e configure `SUPABASE_URL` e `SUPABASE_SERVICE_ROLE_KEY` nas variáveis de ambiente.
3. Confirme em `https://<sua-url>.onrender.com/health` que `"storage": "supabase-postgres"`.
4. **Nenhuma chave secreta fica no código-fonte**: os segredos HMAC dos dispositivos são gerados dinamicamente e protegidos no `AndroidKeyStore`.
