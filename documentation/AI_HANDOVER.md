# AI Handover — MonitorDad • by HoD

## Resumo para Continuidade
Foi implementada a arquitetura unificada de pareamento e comunicação **Local (Wi-Fi) + Remota (Internet)** sem mecanismos descartáveis:

1. **Protocolo Único**: `LocalHttpTransport` (LAN Wi-Fi) e `HttpsRemoteTransport` (Internet) implementam `RemoteTransport` e usam exatamente as mesmas rotas `/api/v1/devices/{deviceId}/...`, payloads `RemoteJsonCodec` e assinatura `RemoteSecurity` (`signRequest`, `verifyRequestSignature`, `isTimestampFresh` com HMAC-SHA256 + SHA-256 do corpo + timestamp + nonce).
2. **Tema Sage Green & Nature (`Color.kt`)**: Tokens semânticos (`DoHBorderLight`, `DoHSageSurface`, `DoHTextMuted`, `DoHStatusOnline`, `DoHStatusWarning`, `DoHStatusOffline`) centralizados em `shared/src/main/java/com/dohguard/shared/theme/Color.kt`.
2. **Orquestração (`ConnectionManager`)**:
   - Modo `AUTO`: prefere `LOCAL_WIFI` (descoberto via `AndroidNsdDiscovery` `_monitordad._tcp.`) e faz failover automático para `REMOTE_INTERNET` se configurado.
   - Modos `LOCAL_ONLY` e `REMOTE_ONLY` disponíveis no Hub de Conexão.
   - `runDiagnostics` executa 6 verificações reais (pareamento, descoberta local, `/health` local, `/health` remoto, heartbeat assinado, fila pendente).
3. **Pareamento Seguro (`PairingManager` + `LocalPairingServer` + `QrCodeGenerator`)**:
   - Parent gera código temporário (6 chars, 10 min, uso único, bloqueio após 5 erros) e QR Code (`monitordad://pair?...`) renderizado via Compose `Canvas` sem dependências externas.
   - Kids descobre `_monitordad-pair._tcp.` via `NsdManager` (ou usa host/port do QR) e conclui o pareamento em `/api/v1/local-pairing/claim`.
   - `backend/src/server.mjs` também aplica rate limit anti-força bruta (5 falhas -> HTTP 429) em `/api/v1/pairing/claim`.

4. **Continuidade Local → Remota & Revogação**:
   - Dispositivos pareados localmente no Wi-Fi se auto-provisionam no relay HTTPS (`POST /api/v1/devices/:deviceId/provision`) via `RemoteControlRepository.ensureRelayProvisionedIfNeeded()` assim que uma URL de relay válida é configurada — sem exigir novo pareamento e com verificação HMAC anti-sequestro (HTTP 403).
   - `ParentApp/MainActivity.kt` trata `onServiceLost` -> `RemoteControlRepository.handleLocalServiceLost()` para failover imediato para a internet ao sair do Wi-Fi, e `onServiceResolved` retorna automaticamente para `LOCAL_WIFI`.
   - `unpairAndRevokeDevice` também dispara `DELETE /api/v1/devices/:deviceId` no relay.
5. **Persistência Gratuita em Produção (`backend/src/supabaseStore.mjs`, `backend/supabase/schema.sql`)**:
   - Quando `SUPABASE_URL` e `SUPABASE_SERVICE_ROLE_KEY` estão configuradas (Render Free), `backend/src/index.mjs` usa `SupabaseRelayStore` (PostgreSQL via REST API HTTPS, sem dependências npm externas e com RLS habilitado). Sem essas variáveis, faz fallback automático para `RelayStore` em disco para desenvolvimento/testes locais.
   - Guia de deploy gratuito passo a passo em `documentation/REMOTE_DEPLOY_GUIDE.md`.
6. **Correção `v1.3.2` (`RemoteControlRepository.kt`)**:
   - Restauradas as definições de `ensureRelayProvisionedIfNeeded()` e `handleLocalServiceLost()` (que haviam ficado ausentes no commit `13e6842`) e adicionada revogação remota em `unpairAndRevokeDevice()`.

## Regras Críticas do Repositório
- **Pureza JVM em `:shared`**: `RemoteModels.kt`, `PairingManager.kt`, `QrCodeGenerator.kt`, `ConnectionManager.kt`, `LocalHttpServer.kt`, `RemoteCommandEngine.kt` e os testes em `shared/src/test/` **não devem** importar `android.*` ou `org.json.*`.
- **Sem dependências Maven/npm externas**: não adicionar bibliotecas no `libs.versions.toml` nem no `backend/package.json` sem verificar acesso de rede.
- **Próximo passo operacional**: executar `git push origin master` para rodar o workflow `.github/workflows/android-ci.yml` no GitHub Actions, criar o banco gratuito no Supabase (`backend/supabase/schema.sql`), publicar `backend/` no Render Free (`backend/render.yaml`) e validar os 10 cenários em dois dispositivos reais.
