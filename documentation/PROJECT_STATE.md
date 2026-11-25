# Estado Atual do Projeto — MonitorDad • by HoD

**Última atualização:** 2026-11-23  
**Versão:** v1.4.0  
**Status geral:** Arquitetura híbrida **Local (Wi-Fi / NsdManager) ↔ Remota (Internet / HTTPS Relay)** completa em `:shared`, `:ParentApp`, `:KidsApp` e `backend/`, agora com **persistência gratuita em PostgreSQL (Supabase Free + Render Free)** sem armazenamento temporário para dados críticos.

---

## 1. Módulos e Recursos Implementados

### `:shared` (`com.dohguard.shared.remote`, `components` & `theme`)
- **`Color.kt`**: Tokens semânticos da paleta Sage Green & Nature (`DoHBorderLight`, `DoHSageSurface`, `DoHTextMuted`, `DoHStatusOnline`, `DoHStatusWarning`, `DoHStatusOffline`).
- **`RemoteSecurity.kt`**: Assinatura HMAC-SHA256 para comandos, ACKs e requisições HTTP (`signRequest`, `verifyRequestSignature` com `MessageDigest.isEqual` em tempo constante e `isTimestampFresh` com janela de 5 min), além de `redactSensitive`.
- **`ConnectionManager`**: Seleção automática de transporte (`AUTO`, `LOCAL_ONLY`, `REMOTE_ONLY`), failover transparente `Wi-Fi local -> Internet -> Offline` sem duplicar comandos ou perder fila, retorno automático ao `LOCAL_WIFI` ao redescobrir o endpoint local, provisionamento remoto (`ensureRemoteProvisioned`), revogação no relay (`revokeDeviceOnRelay`), indicadores de saúde (`GREEN`, `YELLOW`, `RED`, `GRAY`) e diagnóstico real em 6 etapas (`runDiagnostics`).
- **`RemoteControlRepository`**: Provisionamento automático (`ensureRelayProvisionedIfNeeded`) quando um aparelho pareado localmente recebe URL de relay remoto; `handleLocalServiceLost()` para failover imediato ao sair do Wi-Fi; revogação remota best-effort ao desvincular.
- **`PairingManager` & `QrCodeGenerator`**: Códigos temporários de 6 caracteres (uso único, expiração em 10 minutos, bloqueio após 5 tentativas incorretas), payload `monitordad://pair?...`, geração pura de matriz QR Code (ISO v1..v8) sem dependências externas e revogação de dispositivos.
- **`LocalHttpTransport`, `HttpsRemoteTransport` & `LocalHttpServer`**: Transportes LAN e Internet compartilhando o mesmo protocolo HMAC-SHA256 e JSON, incluindo `provisionPairedDevice` e `revokeDevice`. `HttpsRemoteTransport` configurado com timeout tolerante (`15s` conexão / `35s` leitura) para cold start do Render Free.
- **`AndroidNsdDiscovery`**: Descoberta e anúncio mDNS (`_monitordad._tcp.` e `_monitordad-pair._tcp.`) via `NsdManager` + `MulticastLock`.
- **`ConnectionSettingsRepository`**: Persistência de modo de conexão, descoberta local, URL do relay, estado do assistente e lista de dispositivos vinculados.
- **`ConnectionHubComponents`**: UI reutilizável (`QrCodeView`, `SetupWizardDialog` em 4 passos, `ConnectionHubDialog` com **Estado do servidor**, diagnóstico real em 6 etapas, seletor de modo e configurações avançadas ocultas por padrão).

### `:ParentApp`
- **`LocalPairingServer`**: Servidor temporário de pareamento local em Wi-Fi ativado na tela "Adicionar dispositivo".
- **`RemotePairingDialog`**: Tela "Adicionar dispositivo" com QR Code escaneável, código temporário, contador regressivo, botão "Gerar novo código", lista de dispositivos vinculados com remoção/revogação e aba alternativa para relay pela internet.
- **`OverviewScreen` & `MainActivity`**: Cartão **"Conexão e dispositivos"** com indicador visual, descoberta automática NSD (`_monitordad._tcp.`) com tratamento de `onServiceLost` para troca imediata para internet, acesso ao Hub de Conexão e Assistente guiado.

### `:KidsApp`
- **`LocalKidsServer`**: Servidor HTTP local em Wi-Fi autenticado com HMAC-SHA256, proteção anti-replay (`X-Timestamp` + `X-Nonce`) e execução imediata de comandos via `KidsCommandExecutor`.
- **`KidsPairingDialog`**: Tela protegida por PIN com descoberta automática do Parent na rede Wi-Fi, campo para código ou QR payload, confirmação, exibição do responsável vinculado e acesso ao Hub de diagnóstico.

### `backend/`
- Relay Node.js 22 ESM sem dependências externas (`src/supabaseStore.mjs`, `src/store.mjs`, `src/server.mjs`, `src/index.mjs`) com persistência em **PostgreSQL via Supabase REST API** (`SUPABASE_URL` + `SUPABASE_SERVICE_ROLE_KEY`) e fallback local para testes, proteção contra força bruta (`429 Too Many Requests` após 5 falhas em `/api/v1/pairing/claim`), provisionamento HMAC anti-sequestro (`POST /api/v1/devices/:deviceId/provision`) e revogação (`DELETE /api/v1/devices/:deviceId`).
- Pronto para deploy 100% gratuito (sem cartão de crédito) com HTTPS via `backend/render.yaml` (Render Free) + `backend/supabase/schema.sql` (Supabase Free), documentado em `documentation/REMOTE_DEPLOY_GUIDE.md`.

---

## 2. Verificação e Pendências de Build
- **Verificado localmente no sandbox:**
  - `backend/` (`npm test`): **7/7 testes PASS** (incluindo persistência em `SupabaseRelayStore` simulando reinicialização do servidor).
  - Verificação de pureza JVM e sintaxe em `:shared` e `:ParentApp` (zero imports `android.*` / `org.json.*` nos motores e testes): **PASS**.
- **Pendente de ambiente com JDK/Android SDK e infraestrutura externa:**
  - Compilação Gradle (`:shared:testDebugUnitTest`, `:ParentApp:assembleDebug`, `:KidsApp:assembleDebug`) após `git push origin master`.
  - Criação das contas gratuitas no Supabase e Render, execução de `backend/supabase/schema.sql` e publicação via `backend/render.yaml` (seguindo `documentation/REMOTE_DEPLOY_GUIDE.md`).
  - Teste ponta a ponta em 2 dispositivos físicos (celular Parent em 4G/5G + tablet Kids em Wi-Fi diferente) seguindo o roteiro em `documentation/TEST_REPORT.md`.
