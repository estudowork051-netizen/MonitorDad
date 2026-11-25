# MonitorDad • by HoD

> Sistema de controle parental inteligente, acolhedor e seguro, projetado com a identidade visual **Sage Green & Nature**.

---

## 🌿 Visão Geral do Projeto

O **MonitorDad • by HoD** é composto por dois aplicativos Android complementares:

1. **MonitorDad Parent**: Aplicativo instalado no celular do responsável para monitoramento, concessão de tempo, regras de horário e acompanhamento geográfico.
2. **MonitorDad Kids**: Aplicativo instalado no tablet da criança, proporcionando um ambiente digital seguro, estimulante e acolhedor.

### 🛡️ Requisito Fundamental de Preservação
O tablet da criança possui fotos, desenhos, aplicativos e memórias que **não devem ser apagados**. O MonitorDad **NÃO EXIGE RESTAURAÇÃO DE FÁBRICA** para entrar em operação.

---

## 🎨 Identidade Visual (Sage Green & Nature)

O MonitorDad utiliza uma paleta de cores acolhedora, harmoniosa e natural, evitando intencionalmente estilos policiais, corporativos ou excessivamente técnicos:

| Elemento | Cor | Hexadecimal |
|---|---|---|
| Fundo principal | Bege claro | `#FDFBF7` |
| Verde principal | Sage Green | `#75937B` |
| Verde escuro | Forest Dark | `#465B4B` |
| Verde claro | Sage Light | `#E8EFE9` |
| Verde pastel | Pastel Sage | `#A3B899` |
| Creme secundário | Soft Cream | `#F7F4EC` |
| Branco | White | `#FFFFFF` |
| Texto principal | Dark Foliage | `#2C352E` |
| Texto secundário | Muted Foliage | `#6B7A6E` |
| Status positivo | Leaf Green | `#4E8A63` |
| Alertas | Warm Amber | `#D98A4E` |
| Bloqueio | Soft Coral | `#C85A53` |

---

## 📁 Estrutura do Projeto

```
DoH-Guard/
├── ParentApp/              # Aplicativo Android para o celular dos responsáveis
│   ├── src/main/java/com/dohguard/parent/
│   │   ├── navigation/     # Abas inferiores e roteamento de telas
│   │   ├── screens/        # Visão Geral, Apps & Conteúdo, Mapa GPS, Regras e Modais
│   │   └── MainActivity.kt
│   └── build.gradle.kts
├── KidsApp/                # Aplicativo Android para o tablet da criança
│   ├── src/main/java/com/dohguard/kids/
│   │   ├── screens/        # Tela Inicial Kids, Descanso, Bloqueio e Diálogo de PIN
│   │   └── MainActivity.kt
│   └── build.gradle.kts
├── shared/                 # Biblioteca compartilhada Android
│   ├── src/main/java/com/dohguard/shared/
│   │   ├── theme/          # Paleta de Cores, Tipografia, Shapes e Theme Compose
│   │   ├── model/          # Modelos de Domínio (Device, Apps, Regras, GPS)
│   │   └── components/     # Componentes UI (Header, Cards, Botões, Medidor de Onda)
│   └── build.gradle.kts
├── web-preview/            # Preview web independente (HTML5 / CSS3 / Vanilla JS)
│   ├── index.html          # Interface navegável no browser
│   ├── css/styles.css      # Design System oficial
│   ├── js/app.js           # Lógica interativa demonstrativa e animações
│   ├── js/navigation.js    # Controle de abas e dispositivos
│   ├── PREVIEW_GUIDE.md    # Guia passo a passo de execução
│   └── README.md
├── documentation/          # Memória técnica permanente para continuidade entre IAs
│   ├── PROJECT_STATE.md    # Estado atual e capacidades
│   ├── AI_HANDOVER.md      # Instruções para o próximo agente de IA
│   ├── CHANGELOG.md        # Registro de alterações
│   ├── TODO.md             # Tarefas concluídas, pendentes e bloqueadas
│   ├── BUG_REPORT.md       # Relatório de inconformidades e mitigação
│   ├── ARCHITECTURE.md     # Documentação arquitetural
│   └── TEST_REPORT.md      # Registro formal dos testes executados
├── logs/                   # Histórico de compilação e runtime
│   ├── build.log
│   ├── runtime.log
│   └── latest_build_summary.log
├── build.gradle.kts        # Configuração Gradle raiz
├── settings.gradle.kts     # Declaração dos módulos Gradle
└── gradle/libs.versions.toml # Catálogo centralizado de versões
```

---

## 🌐 Como Executar o Preview Web

Para visualizar e interagir com o MonitorDad no navegador sem precisar compilar o Android:

1. Acesse a pasta `web-preview/`.
2. Dê um duplo clique no arquivo `index.html` para abrir diretamente no Google Chrome ou Edge.
3. Ou, via terminal com servidor local embutido:
   ```bash
   cd web-preview
   python -m http.server 8080
   ```
   Acesse: `http://localhost:8080`

Consulte o [PREVIEW_GUIDE.md](file:///workspace/web-preview/PREVIEW_GUIDE.md) para detalhes de cada recurso interativo.

---

## 🤖 Continuidade entre Agentes de Inteligência Artificial

Este projeto foi concebido para evolução progressiva por diferentes agentes de IA. Antes de qualquer alteração, consulte sempre os arquivos da pasta `documentation/`, com prioridade para:
1. `PROJECT_STATE.md`
2. `AI_HANDOVER.md`
3. `TODO.md`

Ao finalizar cada etapa, atualize obrigatoriamente a documentação e os logs.

---

**MonitorDad • Desenvolvido por HoD**  
*Protegendo sua família com respeito, segurança e harmonia.*
