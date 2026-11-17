"use strict";

const byId = (id) => document.getElementById(id);
const views = ["login", "dashboard", "list", "donation", "sale", "history"];
let statusChart = null;
let salesChart = null;
let signedIn = false;
let selectedPieceId = null;

async function api(path, data) {
  const options = data ? {
    method: "POST",
    headers: { "X-Bazar": "1" },
    body: new URLSearchParams(data)
  } : {};

  let response;
  try {
    response = await fetch("/api/" + path, options);
  } catch {
    throw new Error("Não foi possível acessar o servidor. Tente novamente.");
  }
  if (response.status === 401 && path !== "entrar") {
    const sessionExpired = signedIn;
    signedIn = false;
    selectedPieceId = null;
    showView("login");
    if (sessionExpired) {
      showNotice(byId("login-error"), "Sessão encerrada. Entre novamente.", true);
    }
  }
  if (!response.ok) {
    throw new Error(await response.text());
  }
  return response;
}

function showView(view) {
  for (const name of views) {
    byId(name + "-view").hidden = name !== view;
  }
  byId("logout").hidden = !signedIn;
  byId("navigation").hidden = !signedIn;
  byId(view + "-title").focus();
}

function showNotice(element, message, isError = false) {
  element.textContent = message;
  element.hidden = !message;
  element.classList.toggle("error", isError);
}

async function showList(message = "") {
  const list = byId("piece-list");
  list.replaceChildren();
  byId("empty-list").hidden = true;
  showNotice(byId("list-notice"), message);

  try {
    const response = await api("pecas");
    const available = await response.json();
    signedIn = true;
    showView("list");
    for (const piece of available) {
      const row = document.createElement("li");
      row.className = "piece-row";
      row.dataset.search = (piece.id + " " + piece.description).toLowerCase();
      row.dataset.category = piece.categoria;

      const detail = document.createElement("div");
      const code = document.createElement("span");
      code.className = "piece-code";
      code.textContent = "#" + piece.id;

      const name = document.createElement("p");
      name.className = "piece-name";
      name.textContent = piece.description;
      const meta = document.createElement("p");
      meta.className = "piece-meta";
      meta.textContent = piece.categoria + " · " + money(piece.valor);
      detail.append(code, name, meta);

      const button = document.createElement("button");
      button.type = "button";
      button.className = "button button-secondary";
      button.textContent = "Registrar venda";
      button.setAttribute("aria-label", "Registrar venda de " + piece.description + ", peça " + piece.id);
      button.addEventListener("click", () => openSale(piece));
      row.append(detail, button);
      list.append(row);
    }

    filterPieces();
  } catch (error) {
    if (signedIn) {
      showView("list");
      showNotice(byId("list-notice"), error.message, true);
    }
  }
}

function openSale(piece) {
  selectedPieceId = piece.id;
  byId("sale-code").textContent = "#" + piece.id;
  byId("sale-description").textContent = piece.description;
  byId("sale-details").textContent = piece.categoria + " · " + money(piece.valor);
  byId("confirm-sale").hidden = false;
  showNotice(byId("sale-result"), "");
  showView("sale");
}

byId("login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const button = form.querySelector("button");
  button.disabled = true;

  try {
    await api("entrar", {
      usuario: byId("username").value.trim(),
      senha: byId("password").value
    });
    signedIn = true;
    form.reset();
    showNotice(byId("login-error"), "");
    await showDashboard();
  } catch (error) {
    showNotice(byId("login-error"), error.message, true);
    byId("password").focus();
  } finally {
    button.disabled = false;
  }
});

byId("logout").addEventListener("click", async () => {
  try {
    await api("sair", {});
    signedIn = false;
    selectedPieceId = null;
    showView("login");
  } catch (error) {
    if (signedIn) {
      showNotice(byId("list-notice"), error.message, true);
    }
  }
});

function openDonation() {
  byId("donation-form").reset();
  byId("description").removeAttribute("aria-invalid");
  showNotice(byId("donation-error"), "");
  showView("donation");
}
byId("new-donation").addEventListener("click", openDonation);
byId("dashboard-donation").addEventListener("click", openDonation);

byId("cancel-donation").addEventListener("click", () => showList());
byId("back-to-list").addEventListener("click", () => showList());

byId("donation-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const button = event.currentTarget.querySelector("button");
  const input = byId("description");
  const description = input.value.trim();
  const notice = byId("donation-error");
  input.removeAttribute("aria-invalid");

  if (!description || description.length > 100) {
    input.setAttribute("aria-invalid", "true");
    showNotice(notice, "Informe a descrição com até 100 caracteres.", true);
    input.focus();
    return;
  }

  const category = byId("category").value;
  const price = byId("price").value;
  if (!category || price === "" || !byId("price").checkValidity()) {
    showNotice(notice, "Selecione a categoria e informe um valor válido.", true);
    (!category ? byId("category") : byId("price")).focus();
    return;
  }
  button.disabled = true;
  try {
    const response = await api("pecas", { descricao: description, categoria: category, valor: price });
    await showList(await response.text());
  } catch (error) {
    showNotice(notice, error.message, true);
  } finally {
    button.disabled = false;
  }
});

