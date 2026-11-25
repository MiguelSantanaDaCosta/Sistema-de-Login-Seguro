// Alternar sidebar em telas pequenas
document.querySelector(".toggle-btn").addEventListener("click", (event) => {
  const aberto = document.getElementById("sidebar").classList.toggle("active");
  event.currentTarget.setAttribute("aria-expanded", String(aberto));
  event.currentTarget.setAttribute("aria-label", aberto ? "Fechar menu" : "Abrir menu");
});

function toggleSubmenu(event, element) {
  event.preventDefault();
  element.parentElement.classList.toggle("open");
}

async function logout(event) {
  event.preventDefault();
  try {
    await fetch("/api/auth/logout", { method: "POST" });
  } catch (_) {
    /* ignora */
  }
  window.location.href = "/login";
}
