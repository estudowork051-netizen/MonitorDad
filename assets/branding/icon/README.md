# Asset 1 — Ícone Padrão do MonitorDad (Launcher Icon)

- **Uso**: Ícone padrão exibido no Launcher do Android para **ParentApp** e **KidsApp**.
- **Arquivos esperados nesta pasta (fonte)**:
  - `ic_launcher_1024.png` (1024×1024 px, PNG com transparência opcional / fundo seguro)
  - Opcional: `ic_launcher_foreground.png` (432×432 px, PNG com fundo **transparente** para Adaptive Icon)
- **Destinos no Android**:
  - `ParentApp/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` e `ic_launcher_round.png`
  - `KidsApp/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` e `ic_launcher_round.png`
- **Dimensões recomendadas**:
  - `mdpi`: 48×48 px | `hdpi`: 72×72 px | `xhdpi`: 96×96 px | `xxhdpi`: 144×144 px | `xxxhdpi`: 192×192 px
  - Adaptive foreground: 432×432 px (área segura central de 66%).

Consulte `documentation/ASSET_GUIDE.md` para instruções completas de substituição sem alterar código.
