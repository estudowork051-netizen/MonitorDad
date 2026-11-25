# Guia de Instalação, Configuração e Uso — MonitorDad • by HoD

**Versão do Documento**: `1.0.0-rc1` (Parte 5/5 — Versão Estável Inicial)

---

## 1. Visão Geral dos Aplicativos

O sistema **MonitorDad • by HoD** é composto por dois aplicativos Android independentes:

1. **MonitorDad Parent** (`com.dohguard.parent`): Instalado no smartphone do responsável para visualizar o uso do tablet, definir limites e rotinas, bloquear/desbloquear aplicativos e enviar comandos remotos.
2. **MonitorDad Kids** (`com.dohguard.kids`): Instalado no tablet da criança para coletar estatísticas de uso, aplicar limites/rotinas automaticamente e exibir as telas de descanso, bloqueio e proteção por PIN.

> **Transparência e Privacidade**: O MonitorDad não oculta seu ícone, não coleta conteúdo de mensagens ou áudio e exibe claramente à criança o tempo restante e as regras ativas.

---

## 2. Como Obter os APKs

### Opção A — Via GitHub Actions (Recomendado)
1. Acesse o repositório no GitHub e abra a aba **Actions**.
2. Selecione a execução mais recente do workflow **`MonitorDad Android CI`** com status verde.
3. Na seção **Artifacts**, baixe:
   - `MonitorDad-Parent-debug` → contém `MonitorDad-Parent-debug.apk`
   - `MonitorDad-Kids-debug` → contém `MonitorDad-Kids-debug.apk`

### Opção B — Compilação Local (Android Studio / Gradle)
Requer JDK 17 e Android SDK 34:
```bash
./gradlew testDebugUnitTest assembleDebug
```
Os APKs serão gerados em:
- `ParentApp/build/outputs/apk/debug/ParentApp-debug.apk`
- `KidsApp/build/outputs/apk/debug/KidsApp-debug.apk`

---

## 3. Instalação nos Dispositivos (Android 8.0+ / API 26+)

1. Transfira o APK correspondente para cada aparelho (`Parent` no celular do responsável; `Kids` no tablet da criança).
2. Ao tocar no arquivo `.apk`, autorize **Instalar apps desconhecidos** para o gerenciador de arquivos utilizado.
3. Conclua a instalação normalmente (não é necessário root nem Device Owner — nenhum dado do tablet é apagado).

---

## 4. Configuração Inicial no Tablet da Criança (`MonitorDad Kids`)

Ao abrir o **MonitorDad Kids** pela primeira vez:

1. **Permissão de Acesso ao Uso (`PACKAGE_USAGE_STATS`)**:
   - No cartão de status no topo da tela inicial, toque em **Ativar Acesso ao Uso**.
   - Localize **MonitorDad Kids** na lista do Android e ative **Permitir acesso de uso**.
   - Sem essa permissão, o tablet não consegue medir o tempo real dos aplicativos.
2. **Serviço de Acessibilidade (`MonitorDadAccessibilityService`)**:
   - Toque em **Ativar Bloqueio Automático** no mesmo cartão.
   - Em **Configurações > Acessibilidade > Apps instalados**, ative **MonitorDad Kids**.
   - Sem esse serviço, o app continua monitorando, mas não exibe a tela de bloqueio automaticamente ao abrir um app restrito.
3. **Otimização de Bateria (Recomendado)**:
   - Em **Configurações > Apps > MonitorDad Kids > Bateria**, selecione **Irrestrito / Sem restrições** para que o `WorkManager` continue sincronizando em segundo plano.
4. **PIN Parental**:
   - O PIN inicial de demonstração é **`1234`**.
   - O PIN é armazenado localmente apenas como hash SHA-256 com salt (`PinSecurityManager`).

---

## 5. Configuração Inicial no Celular do Responsável (`MonitorDad Parent`)

1. Abra o **MonitorDad Parent**.
2. No Android 13 ou superior, aceite a permissão de **Notificações (`POST_NOTIFICATIONS`)** para receber alertas de limites, bloqueios, pedidos de tempo extra e status de conexão.

