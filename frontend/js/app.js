const API = "http://localhost:8080/api";
const $ = (selector) => document.querySelector(selector);
const $$ = (selector) => [...document.querySelectorAll(selector)];
let csrfToken = "";
let cachedCustomers = [];
let reportRows = [];
let paymentRequestKey = "";
let currentUser = "Staff";

const currency = (value) => new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(Number(value || 0));
const safe = (value) => String(value ?? "").replace(/[&<>"']/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[character]);
const localDate = () => {
  const date = new Date();
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
};

function notify(message, error = false) {
  const node = document.createElement("div");
  node.className = `toast-message${error ? " error" : ""}`;
  node.textContent = message;
  $("#toast-area").append(node);
  window.setTimeout(() => node.remove(), 4500);
}

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  if (!["GET", "HEAD"].includes((options.method || "GET").toUpperCase()) && csrfToken) {
    headers.set("X-XSRF-TOKEN", csrfToken);
  }
  const response = await fetch(`${API}${path}`, { ...options, headers, credentials: "include" });
  if (response.status === 401) {
    window.location.href = "login.html";
    throw new Error("Please sign in again.");
  }
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    const details = body.fields ? Object.values(body.fields).join(". ") : "";
    throw new Error(details || body.message || body.error || `Request failed (${response.status})`);
  }
  if (response.status === 204) return null;
  return response.json();
}

function setEmpty(tbody, columns, message = "No records found.") {
  tbody.innerHTML = `<tr><td colspan="${columns}" class="empty-state">${safe(message)}</td></tr>`;
}

function pageName() {
  const page = (location.hash.slice(1) || "dashboard").split("?")[0];
  return ["dashboard", "customers", "credits", "payments", "ledger", "dues", "reports"].includes(page) ? page : "dashboard";
}

function showPage() {
  const page = pageName();
  $$(".page-section").forEach((section) => section.classList.toggle("d-none", section.id !== `page-${page}`));
  $$("#main-nav .nav-link").forEach((link) => link.classList.toggle("active", link.getAttribute("href") === `#${page}`));
  $("#page-title").textContent = ({ dashboard: "Dashboard", customers: "Customers", credits: "Credit entries", payments: "Payments", ledger: "Customer ledger", dues: "Pending dues", reports: "Reports" })[page];
  const loaders = { dashboard: loadDashboard, customers: loadCustomers, credits: loadCredits, payments: loadPayments, ledger: loadLedger, dues: loadDues, reports: runReport };
  loaders[page]().catch((error) => notify(error.message, true));
}

function updateCustomerSelects() {
  const all = cachedCustomers;
  const active = all.filter((customer) => customer.active);
  const positiveDues = all.filter((customer) => Number(customer.balance) > 0);
  const groups = [
    [$("#credit-customer"), active, "Select a customer"],
    [$("#payment-customer"), positiveDues, "Select a customer with outstanding dues"],
    [$("#ledger-customer"), all, "Select a customer"]
  ];
  groups.forEach(([select, customers, label]) => {
    const previous = select.value;
    select.innerHTML = `<option value="">${label}</option>` + customers.map((customer) =>
      `<option value="${customer.id}">${safe(customer.fullName)} · ${safe(customer.phone)}</option>`).join("");
    if (customers.some((customer) => String(customer.id) === previous)) select.value = previous;
  });
}

async function refreshCustomers(search = "") {
  cachedCustomers = await request(`/customers${search ? `?search=${encodeURIComponent(search)}` : ""}`);
  if (!search) updateCustomerSelects();
}

async function loadDashboard() {
  const data = await request("/dashboard");
  const summary = data.summary;
  $("#stat-customers").textContent = summary.activeCustomers;
  $("#stat-credit").textContent = currency(summary.totalCredit);
  $("#stat-payments").textContent = currency(summary.totalPayments);
  $("#stat-outstanding").textContent = currency(summary.outstanding);
  $("#stat-dues").textContent = summary.customersWithDues;
  const body = $("#recent-table");
  if (!data.recentTransactions.length) return setEmpty(body, 5, "No transactions have been recorded yet.");
  body.innerHTML = data.recentTransactions.map((row) => `<tr>
    <td>${safe(row.transactionDate)}</td><td><span class="customer-name">${safe(row.customerName)}</span></td>
    <td><span class="badge-status ${row.type === "PAYMENT" ? "" : "inactive"}">${safe(row.type)}</span></td>
    <td>${safe(row.description)}</td><td class="text-end ${row.type === "PAYMENT" ? "amount-positive" : ""}">${currency(row.amount)}</td></tr>`).join("");
}

