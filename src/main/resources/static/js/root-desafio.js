/** Desafio da rainha vermelha: N puzzles seguidos dentro do tempo total. O servidor decide tudo. */
(function () {
  "use strict";
  const $ = (id) => document.getElementById(id);
  // a mesma página serve à rainha, ao cavalo e à torre: a API e o destino vêm do <body>
  const BASE = document.body.dataset.base || "/api/root/desafio";
  const SUCESSO = document.body.dataset.sucesso || "/root";
  let game = new Chess();
  let board = null;
  let puzzle = null;
  let aguardando = false;
  let travado = false;
  let timer = null;
  let fimMs = 0;

  // campos anti-bot enviados junto de cada chamada (isca vazia + sinal de webdriver)
  function sinais(extra) {
    const p = new URLSearchParams(extra || {});
    p.set("website", ($("website") || {}).value || "");
    p.set("wd", navigator.webdriver ? "1" : "0");
    return p;
  }

  // 418: IP bloqueado — troca a página pelo bule
  function bule() {
    document.body.innerHTML =
      '<div style="text-align:center;margin-top:20vh;color:#e8e8ea"><h1>Eu sou um bule de chá</h1><p style="font-size:4rem">&#9749;</p></div>';
  }

  function msg(texto, cor) {
    const el = $("msg");
    el.textContent = texto;
    el.style.color = cor || "#e8e8ea";
  }

  function passos(atual, total) {
    $("passos").innerHTML = Array.from({ length: total }, (_, i) =>
      `<span class="${i < atual - 1 ? "ok" : i === atual - 1 ? "atual" : ""}"></span>`).join("");
  }

  function pararTimer() { if (timer) clearInterval(timer); timer = null; }

  function iniciarTimer(seg) {
    pararTimer();
    fimMs = Date.now() + seg * 1000;
    const tick = () => {
      const rest = Math.max(0, Math.ceil((fimMs - Date.now()) / 1000));
      const m = String(Math.floor(rest / 60)).padStart(2, "0");
      const s = String(rest % 60).padStart(2, "0");
      $("tempo").textContent = m + ":" + s;
      $("tempo").classList.toggle("urgente", rest <= 30);
      if (rest === 0) {
        pararTimer();
        falhou("O tempo acabou.");
      }
    };
    tick();
    timer = setInterval(tick, 500);
  }

  function falhou(texto) {
    travado = true;
    pararTimer();
    msg(texto, "#ff8a80");
    $("btn-reiniciar").style.display = "inline-block";
  }

  function carregar(d) {
    puzzle = d;
    travado = false;
    aguardando = false;
    $("btn-reiniciar").style.display = "none";
    game = new Chess();
    if (!game.load(d.fen)) { falhou("Puzzle inválido."); return; }
    const brancas = game.turn() === "w";
    board.orientation(brancas ? "white" : "black");
    board.position(game.fen(), false);
    $("numero").textContent = "Puzzle " + d.puzzleNumero + "/" + d.puzzlesTotal;
    $("vez").textContent = (brancas ? "Brancas" : "Pretas") + " jogam";
    $("rating").textContent = "rating " + d.rating;
    passos(d.puzzleNumero, d.puzzlesTotal);
    msg("Encontre o melhor lance.");
    iniciarTimer(d.expiraEmSegundos);
  }

  async function comecar() {
    $("inicio").style.display = "none";
    $("jogo").style.display = "block";
    if (!board) {
      board = Chessboard("board", {
        draggable: true,
        position: "start",
        pieceTheme: "https://chessboardjs.com/img/chesspieces/wikipedia/{piece}.png",
        onDragStart: (origem, peca) => {
          if (aguardando || travado) return false;
          if ((game.turn() === "w") !== peca.startsWith("w")) return false;
        },
        onDrop: aoSoltar,
        onSnapEnd: () => board.position(game.fen()),
      });
    }
    msg("Preparando o desafio...");
    try {
      const resp = await fetch(BASE + "/iniciar", { method: "POST", body: sinais() });
      if (resp.status === 418) { bule(); return; }
      const d = await resp.json();
      if (!resp.ok) { falhou(d.erro || "Erro ao iniciar."); return; }
      carregar(d);
    } catch (e) {
      falhou("Erro ao contatar o servidor.");
    }
  }

  function aoSoltar(origem, destino) {
    if (destino === "offboard" || origem === destino) return "snapback";
    const lance = game.move({ from: origem, to: destino, promotion: "q" });
    if (lance === null) return "snapback";
    aguardando = true;
    enviar(origem + destino + (lance.promotion || ""));
  }

  async function enviar(uci) {
    msg("Verificando...");
    try {
      const resp = await fetch(BASE + "/lance", {
        method: "POST",
        body: sinais({ lanceFen: uci }),
      });
      if (resp.status === 418) { bule(); return; }
      const d = await resp.json().catch(() => ({}));
      if (resp.ok && d.concluido) {
        pararTimer();
        travado = true;
        msg(d.mensagem, "#7CFC9A");
        setTimeout(() => (window.location = d.redirect || SUCESSO), 1500);
        return;
      }
      if (resp.ok) { // acertou, próximo puzzle
        msg("Certo!", "#7CFC9A");
        setTimeout(() => carregar(d), 600);
        return;
      }
      falhou(d.erro || "Falhou.");
    } catch (e) {
      falhou("Erro ao contatar o servidor.");
    }
  }

  $("btn-iniciar").addEventListener("click", comecar);
  $("btn-reiniciar").addEventListener("click", comecar);
})();