---

## 6. Pareamento Remoto pela Internet (Relay HTTPS)

> **Pré-requisito de Infraestrutura**: O repositório contém os aplicativos clientes Android. Para comunicação fora do Wi-Fi local, é necessário ter um **servidor relay HTTPS** compatível com os endpoints `/api/v1/devices/{deviceId}/...` descritos em `documentation/ARCHITECTURE.md`.

### Passo a passo do Pareamento:
1. No **MonitorDad Parent** (aba **Visão Geral**), toque em **Configurar Pareamento** no card de conexão remota.
2. Preencha:
   - **URL do Relay**: endereço `https://...` do seu servidor relay (não use `http://` em produção).
   - **ID do Tablet (Kids)**: ex.: `tablet-lucas-01`.
   - **ID do Responsável**: ex.: `parent-hod-01`.
   - **Chave Secreta HMAC**: copie a chave gerada automaticamente (ou informe uma chave forte compartilhada).
3. Toque em **Salvar**.
4. No **MonitorDad Kids** (tablet da criança):
   - Toque no ícone de **Cadeado** no canto superior direito da tela inicial.
   - Digite o **PIN do responsável**.
   - No diálogo **Pareamento do Tablet (Pais)**, informe **exatamente os mesmos** `URL do Relay`, `ID do Tablet`, `ID do Responsável` e `Chave Secreta HMAC`.
   - Toque em **Parear**.
5. A chave secreta é armazenada localmente com criptografia **Android Keystore (AES-256-GCM)** em ambos os dispositivos.

---

## 7. Funcionalidades Disponíveis

- **Visão Geral (`Parent`)**: Tempo usado hoje, limite diário, botões rápidos (`+15 min`, `Pausar / Liberar Tablet`, `Tocar Som`), status da conexão remota, fila pendente, alertas recentes e histórico de comandos com confirmação (`ACK`).
- **Aplicativos (`Parent`)**: Lista de apps instalados no tablet, filtro por categoria, tempo diário por app e bloqueio individual imediato.
- **Regras (`Parent`)**: Limite diário do dispositivo, limites por categoria, rotinas personalizadas (Estudos, Dormir, Descanso) com suporte a horários que cruzam a meia-noite e concessão de tempo extra.
- **Mapa (`Parent`)**: **Modo Demonstração** — exibe localização simulada claramente identificada até a integração futura de GPS real.

---

## 8. Limitações Conhecidas desta Versão (`1.0.0-rc1`)

1. **Servidor Relay Externo**: Não há backend incluído neste repositório; sem um relay HTTPS ativo, os apps operam localmente e exibem status `UNCONFIGURED` ou `OFFLINE`.
2. **Notificações Push (FCM)**: Sem Firebase configurado, alertas no `ParentApp` dependem do polling em primeiro plano (15s) ou do `WorkManager` periódico (mínimo de 15 minutos do Android).
3. **Localização (GPS)**: A aba Mapa permanece em **modo demonstração** explícito.
4. **Filtro de Rede / DoH**: Não há `VpnService` / DNS-over-HTTPS implementado nesta versão.

---

## 9. Solução de Problemas (Troubleshooting)

- **Status permanece `UNCONFIGURED`**: Verifique se a URL do Relay começa com `https://` e se a Chave Secreta HMAC foi salva nos dois aparelhos.
- **Status fica `OFFLINE`**: Confirme conexão com a internet e se o servidor relay HTTPS está acessível. Comandos enviados offline ficam na fila local e são reenviados automaticamente dentro do prazo de validade (TTL).
- **Apps não aparecem ou tempo está zerado**: Verifique se a permissão **Acesso ao Uso** foi concedida no tablet e toque em sincronizar.
- **Bloqueio automático não abre**: Confirme que o **Serviço de Acessibilidade** do MonitorDad Kids está ativo nas configurações do Android.
- **Som remoto não toca no tablet**: Verifique se o tablet não está no modo Não Perturbe (DND) ou com o volume de alarme zerado.