async function loadCustomers(search = "") {
  if (!search) await refreshCustomers();
  else cachedCustomers = await request(`/customers?search=${encodeURIComponent(search)}`);
  const body = $("#customers-table");
  if (!cachedCustomers.length) return setEmpty(body, 5, "No matching customers. Add your first customer using the form.");
  body.innerHTML = cachedCustomers.map((customer) => `<tr>
    <td><button class="btn btn-link p-0 customer-name text-decoration-none" data-action="details" data-id="${customer.id}">${safe(customer.fullName)}</button><div class="customer-sub">ID #${customer.id}</div></td>
    <td>${safe(customer.phone)}</td><td><span class="badge-status ${customer.active ? "" : "inactive"}">${customer.active ? "Active" : "Archived"}</span></td>
    <td class="text-end ${Number(customer.balance) > 0 ? "amount-negative" : ""}">${currency(customer.balance)}</td>
    <td class="text-end text-nowrap"><button class="btn btn-light" data-action="edit" data-id="${customer.id}">Edit</button>
      <button class="btn btn-light" data-action="status" data-id="${customer.id}" data-active="${customer.active}">${customer.active ? "Archive" : "Restore"}</button></td></tr>`).join("");
  updateCustomerSelects();
}

async function loadCredits() {
  await refreshCustomers();
  const rows = await request("/credits");
  const body = $("#credits-table");
  if (!rows.length) return setEmpty(body, 4);
  body.innerHTML = rows.map((entry) => `<tr><td>${safe(entry.transactionDate)}</td><td>${safe(entry.customerName)}</td><td>${safe(entry.description)}</td><td class="text-end">${currency(entry.amount)}</td></tr>`).join("");
}

async function loadPayments() {
  await refreshCustomers();
  const rows = await request("/payments");
  const body = $("#payments-table");
  if (!rows.length) return setEmpty(body, 4);
  body.innerHTML = rows.map((entry) => `<tr><td>${safe(entry.transactionDate)}</td><td>${safe(entry.customerName)}</td><td>${safe(entry.paymentMethod)}</td><td class="text-end amount-positive">${currency(entry.amount)}</td></tr>`).join("");
}

async function loadLedger() {
  await refreshCustomers();
  const customerId = $("#ledger-customer").value;
  if (!customerId) {
    $("#ledger-summary").innerHTML = "";
    return setEmpty($("#ledger-table"), 6, "Choose a customer to view their ledger.");
  }
  const ledger = await request(`/customers/${customerId}/ledger`);
  const { customer } = ledger;
  const totals = ledger.totals;
  $("#ledger-summary").innerHTML = `<div class="col-sm-4"><div class="summary-chip"><small>Total credit</small><strong>${currency(totals.totalCredit)}</strong></div></div>
    <div class="col-sm-4"><div class="summary-chip"><small>Total payments</small><strong>${currency(totals.totalPayments)}</strong></div></div>
    <div class="col-sm-4"><div class="summary-chip"><small>Current balance · ${safe(customer.fullName)}</small><strong>${currency(totals.balance)}</strong></div></div>`;
  const body = $("#ledger-table");
  if (!ledger.entries.length) return setEmpty(body, 6, "No transactions for this customer.");
  body.innerHTML = [...ledger.entries].reverse().map((entry) => `<tr><td>${safe(entry.transactionDate)}</td>
    <td><span class="badge-status ${entry.type === "PAYMENT" ? "" : "inactive"}">${safe(entry.type)}</span></td>
    <td>${safe(entry.description)}</td><td class="text-end">${Number(entry.credit) ? currency(entry.credit) : "—"}</td>
    <td class="text-end">${Number(entry.payment) ? currency(entry.payment) : "—"}</td><td class="text-end fw-bold">${currency(entry.balance)}</td></tr>`).join("");
}

async function loadDues() {
  const rows = await request("/reports/dues");
  const body = $("#dues-table");
  if (!rows.length) return setEmpty(body, 5, "There are no pending balances.");
  body.innerHTML = rows.map((row) => `<tr data-search="${safe(`${row.customerName} ${row.phone}`.toLowerCase())}">
    <td class="customer-name">${safe(row.customerName)}</td><td>${safe(row.phone)}</td><td>${safe(row.oldestCredit)}</td>
    <td>${safe(row.daysSinceOldestCredit)}</td><td class="text-end amount-negative">${currency(row.balance)}</td></tr>`).join("");
  filterDues();
}

