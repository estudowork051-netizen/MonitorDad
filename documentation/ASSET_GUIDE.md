# Guia de Assets Visuais (PNG) — MonitorDad • by HoD

Este documento descreve a estrutura oficial para os **4 assets visuais em PNG** do projeto **MonitorDad • by HoD** (**ParentApp** e **KidsApp**), incluindo pastas de origem, destinos no Android, dimensões recomendadas, requisitos de transparência e procedimento de substituição **sem necessidade de alterar código**.

> **Regra de preservação:** Os ícones (`ic_launcher*`), splashes (`monitor_dad_splash.webp`) e os 3 botões da tela de tempo encerrado (`LockScreen.kt`) já estão configurados e preservados.

---

## Resumo dos 4 Assets

| # | Asset | Aplicativo | Pasta Fonte (`assets/branding/`) | Destino no Android | Dimensão Recomendada | Transparência |
|---|---|---|---|---|---|---|
| **1** | Ícone padrão do launcher | ParentApp e KidsApp | `assets/branding/icon/` | `ParentApp/src/main/res/mipmap-*/` e `KidsApp/src/main/res/mipmap-*/` | `48` a `192 px` (ou `432×432 px` foreground) | Recomendada no foreground |
| **2** | Splash de inicialização | **ParentApp** (exclusivo) | `assets/branding/parent/` | `ParentApp/src/main/res/drawable-nodpi/monitor_dad_splash.png` *(ou `.webp`)* | `1080 × 1920 px` (ou `1080 × 1080 px`) | Opcional (fundo `#1A0E2E`) |
| **3** | Splash de inicialização | **KidsApp** (exclusivo) | `assets/branding/kids/` | `KidsApp/src/main/res/drawable-nodpi/monitor_dad_splash.png` *(ou `.webp`)* | `1080 × 1920 px` (ou `1080 × 1080 px`) | Opcional (fundo `#1A0E2E`) |
| **4** | Tela de tempo encerrado | **KidsApp** (exclusivo) | `assets/branding/time_expired/` | `KidsApp/src/main/res/drawable-nodpi/monitor_dad_time_expired.png` | `512 × 512 px` a `1024 × 1024 px` | **Sim** (fundo transparente) |

---

## 1. Asset 1 — Ícone Padrão do MonitorDad (`assets/branding/icon/`)

Ambos os módulos (`ParentApp` e `KidsApp`) utilizam **Adaptive Icons** (Android 8.0+ / API 26+) com fallback PNG para densidades legadas (`mipmap-*`).

### Destinos no Android
- `ParentApp/src/main/res/` e `KidsApp/src/main/res/`:
  - `mipmap-anydpi-v26/ic_launcher.xml` e `ic_launcher_round.xml`
  - `drawable/ic_launcher_background.xml` e `drawable/ic_launcher_foreground.xml`
  - Fallbacks PNG:
    - `mipmap-mdpi/ic_launcher.png` & `ic_launcher_round.png` (**48×48 px**)
    - `mipmap-hdpi/ic_launcher.png` & `ic_launcher_round.png` (**72×72 px**)
    - `mipmap-xhdpi/ic_launcher.png` & `ic_launcher_round.png` (**96×96 px**)
    - `mipmap-xxhdpi/ic_launcher.png` & `ic_launcher_round.png` (**144×144 px**)
    - `mipmap-xxxhdpi/ic_launcher.png` & `ic_launcher_round.png` (**192×192 px**)

### Como substituir pelo PNG definitivo
1. Salve a arte original em `assets/branding/icon/` (ex.: `ic_launcher_1024.png`).
2. Substitua os arquivos `ic_launcher.png` e `ic_launcher_round.png` nas pastas `mipmap-*` de `ParentApp` e `KidsApp` respeitando as dimensões acima.
3. *(Opcional)* Para usar PNG também na camada frontal do Adaptive Icon, coloque `ic_launcher_foreground.png` (**432×432 px**, fundo transparente, área segura central de 66%) em `drawable-nodpi/` e **remova** `drawable/ic_launcher_foreground.xml` (o Android não permite dois recursos com o mesmo nome base).

