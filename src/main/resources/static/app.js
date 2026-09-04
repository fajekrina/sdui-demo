const $ = (id) => document.getElementById(id);
let currentPageKey = null;

// client-side navigation (SDUI router + history stack)
let navStack = [];
let currentClientKey = null;
let submitActions = {};
let submitCounter = 0;
let lastFormData = {};

function merchant() {
  return $("merchantId").value.trim() || "merchant_1";
}

function showMsg(text, ok = true) {
  const m = $("msg");
  // Tailwind utilities: keep same colors as old .msg.ok / .msg.err but via utilities
  const base = "mt-2.5 rounded-lg px-3 py-2 text-[13px]";
  const okCls = " bg-[#134e3a] text-[var(--accent-2)]";
  const errCls = " bg-[#4a2030] text-[#ff9bb0]";
  if (!text) { m.className = "min-h-[28px]"; m.textContent = ""; return; }
  m.className = base + (ok ? okCls : errCls);
  m.textContent = text;
  setTimeout(() => { m.className = "min-h-[28px]"; m.textContent = ""; }, 4000);
}

async function api(path, opts = {}) {
  const res = await fetch(path, opts);
  let body = null;
  try { body = await res.json(); } catch (e) { /* no json */ }
  return { res, body };
}

async function loadPages() {
  const mid = merchant();
  const { res, body } = await api(`/api/merchant/pages/${mid}`);
  if (!res.ok) { showMsg("Failed to load pages: " + (body?.error || res.status), false); return; }
  const list = $("pageList");
  list.innerHTML = "";
  if (!body.pages || body.pages.length === 0) {
    list.innerHTML = '<div class="py-2 text-sm italic text-[var(--muted)]">No pages yet. Click "+ New Page".</div>';
    return;
  }
  body.pages.forEach((key) => {
    const item = document.createElement("div");
    const isActive = key === currentPageKey;
    // Tailwind utilities replicating .page-item + .page-item.active + hover
    item.className = "page-item group flex items-center justify-between gap-2 rounded-lg border px-3 py-2.5 mb-2 cursor-pointer text-sm transition-colors "
      + (isActive
        ? "bg-[var(--panel-2)] border-[var(--accent)]"
        : "bg-[var(--panel)] border-[var(--border)] hover:border-[var(--accent)]");
    item.innerHTML = `<span class="truncate font-medium">${esc(key)}</span>`;
    item.onclick = () => openPage(key);
    list.appendChild(item);
  });
}

async function openPage(key) {
  currentPageKey = key;
  const mid = merchant();
  const { res, body } = await api(`/api/merchant/pages/${mid}/${key}`);
  if (!res.ok) { showMsg("Failed to open: " + (body?.error || res.status), false); return; }
  $("editor").value = JSON.stringify(body, null, 2);
  // refresh active state without full reload for instant feedback
  document.querySelectorAll(".page-item").forEach((el) => {
    const isActive = el.textContent.trim() === key;
    el.classList.toggle("bg-[var(--panel-2)]", isActive);
    el.classList.toggle("border-[var(--accent)]", isActive);
    el.classList.toggle("bg-[var(--panel)]", !isActive);
    el.classList.toggle("border-[var(--border)]", !isActive);
  });
}

function newPage() {
  const key = prompt("New page key (e.g. my_page):");
  if (!key) return;
  currentPageKey = key.trim();
  const tpl = {
    pageId: merchant() + "-" + currentPageKey,
    pageKey: currentPageKey,
    merchantId: merchant(),
    status: "DRAFT",
    version: 1,
    layout: { type: "Screen", title: "New Screen", components: [] }
  };
  $("editor").value = JSON.stringify(tpl, null, 2);
  document.querySelectorAll(".page-item").forEach((el) => {
    el.classList.remove("bg-[var(--panel-2)]", "border-[var(--accent)]");
    el.classList.add("bg-[var(--panel)]", "border-[var(--border)]");
  });
}

function formatJson() {
  try {
    const obj = JSON.parse($("editor").value);
    $("editor").value = JSON.stringify(obj, null, 2);
    showMsg("Formatted.");
  } catch (e) {
    showMsg("Invalid JSON: " + e.message, false);
  }
}

