# 🔐 Thindesk — Sistema de Login Seguro com Spring Boot, Thymeleaf e MongoDB Atlas

Sistema de **autenticação e autorização** construído em Java Spring Boot + Thymeleaf + MongoDB Atlas, projetado de forma **modular e genérica**: a camada de segurança é independente da regra de negócio, permitindo que o tema visual e o domínio da aplicação sejam substituídos sem alterar a lógica central de autenticação.

> O projeto vem com um **tema plugável de demonstração** (puzzle de xadrez como segundo fator de autenticação), mas esse tema é opcional — dá para remover trocando **um service** e **um modal**.

---

## 📚 Sumário

1. [Visão Geral](#visão-geral)
2. [Stack](#stack)
3. [Arquitetura](#arquitetura)
4. [Fluxos de Autenticação](#fluxos-de-autenticação)
5. [Configuração](#configuração)
6. [Execução local](#execução-local)
7. [MongoDB Atlas](#mongodb-atlas)
8. [Passo a passo — testar no terminal e no navegador](#passo-a-passo--testar-no-terminal-e-no-navegador)
9. [Estrutura do Projeto](#estrutura-do-projeto)
10. [Decisões de Design](#decisões-de-design)
11. [Como adaptar a base a outro tema](#como-adaptar-a-base-a-outro-tema)
12. [Testes](#testes)
13. [tema de xadrez](#apêndice-a--base-genérica-vs-tema-de-xadrez)
14. [Diagrama do fluxo de login](#apêndice-b--diagrama-do-fluxo-de-login)
15. [Licença](#licença)

---

## 🎯 Visão Geral

- **Cadastro público** em 2 etapas: cria usuário `PENDENTE` → envia link de confirmação por email → ativa a conta.
- **Login** com senha (BCrypt).
- **Autorização por perfil (Role)** com 3 perfis: `ROLE_ADMIN`, `ROLE_TECNICO`, `ROLE_USUARIO`.
- **Sessão** via **JWT em cookie HttpOnly** (`thindesk_auth`) — imune a leitura por XSS.
- **Segundo fator opcional** (no tema de demonstração, puzzle de xadrez) + **terceiro fator** por link de confirmação em email (token opaco de uso único com TTL no MongoDB).
- **Temas visuais plugáveis** — cada usuário escolhe seu tema; adicionar um tema novo = adicionar 1 arquivo CSS.
- **Defesa em profundidade** — `SecurityConfig` **e** `@PreAuthorize` nos controllers.

---

## 🧱 Stack

| Camada        | Tecnologia                                    |
|---------------|-----------------------------------------------|
| Linguagem     | Java 21                                       |
| Framework     | Spring Boot 3.4                               |
| Segurança     | Spring Security 6 + JWT (jjwt 0.12)           |
| Persistência  | Spring Data MongoDB 4.4 (Atlas ou local)      |
| View          | Thymeleaf 3 + thymeleaf-layout-dialect        |
| Frontend      | Bootstrap 5 + JavaScript (fetch API)          |
| Hash          | BCrypt (via Spring Security)                  |
| Email         | Spring Boot Starter Mail (SMTP)               |
| Build         | Maven (com wrapper `./mvnw`)                  |

---

## 🏛 Arquitetura

```
┌─────────────────────────────────────────────────────────────────┐
│  Navegador (Thymeleaf + fetch API + cookie HttpOnly)            │
└───────────────────────────┬─────────────────────────────────────┘
                            │ HTTP
┌───────────────────────────▼─────────────────────────────────────┐
│  Filtro: JwtAuthFilter (lê cookie/header, popula SecurityContext)
├─────────────────────────────────────────────────────────────────┤
│  Controllers: /api/auth/*  /admin/*  /tecnico/*  /              │
├─────────────────────────────────────────────────────────────────┤
│  Services: UsuarioService · EmailService · EmailTokenService    │
│            ThemeService · (services de domínio)                 │
├─────────────────────────────────────────────────────────────────┤
│  Repositories (Spring Data Mongo)                               │
├─────────────────────────────────────────────────────────────────┤
│  MongoDB Atlas                                                  │
│   coleções: usuarios · email_tokens (TTL) · theme_config        │
└─────────────────────────────────────────────────────────────────┘
```

**Regra de ouro da modularização:** Controllers só falam com Services; Services só falam com Repositories; nada de `Entity` direto no HTML — o controller monta `Model` ou devolve DTO.

---

## 🔀 Fluxos de Autenticação

### Cadastro público (2 etapas)

```
[POST /api/auth/registrar]
    └── valida (Bean Validation) → cria Usuario{ativo=false}
        └── gera token REGISTRO (TTL 24h, hash SHA-256 no Mongo)
            └── envia email com link /auth/confirmar-registro?token=...
                  └── [GET /auth/confirmar-registro] → Usuario{ativo=true}
                        └── redirect /login?confirmado=1
```

### Login (senha → JWT final)

```
[POST /api/auth/login]
    └── AuthenticationManager valida senha (BCrypt)
        └── emite JWT NÃO-final (claim preAuth=true) em cookie thindesk_pre
            └── (fator intermediário opcional — no Thindesk, puzzle de xadrez)
                └── resolve → envia link de login por email
                    └── [GET /auth/confirmar?token=...] consome token
                        └── emite JWT FINAL em cookie thindesk_auth
                            └── redirect /
```

> **Genericização:** se o segundo fator não for desejado, basta emitir o JWT final já no `/login` e remover `ConfirmacaoController` + `EmailTokenService`.

### Logout

```
[POST /api/auth/logout] → limpa os dois cookies (thindesk_auth e thindesk_pre)
```

---

## ⚙️ Configuração

Copie `.env.example` → `.env` e preencha:

```dotenv
SERVER_PORT=8000
MONGO_URI=mongodb+srv://USUARIO:SENHA@cluster0.xxxxx.mongodb.net/thindesk?retryWrites=true&w=majority
JWT_SECRET=<base64 de pelo menos 32 bytes>   # openssl rand -base64 64
PUZZLE_RATING_MAX=1100
PUZZLE_TEMPO_LIMITE=300
PUZZLE_MAX_POR_SESSAO=3
APP_BASE_URL=http://localhost:8000
ADMIN_EMAIL=admin@thindesk.local
MAIL_ENABLED=false
MAIL_HOST=smtp.mailtrap.io
MAIL_PORT=587
MAIL_USERNAME=
MAIL_PASSWORD=
DEV_EXPOR_LINK=false   # true SOMENTE em testes automatizados
```

**Nunca** comite o `.env` real — ele já está no `.gitignore`.

---

## 🚀 Execução local

Pré-requisitos: **JDK 21** e conexão com um MongoDB (Atlas ou local).

```bash
# 1. Clonar e entrar
git clone https://github.com/<seu-usuario>/<seu-repo>.git
cd <seu-repo>

# 2. Copiar variáveis de ambiente
cp .env.example .env
# edite .env com MONGO_URI e JWT_SECRET

# 3. Rodar (o Maven Wrapper baixa o Maven sozinho)
./mvnw spring-boot:run        # Linux/macOS
mvnw.cmd spring-boot:run      # Windows

# 4. Abrir
http://localhost:8000/login
```

**Usuário seed** (criado automaticamente na 1ª execução): `admin` / `admin123`
(alteração obrigatória em produção).

---

## ☁️ MongoDB Atlas

1. Crie uma conta gratuita em https://cloud.mongodb.com
2. Crie um cluster **M0 (free)** e um database user (senha forte).
3. Em **Network Access** libere o IP (dev: `0.0.0.0/0`; produção: IP fixo).
4. Em **Database → Connect → Drivers**, copie a URI `mongodb+srv://...` para `MONGO_URI` no `.env`.
5. `spring.data.mongodb.auto-index-creation=true` já cria:
   - índices únicos em `usuarios.username` e `usuarios.email`
   - **índice TTL** em `email_tokens.expiraEm` (o MongoDB apaga sozinho)

---

## 🧪 Passo a passo — testar no terminal e no navegador

Esta seção assume que a app **já está no ar** (`./mvnw spring-boot:run`) escutando em `http://localhost:8000`, e que `MONGO_URI` e `JWT_SECRET` estão preenchidos no `.env`.

> **Dica geral:** abra **dois terminais** — um com o servidor rodando (para ver o log, especialmente o link de confirmação que aparece quando `MAIL_ENABLED=false`) e outro para disparar os `curl`.

---

### Parte 1 — Testando no terminal (via `curl`)

#### 1.1. Verificar se a app subiu

```bash
curl -i http://localhost:8000/login
```

**Esperado:** `HTTP/1.1 200` e HTML com o campo `id="username"`.

Se der `Connection refused`, o servidor não subiu — confira o log.

#### 1.2. Criar uma conta (etapa 1 do cadastro)

```bash
curl -i -X POST http://localhost:8000/api/auth/registrar \
  -H "Content-Type: application/json" \
  -d '{
    "nomeCompleto": "Maria Teste",
    "username": "maria",
    "email": "maria@exemplo.com",
    "password": "senha123"
  }'
```

**Esperado:** `HTTP/1.1 201` com:

```json
{
  "mensagem": "Cadastro recebido! Enviamos um link de confirmação para o seu email. Clique nele para ativar a conta.",
  "aguardandoConfirmacao": true
}
```

**No log do servidor**, como `MAIL_ENABLED=false`, você vai ver algo assim:

```
=== MAIL DESABILITADO — Thindesk — Confirme o seu cadastro ===
Para: maria@exemplo.com
Link: http://localhost:8000/auth/confirmar-registro?token=XXXXXXXXXXXX
================================================================
```

Copie esse link. Se você habilitou `DEV_EXPOR_LINK=true`, o link também vem no JSON (`linkConfirmacao`).

#### 1.3. Confirmar o cadastro

```bash
# troque pelo token que apareceu no log
curl -i "http://localhost:8000/auth/confirmar-registro?token=XXXXXXXXXXXX"
```

**Esperado:** `HTTP/1.1 302` com `Location: /login?confirmado=1`.

No Mongo (Atlas ou `mongosh`), o usuário `maria` agora tem `ativo: true` e `emailConfirmado: true`:

```javascript
// mongosh
use thindesk
db.usuarios.findOne({ username: "maria" }, { ativo: 1, emailConfirmado: 1 })
```

#### 1.4. Login — etapa 1 (senha)

```bash
curl -i -c cookies.txt -X POST http://localhost:8000/api/auth/login \
  -d "username=maria&password=senha123"
```

**Esperado:** `HTTP/1.1 200`, um header `Authorization: Bearer <preAuth>` e um `Set-Cookie: thindesk_pre=...`. O `-c cookies.txt` salva os cookies para os próximos passos.

**No corpo do JSON**, o servidor devolve o **FEN do puzzle** e o prazo:

```json
{
  "mensagem": "Credenciais válidas. Resolva o puzzle de xadrez para continuar.",
  "puzzleId": "abc123",
  "fen": "r1bqk2r/...",
  "vez": "brancas",
  "rating": 1050,
  "expiraEmSegundos": 300,
  "maxTentativas": 5,
  "restantes": 5,
  "puzzleNumero": 1,
  "puzzlesMax": 3
}
```

> **Se você não quiser testar o xadrez no terminal**, pule para "1.5 alternativo" mais abaixo. Caso contrário, anote o `puzzleId`.

#### 1.5. Resolver o puzzle (o caminho "de verdade")

Para saber qual é o lance correto **sem depender do navegador**, consulte o Mongo:

```javascript
// mongosh — descubra o puzzle atual do usuário
use thindesk
const u = db.usuarios.findOne({ username: "maria" })
db.puzzles_xadrez.findOne({ _id: u.puzzleAtualId }, { lanceCorreto: 1, descricao: 1 })
```

Suponha que a resposta seja `e2e4`. Envie:

```bash
curl -i -b cookies.txt -X POST http://localhost:8000/api/auth/resolver-puzzle \
  -d "lanceFen=e2e4&puzzleId=abc123"
```

- **Acertou** → `HTTP/1.1 200` com `aguardandoConfirmacao: true` e `emailMascarado: "m***@exemplo.com"`. No log aparece o **link de login**:

  ```
  === MAIL DESABILITADO — Thindesk — Confirmação de login ===
  Link: http://localhost:8000/auth/confirmar?token=YYYYYYYYYY
  ==========================================================
  ```

- **Errou** → `HTTP/1.1 403` com `restantes`. A cada 5 erros ou ao estourar o prazo, o servidor manda um `novoPuzzle` no corpo.

#### 1.5 alternativo — Testar sem o puzzle

Se quiser pular o xadrez (por exemplo, para testar só auth):

1. No `.env`, `MAIL_ENABLED=true` e configure um SMTP de teste (Mailtrap).
2. Ou use `DEV_EXPOR_LINK=true` e capture o `linkConfirmacao` direto do JSON (tanto no `/registrar` quanto no `/resolver-puzzle`).

#### 1.6. Concluir o login (link por email)

```bash
curl -i -c cookies.txt "http://localhost:8000/auth/confirmar?token=YYYYYYYYYY"
```

**Esperado:** `HTTP/1.1 302` com `Location: /` e um `Set-Cookie: thindesk_auth=<JWT final>`. O cookie `thindesk_pre` é **limpo** (`Max-Age=0`) — é o comportamento correto.

#### 1.7. Acessar uma rota autenticada

```bash
curl -i -b cookies.txt http://localhost:8000/admin/usuarios
```

- Logado como `maria` (ROLE_USUARIO) → `HTTP/1.1 403 Forbidden`.
- Logado como `admin` → `HTTP/1.1 200` e o HTML da página.

#### 1.8. Logout

```bash
curl -i -b cookies.txt -X POST http://localhost:8000/api/auth/logout
```

**Esperado:** `HTTP/1.1 200` e dois `Set-Cookie` zerados (`thindesk_auth` e `thindesk_pre`).

#### 1.9. Testar os casos de erro

| Cenário | Comando | Esperado |
|---------|---------|----------|
| Login com senha errada | `curl -i -X POST http://localhost:8000/api/auth/login -d "username=maria&password=errada"` | `401` |
| Registro duplicado | repetir o `POST /api/auth/registrar` de `1.2` | `409` |
| Registro com email inválido | `POST /registrar` com `"email":"a@b"` | `400` com `campos.email` |
| Link de email reaproveitado | chamar `GET /auth/confirmar?token=...` de novo | `302` → `/login?erro=link-invalido` |
| Sem cookie em rota autenticada | `curl -i http://localhost:8000/admin/usuarios` | `302` → `/login` |

---

### Parte 2 — Testando no navegador (fluxo manual)

#### 2.1. Cadastro

1. Abra **http://localhost:8000/registrar**.
2. Preencha os campos e clique em **Cadastrar**.
3. A página mostra a mensagem verde *"Cadastro recebido! Enviamos um link de confirmação..."*
4. Vá ao terminal onde o servidor está rodando e **copie o link** impresso no log.
5. Cole o link no navegador → você cai em `/login?confirmado=1` com a mensagem *"Email confirmado! Agora você já pode entrar."*

#### 2.2. Login + puzzle

1. Em **http://localhost:8000/login**, entre com `maria` / `senha123`.
2. O pop-up **"Verificação de segurança"** abre automaticamente com o tabuleiro.
3. No cabeçalho você vê: cronômetro (`05:00`), barra de progresso, vez de quem joga, número do puzzle (`Puzzle 1/3`) e bolinhas de tentativas.
4. **Arraste a peça** para o lance que você acha correto.
   - **Acertou:** o pop-up muda para *"Puzzle resolvido! Enviamos um link de confirmação para m***@exemplo.com."*
   - **Errou:** a casa fica vermelha, a peça volta, e o contador de bolinhas avança.
   - **Tempo esgotou:** o JS chama `/api/auth/novo-puzzle` sozinho e carrega outro puzzle (contando no `Puzzle N/3`).

> **Atalho de teste:** para saber o lance correto sem consultar o Mongo, abra o DevTools → **Network** → clique no request `POST /api/auth/login` → veja o JSON. Ainda assim, `lanceCorreto` **não** vai no JSON (é de propósito). Use o `mongosh` da seção 1.5 ou abra o puzzle em `http://localhost:8000/puzzle?fen=<fen>&puzzleId=<id>` para inspecionar visualmente (mas o correto continua no servidor).

#### 2.3. Confirmar o login pelo link

1. Copie o **link de confirmação de login** impresso no terminal.
2. Cole no navegador.
3. Você é redirecionado para `/` — já autenticado. Verifique o cookie no DevTools → **Application → Cookies**:
   - `thindesk_auth` → presente, `HttpOnly ✓`, `SameSite=Lax`.
   - `thindesk_pre` → ausente (foi limpo).

#### 2.4. Navegar por áreas restritas

| Logado como | Página | Resultado |
|-------------|--------|-----------|
| `maria` (ROLE_USUARIO) | `/admin/usuarios` | 403 → página de erro |
| `maria` | `/tecnico/chamados` | 403 → página de erro |
| `admin` | `/admin/usuarios` | lista de usuários |
| `admin` | `/tecnico/chamados` | fila técnica |
| Qualquer autenticado | `/`, `/chamados`, `/clientes` | OK |

#### 2.5. Trocar o tema

1. Autenticado, dispare um POST pelo console do DevTools (ou crie um botão):

   ```javascript
   fetch("/tema", {
     method: "POST",
     body: new URLSearchParams({ tema: "gruvbox-dark", redirect: "/" })
   }).then(() => location.reload());
   ```

2. A página recarrega com a paleta Gruvbox Dark. Recarregue de novo → o tema **persiste** (foi salvo em `theme_config`).

#### 2.6. Logout

1. Clique em **Sair** na sidebar.
2. Você volta ao `/login`. Os dois cookies foram apagados.

---

### Parte 3 — Verificar o estado no MongoDB

Depois de rodar o fluxo, cheque o banco:

```javascript
// mongosh <MONGO_URI>
use thindesk

// usuários e seus estados
db.usuarios.find({}, { username: 1, ativo: 1, emailConfirmado: 1, roles: 1 })

// tokens de email (devem sumir sozinhos após o TTL)
db.email_tokens.find({}, { username: 1, tipo: 1, expiraEm: 1 })

// tema salvo
db.theme_config.find()
```

Índices criados automaticamente (confirme com `db.usuarios.getIndexes()` e `db.email_tokens.getIndexes()`):

- `usuarios.username` → **unique**
- `usuarios.email` → **unique**
- `email_tokens.tokenHash` → **unique**
- `email_tokens.expiraEm` → **TTL** (`expireAfterSeconds: 0`)

---

### Parte 4 — Encerrando o servidor

No terminal onde o `./mvnw spring-boot:run` está rodando: **Ctrl+C**.

Se quiser apagar o estado dos testes:

```javascript
// mongosh
use thindesk
db.usuarios.deleteMany({ username: { $in: ["maria"] } })
db.email_tokens.deleteMany({ username: "maria" })
db.theme_config.deleteMany({ username: "maria" })
```

---

## 📁 Estrutura do Projeto

```
src/main/java/com/pfc/
├── ThindeskApplication.java              ← @SpringBootApplication
├── SecurityConfig.java                   ← regras de URL + BCrypt + JwtAuthFilter
├── DataSeeder.java                       ← cria admin se não existir
├── MongoInitConfig.java                  ← cria coleções se não existirem
├── config/
│   └── GlobalModelAdvice.java            ← injeta temaAtivo/username em TODA view
├── controller/
│   ├── HomeController.java               ← páginas públicas e login
│   ├── AuthController.java               ← POST /api/auth/login|logout
│   ├── RegistroController.java           ← POST /api/auth/registrar
│   ├── ConfirmacaoController.java        ← GET /auth/confirmar[-registro]
│   ├── AdminController.java              ← @PreAuthorize("hasRole('ADMIN')")
│   ├── TecnicoController.java            ← @PreAuthorize("hasAnyRole('TECNICO','ADMIN')")
│   └── ThemeController.java              ← POST /tema
├── dto/
│   └── RegistroRequest.java              ← Bean Validation
├── entity/
│   ├── Usuario.java     Role.java        ← 3+ perfis
│   ├── EmailToken.java  TipoToken.java
│   └── ThemeConfig.java
├── exception/
│   └── ConflitoCadastroException.java
├── repository/  (UsuarioRepository, EmailTokenRepository, ThemeConfigRepository, ...)
├── security/
│   ├── JwtUtil.java                      ← gera/valida JWT
│   └── JwtAuthFilter.java                ← OncePerRequestFilter
├── service/
│   ├── UsuarioService.java               ← UserDetailsService + registrar/confirmar
│   ├── EmailService.java
│   ├── EmailTokenService.java            ← gera/consome tokens de email (SHA-256)
│   └── ThemeService.java

src/main/resources/
├── application.properties                ← parametrizado por ${ENV_VAR}
├── static/css/  static/js/               ← assets
├── templates/
│   ├── layout.html          ← layout base (inclui a sidebar)
│   ├── login.html  registrar.html
│   ├── home.html
│   ├── fragments/sidebar.html
│   └── admin/usuarios.html  tecnico/chamados.html
```

---

## 🧭 Decisões de Design

| # | Decisão | Por quê |
|---|---------|---------|
| 1 | JWT em **cookie HttpOnly + SameSite=Lax** | XSS não lê; CSRF mitigado pelo SameSite. |
| 2 | **BCrypt** para senha | Recomendação OWASP; custo adaptativo. |
| 3 | Dois JWTs (`preAuth` / `final`) | Permite múltiplos fatores sem reautenticar senha a cada etapa. |
| 4 | Tokens de email **opacos** + hash SHA-256 no Mongo | Se o banco vazar, os hashes não são utilizáveis. |
| 5 | **TTL no Mongo** (`@Indexed(expireAfterSeconds=0)`) | Faxina automática; código também valida `expiraEm` (defesa dupla). |
| 6 | `@PreAuthorize` **e** URL matchers | Defesa em profundidade. |
| 7 | `Role` como `enum` | Type-safe, sem strings mágicas. |
| 8 | **Temas via CSS + `ThemeConfig`** | Novo tema = 1 arquivo CSS em `/css/themes/`; zero código Java. |
| 9 | `GlobalModelAdvice` injetando tema/usuário | Toda view recebe `temaAtivo` e `username` sem repetição. |
| 10 | `.env` + `application.properties` parametrizado | Nenhum secret hardcoded; trocar de ambiente não muda o código. |
| 11 | Pacote `security/` isolado | Trocar JWT por session-based = mudar 2 arquivos. |
| 12 | Thymeleaf com `th:replace` de fragments | Layout reutilizável, tema desacoplado. |

---

## 🔌 Como adaptar a base a outro tema

A base **não sabe** o que é xadrez, chamado, cliente, etc. Para plugar outro domínio:

1. **Troque as entidades de domínio.** Substitua `PuzzleXadrez` (ou `Chamado`) por `SeuRecurso`. Mantenha `Usuario`, `Role`, `EmailToken`, `ThemeConfig` — eles são genéricos.
2. **Escreva um `SeuRecursoService`.** Nada em `service/` atual depende do tema.
3. **Escreva um `SeuRecursoController`.** Use os mesmos padrões (`@PreAuthorize`, `Model`).
4. **Adicione um link na sidebar** (`fragments/sidebar.html`).
5. **Escolha o segundo fator** (opcional). Se for TOTP, substitua o único service responsável — o resto não muda.
6. **Crie um CSS de tema.** Coloque em `static/css/themes/{nome}.css` e adicione o botão em `sidebar.html` ou `layout.html`.

> **Regra:** segurança, sessão, autorização, tema → **não tocar**. Domínio → **substituir**.

---

## 🧪 Testes

O `pom.xml` já traz `spring-boot-starter-test` e `spring-security-test`. Recomendado cobrir:

- `POST /api/auth/registrar` com username duplicado → **409**
- `POST /api/auth/login` com senha errada → **401**
- `GET /admin/usuarios` com `ROLE_USUARIO` → **403**
- `GET /admin/usuarios` com `ROLE_ADMIN` → **200**
- Fluxo completo de confirmação por email (com `mail.enabled=false`)

Dica: use `@SpringBootTest` + `MockMvc` + `@AutoConfigureMockMvc`. Nos testes, suba um Mongo em memória (ou aponte `MONGO_URI` para um Atlas de teste).

---

## Tema de xadrez

O projeto foi construído em **duas camadas independentes**:

```
┌─────────────────────────────────────────────────────────────┐
│  CAMADA GENÉRICA  (reaproveitável em qualquer aplicação)    │
│  ─────────────────────────────────────────────              │
│  • Usuario, Role, EmailToken, ThemeConfig                   │
│  • UsuarioService, EmailService, EmailTokenService, Theme…  │
│  • SecurityConfig, JwtUtil, JwtAuthFilter                   │
│  • AuthController, RegistroController, ConfirmacaoController│
│  • AdminController, ThemeController, HomeController         │
│  • layout.html, fragments/sidebar.html, login.html, …       │
│  • GlobalModelAdvice (injeta tema e usuário em toda view)   │
└─────────────────────────────────────────────────────────────┘
                            ▲
                            │  plug-in (não altera a base)
                            │
┌─────────────────────────────────────────────────────────────┐
│  CAMADA DE TEMA  (específica do Thindesk)                   │
│  ─────────────────────────────────────────                  │
│  • PuzzleXadrez, PuzzleXadrezRepository                     │
│  • PuzzleService, FenUtil                                   │
│  • PuzzleImporter (importa CSV do Lichess)                  │
│  • puzzle-modal.js, puzzle-modal.css, templates/puzzle.html │
│  • Bloco extra em AuthController (`/resolver-puzzle`,        │
│    `/novo-puzzle`) e campos de estado em `Usuario`          │
└─────────────────────────────────────────────────────────────┘
```

Trocar "puzzle de xadrez" por, por exemplo, "TOTP" ou "pergunta de segurança" é substituir **um service** e **um modal**. Autenticação, sessão, controle de roles, temas visuais e cadastro por email permanecem intactos.

**Contrato entre as camadas** (o que a base espera do segundo fator plugável):

1. Existe um endpoint que, **após validar a senha**, **não** emite o JWT final — emite só um `preAuth` e devolve algo para o usuário resolver.
2. Existe um endpoint que consome a "resposta" do usuário e, se estiver correta, **envia por email** o link que, ao ser clicado, emite o JWT final.
3. Se o usuário falhar N vezes ou o tempo esgotar, o desafio **rotaciona** sem exigir nova senha (dentro de um limite por sessão).

Qualquer coisa que caiba nesse contrato pluga na base sem tocar em `SecurityConfig`.

---

### Como funciona o xadrez neste projeto

O xadrez aqui **não é decoração**: ele é o **segundo fator de autenticação**. A ideia é:

> *Se um atacante roubar a senha, ele ainda precisa resolver uma posição de xadrez — tarefa não-trivial para bots simples, e com prazo curto.*

#### Fluxo completo, passo a passo

```
1) POST /api/auth/login   (usuário + senha)
   ├─ AuthenticationManager valida senha (BCrypt)
   ├─ PuzzleService.iniciarSessao(usuario):
   │     • sorteia puzzle com rating ≤ PUZZLE_RATING_MAX
   │     • grava no documento do usuário:
   │         puzzleAtualId, puzzleExpiraEm, tentativasPuzzle=0,
   │         puzzlesNaSessao=1
   └─ emite JWT preAuth (cookie thindesk_pre) + devolve FEN + prazo

2) [navegador] abre o modal e carrega o FEN no tabuleiro
   (chess.js valida lances legais; chessboard.js desenha)

3) POST /api/auth/resolver-puzzle   { lanceFen, puzzleId }
   ├─ servidor confere prazo (puzzleExpiraEm) → expirou? → /novo-puzzle
   ├─ servidor confere puzzleId (defesa: cliente não escolhe puzzle)
   ├─ compara UCI normalizado com lanceCorreto (case-insensitive)
   │
   ├─ ACERTOU:
   │     • EmailTokenService.gerar(username, LOGIN)  → 15 min, uso único
   │     • EmailService.enviarTokenConfirmacao(email, token)
   │     • PuzzleService.limpar(usuario)
   │     • limpa cookie thindesk_pre
   │     └─ devolve { aguardandoConfirmacao:true, emailMascarado:"m***@x" }
   │
   └─ ERROU:
         • tentativasPuzzle++
         • se < 5 → 403 { restantes }
         • se ≥ 5 → 403 + { novoPuzzle } (rotaciona, se ainda houver cota)

4) [usuário] abre o email, clica no link:
   GET /auth/confirmar?token=...
   ├─ EmailTokenService.consumir (findAndRemove + checa TTL)
   ├─ gera JWT FINAL (cookie thindesk_auth, HttpOnly)
   └─ redirect /

5) Fim. O cookie preAuth morreu no passo 3; o cookie auth nasce só aqui.
```

#### Garantias do lado do servidor

| Ameaça                                 | Como o servidor bloqueia |
|----------------------------------------|--------------------------|
| Atacante escolhe um puzzle fácil       | Servidor **sorteia**; `puzzleId` do cliente é ignorado (só serve para checar frescor). |
| Bots testando lances em força bruta    | Máx. **5 tentativas** por puzzle, **3 puzzles por sessão** (`PUZZLE_MAX_POR_SESSAO`). |
| Congelar o tempo / adiantar o relógio  | Prazo é do **servidor** (`puzzleExpiraEm`). `expirou()` é checado em cada request. |
| Reaproveitar o cookie preAuth por 1h   | Ao acertar, o preAuth é limpo (`Set-Cookie: thindesk_pre=; Max-Age=0`). |
| Reusar o mesmo puzzle em outro login   | `puzzlesNaSessao` é zerado em `iniciarSessao`. |
| Ler o cookie via XSS                   | Ambos os cookies são `HttpOnly`. |
| Enumerar usuários                      | Sempre 401 "Credenciais inválidas", independente de existir. |

#### Tuning por variável de ambiente

| Variável                | Default | Efeito |
|-------------------------|:-------:|--------|
| `PUZZLE_RATING_MAX`     | 1100    | Dificuldade máxima (também filtro do import). |
| `PUZZLE_TEMPO_LIMITE`   | 300     | Segundos por puzzle. |
| `PUZZLE_MAX_POR_SESSAO` | 3       | Trocas permitidas antes de exigir nova senha. |
| `app.security.puzzle.max-tentativas` | 5 | Erros por puzzle antes de rotacionar. |

> **Dica para demo:** baixe `PUZZLE_RATING_MAX` para 800 se quiser puzzles bem fáceis na apresentação.

---

### De onde vêm os puzzles (e por que isso é legal)

#### Fonte

Os puzzles vêm do **banco público de puzzles do Lichess**:

- **URL oficial:** https://database.lichess.org/#puzzles
- **Licença:** **CC0** (domínio público) — pode ser usado, redistribuído e embutido em qualquer aplicação sem obrigação de atribuição.
- **Formato:** CSV comprimido (`.csv.zst`), com ~4 milhões de posições.

#### Colunas do CSV original

| Coluna            | Uso no Thindesk |
|-------------------|-----------------|
| `PuzzleId`        | vira `_id` no Mongo (evita duplicatas em reimportações) |
| `FEN`             | posição **antes** do lance do adversário |
| `Moves`           | lista UCI; `Moves[0]` = lance do adversário, `Moves[1]` = **solução** |
| `Rating`          | dificuldade (filtrada por `PUZZLE_RATING_MAX`) |
| `Themes`          | tags (`mateIn2`, `fork`, `endgame`…) → vão para `descricao` |
| *(demais)*        | ignoradas |

#### Pipeline de importação (`PuzzleImporter`)

```
puzzles_lichess.csv (em src/main/resources)
        │
        ▼  (na 1ª subida da app, se a coleção estiver vazia)
PuzzleImporter.converter(linha):
   1. parse do rating; descarta se > PUZZLE_RATING_MAX
   2. Move[0] = lance do adversário
   3. FenUtil.aplicarLance(FEN, Move[0])
        → gera o FEN que o USUÁRIO realmente vê
   4. lanceCorreto = Move[1]
   5. dificuldade = fácil/média/difícil (por rating)
   6. descricao = "Brancas jogam · rating 1100 · mateIn2, fork"
        │
        ▼  (lotes de 500)
puzzleRepository.saveAll(lote)
        │
        ▼
Coleção `puzzles_xadrez` no MongoDB Atlas
```

**FEN do Lichess é a posição **antes** do lance do oponente. Como o projeto **não carrega engine de xadrez**, o `FenUtil` aplica esse lance "na mão" (movendo a peça, tratando en passant, roque, promoção e atualizando contadores) para chegar à posição que o usuário vê. Não valida legalidade — confia no Lichess. É simples, determinístico e cabe em uma classe de ~200 linhas.

#### Sorteio em tempo real (`PuzzleXadrezRepository`)

Sorteio via **aggregation pipeline** do Mongo:

```java
@Aggregation(pipeline = {
  "{ $match: { rating: { $lte: ?0 }, _id: { $ne: ?1 } } }",
  "{ $sample: { size: 1 } }"
})
List<PuzzleXadrez> sortearAteRating(int ratingMax, String excluirId);
```

- `$match` filtra por rating e exclui o puzzle atual (para não repetir).
- `$sample` sorteia 1 documento em **O(1)** médio — não precisa carregar tudo em memória.
- Se `sortearAteRating` voltar vazio, o service tenta de novo **sem** o `$ne` (garante que sempre há puzzle, desde que a coleção não esteja vazia).

#### Por que esse dataset é adequado

| Vantagem | Detalhe |
|----------|---------|
| **Domínio público** | Sem restrições de licença. |
| **Volume** | 4M+ puzzles — impossível "decorar" a resposta. |
| **Rating calibrado** | Você controla a dificuldade pelo `PUZZLE_RATING_MAX`. |
| **Temas** | `mateIn1`, `fork`, `pin`, `endgame`… cada puzzle já vem com metadados. |
| **Determinismo offline** | Depois de importado, o Atlas responde sem depender do Lichess. |
| **Fácil de explicar** | "Peguei um dataset público, filtrei por rating, apliquei o primeiro lance com FenUtil." |

#### Como regenerar/atualizar o CSV

O arquivo `puzzles_lichess.csv` está versionado apenas por conveniência didática. Para regenerá-lo:

```bash
# baixar (Linux/macOS) — precisa de zstd
curl -O https://database.lichess.org/lichess_db_puzzle.csv.zst
unzstd lichess_db_puzzle.csv.zst -o puzzles_lichess.csv

# opcional: reduzir o arquivo mantendo só rating ≤ 1100 e 50k linhas
head -1 lichess_db_puzzle.csv > puzzles_lichess.csv
awk -F',' 'NR>1 && $4 <= 1100 {print; if(++n==50000) exit}' \
    lichess_db_puzzle.csv >> puzzles_lichess.csv
```

Depois é só apagar a coleção `puzzles_xadrez` no Atlas — o `PuzzleImporter` repopula na próxima subida.

---

## Diagrama do fluxo de login

```mermaid
sequenceDiagram
    autonumber
    participant U as Usuário (navegador)
    participant A as AuthController
    participant P as PuzzleService
    participant E as EmailService
    participant M as MongoDB

    U->>A: POST /api/auth/login {username, password}
    A->>M: valida senha (BCrypt via AuthenticationManager)
    A->>P: iniciarSessao(usuario)
    P->>M: $sample puzzle com rating ≤ max
    P->>M: salva puzzleAtualId, expiraEm, contadores
    A-->>U: 200 + FEN + prazo + cookie thindesk_pre

    U->>A: POST /api/auth/resolver-puzzle {lanceFen}
    A->>P: validar lance + prazo + puzzleId
    alt acertou
        P->>M: limpa puzzle
        A->>A: gera token de email (LOGIN, 15 min)
        A->>E: enviarTokenConfirmacao(email, token)
        A-->>U: 200 { aguardandoConfirmacao:true, emailMascarado }
    else errou
        P->>M: tentativasPuzzle++
        A-->>U: 403 { restantes } ou 403 { novoPuzzle }
    end

    U->>A: GET /auth/confirmar?token=...
    A->>M: consome token (findAndRemove)
    A->>A: gera JWT FINAL
    A-->>U: redirect / + Set-Cookie: thindesk_auth (HttpOnly)
```

---

## 📄 Licença

Uso livre para fins acadêmicos e comerciais. Os dados de puzzles do Lichess são **CC0**.

**Citação sugerida** (para trabalhos acadêmicos):

> LICHESS. **Lichess Open Database — Puzzles**. Disponível em: <https://database.lichess.org/#puzzles>. Licença CC0 1.0 Universal.
