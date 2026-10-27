const STATUSES = ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED', 'CANCELLED'];
const PRIORITIES = ['URGENT', 'HIGH', 'MEDIUM', 'LOW'];
const CATEGORIES = ['HVAC', 'ELECTRICAL', 'PLUMBING', 'CLEANING', 'SECURITY', 'GENERAL'];
const CLOSED = new Set(['COMPLETED', 'CANCELLED']);

const $ = (id) => document.getElementById(id);
const state = { selectedId: null };

function label(value) {
  if (value === 'HVAC') return value;
  return value.toLowerCase().split('_').map((w) => w[0].toUpperCase() + w.slice(1)).join(' ');
}

function el(tag, attrs = {}, text) {
  const node = document.createElement(tag);
  Object.entries(attrs).forEach(([key, value]) => node.setAttribute(key, value));
  if (text !== undefined && text !== null) node.textContent = text;
  return node;
}

function fillSelect(select, values) {
  values.forEach((value) => select.append(el('option', { value }, label(value))));
}

function formatDuration(ms) {
  const minutes = Math.round(Math.abs(ms) / 60000);
  if (minutes < 60) return `${minutes}m`;
  const hours = Math.round(minutes / 60);
  if (hours < 48) return `${hours}h`;
  return `${Math.round(hours / 24)}d`;
}

function dueText(order) {
  if (CLOSED.has(order.status)) return label(order.status);
  const diff = new Date(order.dueAt).getTime() - Date.now();
  return order.overdue ? `Overdue by ${formatDuration(diff)}` : `Due in ${formatDuration(diff)}`;
}

function formatTime(iso) {
  return iso ? new Date(iso).toLocaleString() : '—';
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });
  const body = response.status === 204 ? null : await response.json();
  if (!response.ok) {
    const error = new Error(body?.detail || `Request failed (${response.status})`);
    error.fieldErrors = body?.errors || {};
    throw error;
  }
  return body;
}

function showError(node, message) {
  node.textContent = message || '';
  node.hidden = !message;
}

async function loadSummary() {
  const summary = await api('/api/work-orders/summary');
  $('summary-active').textContent = summary.active;
  $('summary-overdue').textContent = summary.overdue;
  $('summary-at-risk').textContent = summary.atRisk;
  $('summary-urgent').textContent = summary.urgentActive;
  $('summary-completed').textContent = summary.byStatus.COMPLETED;
}

function currentFilters() {
  const params = new URLSearchParams();
  const query = $('filter-query').value.trim();
  if (query) params.set('q', query);
  if ($('filter-status').value) params.set('status', $('filter-status').value);
  if ($('filter-priority').value) params.set('priority', $('filter-priority').value);
  if ($('filter-category').value) params.set('category', $('filter-category').value);
  if ($('filter-overdue').checked) params.set('overdue', 'true');
  if ($('filter-at-risk').checked) params.set('atRisk', 'true');
  return params.toString();
}

async function loadOrders() {
  try {
    const query = currentFilters();
    const orders = await api(`/api/work-orders${query ? `?${query}` : ''}`);
    renderRows(orders);
    showError($('list-error'), '');
  } catch (error) {
    showError($('list-error'), error.message);
  }
}

function renderRows(orders) {
  const rows = $('work-order-rows');
  rows.replaceChildren();
  $('empty-state').hidden = orders.length > 0;
  orders.forEach((order) => {
    const row = el('tr', { 'data-testid': `work-order-row-${order.id}`, tabindex: '0' });
    if (order.id === state.selectedId) row.classList.add('selected');
    row.append(
      el('td', { class: 'mono' }, order.id),
      cellWithSub(order.title, `${order.location} · ${label(order.category)}`),
      badgeCell(label(order.priority), `priority-${order.priority.toLowerCase()}`),
      badgeCell(label(order.status), `status-${order.status.toLowerCase()}`),
      el('td', {}, order.assignee || 'Unassigned'),
      dueCell(order),
    );
    row.addEventListener('click', () => selectOrder(order.id));
    row.addEventListener('keydown', (event) => { if (event.key === 'Enter') selectOrder(order.id); });
    rows.append(row);
  });
}

function dueCell(order) {
  const classes = [];
  if (order.overdue) classes.push('overdue');
  if (order.atRisk) classes.push('at-risk');
  const cell = el('td', { class: classes.join(' ') }, dueText(order));
  if (order.atRisk) {
    cell.append(' ', el('span', { class: 'badge at-risk-badge', 'data-testid': `at-risk-badge-${order.id}` }, 'At risk'));
  }
  return cell;
}