async function savePage() {
  if (!currentPageKey) { showMsg("Open or create a page first.", false); return; }
  let payload;
  try { payload = JSON.parse($("editor").value); }
  catch (e) { showMsg("Invalid JSON: " + e.message, false); return; }
  const mid = merchant();
  const { res, body } = await api(`/api/merchant/pages/${mid}/${currentPageKey}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
  if (!res.ok) { showMsg("Save failed: " + (body?.error || res.status), false); return; }
  showMsg("Saved " + currentPageKey + " (v" + body.page?.version + ")");
  loadPages();
}

async function publishPage() {
  if (!currentPageKey) { showMsg("Open a page first.", false); return; }
  const mid = merchant();
  const { res, body } = await api(`/api/merchant/pages/${mid}/${currentPageKey}/publish`, { method: "POST" });
  if (!res.ok) { showMsg("Publish failed: " + (body?.error || res.status), false); return; }
  showMsg("Published " + currentPageKey);
  $("editor").value = JSON.stringify(body.page, null, 2);
  loadPages();
}

async function deletePage() {
  if (!currentPageKey) { showMsg("Open a page first.", false); return; }
  if (!confirm("Delete " + currentPageKey + "?")) return;
  const mid = merchant();
  const { res, body } = await api(`/api/merchant/pages/${mid}/${currentPageKey}`, { method: "DELETE" });
  if (!res.ok) { showMsg("Delete failed: " + (body?.error || res.status), false); return; }
  showMsg("Deleted " + currentPageKey);
  currentPageKey = null;
  $("editor").value = "";
  loadPages();
}

async function previewPage() {
  if (!currentPageKey) { showMsg("Open a page first.", false); return; }
  navStack = [currentPageKey];
  await renderClientPage(currentPageKey);
}

// ---- Client-side navigation (SDUI router + history) ----
async function renderClientPage(key) {
  const mid = merchant();
  currentClientKey = key;
  submitActions = {};
  submitCounter = 0;
  const { res, body } = await api(`/api/client/page/${mid}/${key}`);
  const box = $("preview");
  if (!res.ok) {
    box.innerHTML = `<div class="py-2 text-sm italic text-[var(--muted)]">${esc(body?.error || "Not published.")}</div>`;
    return;
  }
  const layoutCls = body.layout && body.layout.class ? esc(body.layout.class) : "";
  // Tailwind preview-frame: bg-white text-[#111] rounded-[10px] p-4 min-h-[200px]
  box.innerHTML = `<div class="bg-white text-[#111] rounded-[10px] p-4 min-h-[200px] shadow-sm ${layoutCls}">${renderScreen(body.layout)}</div>`;
  updateNavBar();
}

async function navigateTo(key) {
  if (!key) return;
  navStack.push(key);
  await renderClientPage(key);
}

async function goBack() {
  if (navStack.length <= 1) return;
  navStack.pop();
  await renderClientPage(navStack[navStack.length - 1]);
}

function updateNavBar() {
  const back = $("navBack");
  const path = $("navPath");
  if (back) back.disabled = navStack.length <= 1;
  if (path) path.textContent = navStack.join("  ›  ");
}

function navBtn(el) {
  if (el.dataset.back) { goBack(); return; }
  if (el.dataset.nav) { navigateTo(el.dataset.nav); return; }
}

async function submitForm(btn) {
  const entry = submitActions[btn.dataset.submitId];
  if (!entry) return;
  const action = entry.action;
  const comp = btn.closest(".comp");
  const data = {};
  comp.querySelectorAll("input, select").forEach((i) => {
    const name = i.name || i.placeholder;
    if (name) data[name] = i.value;
  });
  if (action.action === "PAYMENT") {
    if (!data.amount && entry.amount != null) data.amount = entry.amount;
    if (!data.method && entry.methods && entry.methods[0]) data.method = entry.methods[0];
  }
  const { res, body } = await api(action.endpoint || "/api/client/action/submit", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ action: action.action, data })
  });
  const ok = res.ok && body?.success;
  // store last submitted data for success page (e.g. fullName/email)
  if (ok) {
    lastFormData = { ...data, ...(body && body.data ? body.data : {}) };
  }
  if (ok && action.success && action.success.type === "navigate") {
    if (action.success.message) showMsg(action.success.message);
    navigateTo(action.success.target);
    return;
  }
  if (!ok && action.error && action.error.message) { showMsg(action.error.message, false); return; }
  showMsg(body?.message || (ok ? "Submitted" : "Request failed"), ok);
}

async function selectSeat(el) {
  const action = submitActions[el.dataset.seatActionId];
  if (!action) return;
  const data = { seatId: el.dataset.seatId, section: el.dataset.section };
  const { res, body } = await api(action.endpoint || "/api/client/action/submit", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ action: action.action, data })
  });
  const ok = res.ok && body?.success;
  if (ok && action.success && action.success.type === "navigate") {
    if (action.success.message) showMsg(action.success.message);
    navigateTo(action.success.target);
    return;
  }
  if (!ok) { showMsg(body?.error || (action.error && action.error.message) || "Reserve failed", false); return; }
  showMsg(body?.message || "Reserved");
}

function esc(s) {
  return String(s == null ? "" : s).replace(/[&<>"]/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
}

// resolve {{key}} placeholders with lastFormData (escapes values)
function tpl(str) {
  if (typeof str !== "string") return esc(str);
  return str.replace(/\{\{(\w+)\}\}/g, (_, k) => esc(lastFormData[k] ?? ""));
}

// join class names, dropping falsy values
const cx = (...parts) => parts.filter(Boolean).join(" ");

function renderScreen(layout) {
  if (!layout) return '<div class="py-2 text-sm italic text-[#888]">No layout.</div>';
  let html = `<div class="text-xl font-bold leading-tight mb-1 text-[#111]">${tpl(layout.title)}</div>`;
  if (layout.subtitle) html += `<div class="text-sm text-[#555] mb-4">${tpl(layout.subtitle)}</div>`;
  const comps = layout.components || [];
  comps.forEach((c) => { html += renderComponent(c); });
  return html;
}

function renderComponent(c) {
  if (!c) return "";
  const cclass = c.class || "";
  // Tailwind comp: border border-[#ddd] rounded-[10px] p-3 mb-3 bg-white
  // Keep "comp" marker for JS closest() queries, but style with utilities
  let html = `<div class="${cx("comp border border-[#ddd] rounded-[10px] p-3 mb-3 bg-white", c.type === "Button" ? "" : cclass)}">`;
  html += `<div class="text-[11px] uppercase tracking-[0.05em] text-[#888] font-medium">${esc(c.type)}</div>`;
  if (c.title) html += `<div class="font-semibold text-sm mt-1 mb-1 text-[#111]">${esc(c.title)}</div>`;
  if (c.label && c.type !== "Button") html += `<div class="font-semibold text-sm mt-1 mb-1 text-[#111]">${esc(c.label)}</div>`;

  if (c.type === "SeatGrid" && c.sections) {
    const saId = c.actions && c.actions.onSelect ? ("sa" + (++submitCounter)) : null;
    if (saId) submitActions[saId] = c.actions.onSelect;
    c.sections.forEach((s) => {
      const baseRowCls = "seat-row flex items-center justify-between rounded-lg border border-dashed border-[#bbb] bg-[#f7f8ff] px-3 py-2 my-1.5 cursor-pointer text-sm text-[#111] hover:border-[var(--accent)] transition-colors";
      const attrs = saId
        ? ` class="${baseRowCls}" data-seat-action-id="${saId}" data-seat-id="${esc(s.id)}" data-section="${esc(s.name)}" onclick="selectSeat(this)"`
        : ` class="${baseRowCls} cursor-default"`;
      html += `<div${attrs}>${esc(s.name)} — $${esc(s.price)} (${esc(s.available)} seats)${saId ? " →" : ""}</div>`;
    });
  }

  if (c.type === "OrderSummary" && c.lines) {
    c.lines.forEach((l) => { html += `<div class="text-sm text-[#111] py-0.5">${esc(l.label)}: $${esc(l.amount)}</div>`; });
    html += `<div class="font-bold text-sm text-[#111] mt-1">Total: $${esc(c.total)}</div>`;
  }

  if (c.type === "ConfirmationCard") {
    html += `<div class="text-sm text-[#111]">Order: ${esc(c.orderId)}</div>`;
    html += `<div class="text-sm text-[#111]">Event: ${esc(c.event)}</div>`;
    html += `<div class="text-sm text-[#111]">Seats: ${esc((c.seats || []).join(", "))}</div>`;
    html += `<div class="font-bold text-sm text-[#111] mt-1">Total: $${esc(c.total)}</div>`;
  }

  if (c.type === "SuccessCard") {
    const fullName = tpl(c.fullName || "{{fullName}}");
    const email = tpl(c.email || "{{email}}");
    const message = c.message ? tpl(c.message) : "";
    const icon = c.icon ? esc(c.icon) : "✅";
    const hasData = lastFormData.fullName || lastFormData.email;
    html += `<div class="flex items-start gap-3">`;
    html += `<div class="text-2xl leading-none">${icon}</div><div class="flex-1">`;
    if (message) html += `<div class="text-sm font-medium text-[#111] mb-2">${message}</div>`;
    if (hasData) {
      html += `<div class="rounded-lg bg-white border border-[#ddd] p-3 space-y-1">`;
      html += `<div class="text-sm text-[#111]"><span class="font-semibold">Full Name:</span> ${fullName || '<span class="text-[#888] italic">—</span>'}</div>`;
      html += `<div class="text-sm text-[#111]"><span class="font-semibold">Email:</span> ${email || '<span class="text-[#888] italic">—</span>'}</div>`;
      html += `</div>`;
    } else {
      html += `<div class="rounded-lg bg-amber-50 border border-amber-200 p-3 text-sm text-amber-800">No submission data yet. Please submit the checkout form first.</div>`;
    }
    html += `</div></div>`;
  }

  if (c.type === "Button") {
    const act = c.actions && c.actions.onClick ? c.actions.onClick : c.action;
    let attr = "";
    if (act && act.type === "back") attr = `data-back="1"`;
    else if (act && act.type === "navigate") attr = `data-nav="${esc(act.target)}"`;
    // Use class from JSON (Tailwind utilities) instead of style
    // Fallback to primary style if no class provided
    const btnCls = c.class
      ? c.class
      : "inline-flex items-center justify-center rounded-lg bg-[var(--accent)] text-white px-4 py-2 text-sm font-semibold border-0 hover:brightness-110 transition-colors";
    html += `<button class="${btnCls}" ${attr} onclick="navBtn(this)">${esc(c.label || "Button")}</button>`;
    html += "</div>";
    return html;
  }

  if (c.type === "Form" || c.type === "PaymentForm") {
    (c.fields || []).forEach((f) => {
      const req = f.required ? ' <span class="text-red-500">*</span>' : "";
      html += `<div class="mt-2"><label class="text-sm font-medium text-[#111]">${esc(f.label)}${req}</label><br/><input class="mt-1 w-[90%] rounded-lg border border-[#ddd] bg-white px-2.5 py-2 text-sm text-[#111] placeholder:text-[#888] focus:outline-none focus:ring-2 focus:ring-[var(--accent)] focus:border-[var(--accent)]" placeholder="${esc(f.name)}" name="${esc(f.name)}" /></div>`;
    });
    if (c.actions && c.actions.onSubmit) {
      const saId = "sa" + (++submitCounter);
      submitActions[saId] = { action: c.actions.onSubmit, amount: c.amount, methods: c.methods };
      html += `<div class="mt-3"><button class="inline-flex items-center justify-center rounded-lg bg-[var(--accent)] text-white px-4 py-2 text-sm font-semibold border-0 hover:brightness-110 transition-colors" data-submit-id="${saId}" onclick="submitForm(this)">${esc(c.label || "Submit")}</button></div>`;
    } else if (c.actions && c.actions.onClick) {
      const act = c.actions.onClick;
      let attr = act.type === "back" ? `data-back="1"` : act.type === "navigate" ? `data-nav="${esc(act.target)}"` : "";
      html += `<div class="mt-3"><button class="inline-flex items-center justify-center rounded-lg border border-[#ddd] bg-white px-4 py-2 text-sm font-medium text-[#111] hover:border-[var(--accent)] transition-colors" ${attr} onclick="navBtn(this)">${esc(c.label || "Action")}</button></div>`;
    }
    html += "</div>";
    return html;
  }

  html += "</div>";
  return html;
}

// initial load
loadPages();
