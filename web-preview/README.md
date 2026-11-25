# Web Preview — MonitorDad • by HoD

Módulo de visualização web independente para o projeto **MonitorDad • by HoD**.

## Finalidade

Permite aos responsáveis, designers e desenvolvedores validar a identidade visual (Sage Green & Nature), o fluxo das telas e a interatividade dos aplicativos **MonitorDad Parent** e **MonitorDad Kids** diretamente no navegador, sem precisar compilar o código Kotlin ou instalar APKs no dispositivo físico a cada iteração.

## Estrutura de Arquivos

```
web-preview/
├── index.html           # Página principal com visualização de MonitorDad Parent e MonitorDad Kids
├── css/
│   └── styles.css       # Estilos completos e paleta oficial Sage Green & Nature
├── js/
│   ├── app.js           # Lógica interativa, animação da onda e dados de demonstração
│   └── navigation.js    # Controle de navegação entre abas, apps e molduras
├── test_runner.html     # Teste automatizado de todas as interações e asserções
├── PREVIEW_GUIDE.md     # Instruções completas de execução e validação
└── README.md            # Este arquivo de documentação
```

## Requisitos e Compatibilidade

- **100% Offline**: Sem dependência de CDNs ou bibliotecas externas.
- **Navegadores suportados**: Google Chrome, Microsoft Edge, Mozilla Firefox, Safari e navegadores mobile Android.
- **Dispositivos**: Adaptável a monitores desktop, notebooks, tablets e smartphones.

Para instruções passo a passo de como rodar e testar, consulte [PREVIEW_GUIDE.md](file:///workspace/web-preview/PREVIEW_GUIDE.md).
