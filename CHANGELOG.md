# Changelog

Todas as mudanças relevantes deste projeto são registradas aqui.
O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o projeto adota o
[Versionamento Semântico 2.0.0](https://semver.org/lang/pt-BR/) (`MAJOR.MINOR.PATCH[-PRÉ-RELEASE]`).

## [1.0.0-beta.5] - 2026-11-25

### Adicionado
- **Páginas que faltavam:** Técnicos (equipe ativa), Tipo de chamado e Recursos (catálogos gerenciados por
  administradores) e Clientes com cadastro, edição e exclusão de verdade (antes os botões eram links mortos).
- **Horários de atendimento:** formulário e exclusão exclusivos de administrador, validação do intervalo
  (fim depois do início), estado vazio e mensagens de erro no próprio formulário.
- **Tipo de chamado na abertura:** o formulário lista o catálogo e grava o tipo escolhido junto do chamado.
- **Reenviar confirmação de cadastro:** página `/reenviar-confirmacao` (também ligada no login e no cadastro) para
  quem perdeu o email de ativação, com o mesmo limite de 60 s entre pedidos.
- **Login por usuário ou email** e mensagem específica para conta ainda não confirmada (somente depois de conferir
  a senha, para não revelar contas).
- **Sessão expirada com explicação:** páginas redirecionam para `/login?sessao=expirada`; APIs respondem 401/403 em
  JSON em vez de devolver HTML.
- Testes `JwtAuthFilterTest`, `PaginasTest` e `RegistroControllerTest` (27 testes no total).

### Corrigido
- A página inicial anônima e as demais páginas não devolvem mais **403 em JSON**: o visitante vai para o login.
- **404** em `/tecnicos`, `/tipo-chamado` e `/recursos`; **500** em `/ajustes-horarios` quando o formulário era
  aberto sem um horário novo no modelo.
- Cookie JWT de conta removida ou desativada é apagado sem derrubar a tela de login.
- Cadastro e redefinição de senha contam os **72 bytes do BCrypt** (acentos ocupam mais de um byte), normalizam
  usuário/email e exigem confirmação da senha também no servidor.
- Limite de pedidos de email não deixa passar uma tentativa a mais e o executor de envio é encerrado no shutdown.
- Login já autenticado volta ao dashboard e `/puzzle` redireciona para `/login`.
- Lance de **promoção** (peão chegando à última fileira) passou a ser enviado com a peça escolhida no puzzle de
  login e no desafio da Rainha Vermelha.
- Menu móvel mostra os rótulos (antes só os ícones), abre o submenu e anuncia corretamente o estado ao leitor de
  tela; ícones decorativos foram escondidos da acessibilidade.

## [1.0.0-beta.4] - 2026-11-25

### Adicionado
- **Esqueci minha senha:** link na tela de login → `/esqueci-senha` pede o email e envia um link de uso único
  (válido por 30 minutos) para `/redefinir-senha`, onde se cria a nova senha (com confirmação). A resposta é sempre a
  mesma exista ou não a conta, o envio sai em segundo plano (sem vazar pelo tempo de resposta) e há intervalo mínimo
  de 60 s entre emails da mesma conta e limite de pedidos por IP. Depois da troca, o link é invalidado, o puzzle em
  andamento e os links de login antigos são descartados e o dono recebe um aviso por email.
- **Reenvio da confirmação de cadastro:** quem nunca confirmou o email recebe o link de confirmação ao usar
  "Esqueci minha senha" (antes ficava preso com "Credenciais inválidas").
- **Botão de olho** (mostrar/ocultar senha) no login, no cadastro e na redefinição.

### Corrigido
- O envio de email **não finge mais sucesso**: se o SMTP falhar, o cadastro avisa que o email não saiu e indica o
  "Esqueci minha senha"; o login (após o puzzle) responde 503 com orientação em vez de dizer que o link foi enviado.
- O email passa a sair com remetente definido (`Thindesk <usuário SMTP>`).

### Alterado
- Novo tipo de token `REDEFINICAO` e novo campo `criadoEm` nos tokens de email.

## [1.0.0-beta.3] - 2026-11-25

### Adicionado
- **Desafio do Cavalo Verde** (♞): usuário logado resolve 3 puzzles seguidos de rating 1900–2600 em 5 minutos e é
  promovido a **Técnico**.
- **Desafio da Torre Azul** (♜): usuário logado resolve 5 puzzles seguidos de rating 2600–3000 em 5 minutos e é
  promovido a **Administrador**.
- Ambos usam o mesmo mecanismo da Rainha Vermelha (estado no servidor, um erro reinicia) e a **mesma proteção
  contra bots e IAs** (bloqueio de IP por 1 hora, HTTP 418). Contas demo e usuários que já têm o perfil não participam.
- Ícones dos desafios no canto da tela (cavalo verde, torre azul) e aviso no dashboard ao ser promovido.
- Mais ~1,5 mil puzzles de rating 2600–2999 para o desafio da torre.
- Teste unitário `NomeUsuarioTest` com a regra do nome de usuário.

### Alterado
- **Nome de usuário no cadastro:** passa a aceitar letras latinas com acento (`joão`, `José`), espaço entre palavras e
  `. _ - @ +` (antes só letras ASCII, números e `. _ -`). Letras de outros alfabetos continuam recusadas para evitar nomes
  visualmente idênticos aos de outras pessoas; a acentuação é normalizada (NFC) no cadastro e no login.
- Todo cadastro começa com o perfil **Usuário**; a evolução de perfil acontece pelos desafios (ou por um administrador).
- O serviço do desafio da Rainha Vermelha foi generalizado para os três tipos de desafio.

## [1.0.0-beta.2] - 2026-11-25

### Alterado
- **Hierarquia única de papéis** (`ROOT › ADMIN › TECNICO › USUARIO`) em `Role`: o perfil mais alto passa a ter
  os papéis dos mais baixos, tanto nas permissões do Thindesk quanto na claim `roles` do JWT enviada ao `ms-chamados`.
- **Contas de demonstração deixam de ser fixas no código**: um ADMIN as cria, redefine e remove em
  *Contas demo* (`/admin/demos`), para os perfis USUARIO, TECNICO e ADMIN (ROOT não é permitido).
- O administrador agora vem do ambiente (`ADMIN_USERNAME`, `ADMIN_PASSWORD`, `ADMIN_EMAIL`); se o usuário
  configurado mudar, o admin padrão antigo é promovido em vez de permanecer com a senha padrão.

### Removido
- Criação automática das contas `demo` e `demo2` e suas senhas padrão no código e no `application.properties`.

## [1.0.0-beta] - 2026-11-25

Primeira versão funcional de ponta a ponta (aplicação web + microserviço de chamados + deploy).

### Adicionado
- **Chamados técnicos** integrados ao microserviço `ms-chamados`: abrir, acompanhar e resolver, com o
  usuário repassado em JWT com os papéis (claim `roles`).
- **Dashboard** inicial com indicadores, barra de andamento, últimos chamados e fila da equipe.
- **Puzzles por perfil:** faixas de rating para `USUARIO`, `TECNICO` e `ADMIN`, e conta do dono com puzzles
  fáceis definida por variável de ambiente; novo CSV com ~1,9 mil puzzles de rating ≥ 3000.
- **Perfil `ROOT`** (recurso opcional, desligado por padrão): desafio da rainha vermelha e painel de banco de dados.
- **Proteção contra bots e IAs:** bloqueio de IP por 1 hora e resposta HTTP 418.
- **Contas de demonstração** `demo` (somente senha) e `demo2` (admin, com puzzle e sem email).
- **Análise de puzzles × partidas reais do Lichess** exibida no dashboard.
- **Deploy:** Dockerfile, Docker Compose, HTTPS automático com Caddy (Let's Encrypt) e versão exibida na barra lateral.

### Corrigido
- Telas que usam o layout retornavam erro 500 no Thymeleaf 3.1 (`th:replace` com string completa).
- Importador de puzzles lento (um upsert por documento) e que só importava puzzles fáceis.

### Segurança
- O perfil `ROOT` não pode ser atribuído pela tela de administração.
- O IP real do cliente é lido de `X-Forwarded-For` apenas de proxies da rede interna do Docker.

## [1.0.0-alpha] - 2026-10-09

### Adicionado
- Cadastro público em 2 etapas com confirmação por email.
- Login com senha (BCrypt), sessão em JWT dentro de cookie `HttpOnly` e perfis `ADMIN`, `TECNICO` e `USUARIO`.
- Segundo fator por puzzle de xadrez (puzzles públicos do Lichess) e terceiro fator por link enviado por email.
- Temas visuais plugáveis e persistência no MongoDB Atlas.

[1.0.0-beta.5]: https://github.com/MiguelSantanaDaCosta/Sistema-de-Login-Seguro/releases/tag/v1.0.0-beta.5
[1.0.0-beta.4]: https://github.com/MiguelSantanaDaCosta/Sistema-de-Login-Seguro/releases/tag/v1.0.0-beta.4
[1.0.0-beta.3]: https://github.com/MiguelSantanaDaCosta/Sistema-de-Login-Seguro/releases/tag/v1.0.0-beta.3
[1.0.0-beta.2]: https://github.com/MiguelSantanaDaCosta/Sistema-de-Login-Seguro/releases/tag/v1.0.0-beta.2
[1.0.0-beta]: https://github.com/MiguelSantanaDaCosta/Sistema-de-Login-Seguro/releases/tag/v1.0.0-beta
[1.0.0-alpha]: https://github.com/MiguelSantanaDaCosta/Sistema-de-Login-Seguro/releases/tag/v1.0.0-alpha
