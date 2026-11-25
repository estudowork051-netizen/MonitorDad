# Relatório de Testes — MonitorDad • by HoD

**Data:** 2026-11-23  
**Escopo:** v1.4.0 — "EM USO AGORA", cronômetro Kids em tempo real, identidade visual roxa/verde neon, arquitetura híbrida Wi-Fi/Internet e persistência Supabase.

## 1. Testes Executados no Sandbox

| Suíte / Verificação | Comando | Resultado | Detalhes |
|---|---|---|---|
| Backend Relay (`backend/test/relay.test.mjs`) | `npm test` | **PASS (8/8)** | Health (`storage` + `databaseConnected`), ciclo completo de pareamento, expiração, comandos/ACKs/regras/telemetria persistidos, rate limit anti-força bruta (HTTP 429), provisionamento de pareamento local sem re-pareamento (anti-sequestro 403), revogação (`DELETE`), persistência em `SupabaseRelayStore` após reinicialização simulada e compatibilidade com chaves `sb_secret_...`. |
| Validação XML Android | `xml.etree.ElementTree` | **PASS (3/3)** | `ParentApp/AndroidManifest.xml`, `KidsApp/AndroidManifest.xml`, `shared/res/xml/network_security_config.xml`. |
| Pureza JVM `:shared` & Sintaxe | Script Python (imports + chaves/parênteses) | **PASS (8/8)** | Zero imports `android.*` ou `org.json.*` em `ConnectionManager.kt`, `RemoteCommandEngine.kt`, `RemoteTransport.kt` e `UnifiedConnectionArchitectureTest.kt`. |

## 2. Testes Unitários JVM (`:shared`, Aguardando GitHub Actions)
- Arquivo: `shared/src/test/java/com/dohguard/shared/UnifiedConnectionArchitectureTest.kt`
  - `connectionManagerPrefersLocalWifiAndFailsOverToInternetWithoutDuplicatingCommands`
  - `connectionManagerRespectsLocalOnlyAndRemoteOnlyModes`
  - `pairingManagerEnforcesSingleUseExpiryBruteForceLockoutAndQrRoundtrip`
  - `qrCodeGeneratorProducesValidMatrixWithFinderAndTimingPatterns`
  - `localHttpServerHandlesRealLoopbackHealthAndDiagnosticsReport`
  - `remoteSecuritySignsVerifiesHttpRequestsAndEnforcesTimestampFreshness`
  - `connectionManagerReturnsAutomaticallyToLocalWifiAndProvisionsRelayWithoutRepairing`
  - `offlineQueuedCommandTransitionsHonestlyFromPendingToSentToExecutedOnlyAfterSignedAck`

## 3. Roteiro de Testes Reais em Dois Dispositivos (10 Cenários)
> **Nota:** Os cenários abaixo exigem dois dispositivos físicos (Parent + Kids), deploy do `backend/` em HTTPS e instalação dos APKs gerados pelo GitHub Actions. **Não foram executados no sandbox.**

1. **Pareamento na mesma rede Wi-Fi**: Abrir Parent → *Conexão e dispositivos* → *Adicionar dispositivo*; no Kids → *Vincular aparelho* (PIN) → digitar código ou ler QR. Confirmar vínculo sem servidor configurado.
2. **Bloqueio e desbloqueio local**: No Parent (mesmo Wi-Fi), bloquear um app no Kids e confirmar que o bloqueio aplica imediatamente e o status muda de `Enviado` para `Executado` após ACK real.
3. **Configuração do servidor remoto sem re-pareamento**: Em ambos os apps, abrir *Conexão e dispositivos* → *Configurações avançadas* → informar a URL `https://...` do relay → salvar → tocar em *Testar conexão*. Confirmar que o diagnóstico aprova `Conexão remota pela internet` e `Autenticação e troca de mensagens` sem pedir novo pareamento.
4. **Saída da rede Wi-Fi (4G/5G no Parent, Wi-Fi no Kids)**: Desligar o Wi-Fi do celular Parent (ficar nos dados móveis). Confirmar que o canal muda automaticamente para **Internet** sem perder o vínculo.
5. **Bloqueio de app pela internet**: Pelo 4G/5G no Parent, bloquear o YouTube (ou outro app) no tablet Kids. Confirmar recebimento e execução real no tablet + ACK `Executado` no Parent.
6. **Envio de regra / limite de tempo pela internet**: Alterar o limite diário ou rotina no Parent pelo 4G/5G e confirmar sincronização no tablet Kids.
7. **Desbloqueio e pausa remota pela internet**: Pausar e retomar o tablet pelo 4G/5G; confirmar tela de pausa e liberação no Kids.
8. **Sincronização de uso (telemetria)**: Usar o tablet por alguns minutos em outra rede e verificar se o consumo atualizado aparece no painel do Parent pela internet.
9. **Fila offline e reconexão**: Colocar o tablet em modo avião, enviar um comando pelo Parent (deve ficar `Na fila` / `Enviado` no relay, nunca `Executado`), reconectar o tablet à internet e confirmar execução + ACK posterior.
10. **Retorno automático ao Wi-Fi local + revogação**: Religar o Wi-Fi do celular Parent na mesma rede do tablet e confirmar que o canal volta para **Wi-Fi local** automaticamente. Em seguida, testar *Remover* dispositivo no Hub e confirmar que comandos antigos são rejeitados.

## 4. Limitações do Ambiente Sandbox
- Este container não possui JDK, Android SDK nem Gradle instalados e não possui acesso de rede externo para baixar dependências.
- Portanto, `:shared:testDebugUnitTest`, `:ParentApp:assembleDebug`, `:KidsApp:assembleDebug` e os 10 cenários em 2 dispositivos físicos dependem de `git push origin master`, deploy do `backend/` e execução presencial.