function cellWithSub(main, sub) {
  const cell = el('td');
  cell.append(el('div', { class: 'strong' }, main), el('div', { class: 'muted small' }, sub));
  return cell;
}

function badgeCell(text, cls) {
  const cell = el('td');
  cell.append(el('span', { class: `badge ${cls}` }, text));
  return cell;
}

async function selectOrder(id) {
  state.selectedId = id;
  try {
    renderDetail(await api(`/api/work-orders/${encodeURIComponent(id)}`));
  } catch (error) {
    showError($('list-error'), error.message);
  }
  await loadOrders();
}

function renderDetail(order) {
  $('detail').hidden = false;
  $('detail-id').textContent = order.id;
  $('detail-title').textContent = order.title;
  $('detail-description').textContent = order.description || 'No description provided.';
  showError($('detail-error'), '');

  const meta = $('detail-meta');
  meta.replaceChildren();
  [
    ['Location', order.location],
    ['Category', label(order.category)],
    ['Priority', label(order.priority)],
    ['Status', label(order.status)],
    ['Requester', order.requester],
    ['Assignee', order.assignee || 'Unassigned'],
    ['Created', formatTime(order.createdAt)],
    ['Due', `${formatTime(order.dueAt)} (${dueText(order)})`],
  ].forEach(([term, value]) => meta.append(el('dt', {}, term), el('dd', {}, value)));

  const closed = CLOSED.has(order.status);
  $('assign-form').hidden = closed;
  $('assignee-input').value = order.assignee || '';

  const buttons = $('status-buttons');
  buttons.replaceChildren();
  order.nextStatuses
    .filter((status) => status !== 'ASSIGNED')
    .forEach((status) => {
      const button = el('button', { type: 'button', 'data-testid': `status-${status}` }, label(status));
      button.addEventListener('click', () => changeStatus(order.id, status));
      buttons.append(button);
    });
  buttons.closest('.status-actions').hidden = buttons.children.length === 0;

  const activity = $('detail-activity');
  activity.replaceChildren();
  order.activity.slice().reverse().forEach((entry) => {
    const item = el('li');
    item.append(el('span', { class: 'muted small' }, formatTime(entry.at)), el('div', {}, entry.message));
    activity.append(item);
  });
}

async function refreshSelected(order) {
  renderDetail(order);
  await Promise.all([loadOrders(), loadSummary()]);
}

async function changeStatus(id, status) {
  try {
    const note = $('status-note').value.trim();
    const order = await api(`/api/work-orders/${encodeURIComponent(id)}/status`, {
      method: 'POST',
      body: JSON.stringify({ status, note: note || null }),
    });
    $('status-note').value = '';
    await refreshSelected(order);
  } catch (error) {
    showError($('detail-error'), error.message);
  }
}

$('assign-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  try {
    const order = await api(`/api/work-orders/${encodeURIComponent(state.selectedId)}/assign`, {
      method: 'POST',
      body: JSON.stringify({ assignee: $('assignee-input').value }),
    });
    await refreshSelected(order);
  } catch (error) {
    const fieldMessage = error.fieldErrors.assignee ? `Assignee ${error.fieldErrors.assignee}` : '';
    showError($('detail-error'), fieldMessage || error.message);
  }
});

$('detail-close').addEventListener('click', () => {
  state.selectedId = null;
  $('detail').hidden = true;
  loadOrders();
});

const dialog = $('new-request-dialog');
const form = $('new-request-form');

$('new-request-button').addEventListener('click', () => {
  form.reset();
  showError($('form-errors'), '');
  dialog.showModal();
});
$('cancel-new').addEventListener('click', () => dialog.close());

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  const payload = Object.fromEntries(new FormData(form).entries());
  try {
    const order = await api('/api/work-orders', { method: 'POST', body: JSON.stringify(payload) });
    dialog.close();
    state.selectedId = order.id;
    await refreshSelected(order);
  } catch (error) {
    const fields = Object.entries(error.fieldErrors).map(([field, message]) => `${label(field)} ${message}`);
    showError($('form-errors'), fields.length ? fields.join('. ') : error.message);
  }
});

let searchTimer;
$('filter-query').addEventListener('input', () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(loadOrders, 200);
});
['filter-status', 'filter-priority', 'filter-category', 'filter-overdue', 'filter-at-risk']
  .forEach((id) => $(id).addEventListener('change', loadOrders));

fillSelect($('filter-status'), STATUSES);
fillSelect($('filter-priority'), PRIORITIES);
fillSelect($('filter-category'), CATEGORIES);
fillSelect(form.elements.category, CATEGORIES);
fillSelect(form.elements.priority, PRIORITIES);
form.elements.priority.value = 'MEDIUM';

loadSummary();
loadOrders();
