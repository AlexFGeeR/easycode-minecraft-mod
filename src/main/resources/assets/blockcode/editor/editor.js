/* BlockCode: рабочая область, Python-панель и связь с игрой. */
'use strict';

// ------------------------------------------------------------------ связь с игрой
// В игре MCEF добавляет window.cefQuery. В обычном браузере работает демо-режим —
// удобно, чтобы учитель посмотрел редактор без запуска Minecraft.
const IN_GAME = typeof window.cefQuery === 'function';

function query(cmd, extra = {}) {
  const request = JSON.stringify(Object.assign({ cmd }, extra));
  if (IN_GAME) {
    return new Promise((resolve, reject) => window.cefQuery({
      request, persistent: false,
      onSuccess: resolve,
      onFailure: (code, message) => reject(new Error(message)),
    }));
  }
  return Promise.resolve(demoQuery(cmd, extra));
}

function demoQuery(cmd, extra) {
  const key = (name) => 'blockcode:' + name;
  const store = (() => { try { return window.localStorage; } catch (e) { return null; } })();
  switch (cmd) {
    case 'run':
      console.log('Программа для агента:', JSON.stringify(extra.program, null, 1));
      return 'demo';
    case 'save': if (store) store.setItem(key(extra.name), extra.data); return 'ok';
    case 'load': return (store && store.getItem(key(extra.name))) || '';
    case 'delete': if (store) store.removeItem(key(extra.name)); return 'ok';
    case 'list': {
      const names = [];
      if (store) for (let i = 0; i < store.length; i++) {
        const k = store.key(i);
        if (k.startsWith('blockcode:') && k !== key(AUTOSAVE)) names.push(k.slice(10));
      }
      return JSON.stringify(names.sort());
    }
    default: return 'demo';
  }
}

const AUTOSAVE = '_автосохранение'; // то же имя в Projects.java

// ------------------------------------------------------------------ рабочая область
const workspace = Blockly.inject('workspace', {
  toolbox: TOOLBOX,
  renderer: 'zelos',
  media: 'blockly/media/',
  sounds: false,
  trashcan: true,
  move: { scrollbars: true, drag: true, wheel: false },
  zoom: { controls: true, wheel: true, startScale: 0.85, maxScale: 2, minScale: 0.4, scaleSpeed: 1.15 },
  grid: { spacing: 24, length: 2, colour: '#dde2ee', snap: true },
});

function loadXml(xmlText) {
  workspace.clear();
  const dom = (Blockly.utils.xml.textToDom || Blockly.Xml.textToDom)(xmlText);
  Blockly.Xml.domToWorkspace(dom, workspace);
  workspace.scrollCenter();
}

function loadState(text) {
  workspace.clear();
  Blockly.serialization.workspaces.load(JSON.parse(text), workspace);
}

const saveState = () => JSON.stringify(Blockly.serialization.workspaces.save(workspace));

// ------------------------------------------------------------------ Python
const pyEl = document.getElementById('python');
const KW = new Set('def for in range while if elif else not and or return pass global True False None break continue'.split(' '));

function highlight(code) {
  const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  // один проход регуляркой, чтобы не подсветить слова внутри строк
  const re = /(#.*$)|("(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')|(\b\d+(?:\.\d+)?\b)|(\b[A-Z][A-Z_]{2,}\b)|([\p{L}_][\p{L}\p{N}_]*)(?=\()|([\p{L}_][\p{L}\p{N}_]*)/gmu;
  let out = '', last = 0, m;
  while ((m = re.exec(code))) {
    out += esc(code.slice(last, m.index));
    const [t] = m;
    if (m[1]) out += `<span class="com">${esc(t)}</span>`;
    else if (m[2]) out += `<span class="str">${esc(t)}</span>`;
    else if (m[3]) out += `<span class="num">${t}</span>`;
    else if (m[4]) out += `<span class="const">${t}</span>`;
    else if (KW.has(t) && t !== 'range') out += `<span class="kw">${t}</span>`;
    else if (m[5]) out += `<span class="fn">${esc(t)}</span>`;
    else out += esc(t);
    last = m.index + t.length;
  }
  return out + esc(code.slice(last));
}

function updatePython() {
  let code;
  try {
    code = PY.workspaceToCode(workspace).trim();
  } catch (e) {
    code = '# Не удалось показать Python: ' + e.message;
  }
  pyEl.innerHTML = highlight(code || '# Перетащи блоки слева — здесь появится код');
}

// ------------------------------------------------------------------ подсказки
const toastEl = document.getElementById('toast');
let toastTimer;
function toast(text, isError = false) {
  toastEl.textContent = text;
  toastEl.classList.toggle('err', isError);
  toastEl.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { toastEl.hidden = true; }, isError ? 6000 : 3000);
}

