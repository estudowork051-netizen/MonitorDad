# Guia de Deploy Gratuito — MonitorDad Relay (Render Free + Supabase Free)

Este guia explica como publicar o servidor Relay do **MonitorDad • by HoD** na internet usando **planos 100% gratuitos** (sem necessidade de cartão de crédito), com **HTTPS automático** e **banco de dados persistente**.

---

## Por que Render + Supabase?
- **Render Free (Web Service)**: hospeda o servidor Node.js 22 com HTTPS automático (`https://...onrender.com`). Porém, no plano gratuito, o disco local é temporário (reinicia quando o serviço hiberna ou é atualizado).
- **Supabase Free (PostgreSQL)**: mantém pareamentos, credenciais dos dispositivos, regras, comandos, confirmações (ACK) e telemetria salvos permanentemente no banco de dados.

---

## Passo 1 — Criar o banco de dados gratuito no Supabase

1. Acesse [https://supabase.com](https://supabase.com) e clique em **Start your project** (faça login com sua conta GitHub). **Não pede cartão de crédito.**
2. Clique em **New Project**:
   - **Name**: `monitordad-relay` (ou o nome que preferir).
   - **Database Password**: crie uma senha forte e guarde com segurança (não será necessário colocá-la no código).
   - **Region**: escolha a região mais próxima (ex.: `South America (São Paulo)`).
   - **Plan**: selecione **Free ($0/month)**.
3. Aguarde ~1–2 minutos até o projeto ficar pronto.
4. No menu lateral esquerdo do Supabase, abra **SQL Editor** → **New query**.
5. Copie todo o conteúdo do arquivo `backend/supabase/schema.sql` do repositório, cole no editor e clique em **Run**.
   - Isso cria as tabelas `relay_pairings` e `relay_devices` com **Row Level Security (RLS)** ativado (nenhum acesso público anônimo é permitido).
6. Agora pegue as duas chaves para o servidor:
   - No menu lateral, vá em **Project Settings** (ícone de engrenagem) → **API** (ou **Data API / API Keys**).
   - Copie e guarde temporariamente:
     - **Project URL** (`https://xxxxxxxxxxxx.supabase.co`) → será a variável `SUPABASE_URL`.
     - **service_role** (chave secreta) → será a variável `SUPABASE_SERVICE_ROLE_KEY`.
   - ⚠️ **Importante:** nunca compartilhe a chave `service_role` e nunca a coloque no código-fonte ou no aplicativo Android. Ela fica somente nas variáveis de ambiente do Render.

---

## Passo 2 — Publicar o backend gratuitamente no Render

1. Envie suas alterações para o GitHub (`git push origin master`).
2. Acesse [https://render.com](https://render.com) e faça login com sua conta **GitHub**. **Não pede cartão de crédito para Web Services Free.**
3. Clique em **New +** → **Blueprint** (ou **Web Service**):
   - **Opção recomendada (Blueprint):** selecione o repositório do MonitorDad. O Render detectará automaticamente o arquivo `backend/render.yaml`.
   - **Opção manual (Web Service):** se preferir criar manualmente:
     - **Root Directory**: `backend`
     - **Runtime**: `Node`
     - **Build Command**: `echo 'Zero runtime dependencies'`
     - **Start Command**: `node src/index.mjs`
     - **Instance Type**: `Free`
4. Na tela de **Environment Variables** do serviço no Render, preencha as duas variáveis:
   - `SUPABASE_URL` = cole a **Project URL** do Supabase (`https://...supabase.co`).
   - `SUPABASE_SERVICE_ROLE_KEY` = cole a chave **service_role** do Supabase.
5. Clique em **Deploy** / **Create Web Service** e aguarde a conclusão.
6. Quando o status ficar **Live**, copie a URL HTTPS exibida no topo do painel do Render (exemplo: `https://monitordad-relay.onrender.com`).

---

## Passo 3 — Verificar se o servidor está usando o banco persistente

1. Abra no navegador o endereço:
   `https://SUA-URL-DO-RENDER.onrender.com/health`
2. O resultado esperado é semelhante a:
   ```json
   {
     "status": "ok",
     "service": "monitordad-relay",
     "storage": "supabase-postgres",
     "uptimeSeconds": 12
   }
   ```
3. Confirme que `"storage"` está exatamente como `"supabase-postgres"`.
   - Se aparecer `"local-file"`, significa que `SUPABASE_URL` ou `SUPABASE_SERVICE_ROLE_KEY` não foram preenchidas corretamente no Render.

---

## Passo 4 — Configurar nos aplicativos MonitorDad Parent e Kids

1. Abra o **MonitorDad Parent** e o **MonitorDad Kids**.
2. Em ambos, acesse **Conexão e dispositivos → Configurações avançadas**.
3. No campo de endereço do servidor remoto, informe a URL HTTPS do Render (ex.: `https://monitordad-relay.onrender.com`).
4. Salve e toque em **Testar conexão**.
5. Se os dois aparelhos já estavam pareados no Wi-Fi local, o provisionamento no servidor remoto acontece automaticamente — **não é necessário parear novamente**.

---

## Observação sobre hibernação no plano gratuito do Render
No plano **Free**, o Render coloca o servidor em repouso após 15 minutos sem requisições; na primeira requisição seguinte, o servidor leva ~20 a 30 segundos para acordar. Os aplicativos já estão configurados com timeout tolerante (35 segundos de leitura) e fila offline automática para lidar com esse despertar sem perder comandos ou regras.