---

## 2. Asset 2 — Splash de Inicialização do ParentApp (`assets/branding/parent/`)

- **Pasta fonte**: `assets/branding/parent/monitor_dad_splash_parent.png`
- **Destino no Android**: `ParentApp/src/main/res/drawable-nodpi/monitor_dad_splash.png` *(atualmente existe `monitor_dad_splash.webp`)*
- **Dimensão recomendada**: `1080 × 1920 px` (ou quadrada `1080 × 1080 px`), renderizada com `ContentScale.Fit`.
- **Transparência**: Opcional (caso transparente, integra-se sobre o fundo `#1A0E2E`).

### Como substituir sem alterar código
1. Coloque seu PNG definitivo em `ParentApp/src/main/res/drawable-nodpi/monitor_dad_splash.png`.
2. **Importante:** Apague `ParentApp/src/main/res/drawable-nodpi/monitor_dad_splash.webp` ao adicionar o `.png`, pois o Android AAPT2 não permite dois arquivos com o mesmo nome base (`monitor_dad_splash`).
3. Não altere `R.drawable.monitor_dad_splash` no código.

---

## 3. Asset 3 — Splash de Inicialização do KidsApp (`assets/branding/kids/`)

- **Pasta fonte**: `assets/branding/kids/monitor_dad_splash_kids.png`
- **Destino no Android**: `KidsApp/src/main/res/drawable-nodpi/monitor_dad_splash.png` *(atualmente existe `monitor_dad_splash.webp`)*
- **Dimensão recomendada**: `1080 × 1920 px` (ou quadrada `1080 × 1080 px`), renderizada com `ContentScale.Fit`.
- **Transparência**: Opcional (sobre fundo `#1A0E2E`).

### Como substituir sem alterar código
1. Coloque seu PNG definitivo em `KidsApp/src/main/res/drawable-nodpi/monitor_dad_splash.png`.
2. **Importante:** Apague `KidsApp/src/main/res/drawable-nodpi/monitor_dad_splash.webp` ao adicionar o `.png` para evitar colisão de recurso no AAPT2.

---

## 4. Asset 4 — Tela de Tempo Encerrado do KidsApp (`assets/branding/time_expired/`)

- **Pasta fonte**: `assets/branding/time_expired/monitor_dad_time_expired.png`
- **Destino no Android**: `KidsApp/src/main/res/drawable-nodpi/monitor_dad_time_expired.png`
- **Dimensão recomendada**: `512 × 512 px` a `1024 × 1024 px` (`ContentScale.Fit` em `136.dp`).
- **Transparência**: **Sim** (PNG com fundo transparente para compor perfeitamente sobre o card `#351C5B`).
- **Comportamento**:
  - Atualmente há um placeholder transparente `1×1 px` em `KidsApp/src/main/res/drawable-nodpi/monitor_dad_time_expired.png`.
  - Enquanto o arquivo tiver `<= 4 px`, o `LockScreen.kt` exibe automaticamente o ícone padrão (`Bedtime`).
  - Assim que você substituir `KidsApp/src/main/res/drawable-nodpi/monitor_dad_time_expired.png` pelo seu PNG definitivo (`> 4×4 px`), o `LockScreen.kt` passa a exibi-lo automaticamente **sem alterar código** e preservando integralmente os 3 botões (`Pedir Mais Tempo aos Pais`, `Voltar à Tela Kids (Demo)` e `Acesso dos Pais (PIN)`).

---

## 5. Duração da Splash Screen e Compilação

- Duração configurável em `shared/src/main/java/com/dohguard/shared/config/SplashConfig.kt` (`SPLASH_DURATION_MS = 4_000L`).
- Após substituir os PNGs, faça `git add`, `git commit` e `git push` para `master`; o workflow `.github/workflows/android-ci.yml` recompilará `:shared:testDebugUnitTest`, `:ParentApp:assembleDebug` e `:KidsApp:assembleDebug`.