// ------------------------------------------------------------------ кнопки
async function run() {
  let program;
  try {
    program = compileWorkspace(workspace);
  } catch (e) {
    if (e.block) { e.block.select(); workspace.centerOnBlock(e.block.id); }
    toast(e.message, true);
    return;
  }
  try {
    await query('save', { name: AUTOSAVE, data: saveState() });
    const res = await query('run', { program, speed: Number(document.getElementById('speed').value) });
    if (res === 'demo') toast('Демо-режим: в игре агент выполнит эту программу. Её JSON — в консоли браузера.');
  } catch (e) {
    toast(e.message, true);
  }
}

document.getElementById('run').onclick = run;
document.getElementById('stop').onclick = () => query('stop').then(() => toast('Агент остановлен')).catch((e) => toast(e.message, true));
document.getElementById('agentHere').onclick = () => query('agentHere').catch((e) => toast(e.message, true));
document.getElementById('close').onclick = () => query('close');
document.getElementById('example').onclick = () => {
  if (workspace.getAllBlocks(false).length > 1 && !confirmReplace()) return;
  loadXml(HOUSE_XML);
  toast('Пример «Домик»: нажми ▶ Запустить. Агент построит дом перед тобой.');
};

const pyBtn = document.getElementById('togglePy');
pyBtn.classList.add('on');
pyBtn.onclick = () => {
  document.body.classList.toggle('noPy');
  pyBtn.classList.toggle('on', !document.body.classList.contains('noPy'));
  Blockly.svgResize(workspace);
};

document.addEventListener('keydown', (e) => {
  if (e.key === 'F5') { e.preventDefault(); run(); }
});

// В встроенном браузере игры системные окна confirm() могут не показываться — свой вариант
let pendingReplace = false;
function confirmReplace() {
  if (pendingReplace) { pendingReplace = false; return true; }
  pendingReplace = true;
  toast('Текущие блоки заменятся примером. Нажми кнопку ещё раз, чтобы продолжить. (Сначала можно сохранить в «Проекты».)');
  setTimeout(() => { pendingReplace = false; }, 6000);
  return false;
}

// ------------------------------------------------------------------ проекты
const dlg = document.getElementById('projectsDialog');
const nameInput = document.getElementById('projectName');
const listEl = document.getElementById('projectList');

async function refreshProjects() {
  let names = [];
  try { names = JSON.parse((await query('list')) || '[]'); } catch (e) { toast(e.message, true); }
  listEl.innerHTML = '';
  if (!names.length) {
    listEl.innerHTML = '<li class="empty">Пока нет сохранённых проектов</li>';
    return;
  }
  for (const name of names) {
    const li = document.createElement('li');
    const label = document.createElement('span');
    label.textContent = name;
    const open = document.createElement('button');
    open.className = 'btn small-open'; open.textContent = 'Открыть';
    open.onclick = async () => {
      const data = await query('load', { name });
      if (data) { loadState(data); nameInput.value = name; dlg.hidden = true; toast('Открыт проект «' + name + '»'); }
    };
    const del = document.createElement('button');
    del.className = 'btn small-del'; del.textContent = 'Удалить';
    let armed = false;
    del.onclick = async () => {
      if (!armed) { armed = true; del.textContent = 'Точно?'; setTimeout(() => { armed = false; del.textContent = 'Удалить'; }, 3000); return; }
      await query('delete', { name });
      refreshProjects();
    };
    li.append(label, open, del);
    listEl.append(li);
  }
}

document.getElementById('projects').onclick = () => { dlg.hidden = false; refreshProjects(); nameInput.focus(); };
document.getElementById('closeProjects').onclick = () => { dlg.hidden = true; };
document.getElementById('saveProject').onclick = async () => {
  const name = nameInput.value.trim();
  if (!name) { toast('Напиши название проекта', true); return; }
  await query('save', { name, data: saveState() });
  toast('Сохранено: «' + name + '»');
  refreshProjects();
};
document.getElementById('newProject').onclick = () => {
  loadXml(STARTER_XML); nameInput.value = ''; dlg.hidden = true;
};

// ------------------------------------------------------------------ старт
let autosaveTimer;
workspace.addChangeListener((e) => {
  if (e.isUiEvent) return;
  updatePython();
  clearTimeout(autosaveTimer);
  autosaveTimer = setTimeout(() => query('save', { name: AUTOSAVE, data: saveState() }).catch(() => {}), 1500);
});

(async function start() {
  let restored = false;
  try {
    const saved = await query('load', { name: AUTOSAVE });
    if (saved) { loadState(saved); restored = true; }
  } catch (e) { /* первый запуск */ }
  if (!restored) loadXml(STARTER_XML);
  updatePython();
  if (!IN_GAME) toast('Демо-режим: редактор открыт в обычном браузере');
})();
