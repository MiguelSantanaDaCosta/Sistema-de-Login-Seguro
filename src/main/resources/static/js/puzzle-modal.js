/**
 * Pop-up do puzzle de xadrez (2FA).
 * Depende de: jQuery, chess.js 0.10.x, chessboard.js 1.0.0 e Bootstrap 5 (JS).
 *
 * Uso: PuzzleModal.abrir(dadosDoLogin, { aoEncerrar: (mensagem) => { ... } })
 *
 * O servidor decide qual é o puzzle, o prazo e quando trocar; aqui só desenhamos.
 */
(function () {
  "use strict";

  const $ = (id) => document.getElementById(id);

  let modal = null;
  let game = null;
  let board = null;
  let opcoes = {};
  let puzzle = null; // dados do puzzle atual (vindos do servidor)
  let aguardando = false; // true enquanto o backend valida um lance
  let travado = false; // true quando o tempo acabou ou a sessão encerrou
  let timerId = null;
  let fimMs = 0;
  let totalMs = 1;

  function abrir(dados, opts) {
    opcoes = opts || {};
    game = new Chess();
    const el = $("puzzleModal");

    modal = bootstrap.Modal.getOrCreateInstance(el, {
      backdrop: "static", // clicar fora não fecha
      keyboard: false, // ESC não fecha
    });

    // O tabuleiro só pode ser criado com o modal visível (precisa medir a largura)
    el.addEventListener(
      "shown.bs.modal",
      () => {
        if (board) board.destroy();
        board = Chessboard("puzzle-board", {
          draggable: true,
          position: "start",
          pieceTheme:
            "https://chessboardjs.com/img/chesspieces/wikipedia/{piece}.png",
          onDragStart: aoComecarArrasto,
          onDrop: aoSoltar,
          onSnapEnd: aoFimDoSnap,
        });
        carregar(dados);
      },
      { once: true },
    );

    el.addEventListener("hidden.bs.modal", limparAoFechar, { once: true });
    $("pz-cancelar").onclick = cancelar;
    window.addEventListener("resize", redimensionar);

    modal.show();
  }

  function limparAoFechar() {
    pararTimer();
    window.removeEventListener("resize", redimensionar);
    if (board) {
      board.destroy();
      board = null;
    }
  }

  function redimensionar() {
    if (board) board.resize();
  }

  async function cancelar() {
    pararTimer();
    try {
      await fetch("/api/auth/logout", { method: "POST" });
    } catch (_) {
      /* ignora */
    }
    encerrar("");
  }

  /** Fecha o pop-up e avisa a página de login. */
  function encerrar(mensagem) {
    travado = true;
    pararTimer();
    setTimeout(
      () => {
        modal.hide();
        if (opcoes.aoEncerrar) opcoes.aoEncerrar(mensagem);
      },
      mensagem ? 1800 : 0,
    );
  }

  //Carregar um puzzle

  function carregar(dados) {
    puzzle = dados;
    travado = false;
    aguardando = false;

    if (!game.load(dados.fen)) {
      mensagem("Puzzle inválido. Faça login novamente.", "erro");
      encerrar("Puzzle inválido. Faça login novamente.");
      return;
    }

    limparMarcas();
    const brancas = game.turn() === "w";
    board.orientation(brancas ? "white" : "black"); // o jogador vê do seu lado
    board.position(game.fen(), false);

    $("pz-vez").textContent = (brancas ? "Brancas" : "Pretas") + " jogam";
    $("pz-numero").textContent =
      "Puzzle " + dados.puzzleNumero + "/" + dados.puzzlesMax;
    desenharTentativas(dados.restantes, dados.maxTentativas);
    mensagem("Encontre o melhor lance.", "info");
    iniciarTimer(dados.expiraEmSegundos, dados.tempoLimiteSegundos);
  }

  // ---------- Cronômetro ----------

  function iniciarTimer(restanteSeg, limiteSeg) {
    pararTimer();
    totalMs = Math.max(1, limiteSeg) * 1000;
    fimMs = Date.now() + restanteSeg * 1000;
    timerId = setInterval(tick, 250);
    tick();
  }

  function pararTimer() {
    if (timerId) clearInterval(timerId);
    timerId = null;
  }

  function tick() {
    const resta = Math.max(0, fimMs - Date.now());
    const total = Math.ceil(resta / 1000);
    const mm = String(Math.floor(total / 60)).padStart(2, "0");
    const ss = String(total % 60).padStart(2, "0");
    $("pz-tempo").textContent = mm + ":" + ss;
    $("pz-tempo").classList.toggle("urgente", total <= 30);
    $("pz-barra").style.width = (100 * resta) / totalMs + "%";

    if (resta <= 0 && !travado) tempoEsgotado();
  }

  async function tempoEsgotado() {
    travado = true;
    pararTimer();
    mensagem("Tempo esgotado. Gerando novo puzzle...", "aviso");
    try {
      const resp = await fetch("/api/auth/novo-puzzle", { method: "POST" });
      const dados = await resp.json().catch(() => ({}));

      if (resp.status === 429 || resp.status === 401 || resp.status === 409) {
        mensagem(dados.erro || "Sessão encerrada.", "erro");
        encerrar(dados.erro || "Sessão encerrada. Faça login novamente.");
        return;
      }
      if (!resp.ok) {
        mensagem(dados.erro || "Não foi possível gerar outro puzzle.", "erro");
        return;
      }
      carregar(dados); // também cobre "renovado: false" (relógio adiantado): só sincroniza o prazo
      if (dados.renovado) mensagem("Novo puzzle gerado.", "aviso");
    } catch (ex) {
      mensagem("Erro ao contatar o servidor.", "erro");
      console.error(ex);
    }
  }

  // ---------- Arrastar e soltar ----------

  function aoComecarArrasto(origem, peca) {
    if (aguardando || travado || game.game_over()) return false;

    const pecaBranca = peca.startsWith("w");
    if ((game.turn() === "w") !== pecaBranca) return false; // não é a vez dessa cor

    const lances = game.moves({ square: origem, verbose: true });
    if (lances.length === 0) return false; // peça sem lance legal

    limparDicas();
    marcar(origem, "sq-origem");
    lances.forEach((m) => marcar(m.to, m.captured ? "dica-captura" : "dica"));
  }

  function aoSoltar(origem, destino) {
    limparDicas();
    if (destino === "offboard" || origem === destino) return "snapback";

    const lance = game.move({ from: origem, to: destino, promotion: "q" });
    if (lance === null) return "snapback"; // ilegal: a peça volta

    aguardando = true;
    marcar(origem, "sq-origem");
    marcar(destino, "sq-origem");
    enviar(origem + destino, destino); // ex.: "e2e4"
  }

  // Sincroniza o tabuleiro visual com o estado real (roque, en passant...)
  function aoFimDoSnap() {
    board.position(game.fen());
  }

  function desfazerLance() {
    game.undo();
    board.position(game.fen()); // anima a peça de volta
    limparMarcas();
    aguardando = false;
  }

  async function enviar(uci, destino) {
    mensagem("Verificando...", "info");
    try {
      const resp = await fetch("/api/auth/resolver-puzzle", {
        method: "POST",
        body: new URLSearchParams({ lanceFen: uci, puzzleId: puzzle.puzzleId }),
      });

      if (resp.ok) {
        limparMarcas();
        marcar(destino, "sq-certo");
        mensagem("Correto! Entrando...", "ok");
        pararTimer();
        setTimeout(() => (window.location.href = "/"), 700);
        return;
      }

      const dados = await resp.json().catch(() => ({}));

      if (resp.status === 429 || resp.status === 401) {
        mensagem(dados.erro || "Sessão encerrada.", "erro");
        encerrar(dados.erro || "Sessão encerrada. Faça login novamente.");
        return;
      }

      // 403 (errou), 410 (tempo) ou 409 (puzzle trocado): mostra o erro e reverte/troca
      limparMarcas();
      marcar(destino, "sq-errado");
      mensagem(dados.erro || "Lance incorreto.", "erro");

      setTimeout(() => {
        if (dados.novoPuzzle) {
          carregar(dados.novoPuzzle); // 5 erros ou tempo: outro puzzle
          mensagem(dados.erro, "aviso");
        } else {
          desfazerLance();
          puzzle.restantes = dados.restantes;
          desenharTentativas(dados.restantes, puzzle.maxTentativas);
          mensagem("Tente outro lance.", "info");
        }
      }, 900);
    } catch (ex) {
      desfazerLance();
      mensagem("Erro ao contatar o servidor.", "erro");
      console.error(ex);
    }
  }

  // ---------- Auxiliares de interface ----------

  function marcar(casa, classe) {
    jQuery("#puzzle-board .square-" + casa).addClass(classe);
  }

  function limparDicas() {
    jQuery("#puzzle-board .square-55d63").removeClass(
      "dica dica-captura sq-origem",
    );
  }

  function limparMarcas() {
    jQuery("#puzzle-board .square-55d63").removeClass(
      "dica dica-captura sq-origem sq-certo sq-errado",
    );
  }

  function mensagem(texto, tipo) {
    const el = $("pz-msg");
    el.textContent = texto || ""; // textContent: nunca interpreta HTML
    el.className = "text-center " + (tipo || "info");
  }

  function desenharTentativas(restantes, max) {
    const usadas = max - restantes;
    const caixa = $("pz-tentativas");
    caixa.innerHTML = "";
    for (let i = 0; i < max; i++) {
      const bolinha = document.createElement("span");
      if (i < usadas) bolinha.className = "usada";
      caixa.appendChild(bolinha);
    }
  }

  window.PuzzleModal = { abrir: abrir };
})();
