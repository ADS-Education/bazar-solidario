"use strict";

const byId = (id) => document.getElementById(id);
const views = ["login", "list", "donation", "sale"];
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

      const detail = document.createElement("div");
      const code = document.createElement("span");
      code.className = "piece-code";
      code.textContent = "#" + piece.id;

      const name = document.createElement("p");
      name.className = "piece-name";
      name.textContent = piece.description;
      detail.append(code, name);

      const button = document.createElement("button");
      button.type = "button";
      button.className = "button button-secondary";
      button.textContent = "Registrar venda";
      button.setAttribute("aria-label", "Registrar venda de " + piece.description + ", peça " + piece.id);
      button.addEventListener("click", () => openSale(piece));
      row.append(detail, button);
      list.append(row);
    }

    byId("empty-list").hidden = available.length > 0;
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
    await showList();
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

byId("new-donation").addEventListener("click", () => {
  byId("donation-form").reset();
  byId("description").removeAttribute("aria-invalid");
  showNotice(byId("donation-error"), "");
  showView("donation");
});

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

  button.disabled = true;
  try {
    const response = await api("pecas", { descricao: description });
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
showList();
