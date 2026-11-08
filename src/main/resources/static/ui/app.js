const byId = (id) => document.getElementById(id);
const root = "/api/v1";
const media = "/media";
const sessionKey = "mediavault.token";
const pageSize = 12;
const typeLabels = { IMAGE: "Фото", AUDIO: "Аудио", VIDEO: "Видео" };
const statusLabels = { READY: "Готов", PENDING_UPLOAD: "Ожидает подтверждения", FAILED: "Сбой загрузки" };

const authForm = byId("auth-form");
const uploadForm = byId("upload-form");
const list = byId("media-list");
const preview = byId("preview");
const notice = byId("notice");
let token = sessionStorage.getItem(sessionKey);
let session = new AbortController();
let registerMode = false;
let page = 0;
let catalogVersion = 0;

function message(text, error = false) {
  notice.textContent = text;
  notice.dataset.error = String(error);
  notice.hidden = !text;
}

function report(error) {
  if (error.name !== "AbortError") message(error.message || "Что-то пошло не так.", true);
}

function showSession() {
  byId("auth-panel").hidden = Boolean(token);
  byId("library").hidden = !token;
  byId("sign-out").hidden = !token;
}

function setSession(value) {
  session.abort();
  session = new AbortController();
  token = value;
  page = 0;
  catalogVersion++;
  list.replaceChildren();
  uploadForm.reset();
  byId("upload-status").textContent = "";
  if (preview.open) preview.close();
  if (token) sessionStorage.setItem(sessionKey, token);
  else sessionStorage.removeItem(sessionKey);
  showSession();
}

async function api(path, { method = "GET", body, authenticated = true } = {}) {
  const headers = new Headers({ Accept: "application/json" });
  if (authenticated) headers.set("Authorization", "Bearer " + token);
  if (body !== undefined) headers.set("Content-Type", "application/json");

  const response = await fetch(root + path, {
    method, headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: authenticated ? session.signal : undefined
  });
  if (response.status === 401 && authenticated) {
    setSession(null);
    message("Сессия истекла. Войдите снова.", true);
    throw new DOMException("Session expired", "AbortError");
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    if (response.status === 429) {
      const wait = response.headers.get("Retry-After");
      throw new Error(wait ? "Лимит загрузок. Повторите через " + wait + " сек." : "Лимит загрузок исчерпан.");
    }
    const detail = Object.values(problem.violations ?? {}).join(" ") || problem.detail;
    throw new Error(detail || "Ошибка запроса (" + response.status + ").");
  }
  return response.status === 204 ? null : response.json();
}

byId("auth-switch").addEventListener("click", () => {
  registerMode = !registerMode;
  byId("auth-title").textContent = registerMode ? "Создать аккаунт" : "Войти в медиатеку";
  byId("auth-submit").textContent = registerMode ? "Зарегистрироваться" : "Войти";
  byId("auth-switch").textContent = registerMode ? "У меня есть аккаунт" : "Создать аккаунт";
  byId("password").autocomplete = registerMode ? "new-password" : "current-password";
  byId("auth-hint").hidden = !registerMode;
  message("");
});

authForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const credentials = Object.fromEntries(new FormData(authForm));
  byId("auth-submit").disabled = byId("auth-switch").disabled = true;
  message("");
  try {
    if (registerMode) {
      await api("/auth/register", { method: "POST", body: credentials, authenticated: false });
    }
    const result = await api("/auth/token", { method: "POST", body: credentials, authenticated: false });
    authForm.reset();
    setSession(result.accessToken);
    await loadCatalog();
  } catch (error) {
    report(error);
  } finally {
    byId("auth-submit").disabled = byId("auth-switch").disabled = false;
  }
});

byId("sign-out").addEventListener("click", () => {
  setSession(null);
  message("");
  byId("username").focus();
});

function putFile(ticket, file, signal) {
  return new Promise((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open("PUT", ticket.uploadUrl);
    for (const [name, value] of Object.entries(ticket.uploadHeaders)) {
      if (!["content-length", "host"].includes(name.toLowerCase())) request.setRequestHeader(name, value);
    }
    const abort = () => request.abort();
    request.addEventListener("loadend", () => signal.removeEventListener("abort", abort));
    request.addEventListener("abort", () => reject(new DOMException("Upload canceled", "AbortError")));
    request.addEventListener("error", () => reject(new Error("Хранилище недоступно. Проверьте адрес S3 и настройки CORS.")));
    request.addEventListener("load", () => {
      if (request.status >= 200 && request.status < 300) resolve();
      else reject(new Error("Хранилище отклонило загрузку (" + request.status + ")."));
    });
    request.upload.addEventListener("progress", (event) => {
      if (event.lengthComputable) {
        byId("upload-status").textContent = "Передача файла: " + Math.round(event.loaded / event.total * 100) + "%";
      }
    });
    signal.throwIfAborted();
    signal.addEventListener("abort", abort, { once: true });
    request.send(file);
  });
}

uploadForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const file = byId("media-file").files[0];
  if (!file) return;
  if (!/^(image|audio|video)\//.test(file.type) || file.size === 0) {
    message("Выберите непустое изображение, аудио или видео с распознаваемым форматом.", true);
    return;
  }
  const signal = session.signal;
  const status = byId("upload-status");
  let ticket;
  byId("upload-submit").disabled = byId("media-file").disabled = true;
  message("");
  try {
    status.textContent = "Подготавливаем загрузку…";
    const body = { filename: file.name, mimeType: file.type, sizeBytes: file.size };
    if (globalThis.crypto?.subtle && file.size <= 16 * 1024 * 1024) {
      const digest = await crypto.subtle.digest("SHA-256", await file.arrayBuffer());
      body.checksumSha256 = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
    }
    signal.throwIfAborted();
    ticket = await api(media + "/uploads", { method: "POST", body });
    await putFile(ticket, file, signal);
    status.textContent = "Подтверждаем загрузку…";
    await api(media + "/" + ticket.mediaId + "/complete", { method: "POST" });
    uploadForm.reset();
    page = 0;
    message("Файл добавлен в медиатеку.");
  } catch (error) {
    report(error);
  } finally {
    status.textContent = "";
    byId("upload-submit").disabled = byId("media-file").disabled = false;
    if (ticket && !signal.aborted) refreshCatalog();
  }
});

function element(tag, className, text) {
  const node = document.createElement(tag);
  node.className = className;
  node.textContent = text;
  return node;
}

function action(label, handler, danger = false) {
  const button = element("button", "button " + (danger ? "text-button danger" : "secondary"), label);
  button.type = "button";
  button.addEventListener("click", async () => {
    button.disabled = true;
    message("");
    try {
      await handler();
    } catch (error) {
      report(error);
    } finally {
      button.disabled = false;
    }
  });
  return button;
}

function mediaRow(item) {
  const path = media + "/" + item.mediaId;
  const row = element("li", "media-row", "");
  const icon = element("span", "media-icon", typeLabels[item.mediaType] || "Файл");
  icon.dataset.kind = item.mediaType;
  const info = document.createElement("div");
  const status = statusLabels[item.status] ?? item.status;
  const thumbnail = item.thumbnailStatus === "PENDING" ? " · миниатюра в обработке" : "";
  info.append(element("strong", "media-name", item.originalFilename),
    element("span", "media-meta", new Intl.NumberFormat("ru", { maximumFractionDigits: 1 })
      .format(item.sizeBytes / 1024) + " КиБ · " + status + thumbnail));
  const actions = element("div", "row-actions", "");
  if (item.status === "READY") {
    actions.append(action("Смотреть", () => openPreview(item, path)));
    if (item.thumbnailStatus === "READY") {
      actions.append(action("Миниатюра", () => openPreview(item, path, true)));
    }
  } else if (item.status === "PENDING_UPLOAD") {
    actions.append(action("Подтвердить", async () => {
      await api(path + "/complete", { method: "POST" });
      await loadCatalog();
    }));
  }
  actions.append(action("Удалить", async () => {
    if (!confirm("Удалить «" + item.originalFilename + "»?")) return;
    await api(path, { method: "DELETE" });
    await loadCatalog();
    message("Файл удалён из каталога.");
  }, true));
  row.append(icon, info, actions);
  return row;
}

async function loadCatalog() {
  const version = ++catalogVersion;
  const status = byId("catalog-status");
  status.textContent = "Загружаем каталог…";
  byId("previous").disabled = byId("next").disabled = true;
  try {
    const result = await api(media + "?page=" + page + "&size=" + pageSize);
    if (version !== catalogVersion) return;
    if (!result.items.length && page > 0) {
      page--;
      return loadCatalog();
    }
    list.replaceChildren(...result.items.map(mediaRow));
    status.textContent = result.items.length ? "" : "Здесь пока нет файлов. Загрузите первый.";
    byId("page-number").textContent = String(result.page + 1);
    byId("previous").disabled = result.page === 0;
    byId("next").disabled = !result.hasNext;
  } catch (error) {
    if (version !== catalogVersion) return;
    status.textContent = "Не удалось загрузить каталог.";
    throw error;
  }
}

function refreshCatalog() {
  return loadCatalog().catch(report);
}

byId("refresh").addEventListener("click", refreshCatalog);
byId("previous").addEventListener("click", () => { page--; refreshCatalog(); });
byId("next").addEventListener("click", () => { page++; refreshCatalog(); });

async function openPreview(item, path, thumbnail = false) {
  const original = await api(path + "/access-url");
  const resource = thumbnail ? await api(path + "/thumbnail-url") : original;
  const tag = { IMAGE: "img", AUDIO: "audio", VIDEO: "video" }[item.mediaType];
  const player = document.createElement(tag);
  if (tag === "img") player.alt = item.originalFilename;
  else {
    player.controls = true;
    player.preload = "metadata";
  }
  player.addEventListener("error", () => {
    if (player.isConnected) {
      byId("preview-content").textContent = "Не удалось показать файл. Попробуйте открыть оригинал.";
    }
  }, { once: true });
  player.src = resource.downloadUrl;
  byId("preview-title").textContent = item.originalFilename;
  byId("original-link").href = original.downloadUrl;
  byId("preview-content").replaceChildren(player);
  if (!preview.open) preview.showModal();
}

preview.addEventListener("close", () => {
  byId("preview-content").querySelector("audio, video")?.pause();
  byId("preview-content").replaceChildren();
  byId("original-link").removeAttribute("href");
});

showSession();
if (token) refreshCatalog();
