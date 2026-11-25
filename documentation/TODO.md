# Lista de Tarefas — MonitorDad • by HoD

## Concluído
- [x] Arquitetura unificada `ConnectionManager` (Local Wi-Fi <-> Internet + failover automático).
- [x] `LocalHttpTransport`, `LocalHttpServer`, `LocalKidsServer` e `LocalPairingServer` com `NsdManager`.
- [x] `PairingManager` (uso único, expiração, bloqueio pós-5 falhas, revogação) e `QrCodeGenerator` em Kotlin puro.
- [x] Central "Conexão e dispositivos", Assistente guiado (4 passos) e Diagnóstico real (6 etapas) em `ParentApp` e `KidsApp`.
- [x] Rate limiting anti-força bruta no `backend/` e persistência em PostgreSQL via `SupabaseRelayStore` (`npm test` 9/9 PASS).
- [x] Schema SQL com RLS (`backend/supabase/schema.sql`), migração `001_grant_service_role.sql` e guia (`documentation/REMOTE_DEPLOY_GUIDE.md`).
- [x] Seção **EM USO AGORA** no `ParentApp`, cronômetro monotônico em tempo real no `KidsApp` e identidade visual roxa/verde neon (`v1.4.0`).
- [x] Testes unitários JVM em `UnifiedConnectionArchitectureTest.kt`.

## Pendente (Fora do Sandbox)
- [ ] Executar `git push origin master` para validar o build completo no GitHub Actions (`:shared:testDebugUnitTest`, `:ParentApp:assembleDebug`, `:KidsApp:assembleDebug`).
- [ ] Criar projeto gratuito no Supabase, rodar `backend/supabase/schema.sql` e publicar `backend/` no Render Free (`backend/render.yaml`) com `SUPABASE_URL` e `SUPABASE_SERVICE_ROLE_KEY`.
- [ ] Instalar APKs no celular (Parent) e no tablet (Kids) e validar pareamento + conexão local e remota seguindo `documentation/TEST_REPORT.md`.