byId("confirm-sale").addEventListener("click", async () => {
  const button = byId("confirm-sale");
  const result = byId("sale-result");
  button.disabled = true;

  try {
    const response = await api("vendas", { id: selectedPieceId });
    showNotice(result, await response.text());
    button.hidden = true;
  } catch (error) {
    showNotice(result, error.message, true);
  } finally {
    button.disabled = false;
  }

  result.focus();
});

showView("login");

// Atualiza os dados do painel.
async function showDashboard() {
  try {
    const response = await api("dashboard");
    const data = await response.json();
    showView("dashboard");
    showNotice(byId("dashboard-error"), "");
    byId("total-pieces").textContent = data.cadastradas;
    byId("total-available").textContent = data.disponiveis;
    byId("total-sold").textContent = data.vendidas;
    byId("total-today").textContent = data.vendasHoje;
    await showRecentSales();
    if (typeof Chart === "undefined") { byId("chart-error").hidden = false; return; }
    byId("chart-error").hidden = true;
    if (statusChart) statusChart.destroy();
    if (salesChart) salesChart.destroy();
    statusChart = new Chart(byId("status-chart"), {
      type: "doughnut",
      data: { labels: ["Disponíveis", "Vendidas"], datasets: [{ data: [data.disponiveis, data.vendidas], backgroundColor: ["#6950ad", "#47a47c"] }] },
      options: { responsive: true, maintainAspectRatio: false }
    });
    salesChart = new Chart(byId("sales-chart"), {
      type: "bar",
      data: { labels: data.vendasPorDia.map(item => formatDate(item.data)), datasets: [{ label: "Peças vendidas", data: data.vendasPorDia.map(item => item.total), backgroundColor: "#6950ad" }] },
      options: { responsive: true, maintainAspectRatio: false, scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } }
    });
  } catch (error) {
    if (signedIn) { showView("dashboard"); showNotice(byId("dashboard-error"), error.message, true); }
  }
}

function formatDate(date) {
  const parts = date.split("-");
  return parts[2] + "/" + parts[1] + "/" + parts[0];
}

function filterPieces() {
  const term = byId("search-piece").value.trim().toLowerCase().replace(/^#/, "");
  let visible = 0;
  for (const row of byId("piece-list").children) {
    const category = byId("filter-category").value;
    row.hidden = !row.dataset.search.includes(term) || (category && row.dataset.category !== category);
    if (!row.hidden) visible++;
  }
  byId("empty-list").hidden = visible > 0;
  byId("empty-list").textContent = term || byId("filter-category").value ? "Nenhuma peça encontrada." : "Nenhuma peça disponível.";
}

async function showHistory() {
  try {
    const response = await api("historico");
    const sales = await response.json();
    const list = byId("history-list");
    list.replaceChildren();
    for (const sale of sales) {
      const row = document.createElement("li");
      row.className = "piece-row";
      const details = document.createElement("div");
      const code = document.createElement("span");
      code.className = "piece-code";
      code.textContent = "#" + sale.id;
      const name = document.createElement("p");
      name.className = "piece-name";
      name.textContent = sale.description;
      const date = document.createElement("span");
      date.textContent = "Venda: " + formatDate(sale.dataVenda) + " · " + money(sale.valor);
      details.append(code, name);
      row.append(details, date);
      list.append(row);
    }
    byId("empty-history").hidden = sales.length > 0;
    showNotice(byId("history-notice"), "");
    showView("history");
  } catch (error) {
    if (signedIn) { showView("history"); showNotice(byId("history-notice"), error.message, true); }
  }
}

function money(value) {
  return Number(value).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

// Pega só as ultimas vendas pro painel nao ficar cheio.
async function showRecentSales() {
  const response = await api("historico");
  const sales = await response.json();
  const list = byId("recent-list");
  list.replaceChildren();
  for (const sale of sales.slice(0, 3)) {
    const row = document.createElement("li");
    row.className = "piece-row";
    const name = document.createElement("span");
    name.textContent = "#" + sale.id + " · " + sale.description;
    const details = document.createElement("span");
    details.className = "piece-meta";
    details.textContent = formatDate(sale.dataVenda) + " · " + money(sale.valor);
    row.append(name, details);
    list.append(row);
  }
  byId("recent-empty").hidden = sales.length > 0;
}

byId("view-history").addEventListener("click", showHistory);
byId("filter-category").addEventListener("change", filterPieces);
byId("search-piece").addEventListener("input", filterPieces);
byId("nav-dashboard").addEventListener("click", showDashboard);
byId("nav-pieces").addEventListener("click", () => { byId("search-piece").value = ""; byId("filter-category").value = ""; showList(); });
byId("nav-history").addEventListener("click", showHistory);
