# Guia de Execução do Preview Web — MonitorDad • by HoD

Este guia orienta como visualizar e testar a interface do **MonitorDad • by HoD** diretamente no navegador, sem necessidade de compilar ou instalar os APKs Android.

---

## 1. Como Abrir o Preview Web

Existem duas formas principais de abrir o preview:

### Opção A: Abertura Direta no Navegador (Recomendada para visualização rápida)
1. Localize o arquivo `web-preview/index.html` em seu explorador de arquivos.
2. Dê um duplo clique no arquivo `index.html` ou clique com o botão direito e selecione **"Abrir com" -> Google Chrome / Microsoft Edge**.
3. O preview será carregado imediatamente sem requisições externas.

### Opção B: Servidor Local Leve (Recomendada para desenvolvimento)
Se preferir executar através de um servidor HTTP local:

#### Usando Python (qualquer versão 3.x):
```bash
# Navegue até a pasta do preview
cd web-preview

# Inicie o servidor embutido na porta 8080 (ou qualquer porta de sua preferência)
python -m http.server 8080
```

#### Usando Node.js (npx serve ou http-server):
```bash
cd web-preview
npx http-server -p 8080
```

---

## 2. Qual Endereço Acessar no Navegador

Após iniciar o servidor local, abra o navegador e acesse:

```
http://localhost:8080
```
ou, se estiver executando a partir da raiz do projeto:
```
http://localhost:8080/web-preview/index.html
```

---

## 3. Quais Dependências são Necessárias

- **Nenhuma dependência externa obrigatória.**
- O preview foi construído com **HTML5, CSS3 e JavaScript puro (Vanilla JS)**.
- Não requer Node modules, webpack, Vite, React, nem conexão com a internet.
- Funciona 100% offline em computadores Windows, macOS, Linux e celulares Android.

---

## 4. Recursos e Interatividade Disponíveis no Preview

### Barra Superior de Controles:
- **Alternador de Aplicativo**:
  - `📱 MonitorDad Parent`: Exibe o painel de controle do responsável com suas 4 abas.
  - `🧒 MonitorDad Kids`: Exibe o ambiente seguro do tablet infantil.
- **Alternador de Visualização / Formato**:
  - `Celular`: Simula uma moldura realista de smartphone (390x844).
  - `Tablet`: Simula a proporção de um tablet (768x900).
  - `Expandido`: Layout amplo para visualização em monitores desktop.

### No Painel do Responsável (MonitorDad Parent):
1. **Aba Visão Geral**:
   - Medidor circular de tempo de tela com animação de onda fluida em Canvas.
   - Botão `+15 min`: Adiciona tempo demonstrativo e recalcula a onda e os limites em tempo real.
   - Botão `Pausar / Liberar`: Alterna visualmente o estado de bloqueio e atualiza o badge de status.
   - Botão `Tocar Som` (ícone de sino): Abre modal demonstrativo e emite sinal sonoro suave via Web Audio API.
   - Card de Solicitações Pendentes: Botão `Revisar` abre modal de aprovação/recusa para o pedido de +30 min do YouTube Kids.
   - Lista dos aplicativos mais utilizados hoje com barras de progresso.
2. **Aba Apps & Conteúdo**:
   - Filtros de categoria interativos (`Todos`, `Jogos`, `Vídeos`, `Educação`).
   - Switches de proteção de conteúdo (Navegação Segura DoH, Filtro SafeSearch, Bloqueio de Compras).
   - Lista detalhada de apps com switches visuais para bloquear ou liberar cada app.
3. **Aba Mapa GPS**:
   - Área reservada para mapa ilustrada e harmonizada com a identidade visual Sage Green.
   - Botão `Atualizar`: Simula requisição GPS e atualiza o timestamp da última localização.
   - Lista de Zonas Seguras (Escola, Casa, Casa dos Avós) com indicadores de presença.
   - Linha do tempo dos deslocamentos do dia.
4. **Aba Regras**:
   - Configurações visuais de Horário de Dormir e Modo Aula com switches.
   - Lista de Contatos Autorizados (com destaque SOS para pais).
   - Informações técnicas do tablet e status de sincronização.
   - Assinatura oficial: **MonitorDad • Desenvolvido por HoD**.

### No Ambiente Infantil (MonitorDad Kids):
- Saudação acolhedora para a criança.
- Card de tempo restante para brincar.
- Grid de aplicativos autorizados e liberados.
- Botão `Ver Descanso`: Exibe a tela de descanso ocular com dicas saudáveis.
- Botão `Ver Bloqueio`: Exibe a tela de repouso noturno com opção de solicitar mais tempo.
- Botão `Acesso dos Pais`: Abre modal de autenticação por PIN com teclado numérico (PIN demonstrativo: `1234`).

---

## 5. Como Atualizar a Interface

Para aplicar alterações visuais ou funcionais:
1. Edite os arquivos:
   - `web-preview/index.html` (estrutura)
   - `web-preview/css/styles.css` (estilos e paleta)
   - `web-preview/js/app.js` (dados de demonstração e interatividades)
   - `web-preview/js/navigation.js` (navegação e alternância de abas/telas)
2. Salve os arquivos modificados.
3. No navegador, pressione `Ctrl + F5` (ou `F5`) para recarregar a página e ver as atualizações imediatamente.

---

## 6. Como Verificar se Está Funcionando

1. Ao carregar a página, a onda verde dentro do círculo de tempo de tela deve ondular continuamente.
2. Clique no botão `+15 min`: o valor de limite deve aumentar imediatamente e a onda deve se reajustar.
3. Clique nas quatro abas inferiores (`Visão Geral`, `Apps`, `Mapa GPS`, `Regras`): a tela deve rolar suavemente para o topo e exibir o conteúdo da respectiva aba.
4. Alterne para o modo `🧒 MonitorDad Kids` no topo: a tela do tablet infantil deve aparecer.
5. Clique em `🔒 Acesso dos Pais`, digite `1`, `2`, `3`, `4` no teclado na tela: o modal deve fechar e emitir um aviso de sucesso.
6. Não devem existir erros no console do navegador (abra pressionando `F12` -> aba `Console`).