async function runReport() {
  const type = $("#report-type").value;
  const from = $("#report-from").value || localDate();
  const to = $("#report-to").value || localDate();
  let endpoint = `/reports/${type}`;
  if (type !== "outstanding") endpoint += `?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
  reportRows = await request(endpoint);
  const head = $("#report-head");
  const body = $("#report-table");
  const shapes = {
    credits: { columns: ["Date", "Customer", "Phone", "Description", "Amount"], cells: (row) => [row.transactionDate, row.customerName, row.phone, row.description, currency(row.amount)] },
    payments: { columns: ["Date", "Customer", "Phone", "Method", "Amount"], cells: (row) => [row.transactionDate, row.customerName, row.phone, row.paymentMethod, currency(row.amount)] },
    outstanding: { columns: ["Customer", "Phone", "Total credit", "Total payments", "Balance"], cells: (row) => [row.fullName, row.phone, currency(row.totalCredit), currency(row.totalPayments), currency(row.balance)] }
  }[type];
  head.innerHTML = `<tr>${shapes.columns.map((column) => `<th>${safe(column)}</th>`).join("")}</tr>`;
  if (!reportRows.length) return setEmpty(body, shapes.columns.length, "No report records match this selection.");
  body.innerHTML = reportRows.map((row) => `<tr>${shapes.cells(row).map((cell) => `<td>${safe(cell)}</td>`).join("")}</tr>`).join("");
}

function filterDues() {
  const term = $("#dues-search").value.trim().toLowerCase();
  $$("#dues-table tr[data-search]").forEach((row) => row.classList.toggle("d-none", !row.dataset.search.includes(term)));
}

function downloadCsv() {
  if (!reportRows.length) return notify("Run a report with data before exporting CSV.", true);
  const type = $("#report-type").value;
  const fields = type === "credits" ? ["transactionDate", "customerName", "phone", "description", "amount", "notes"]
    : type === "payments" ? ["transactionDate", "customerName", "phone", "paymentMethod", "amount", "notes"]
      : ["fullName", "phone", "totalCredit", "totalPayments", "balance"];
  const csv = [fields.join(","), ...reportRows.map((row) => fields.map((field) => `"${String(row[field] ?? "").replaceAll('"', '""')}"`).join(","))].join("\r\n");
  const link = document.createElement("a");
  link.href = URL.createObjectURL(new Blob(["\ufeff", csv], { type: "text/csv;charset=utf-8" }));
  link.download = `smart-shop-${type}-${localDate()}.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
}

function resetCustomerForm() {
  $("#customer-form").reset();
  $("#customer-id").value = "";
  $("#customer-form-title").textContent = "Add customer";
  $("#customer-submit").textContent = "Save customer";
  $("#cancel-edit").classList.add("d-none");
}

$("#customer-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const id = $("#customer-id").value;
  const payload = { fullName: $("#customer-name").value.trim(), phone: $("#customer-phone").value.trim(), address: $("#customer-address").value.trim() || null };
  try {
    await request(id ? `/customers/${id}` : "/customers", { method: id ? "PUT" : "POST", body: JSON.stringify(payload) });
    notify(id ? "Customer details updated." : "Customer added successfully.");
    resetCustomerForm();
    await loadCustomers($("#customer-search").value);
  } catch (error) { notify(error.message, true); }
});

$("#customers-table").addEventListener("click", async (event) => {
  const button = event.target.closest("[data-action]");
  if (!button) return;
  const customer = cachedCustomers.find((item) => String(item.id) === button.dataset.id) || await request(`/customers/${button.dataset.id}`);
  if (button.dataset.action === "edit") {
    $("#customer-id").value = customer.id;
    $("#customer-name").value = customer.fullName;
    $("#customer-phone").value = customer.phone;
    $("#customer-address").value = customer.address || "";
    $("#customer-form-title").textContent = "Edit customer";
    $("#customer-submit").textContent = "Save changes";
    $("#cancel-edit").classList.remove("d-none");
    window.scrollTo({ top: 0, behavior: "smooth" });
  } else if (button.dataset.action === "status") {
    const active = button.dataset.active !== "true";
    if (!window.confirm(`${active ? "Restore" : "Archive"} ${customer.fullName}? Transaction records will be preserved.`)) return;
    try {
      await request(`/customers/${customer.id}/status`, { method: "PATCH", body: JSON.stringify({ active }) });
      notify(active ? "Customer restored." : "Customer archived; financial history is preserved.");
      await loadCustomers($("#customer-search").value);
    } catch (error) { notify(error.message, true); }
  } else {
    $("#customer-modal-body").innerHTML = `<p><strong>${safe(customer.fullName)}</strong></p><p class="text-secondary">${safe(customer.phone)}</p><p>${safe(customer.address || "No address saved")}</p><p>Outstanding: <strong>${currency(customer.balance)}</strong></p><p>Status: ${customer.active ? "Active" : "Archived"}</p>`;
    $("#modal-ledger-link").onclick = () => { $("#ledger-customer").value = String(customer.id); };
    bootstrap.Modal.getOrCreateInstance($("#customer-modal")).show();
  }
});

$("#cancel-edit").addEventListener("click", resetCustomerForm);
$("#customer-search").addEventListener("input", (event) => loadCustomers(event.target.value).catch((error) => notify(error.message, true)));

$("#credit-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const button = form.querySelector("button[type=submit]");
  button.disabled = true;
  try {
    const result = await request("/credits", { method: "POST", body: JSON.stringify({
      customerId: Number($("#credit-customer").value), description: $("#credit-description").value.trim(),
      amount: Number($("#credit-amount").value), transactionDate: $("#credit-date").value, notes: $("#credit-notes").value.trim() || null
    }) });
    notify(`Credit saved. New balance: ${currency(result.balance)}`);
    form.reset();
    $("#credit-date").value = localDate();
    await Promise.all([refreshCustomers(), loadCredits()]);
  } catch (error) { notify(error.message, true); }
  finally { button.disabled = false; }
});

$("#payment-customer").addEventListener("change", () => {
  const customer = cachedCustomers.find((item) => String(item.id) === $("#payment-customer").value);
  $("#payment-balance").textContent = customer ? `Current outstanding balance: ${currency(customer.balance)}` : "Choose a customer to see their balance.";
});

$("#payment-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const button = form.querySelector("button[type=submit]");
  if (!paymentRequestKey) paymentRequestKey = crypto.randomUUID();
  button.disabled = true;
  try {
    const result = await request("/payments", { method: "POST", body: JSON.stringify({
      customerId: Number($("#payment-customer").value), amount: Number($("#payment-amount").value),
      transactionDate: $("#payment-date").value, paymentMethod: $("#payment-method").value,
      notes: $("#payment-notes").value.trim() || null, requestKey: paymentRequestKey
    }) });
    paymentRequestKey = "";
    notify(`${result.message}. Remaining balance: ${currency(result.balance)}`);
    form.reset();
    $("#payment-date").value = localDate();
    $("#payment-balance").textContent = "Choose a customer to see their balance.";
    await Promise.all([refreshCustomers(), loadPayments()]);
  } catch (error) { notify(error.message, true); }
  finally { button.disabled = false; }
});

$("#ledger-customer").addEventListener("change", () => loadLedger().catch((error) => notify(error.message, true)));
$("#dues-search").addEventListener("input", filterDues);
$("#run-report").addEventListener("click", () => runReport().catch((error) => notify(error.message, true)));
$("#report-type").addEventListener("change", () => runReport().catch((error) => notify(error.message, true)));
$("#export-csv").addEventListener("click", downloadCsv);

$("#logout-button").addEventListener("click", async () => {
  try {
    await request("/auth/logout", { method: "POST" });
    window.location.href = "login.html";
  } catch (error) { notify(error.message, true); }
});

window.addEventListener("hashchange", showPage);

(async function initialize() {
  try {
    const csrfResponse = await fetch(`${API}/auth/csrf`, { credentials: "include" });
    if (!csrfResponse.ok) throw new Error("Cannot reach backend");
    csrfToken = (await csrfResponse.json()).token;
    const session = await request("/auth/session");
    currentUser = session.username;
    $("#signed-in-user").textContent = currentUser;
    $("#today-label").textContent = new Intl.DateTimeFormat("en-IN", { dateStyle: "full" }).format(new Date());
    $("#credit-date").value = localDate();
    $("#payment-date").value = localDate();
    $("#report-from").value = localDate();
    $("#report-to").value = localDate();
    await refreshCustomers();
    showPage();
  } catch (error) {
    if (!location.pathname.endsWith("login.html")) window.location.href = "login.html";
  }
})();
