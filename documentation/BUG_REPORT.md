# Registro de Bugs e Correções — MonitorDad • by HoD

## Resolvidos
1. **`RulesScreen.kt:108` (`Unresolved reference: INFO`)** — Corrigido na Parte 3/5.
2. **`FinalAuditAndSecurityTest.kt:57` (Sanitização de `Authorization: Bearer`)** — Corrigido em `RemoteSecurity.redactSensitive`.
3. **`ControlRulesRepository.kt:478-479` (`optString` nullability)** — Corrigido.
4. **`KidsApp` (`Unresolved reference 'collectUsageTelemetry'`)** — Corrigido via `UsageStatsCollector.refreshTelemetryIfPermitted(context)`.
5. **Ausência de pareamento local sem servidor externo** — Resolvido com `LocalPairingServer`, `LocalKidsServer`, `AndroidNsdDiscovery` (`NsdManager`) e `ConnectionManager` unificado.
6. **`ConnectionHubComponents.kt` (`DoHBorderLight`, `DoHStatusOnline`, `DoHStatusWarning`, `DoHStatusOffline`, `DoHTextMuted`, `DoHSageSurface` não encontrados + ambiguidade em `border`/`BorderStroke`)** — Corrigido definindo os tokens semânticos em `shared/src/main/java/com/dohguard/shared/theme/Color.kt`.
7. **`LocalHttpTransport.kt:257` & `LocalKidsServer.kt` (`Unresolved reference 'signRequest'`, `verifyRequestSignature`, `isTimestampFresh`)** — Corrigido implementando os métodos HMAC-SHA256 e validação de janela temporal em `RemoteSecurity.kt`.
8. **`RemoteControlRepository.kt:267,382,424` (`Unresolved reference 'ensureRelayProvisionedIfNeeded'`) & `ParentApp/MainActivity.kt:46` (`handleLocalServiceLost`)** — No commit `13e6842`, as chamadas e a variável `provisionedRelayKey` foram adicionadas sem as definições dos métodos `ensureRelayProvisionedIfNeeded()` e `handleLocalServiceLost()`. Restauradas ambas as implementações em `RemoteControlRepository.kt`, além da revogação remota em `unpairAndRevokeDevice()`.
9. **HTTP 502 na comunicação remota com Supabase (`SupabaseRelayStore.mjs`)** — `SupabaseRelayStore` enviava `Authorization: Bearer <key>` mesmo quando a chave configurada era do formato novo `sb_secret_...` (não-JWT), causando rejeição 401 no PostgREST convertida em HTTP 502 no Relay; além disso `/health` não verificava a tabela `relay_devices` e `HttpsRemoteTransport` omitia a mensagem de erro do servidor. Corrigido envio condicional de `Authorization: Bearer` apenas para JWTs de 3 partes, sanitização de URL/chave, `checkHealth()` em `/health` (`databaseConnected` / `databaseError`) e exposição segura da mensagem de erro em `HttpsRemoteTransport`.
10. **Supabase REST 401 / `42501 permission denied for table` (`schema.sql` & `supabaseStore.mjs`)** — `schema.sql` habilitava RLS sem `GRANT` explícito ao papel `service_role` e sem `REVOKE` de `anon`/`authenticated`, além de não detectar quando uma chave pública (`anon` / `sb_publishable_`) era informada no Render. Corrigido com `REVOKE ALL` de `PUBLIC, anon, authenticated`, `GRANT` explícito apenas para `service_role`, migração segura `backend/supabase/migrations/001_grant_service_role.sql` e validação segura do papel da chave em `SupabaseRelayStore`.

## Limitações Conhecidas / Pontos de Atenção
- **Descoberta mDNS (`NsdManager`) em roteadores com AP Isolation**: Caso o roteador Wi-Fi bloqueie multicast entre clientes, o usuário pode colar o payload do QR Code (`monitordad://pair?...`, que já inclui IP e porta locais) ou utilizar a aba Internet (Relay).
- **Compilação Gradle**: Não executável dentro do sandbox (ausência de JDK/Android SDK); validação completa ocorre via GitHub Actions após push.
