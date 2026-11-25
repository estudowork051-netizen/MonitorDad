# CHANGELOG — MonitorDad • by HoD

Todas as alterações notáveis neste projeto serão documentadas neste arquivo, seguindo as diretrizes do [Keep a Changelog](https://keepachangelog.com/).

---

## [1.4.1] — 2026-11-25 (Correção de compilação em `KidsApp/.../HomeScreen.kt:342`)

### Corrigido
- **`KidsApp/src/main/java/com/dohguard/kids/screens/HomeScreen.kt` (linha 342)**:
  - Substituídas as referências inexistentes `routine.startHour`, `routine.startMinute`, `routine.endHour` e `routine.endMinute` pelas propriedades reais `routine.startTime` e `routine.endTime` (`String` no formato `"HH:mm"`) definidas em `com.dohguard.shared.model.RoutineRule` (`shared/src/main/java/com/dohguard/shared/model/ControlModels.kt`).
  - Preservada integralmente a exibição da seção *"Sua Rotina Configurada"*, os horários configurados pelos responsáveis, a sincronização ParentApp/KidsApp e o cronômetro em tempo real.

---

## [1.4.0] — 2026-11-23 (EM USO AGORA + Cronômetro Kids em Tempo Real + Identidade Visual Roxa/Verde Neon)

### Adicionado / Corrigido
- **ParentApp — Seção "EM USO AGORA" (`UsageStatsCollector.kt`, `UsageSummary.kt`, `RemoteJsonCodec.kt`, `CurrentAppCard.kt`, `OverviewScreen.kt`)**:
  - `UsageStatsCollector.getCurrentForegroundAppSession()` identifica o aplicativo realmente em primeiro plano e o horário de início da sessão via `UsageEvents` (ignorando `com.android.systemui`).
  - `TelemetryPayload` e `RemoteJsonCodec` transmitem `currentForegroundPackage`, `currentForegroundAppName` e `currentForegroundStartedAtMillis` mantendo compatibilidade retroativa.
  - Novo componente `CurrentAppCard` no dashboard do `ParentApp` exibindo ícone original (`AppIconImage`), nome, `"Desde HH:mm"`, nome do dispositivo, última atualização e estado de conexão — ou `"Informação aguardando atualização"` quando indisponível/desatualizado/demo.
- **KidsApp — Cronômetro em Tempo Real (`KidsScreenTimeController.kt`, `KidsApp/MainActivity.kt`, `ScreenTimeState.kt`)**:
  - Controlador monotônico baseado em `SystemClock.elapsedRealtime()` e persistido por dia em `SharedPreferences`, garantindo atualização contínua (`01h 15min` → `01h 14min`) sem reiniciar em recomposições, navegação ou alterações manuais do relógio, com bloqueio automático ao atingir zero.
- **Identidade Visual Roxa & Verde Neon (`Color.kt`, `Theme.kt`, `DoHButton.kt`, `StatusBadge.kt`, `WaveScreenTimeMeter.kt`, `DoHHeader.kt`, `ParentBottomBar.kt`, `HomeScreen.kt`, `LockScreen.kt`)**:
  - Paleta oficial aplicada (`#1A0E2E`, `#2B164B`, `#351C5B`, `#6B42B0`, `#9EFF00`, `#00E5FF`, `#FF3B5C`, `#FFFFFF`, `#B9A8D6`) preservando todos os tokens semânticos existentes.
  - `HomeScreen.kt` (Kids) reformulada com identidade infantil própria, barra de progresso de tempo restante, seção de rotina configurada real e grade de aplicativos (`LIBERADO` / `BLOQUEADO`).
  - **Preservação integral dos 4 assets obrigatórios**: ícone do launcher, splash do ParentApp, splash do KidsApp e todos os 3 botões/textos/ações da tela `LockScreen.kt`.

---

## [1.3.4] — 2026-11-23 (Correção `42501 permission denied` no Supabase + Migração Segura)

### Corrigido
- **`backend/supabase/schema.sql` & `backend/supabase/migrations/001_grant_service_role.sql`**:
  - Adicionado `REVOKE ALL` para `PUBLIC, anon, authenticated` e `GRANT USAGE ON SCHEMA public` + `GRANT SELECT, INSERT, UPDATE, DELETE` exclusivamente para `service_role` nas tabelas `relay_pairings` e `relay_devices`, mantendo RLS habilitado e preservando todos os dados existentes.
- **`backend/src/supabaseStore.mjs` & `backend/src/server.mjs`**:
  - Detecção segura de chave pública (`sb_publishable_` ou JWT com `role !== 'service_role'`) sem expor tokens nos logs.
  - Mensagens de diagnóstico claras para erros `42501` / `401` / `PGRST205` e log seguro no servidor para erros `>= 500`.
- **`backend/test/relay.test.mjs`**:
  - Testes adicionais para rejeição de chave `anon`/`sb_publishable_` e orientação automática de migração para erro `42501` (**9/9 PASS**).

---

## [1.3.3] — 2026-11-23 (Correção HTTP 502 no Relay Supabase + Diagnóstico `/health` e `HttpsRemoteTransport`)

### Corrigido
- **`backend/src/supabaseStore.mjs`**:
  - Corrigido suporte às chaves modernas do Supabase (`sb_secret_...`), enviando `Authorization: Bearer` apenas quando a chave for um JWT legado de 3 partes (`service_role`), evitando rejeição 401 do PostgREST convertida em HTTP 502.
  - Limpeza automática de aspas acidentais ou sufixo `/rest/v1` em `SUPABASE_URL` e `SUPABASE_SERVICE_ROLE_KEY`.
  - Adicionado `checkHealth()` e detalhamento seguro dos erros PostgREST (`code` + `message`).
- **`backend/src/server.mjs`**:
  - Endpoint `GET /health` agora inclui `databaseConnected` e `databaseError` (quando houver falha) para diagnóstico imediato da conexão e do schema no Supabase.
- **`shared/src/main/java/com/dohguard/shared/remote/HttpsRemoteTransport.kt`**:
  - Mensagens de falha HTTP não-2xx agora exibem o campo `error` retornado pelo servidor (sanitizado por `RemoteSecurity.redactSensitive`).
- **`backend/test/relay.test.mjs`**:
  - Adicionado teste para chaves `sb_secret_...` e diagnóstico `/health` (**8/8 PASS**).

---

## [1.3.2] — 2026-11-23 (Correção de Build `:shared:compileDebugKotlin` — `ensureRelayProvisionedIfNeeded` & `handleLocalServiceLost`)

### Corrigido
- **`RemoteControlRepository.kt` (`:shared`)**:
  - Implementado `private suspend fun ensureRelayProvisionedIfNeeded()`, resolvendo os erros `Unresolved reference 'ensureRelayProvisionedIfNeeded'` nas linhas 267, 382 e 424. A função verifica se há pareamento válido e URL HTTPS configurada, evita provisionamentos duplicados via `provisionedRelayKey`, delega para `connectionManager.ensureRemoteProvisioned(...)` com HMAC-SHA256 e trata falhas de rede sem interromper a conexão local.
  - Implementado `fun handleLocalServiceLost()` (chamado por `ParentApp/MainActivity.kt:46`), marcando o endpoint local descoberto como inacessível para failover imediato à internet sem fallback para IP obsoleto.
  - Atualizado `unpairAndRevokeDevice()` para limpar `provisionedRelayKey` e disparar `connectionManager.revokeDeviceOnRelay(...)` ao desvincular.

---

## [1.3.1] — 2026-11-23 (Persistência Gratuita em Produção: Render Free + Supabase Free)

### Adicionado
- **Persistência em Banco de Dados Gratuito (`backend/src/supabaseStore.mjs`, `backend/supabase/schema.sql`)**:
  - Implementado `SupabaseRelayStore` usando a API REST HTTPS (PostgREST) do **Supabase Free** com `fetch` nativo do Node.js 22 (zero dependências npm externas, 100% compatível com Render Free sem cartão de crédito).
  - Criado `backend/supabase/schema.sql` com as tabelas `relay_pairings` e `relay_devices` e **Row Level Security (RLS)** habilitada sem políticas públicas (acesso restrito à chave `service_role` no servidor).
  - Atualizado `backend/src/server.mjs` para suportar operações assíncronas (`await`) em qualquer store e expor `"storage": "supabase-postgres" | "local-file"` no endpoint `GET /health`.
  - Atualizado `backend/src/index.mjs` para selecionar `SupabaseRelayStore` automaticamente quando `SUPABASE_URL` e `SUPABASE_SERVICE_ROLE_KEY` estiverem configuradas, mantendo fallback para `RelayStore` em testes locais.
- **Configuração e Guia de Deploy Gratuito**:
  - Atualizado `backend/render.yaml` (Render Free sem `/tmp` como armazenamento principal), criado `backend/.env.example` e `documentation/REMOTE_DEPLOY_GUIDE.md`.
- **Testes**:
  - Adicionado teste de integração ponta a ponta em `backend/test/relay.test.mjs` simulando reinicialização/hibernação do Render Free com `SupabaseRelayStore` (**7/7 PASS**).

### Aprimorado
- **`HttpsRemoteTransport.kt` (`:shared`)**: Timeouts padrão ajustados para `connectTimeoutMs = 15_000` e `readTimeoutMs = 35_000` para tolerar o despertar (cold start) do plano gratuito do Render sem falhas falsas.

---

## [1.3.0] — 2026-11-23 (Comunicação Remota pela Internet + Transição Híbrida sem Re-pareamento)

### Adicionado
- **Provisionamento Local → Remoto (`backend/` & `:shared`)**:
  - `POST /api/v1/devices/:deviceId/provision` em `backend/src/server.mjs` e `RelayStore.provisionDevice` em `backend/src/store.mjs`: permite que dispositivos já pareados localmente no Wi-Fi registrem suas credenciais HMAC no relay HTTPS ao configurar a internet, sem exigir novo pareamento e com proteção anti-sequestro (HTTP 403 se o `deviceId` já pertencer a outro segredo).
  - `DELETE /api/v1/devices/:deviceId` e `RelayStore.revokeDevice`: revogação remota do dispositivo no relay ao desvincular no app.
  - `RemoteTransport.provisionPairedDevice` / `revokeDevice`, implementados em `HttpsRemoteTransport` e orquestrados por `ConnectionManager` e `RemoteControlRepository.ensureRelayProvisionedIfNeeded()`.
- **UI ("Estado do servidor")**:
  - `ConnectionHubComponents.kt`: Adicionada linha explícita **"Estado do servidor"** no cartão principal da Central *Conexão e dispositivos*.
- **Testes Automatizados**:
  - `backend/test/relay.test.mjs`: Novo teste para provisionamento local → remoto, bloqueio anti-sequestro (403) e revogação (`DELETE`) — **6/6 PASS**.
  - `UnifiedConnectionArchitectureTest.kt`: Novos testes para retorno automático ao Wi-Fi local, provisionamento sem re-pareamento e ciclo honesto `PENDING -> SENT -> EXECUTED` condicionado a ACK assinado.

### Corrigido / Aprimorado
- **Troca Automática Wi-Fi Local ↔ Internet (`ConnectionManager.kt`, `RemoteControlRepository.kt`, `ParentApp/MainActivity.kt`)**:
  - Tratamento de `onServiceLost` no `AndroidNsdDiscovery` chamando `RemoteControlRepository.handleLocalServiceLost()` para failover imediato para a internet ao sair da rede Wi-Fi, e retorno automático para `LOCAL_WIFI` quando `onServiceResolved` redescobrir o tablet em casa.
  - `ConnectionManager.effectiveLocalEndpoint` agora evita fallback para `credentials.localHost` obsoleto quando o endpoint descoberto foi marcado como inacessível.

---

## [1.2.0-hotfix1] — 2026-11-23 (Correção de Build `:shared:compileDebugKotlin` — `Color.kt` & `RemoteSecurity.signRequest`)

### Corrigido
- **`Color.kt` (`:shared`)**: Adicionados os tokens semânticos da paleta Sage Green & Nature (`DoHBorderLight`, `DoHSageSurface`, `DoHTextMuted`, `DoHStatusOnline`, `DoHStatusWarning`, `DoHStatusOffline`), resolvendo os erros de referência e ambiguidade de `Modifier.border(...)` / `BorderStroke(...)` em `ConnectionHubComponents.kt`, `KidsPairingDialog.kt` e `RemotePairingDialog.kt`.
- **`RemoteSecurity.kt` (`:shared`)**: Implementados `signRequest(...)`, `verifyRequestSignature(...)` (com `MessageDigest.isEqual` em tempo constante e SHA-256 do corpo) e `isTimestampFresh(...)` (janela de 5 minutos), atendendo às chamadas de `LocalHttpTransport.kt` e `LocalKidsServer.kt`.
- **`UnifiedConnectionArchitectureTest.kt` (`:shared/src/test`)**: Adicionado o teste `remoteSecuritySignsVerifiesHttpRequestsAndEnforcesTimestampFreshness`.

---

## [1.2.0] — 2026-11-23 (Pareamento Local + Remoto, QR Code e Central "Conexão e dispositivos")

### Adicionado
- **Arquitetura Unificada de Transporte (`:shared`)**:
  - `ConnectionManager.kt`: Seleção automática entre `LOCAL_WIFI` e `REMOTE_INTERNET` (`AUTO`, `LOCAL_ONLY`, `REMOTE_ONLY`), failover transparente sem duplicar comandos e diagnóstico real em 6 etapas (`runDiagnostics`).
  - `LocalHttpTransport.kt` & `LocalHttpServer.kt`: Transporte HTTP em rede local com HMAC-SHA256 compartilhando o mesmo protocolo do `HttpsRemoteTransport`.
  - `AndroidNsdDiscovery.kt`: Descoberta mDNS automática (`_monitordad._tcp.` e `_monitordad-pair._tcp.`).
  - `PairingManager.kt` & `QrCodeGenerator.kt`: Códigos temporários de uso único (TTL 10 min, bloqueio após 5 falhas), QR Code ISO v1..v8 em Kotlin puro e revogação de dispositivos.
  - `ConnectionSettingsRepository.kt` & `ConnectionHubComponents.kt`: Persistência de preferências, Assistente guiado em 4 passos, Central "Conexão e dispositivos" com indicadores Verde/Amarelo/Vermelho/Cinza e configurações avançadas ocultas por padrão.
- **Pareamento e Servidores Locais (`:ParentApp` e `:KidsApp`)**:
  - `ParentApp`: `LocalPairingServer.kt`, `RemotePairingDialog.kt` (QR Code + código temporário + lista de dispositivos vinculados), `OverviewScreen.kt` e `MainActivity.kt` (descoberta NSD automática).
  - `KidsApp`: `LocalKidsServer.kt` (servidor local HMAC-SHA256 + NSD), `KidsPairingDialog.kt` (descoberta do Parent no Wi-Fi, código/QR, Hub e Assistente) e `MainActivity.kt`.
- **Segurança no Backend (`backend/`)**:
  - Rate limiting anti-força bruta em `POST /api/v1/pairing/claim` (máx. 5 falhas -> HTTP 429) e novo teste automatizado (`5/5 PASS`).
- **Testes Unitários (`:shared`)**:
  - `UnifiedConnectionArchitectureTest.kt`: Testes de failover, modos, pareamento seguro, QR Code e `LocalHttpServer` em loopback.

---

## [1.1.0-rc1-hotfix1] — 2026-11-23 (Correção de Build `:KidsApp:compileDebugKotlin` — `collectUsageTelemetry`)

### Corrigido
- **`UsageStatsCollector.kt` (`:KidsApp`)**: Adicionado `companion object` com o método compartilhado `refreshTelemetryIfPermitted(context: Context): TelemetryPayload?`, que verifica `UsagePermissionHelper.hasUsageStatsPermission(context)`, utiliza `deviceId` e `deviceName` reais de `RemoteControlRepository.credentials.value` (com fallback seguro), instancia `UsageStatsCollector(context).collectUsageTelemetry(...)` e atualiza `UsageSyncRepository.updateTelemetry(fresh)`.
- **`KidsApp/MainActivity.kt` (linhas 81 e 281) & `RemoteSyncWorker.kt` (linha 27)**: Substituídas as chamadas estáticas inválidas `UsageStatsCollector.collectUsageTelemetry(context)` (que causavam `Unresolved reference 'collectUsageTelemetry'` em `:KidsApp:compileDebugKotlin`) por `UsageStatsCollector.refreshTelemetryIfPermitted(...)` dentro de `runCatching { ... }`.
- **`UsageMonitoringWorker.kt` & `KidsCommandExecutor.kt` (`:KidsApp`)**: Padronizados para reutilizar `UsageStatsCollector.refreshTelemetryIfPermitted(...)`, eliminando duplicação de lógica de coleta e publicação de telemetria e removendo imports não utilizados.

---

## [1.1.0-rc1] — 2026-11-23 (Comunicação Real Parent ↔ Kids, Backend Relay & Pareamento Temporário)

### Adicionado
- **Módulo `backend/` (Servidor Relay Node.js 22 ESM sem dependências externas)**:
  - `src/store.mjs`: `RelayStore` com persistência atômica em JSON (`DATA_DIR/relay-state.json`, `0600`), códigos temporários de pareamento de 6 caracteres (TTL 10 min, uso único, `claimToken` via `crypto.timingSafeEqual`) e geração de `sharedSecret` de 256 bits.
  - `src/server.mjs` e `src/index.mjs`: Endpoints `/health`, `/api/v1/pairing/{initiate,claim,complete}` e as 9 rotas `/api/v1/devices/{deviceId}/...` autenticadas por HMAC-SHA256.
  - `Dockerfile`, `render.yaml`, `README.md` e `test/relay.test.mjs` (`4/4 PASS` via `node --test`).
- **Pareamento Temporário nos Apps (`:shared`, `:KidsApp`, `:ParentApp`)**:
  - `RemoteModels.kt`, `RemoteTransport.kt`, `HttpsRemoteTransport.kt`, `RemoteControlRepository.kt`: Fluxo completo `initiateKidsPairing` → `claimParentPairingCode` → `pollKidsPairingCompletion`.
  - `KidsPairingDialog.kt` (`:KidsApp`) e `RemotePairingDialog.kt` (`:ParentApp`): Modo padrão por código temporário com contagem regressiva e polling automático, mantendo fallback manual HMAC opcional.
- **CI (`.github/workflows/android-ci.yml`)**: Adicionado job `test-backend` (Node 22, `npm test` em `backend/`).
- **Testes (`RemoteCommunicationTest.kt`)**: Adicionado teste `10_temporaryPairingCodeSingleUseAndConnectionStateLabels`.

### Corrigido / Aprimorado
- **Estados de Conexão (`RemoteModels.kt`, `OverviewScreen.kt`, `ParentNavHost.kt`)**: Labels padronizados (`Não vinculado`, `Conectando`, `Online`, `Offline`, `Sincronizando`, `Erro de comunicação`) e propagação de `isSyncing` ao painel.
- **Telemetria Real (`ParentApp/MainActivity.kt`, `KidsApp/MainActivity.kt`, `RemoteSyncWorker.kt`)**: Removido fallback fixo `135` min quando pareado ou com permissão `UsageStatsManager` ativa; o `KidsApp` coleta uso real antes de enviar telemetria.

---

## [1.0.0-rc1-hotfix2] — 2026-11-23 (Correção de Segurança `redactSensitive` e Avisos `ControlRulesRepository`)

### Corrigido
- **`RemoteSecurity.kt` (`:shared`)**: Corrigida a ordem de avaliação em `redactSensitive(...)` para processar cabeçalhos `Authorization: Bearer/Basic/Token` antes do casamento genérico de chaves, evitando que tokens JWT após `Authorization: Bearer` fiquem expostos (`FinalAuditAndSecurityTest.kt:57`). Expandida a sanitização para cobrir `password`, `senha`, `api_key`, `credential`, `sharedSecret`, valores entre aspas/JSON e JWTs avulsos.
- **`HttpsRemoteTransport.kt` e `RemoteCommandEngine.kt` (`:shared`)**: Aplicado `RemoteSecurity.redactSensitive(...)` às mensagens de exceção capturadas e a `ack.errorMessage` / `ack.resultMessage` recebidos remotamente.
- **`ControlRulesRepository.kt` (`:shared`)**: Removido o segundo argumento `null` de `obj.optString("targetPackage", null)` e `obj.optString("targetAppName", null)` (linhas 478–479) após `obj.isNull(...)`, eliminando o aviso `Java type mismatch: Nothing? vs String`.
- **`FinalAuditAndSecurityTest.kt` (`:shared/src/test`)**: Mantidas todas as asserções originais e expandido `remoteSecurityRedactsTokensSecretsPinsAndBearerHeaders` com cenários de senha, `Authorization: Basic`, JSON, `api_key`, `credential` e JWT completo.

---

## [1.0.0-rc1-hotfix1] — 2026-11-23 (Correção de Compilação `:shared:compileDebugUnitTestKotlin` e Deprecations)

### Corrigido
- **`FinalAuditAndSecurityTest.kt` (`:shared/src/test`)**: Corrigida chamada inexistente `dedup.record(...)` para `dedup.recordIfNew(ParentNotificationEvent(...))` no teste `notificationDeduplicatorHandlesAllSecurityAndRuleEvents`.
- **`UsageStatsCollector.kt` (`:KidsApp`)**: Substituído uso direto de `UsageEvents.Event.MOVE_TO_FOREGROUND` e `MOVE_TO_BACKGROUND` (linhas 157, 182 e 186) pelos helpers `isForegroundEvent(...)` e `isBackgroundEvent(...)`, priorizando `ACTIVITY_RESUMED` / `ACTIVITY_PAUSED` no Android 10+ (API 29+) com fallback `@Suppress("DEPRECATION")` isolado para API 26–28 (`minSdk = 26`).
- **`PinAuthDialog.kt` (`:KidsApp`)**: Atualizado `Icons.Default.Backspace` (linha 163) para `Icons.AutoMirrored.Filled.Backspace`.

---

## [1.0.0-rc1] — 2026-11-23 (PARTE 5/5 — Segurança, Auditoria, Testes Finais e Versão Estável)

### Segurança e Hardening
- **`SecureCredentialStorage.kt` (`:shared`)**: Criptografia de `sharedSecret` HMAC-SHA256 usando **Android Keystore (`AES/GCM/NoPadding`, 256 bits)** sem dependências externas, com migração automática e remoção da chave legada em texto puro no `RemoteControlRepository`.
- **`KidsPairingDialog.kt` (`:KidsApp`)**: Diálogo de pareamento protegido pelo `PinAuthDialog` no tablet da criança, exigindo `https://` em produção e chave secreta HMAC.

### Correções e Melhorias da Auditoria
- **`KidsCommandExecutor.kt` (`:KidsApp`)**: Liberação segura de `ToneGenerator` (`toneGen.release()`) após `PLAY_SOUND` e suporte direto a `category` + `categoryLimitMinutes` em `UPDATE_LIMITS`.
- **Emissores de Notificação**: Conectados `DAILY_LIMIT_REACHED`, `APP_BLOCKED` (`MonitorDadAccessibilityService`), `EXTRA_TIME_REQUESTED` (`KidsApp/MainActivity`) e `RULES_CHANGED` (`ParentApp/MainActivity`).
- **`ParentBackgroundSyncWorker.kt` (`:ParentApp`)**: Adicionado `WorkManager` (`androidx.work.runtime.ktx`) ao `ParentApp` para sincronização periódica e alertas locais em segundo plano.

### Testes e Documentação
- **`FinalAuditAndSecurityTest.kt` (`:shared`)**: 6 novos testes JVM cobrindo `PinHashUtils`, `ClockTamperDetector`, `RemoteSecurity.redactSensitive`, validação completa de comandos/assinatura/replay/expiração, deduplicação de notificações e `RuleEvaluationEngine`.
- **`documentation/USER_GUIDE.md`**: Guia completo de instalação, permissões, pareamento remoto, uso diário e solução de problemas.
- Atualizados `PROJECT_STATE.md`, `AI_HANDOVER.md`, `TODO.md`, `BUG_REPORT.md`, `TEST_REPORT.md` e `logs/latest_build_summary.log`.

---

## [1.0.0-alpha04-audit] — 2026-11-23 (Auditoria Técnica — Parte 4/5)

### Auditado (Sem alteração de código funcional)
- **Infraestrutura e Comunicação Remota**:
  - Confirmado que `:shared`, `:ParentApp` e `:KidsApp` implementam integralmente a camada cliente REST/HTTPS (`HttpsRemoteTransport`), segurança HMAC-SHA256, fila offline persistida em `SharedPreferences`, executor de comandos (`KidsCommandExecutor`) e painel ao vivo no `ParentApp`.
  - Confirmado que **não há servidor/backend relay nem Firebase (FCM) implementados no repositório**; a comunicação real pela internet depende da criação e hospedagem de um relay HTTPS com os 9 endpoints `/api/v1/devices/{deviceId}/...`.
  - Identificado que `ParentApp` usa polling em primeiro plano (15s) e notificações locais (`ParentNotificationHelper`), sem polling em segundo plano/FCM quando o app está fechado.
  - Identificado que 5 valores de `NotificationEventType` (`DAILY_LIMIT_REACHED`, `APP_BLOCKED`, `EXTRA_TIME_REQUESTED`, `UNLOCK_REQUESTED`, `RULES_CHANGED`) ainda não possuem emissores automáticos em produção.
  - Identificados pontos de melhoria futura: `toneGen.release()` em `KidsCommandExecutor.PLAY_SOUND` e migração de `sharedSecret` para `EncryptedSharedPreferences`.
- **Testes & Documentação**:
  - Executado `web-preview/test_runner.html` em Chrome Headless via servidor HTTP local: **38/38 asserções PASS**.
  - Executada verificação estática em **70 arquivos `.kt`** e manifestos XML.
  - Atualizados `PROJECT_STATE.md`, `AI_HANDOVER.md`, `TODO.md`, `BUG_REPORT.md`, `TEST_REPORT.md` e `ARCHITECTURE.md` com o diagnóstico real da Parte 4/5.

---

## [1.0.0-alpha04] — 2026-11-23 (PARTE 4/5 — Comunicação Remota e Painel dos Responsáveis)

### Adicionado
- **Módulo `:shared` (`com.dohguard.shared.remote`)**:
  - `RemoteModels.kt`: `CommandType`, `CommandStatus` (`PENDING`, `SENT`, `RECEIVED`, `EXECUTED`, `FAILED`, `EXPIRED`), `RemoteCommand`, `CommandAck`, `PairedDeviceCredentials`, `DeviceRemoteSnapshot`, `ParentNotificationEvent` e `NotificationEventType`.
  - `RemoteSecurity.kt`: Assinatura e verificação HMAC-SHA256, proteção contra replay (nonce/commandId), validação de autorização e expiração, e redação de tokens/PINs/segredos em logs.
  - `RemoteTransport.kt` & `HttpsRemoteTransport.kt`: Transporte remoto via HTTPS/TLS (`HttpURLConnection`), retornando falha honesta quando offline ou não configurado (sem simular conexão/execução).
  - `RemoteCommandEngine.kt` & `NotificationEventDeduplicator.kt`: Motor puro em Kotlin para fila offline, expiração, confirmações (ACK) e deduplicação de notificações.
  - `RemoteJsonCodec.kt` & `RemoteControlRepository.kt`: Persistência offline em `SharedPreferences`, sincronização de regras/telemetria/comandos e `StateFlow`s reativos.
  - `ControlRulesRepository.setAppBlocked(...)`: Operação idempotente para comandos remotos `BLOCK_APP`/`UNBLOCK_APP`.
  - `DoHHeader.kt`: Badge Offline exibindo `Icons.Default.WifiOff` e cor de alerta quando desconectado.
- **Módulo `:KidsApp` (`com.dohguard.kids.remote`)**:
  - `KidsCommandExecutor.kt`: Execução local real de pausa/liberação, tempo extra, bloqueio/desbloqueio, limites, rotinas, atualização de uso e sinal sonoro (`ToneGenerator`).
  - `RemoteSyncWorker.kt`: Worker periódico e imediato (`NetworkType.CONNECTED`) com reconexão automática após reinicialização via `BootCompletedReceiver` e polling em primeiro plano na `MainActivity`.
- **Módulo `:ParentApp`**:
  - `ParentNotificationHelper.kt` + permissão `POST_NOTIFICATIONS` (API 33+) no `AndroidManifest.xml`.
  - `RemotePairingDialog.kt`: Configuração de URL HTTPS do relay, IDs pareados e chave secreta HMAC.
  - `OverviewScreen.kt`, `PlaySoundDialog.kt`, `ParentNavHost.kt` e `MainActivity.kt`: Painel ao vivo com estado de conexão, última resposta, alertas recentes, histórico de comandos remotos e despacho real de ações remotas.
- **Testes Unitários (`:shared/src/test`)**:
  - `RemoteCommunicationTest.kt`: 9 testes cobrindo ciclo de vida completo, perda/recuperação de conexão, relay não configurado, expiração, replay/assinatura inválida, falhas, deduplicação, redação e conflitos de sincronização.

---

## [1.0.0-alpha03-hotfix] — 2026-11-23 (Correção `:ParentApp` e `:KidsApp:compileDebugKotlin`)

### Corrigido
- **`ParentApp/src/main/java/com/dohguard/parent/screens/rules/RulesScreen.kt:108`**: Substituído `BadgeTone.INFO` (inexistente) por `BadgeTone.NEUTRAL`, alinhado ao `enum class BadgeTone` em `shared/.../StatusBadge.kt`.
- **`KidsApp/src/main/java/com/dohguard/kids/blocking/MonitorDadAccessibilityService.kt:42,43,46`**: Substituídos `usageTimeMinutes` por `usedMinutesToday` (`AppUsageInfo`) e `totalUsageMinutes` por `totalUsedMinutesToday` (`UsageSummary`).
- **`KidsApp/src/main/java/com/dohguard/kids/MainActivity.kt:84`** e **`ParentApp/src/main/java/com/dohguard/parent/MainActivity.kt:73`**: Substituído `totalUsageMinutes` por `totalUsedMinutesToday` (`UsageSummary`).
- **`KidsApp/src/main/java/com/dohguard/kids/screens/HomeScreen.kt:117-122`**: Encaminhados `hasAccessibilityPermission` e `onRequestAccessibilityPermission` para `UsagePermissionCard`.

---

## [1.0.0-alpha03] — 2026-11-23 (PARTE 3/5 — Limites, Rotinas e Bloqueio Automático)

### Adicionado
- **Módulo `:shared` (Motor de Controle, Segurança & Sync)**:
  - `ControlModels.kt`: Modelos de limites diários, por aplicativo, por categoria, janelas de horário, rotinas (`BEDTIME`, `STUDY`, `REST`, `FREE_TIME`, `CUSTOM`), concessões de tempo extra/desbloqueio temporário, decisões de bloqueio e envelopes de sincronização.
  - `RuleEvaluationEngine`: Avaliação pura de regras com suporte a dias da semana, rotinas que cruzam meia-noite (ex: `21:00`–`07:00`), prioridade de conflitos e expiração de tempo extra.
  - `ClockTamperDetector` & `ClockIntegrityGuard`: Proteção contra manipulação regressiva do relógio e persistência do uso diário acumulado após reinicialização do app/dispositivo.
  - `PinHashUtils` & `PinSecurityManager`: Verificação do PIN parental por hash SHA-256 com salt aleatório (sem armazenamento ou log em texto puro).
  - `RuleSyncValidator` & `ControlRulesRepository`: Persistência offline em `SharedPreferences` (JSON), fila de reconexão, checksum SHA-256, deduplicação e proteção contra pacotes fora de ordem.
  - `ControlEngineAndSecurityTest.kt`: 11 testes unitários JVM cobrindo limites, rotinas, virada de meia-noite, tempo extra, reinício, offline/sync, conflitos e manipulação de relógio.
- **Módulo `:KidsApp` (Bloqueio Automático & Persistência)**:
  - `MonitorDadAccessibilityService` + `accessibility_service_config.xml`: Detecção de apps em primeiro plano via `TYPE_WINDOW_STATE_CHANGED`.
  - `BlockOverlayActivity` & `LockScreen` atualizada: Exibição de motivo real do bloqueio, horário previsto de liberação, pedido de +15 min e desbloqueio via PIN com hash.
  - `BootCompletedReceiver`: Restauração de regras e reagendamento do `UsageMonitoringWorker` após `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`.
  - `UsagePermissionCard`: Exibição transparente do status de Acesso ao Uso e Serviço de Acessibilidade.
- **Módulo `:ParentApp` (Gerenciamento de Limites, Rotinas e Tempo Extra)**:
  - `RulesScreen` + `RoutineEditorDialog`: Edição de limite diário e dias da semana, limites por categoria, CRUD completo de rotinas, concessão de tempo extra com histórico e status de sincronização.
  - Integração de `MainActivity`, `ParentNavHost`, `OverviewScreen` e `AppsScreen`/`AppDetailDialog` ao `ControlRulesRepository`.

---

## [1.0.0-alpha02-hotfix] — 2026-11-23 (Correção `:KidsApp:compileDebugKotlin`)

### Corrigido
- **`KidsApp/src/main/java/com/dohguard/kids/screens/HomeScreen.kt`**: Adicionado o import ausente `com.dohguard.shared.components.AppIconImage` (linha 235), que causava falha em `:KidsApp:compileDebugKotlin` no GitHub Actions enquanto `:ParentApp:compileDebugKotlin` passava via wildcard `com.dohguard.shared.components.*`. Removidos também os imports não utilizados `Chat` e `AppCategory`.
- **Auditoria Estática Preventiva**: Verificação cruzada de símbolos `com.dohguard.*` em todos os 46 arquivos Kotlin (`shared`, `ParentApp`, `KidsApp`) com 0 referências ausentes, além de validação XML/recursos e re-execução do Preview Web (`38/38` PASS).

---

## [1.0.0-alpha02-fase1-fase2] — 2026-11-23 (PROMPT 02/05)

### Adicionado
- **Fase 1 — Tela Cheia Imersiva, Ícones e Splash Screen**:
  - `ImmersiveModeHelper` (`:shared`): Modo tela cheia imersivo com `WindowInsetsControllerCompat` (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`), `WindowCompat.setDecorFitsSystemWindows(window, false)`, suporte a *display cutout* (API 28+) e `imePadding()` para teclado virtual/modais, reaplicado em `onCreate`, `onResume` e `onWindowFocusChanged`.
  - **Adaptive Launcher Icons**: Estrutura completa (`mipmap-anydpi-v26/ic_launcher*.xml`, `drawable/ic_launcher_*.xml`, e fallbacks `mipmap-{mdpi..xxxhdpi}/*.png` placeholders) e atualização dos `AndroidManifest.xml` de `ParentApp` e `KidsApp`.
  - **Splash Screen Personalizada**: `MonitorDadSplashScreen` (`:shared`) e `SplashConfig` (`SPLASH_DURATION_MS = 4000L`, intervalo `3000..5000ms`), `Theme.MonitorDad.Splash` (`#FDFBF7` sem flash preto), imagens placeholder `drawable-nodpi/monitor_dad_splash.webp`, e exibição única por inicialização a frio via `rememberSaveable`.
  - **`documentation/ASSET_GUIDE.md`**: Guia completo para substituição de ícones, imagem de Splash, ajuste de duração e recompilação via CI.
- **Fase 2 — Ícones Reais, Cache Offline e Identificação do Dispositivo**:
  - `AppIconImage` (`:shared`): Carregamento seguro de ícones reais instalados via `PackageManager.getApplicationIcon()` com fallback elegante para ícones de categoria Sage Green (integrado em `AppsScreen`, `AppDetailDialog`, `OverviewScreen` e `KidsHomeScreen`).
  - `UsageSummary` & `UsageSyncRepository`: Adicionados `deviceId`, `deviceName`, `lastSyncFormatted`, `isDemoData`, e persistência offline via `SharedPreferences` (`UsageSyncRepository.init(context)`).
  - `UsageSummaryCard`: Exibe nome/ID do dispositivo, data/hora da última sincronização e aviso explícito quando em modo demonstrativo.
  - Testes unitários: `SplashConfigTest` criado e `UsageSummaryTest` expandido.
- **Preview Web (`web-preview/`)**:
  - Adicionada simulação de Splash Screen (`▶ Splash` / `App.replaySplash()`), exibição do ID do dispositivo e aviso de dados demonstrativos no resumo, com `38/38` asserções aprovadas no Chrome Headless.

---

## [1.0.0-alpha02-stabilization] — 2026-11-22 (Auditoria e Estabilização)

### Corrigido
- **Compilação do `:KidsApp` (`:KidsApp:compileDebugKotlin`)**: Adicionado import ausente `androidx.compose.foundation.background` em `KidsApp/src/main/java/com/dohguard/kids/screens/PermissionCard.kt`.
- **Avisos de Ícones Depreciados**: Substituídos `Icons.Default.Chat`, `Icons.Default.VolumeUp` e `Icons.Default.MenuBook` por `Icons.AutoMirrored.Filled.*` em `AppDetailDialog.kt`, `AppsScreen.kt`, `PlaySoundDialog.kt`, `OverviewScreen.kt` e `HomeScreen.kt`.
- **Prevenção de Bugs & Lint**:
  - `UsageStatsCollector.kt`: Tratamento de `PackageManager.ResolveInfoFlags` para Android 13+ (API 33+) e formatação de horário com `padStart` sem dependência de `Locale` padrão.
  - `UsageSummaryCard.kt`: Removidos imports não utilizados (`AnimatedVisibility`, `Icons.Default.Sync`).
  - `UsageSummary.kt`: Alinhado `UsageSummary.DEMO` (187 min hoje, 220 min ontem, 7 apps) com `AppUsageInfo.DEMO_APPS`.

### Alterado
- **Identidade Oficial**: Atualizada toda a interface visível do usuário, `strings.xml`, Preview Web, `README.md` e documentação ativa para **MonitorDad • by HoD** (**MonitorDad Parent** e **MonitorDad Kids**), preservando pacotes técnicos (`com.dohguard.*`), `applicationId`, `namespace` e o tema **Sage Green & Nature**.
- **CI/CD (`.github/workflows/android-ci.yml`)**: Renomeado para `MonitorDad Android CI`, adicionada compilação explícita de `:shared`, `:ParentApp` e `:KidsApp`, validação de APKs não vazios e upload de artefatos nomeados `MonitorDad-Parent-debug.apk` e `MonitorDad-Kids-debug.apk`.

---

## [1.0.0-alpha02] — 2025-05-23 (Etapa 02/05)

### Adicionado
- **Monitoramento de Uso Real no `KidsApp`**:
  - `UsagePermissionHelper`: Verificação de `AppOpsManager.permission.PACKAGE_USAGE_STATS` e intent direto para Configurações do Android.
  - `UsageStatsCollector`: Coleta oficial de tempo de tela hoje e ontem, identificação de app em primeiro plano, sessões individuais de uso via `UsageEvents` e listagem de aplicativos via `PackageManager`.
  - `UsageMonitoringWorker`: Coleta periódica econômica em segundo plano a cada 15 minutos via `androidx.work.CoroutineWorker`.
  - `UsagePermissionCard`: Card no DoH Kids explicando a finalidade da permissão, compromisso de privacidade e botão para ativação.
- **Camada Compartilhada de Telemetria (`:shared`)**:
  - Modelos: `AppSession`, `UsageSummary`, `SyncStatus`, `TelemetryPayload`, e extensão de `AppCategory` para 6 categorias (`ALL`, `GAMES`, `VIDEOS`, `EDUCATION`, `COMMUNICATION`, `NAVIGATION`, `OTHER`).
  - `UsageSyncRepository`: Repositório reativo em memória com suporte a cache local offline e sincronização manual.
  - Testes Unitários: `UsageSummaryTest` e `AppUsageInfoTest`.
- **Interface do `ParentApp` (Aba Apps & Conteúdo)**:
  - `UsageSummaryCard`: Card de resumo com tempo total hoje, comparativo com ontem (+/- min), app mais utilizado, indicador de status de sincronização (`ONLINE`, `DESATUALIZADO`, `OFFLINE`) e botão `Sincronizar Agora`.
  - Filtros completos de categorias: `Todos`, `Jogos`, `Vídeos`, `Educação`, `Comunicação`, `Outros`.
  - `AppDetailDialog`: Modal aberto ao clicar em qualquer aplicativo exibindo pacote, histórico de sessões com horários de início e término, comparativo hoje vs ontem e slider de limite diário (15m a 180m).
- **Preview Web Independente (`web-preview/`)**:
  - Resumo de utilização no topo com métricas em tempo real.
  - 6 filtros de aplicativos interativos.
  - Modal de Detalhes do Aplicativo com histórico de sessões e controle de limite.
  - Card de Permissão de Acesso ao Uso no Kids com modal explicativo e alternador demonstrativo de status.
  - Suíte de testes automatizados in-browser expandida para 33 asserções com 100% de sucesso no Chrome headless.
- **CI/CD com GitHub Actions**:
  - Workflow oficial configurado em `.github/workflows/android-ci.yml` para compilação automática dos APKs de debug (`ParentApp` e `KidsApp`) e execução dos testes unitários.
  - Script executável `gradlew` e catálogo de versões atualizado.

---

## [1.0.0-alpha01] — 2025-05-22 (Etapa 01/05)

### Adicionado
- **Estrutura Multi-módulos Android (Gradle 8.4 & Kotlin 2.0)**:
  - Criação do `settings.gradle.kts` raiz incluindo `:shared`, `:ParentApp` e `:KidsApp`.
  - Configuração de `gradle/libs.versions.toml` com catálogo centralizado de dependências AndroidX, Jetpack Compose Bom, Material 3 e navegação.
  - Configuração de `gradle.properties` e `gradle-wrapper.properties`.
- **Módulo `shared` (Design System & Domínio)**:
  - `Color.kt`: Paleta oficial completa de 12 cores Sage Green & Nature.
  - `Shape.kt`: Definição de cantos arredondados entre 20dp e 28dp para cards, botões e modais.
  - `Type.kt`: Tipografia moderna, arredondada e acolhedora com tokens Material 3.
  - `Theme.kt`: Composable `DoHGuardTheme` compatível com temas claros e identidade da marca.
  - Modelos de Domínio: `DeviceInfo`, `AppUsageInfo`, `ScreenTimeState`, `PendingRequest`, `RuleSettings` e `LocationInfo`.
  - Componentes Reutilizáveis:
    - `DoHHeader`: Barra de identidade visual com logo DoH Guard • by HoD, status de conectividade e indicador de bateria.
    - `WaveScreenTimeMeter`: Medidor circular de tempo de tela com animação senoidal em Canvas.
    - `DoHCard`: Cards elevados com cantos de 24dp, fundo creme e sombras suaves.
    - `DoHButton`: Botões estilizados com variantes Primária, Secundária, Outlined e Danger.
    - `DoHSwitch`: Switches personalizados nas cores do tema Sage.
    - `StatusBadge`: Badges de status com tons Positivo, Alerta e Bloqueio.
- **Módulo `ParentApp` (Aplicativo dos Pais)**:
  - `AndroidManifest.xml` e `strings.xml`.
  - `MainActivity.kt` com gerenciamento de estado reativo e feedback em memória.
  - `ParentTab.kt` e `ParentBottomBar.kt`: Barra de navegação inferior com ícones e abas personalizadas.
  - `ParentNavHost.kt`: Orquestrador de transição de telas.
  - `OverviewScreen.kt` (Aba 1 - Visão Geral):
    - Identificação do tablet do Lucas.
    - Medidor animado de tempo com onda senoidal.
    - Ações rápidas: botão `+15 min`, botão `Pausar/Liberar`, botão `Tocar Som`.
    - Área de solicitações pendentes e lista dos aplicativos mais utilizados.
  - `AppsScreen.kt` (Aba 2 - Apps & Conteúdo):
    - Filtros dinâmicos: Todos, Jogos, Vídeos e Educação.
    - Card de Proteção de Conteúdo com switches (DNS DoH, SafeSearch e Compras no App).
    - Lista de aplicativos com barras de progresso de uso e switches de bloqueio.
  - `MapScreen.kt` (Aba 3 - Mapa GPS):
    - Área reservada para mapa ilustrada, identificada como demonstração.
    - Indicador de status de localização e precisão.
    - Botão `Atualizar GPS` com animação e atualização de horário.
    - Lista de Zonas Seguras (Escola, Casa, Avós) e Linha do Tempo de deslocamento.
  - `RulesScreen.kt` (Aba 4 - Regras):
    - Configurações de Horário de Dormir e Modo Aula.
    - Lista de Contatos Autorizados com suporte a SOS.
    - Informações de hardware, sistema, armazenamento e status de sincronização.
    - Assinatura oficial: `DoH Guard • Desenvolvido por HoD`.
  - Diálogos Modais: `PlaySoundDialog.kt` e `RequestApprovalDialog.kt`.
- **Módulo `KidsApp` (Aplicativo Infantil)**:
  - `AndroidManifest.xml` e `strings.xml`.
  - `MainActivity.kt` integrando o fluxo de telas infantis.
  - `KidsHomeScreen.kt`: Tela inicial com saudação amigável, tempo restante e grid de aplicativos liberados.
  - `RestScreen.kt`: Tela acolhedora de descanso visual e incentivo à hidratação.
  - `LockScreen.kt`: Tela de encerramento de tempo de tela e repouso noturno.
  - `PinAuthDialog.kt`: Diálogo de autenticação por PIN de 4 dígitos para acesso dos pais com teclado numérico na tela.
- **Módulo `web-preview` (Preview Web Independente)**:
  - `index.html`: Reprodução completa e responsiva das interfaces do DoH Parent e DoH Kids.
  - `css/styles.css`: Estilização pura baseada na paleta Sage Green & Nature.
  - `js/app.js`: Implementação de interatividade, animação contínua da onda em HTML5 Canvas, filtros de aplicativos, botões de ação e modais.
  - `js/navigation.js`: Gerenciamento de navegação entre as 4 abas, troca entre Parent e Kids e alternância de moldura de dispositivo (Celular, Tablet, Expandido).
  - `test_runner.html`: Suíte de testes automatizados in-browser com 100% de aprovação.
  - `PREVIEW_GUIDE.md`: Guia completo de execução, portas, dependências e verificação no Windows/Mac/Linux.
  - `README.md`: Documentação específica do módulo de preview.
- **Sistema de Documentação e Continuidade entre IAs**:
  - `documentation/PROJECT_STATE.md`: Estado atual, capacidades implementadas e limites.
  - `documentation/AI_HANDOVER.md`: Orientações estratégicas para continuidade por outros agentes.
  - `documentation/CHANGELOG.md`: Histórico de alterações.
  - `documentation/TODO.md`: Tarefas concluídas, pendentes e bloqueadas.
  - `documentation/BUG_REPORT.md`: Relatório de bugs e notas técnicas sobre o ambiente.
  - `documentation/ARCHITECTURE.md`: Detalhamento arquitetural, responsabilidades e padrões.
  - `documentation/TEST_REPORT.md`: Registro dos testes sintáticos, automatizados e visuais.
  - `README.md` raiz do projeto.
- **Sistema de Logs**:
  - `logs/build.log`: Log detalhado da validação de build e integridade estática.
  - `logs/runtime.log`: Registro de testes de execução do preview web.
  - `logs/latest_build_summary.log`: Resumo consolidado do status de compilação.
