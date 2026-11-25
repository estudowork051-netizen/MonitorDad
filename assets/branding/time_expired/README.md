# Asset 4 — Ilustração da Tela de Tempo Encerrado (KidsApp)

- **Uso**: Imagem exibida na tela de bloqueio / tempo de uso encerrado (`LockScreen.kt` no **KidsApp**).
- **Arquivo esperado nesta pasta (fonte)**:
  - `monitor_dad_time_expired.png`
- **Destino no Android**:
  - `KidsApp/src/main/res/drawable-nodpi/monitor_dad_time_expired.png`
- **Dimensão recomendada**: `512 × 512 px` a `1024 × 1024 px` (`ContentScale.Fit`).
- **Transparência**: Recomendado PNG com fundo **transparente**.
- **Nota**: Atualmente há um placeholder transparente 1×1 px no destino para manter a compilação limpa; quando substituído pelo PNG definitivo (> 4×4 px), `LockScreen.kt` passa a exibi-lo automaticamente sem qualquer alteração de código, preservando todos os 3 botões originais.

Consulte `documentation/ASSET_GUIDE.md` para instruções completas.
