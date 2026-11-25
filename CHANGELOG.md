# Changelog

Todas as mudanças relevantes deste projeto são registradas aqui.
O formato segue o [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/) e o projeto adota o
[Versionamento Semântico 2.0.0](https://semver.org/lang/pt-BR/) (`MAJOR.MINOR.PATCH[-PRÉ-RELEASE]`).

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

[1.0.0-beta.2]: https://github.com/MiguelSantanaDaCosta/Sistema-de-Login-Seguro/releases/tag/v1.0.0-beta.2
[1.0.0-beta]: https://github.com/MiguelSantanaDaCosta/Sistema-de-Login-Seguro/releases/tag/v1.0.0-beta
[1.0.0-alpha]: https://github.com/MiguelSantanaDaCosta/Sistema-de-Login-Seguro/releases/tag/v1.0.0-alpha
