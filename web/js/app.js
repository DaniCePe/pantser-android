'use strict';
/* Pantser web PWA v1. 1:1 port of the Android app per web/SPEC.md.
   Default theme only (Linear Modern + Ember Night); other styles and
   palettes are reserved slots. No em dashes in this file. */

const APP_VERSION = '1.5.0';

/* ---------------- i18n ---------------- */
function langCode() {
  const l = Store.get('lang', 'system');
  if (l === 'es') return 'es';
  if (l === 'en') return 'en';
  return (navigator.language || 'en').toLowerCase().indexOf('es') === 0 ? 'es' : 'en';
}
function t(key) {
  const args = Array.prototype.slice.call(arguments, 1);
  const table = STRINGS[langCode()] || STRINGS.en;
  const s = (key in table) ? table[key] : (STRINGS.en[key] || key);
  return s.replace(/%(\d+)\$([sd])/g, function (m, n, c) {
    const v = args[Number(n) - 1];
    return v === undefined ? m : String(v);
  });
}

/* ---------------- persistent store (label_store) ---------------- */
const Store = {
  data: {},
  load: function () {
    try { this.data = JSON.parse(localStorage.getItem('label_store') || '{}'); }
    catch (e) { this.data = {}; }
  },
  get: function (k, d) { return (k in this.data) ? this.data[k] : d; },
  set: function (k, v) { this.data[k] = v; this.save(); },
  save: function () {
    try { localStorage.setItem('label_store', JSON.stringify(this.data)); } catch (e) {}
  }
};

/* ---------------- PDF bytes in IndexedDB ---------------- */
const IDB = {
  db: null,
  open: function () {
    const self = this;
    return new Promise(function (res, rej) {
      try {
        const r = indexedDB.open('pantser-db', 1);
        r.onupgradeneeded = function () { r.result.createObjectStore('pdfs'); };
        r.onsuccess = function () { self.db = r.result; res(); };
        r.onerror = function () { rej(r.error); };
      } catch (e) { rej(e); }
    });
  },
  tx: function (mode, fn) {
    const self = this;
    return new Promise(function (res, rej) {
      try {
        const t = self.db.transaction('pdfs', mode);
        const st = t.objectStore('pdfs');
        const rq = fn(st);
        rq.onsuccess = function () { res(rq.result); };
        rq.onerror = function () { rej(rq.error); };
      } catch (e) { rej(e); }
    });
  },
  put: function (id, bytes) { return this.tx('readwrite', function (st) { return st.put(bytes, 'pdf:' + id); }); },
  get: function (id) { return this.tx('readonly', function (st) { return st.get('pdf:' + id); }); },
  del: function (id) { return this.tx('readwrite', function (st) { return st.delete('pdf:' + id); }); }
};

/* ---------------- size helpers ---------------- */
/* Size identity is the RAW string (v1.3.0, web-only change): case and
   spacing count, so "32 x32", "32 x 32" and "32 X 32" are three different
   sizes with three different PDFs. Only the ends are trimmed, by
   createLabel, before a size gets here. */
function strictKey(s) {
  return String(s);
}
function javaHash(s) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (31 * h + s.charCodeAt(i)) | 0;
  return h;
}
function fileNameForSize(size) {
  /* Keep distinct raw sizes in distinct export file names: spaces and
     pipes map to visible separators, case is preserved, and anything
     else unsafe in a file name falls back to a hash suffix. */
  const raw = String(size);
  let clean = '';
  let lossy = false;
  for (let i = 0; i < raw.length; i++) {
    const ch = raw[i];
    if (/[A-Za-z0-9._-]/.test(ch)) clean += ch;
    else if (ch === ' ') clean += '_';
    else if (ch === '|') clean += '_-_';
    else { clean += '_'; lossy = true; }
  }
  if (clean.length > 50) return 'size_adv_' + javaHash(raw) + '.pdf';
  if (lossy) return 'size_' + clean + '_' + javaHash(raw) + '.pdf';
  return 'size_' + clean + '.pdf';
}
function layoutColsRows() {
  const parts = Store.get('layout', '4x15').split('x');
  return [parseInt(parts[0], 10) || 4, parseInt(parts[1], 10) || 15];
}

/* ---------------- PDF generation (mirror PdfGenerator) ---------------- */
/* Code 128 (set B) encoding. C128_PATTERNS[v] is the bar/space width
   pattern for symbol value v (each symbol is 11 modules wide, the stop
   symbol 13). Set B covers ASCII 32..126, so a size is encoded exactly
   as typed, spaces and lowercase included. Returns the run widths in
   modules (alternating bar, space, starting with a bar), or null when
   the text has characters set B cannot encode. */
const C128_PATTERNS = ['212222','222122','222221','121223','121322','131222','122213','122312','132212','221213','221312','231212','112232','122132','122231','113222','123122','123221','223211','221132','221231','213212','223112','312131','311222','321122','321221','312212','322112','322211','212123','212321','232121','111323','131123','131321','112313','132113','132311','211313','231113','231311','112133','112331','132131','113123','113321','133121','313121','211331','231131','213113','213311','213131','311123','311321','331121','312113','312311','332111','314111','221411','431111','111224','111422','121124','121421','141122','141221','112214','112412','122114','122411','142112','142211','241211','221114','413111','241112','134111','111242','121142','121241','114212','124112','124211','411212','421112','421211','212141','214121','412121','111143','111341','131141','114113','114311','411113','411311','113141','114131','311141','411131','211412','211214','211232','2331112'];
function code128Runs(text) {
  const vals = [104];
  for (let i = 0; i < text.length; i++) {
    const code = text.charCodeAt(i);
    if (code < 32 || code > 126) return null;
    vals.push(code - 32);
  }
  let sum = 104;
  for (let i = 1; i < vals.length; i++) sum += i * vals[i];
  vals.push(sum % 103, 106);
  const runs = [];
  vals.forEach(function (v) {
    const pat = C128_PATTERNS[v];
    for (let i = 0; i < pat.length; i++) runs.push(parseInt(pat[i], 10));
  });
  return runs;
}

async function generatePdf(sizeTexts, barcode) {
  const cr = layoutColsRows();
  const cols = cr[0], rows = cr[1];
  const { PDFDocument, StandardFonts, rgb } = PDFLib;
  const doc = await PDFDocument.create();
  const page = doc.addPage([612, 792]);
  const font = await doc.embedFont(StandardFonts.HelveticaBold);
  const fontReg = barcode ? await doc.embedFont(StandardFonts.Helvetica) : null;
  const FS = 24;
  const L = 21.6, T = 39.6, R = 21.6, B = 33.84;
  const uw = 612 - L - R, uh = 792 - T - B;
  const cw = uw / cols, ch = uh / rows;
  /* Helvetica Bold metrics: ascent 718, descent -207 per 1000 upm.
     Baseline offset -(ascent + descent) / 2, same formula as Android. */
  const baseOff = -((718 - 207) / 1000 * FS) / 2;
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      const txt = (c < sizeTexts.length) ? sizeTexts[c] : (sizeTexts[0] || '');
      if (!txt) continue;
      const cx = L + c * cw + cw / 2;
      const cyTop = T + r * ch + ch / 2;
      const runs = barcode ? code128Runs(txt) : null;
      if (runs) {
        /* Barcode cell: bars sized to the cell, the size itself printed
           underneath in plain text (human-readable), the way printed
           barcode labels normally look. A 10-module quiet zone is kept
           on both sides so scanners can lock on. */
        let modules = 0;
        runs.forEach(function (w) { modules += w; });
        let capSize = 11;
        while (capSize > 6 && fontReg.widthOfTextAtSize(txt, capSize) > cw * 0.94) capSize -= 0.5;
        const gap = 3;
        /* Taller and narrower than the text-label footprint, closer to
           a grocery-store barcode: bars take most of the cell height,
           and the total width targets about 3.2x the bar height instead
           of stretching to fill the cell. The module width keeps a
           floor so long sizes stay printable and scannable. */
        const barH = Math.min(ch * 0.66, 36, ch - gap - capSize - 1);
        const moduleW = Math.min((cw * 0.94) / (modules + 20), 2.6,
          Math.max(0.6, (barH * 3.2) / (modules + 20)));
        const codeW = moduleW * (modules + 20);
        const blockH = barH + gap + capSize;
        const topEdge = T + r * ch + (ch - blockH) / 2;
        let bx = L + c * cw + (cw - codeW) / 2 + 10 * moduleW;
        const barY = 792 - (topEdge + barH);
        runs.forEach(function (w, i) {
          if (i % 2 === 0) {
            page.drawRectangle({ x: bx, y: barY, width: w * moduleW, height: barH, color: rgb(0, 0, 0) });
          }
          bx += w * moduleW;
        });
        const capW = fontReg.widthOfTextAtSize(txt, capSize);
        page.drawText(txt, {
          x: cx - capW / 2,
          y: 792 - (topEdge + barH + gap) - capSize * 0.75,
          size: capSize, font: fontReg, color: rgb(0, 0, 0)
        });
        continue;
      }
      const w = font.widthOfTextAtSize(txt, FS);
      page.drawText(txt, {
        x: cx - w / 2,
        y: 792 - cyTop + baseOff,
        size: FS, font: font, color: rgb(0, 0, 0)
      });
    }
  }
  const bytes = await doc.save();
  return bytes;
}

/* ---------------- createLabel (mirror MainActivity.createLabel) ---------------- */
async function createLabel(rawSize, kindOverride) {
  const size = (rawSize || '').trim();
  if (!size) { toast(t('err_empty_size')); return; }
  try {
    const n = strictKey(size);
    /* A size can exist as a text label and as a barcode label; they are
       different PDFs, so the record identity includes the kind. The
       kind comes from the Utilities barcode switch, except tray
       reprints pass the record's own kind explicitly. */
    const kind = kindOverride || (Store.get('barcode', 'false') === 'true' ? 'barcode' : 'text');
    const records = Store.get('records', []);
    const existing = records.find(function (r) { return strictKey(r.size) === n && (r.kind || 'text') === kind; });
    if (existing) {
      const bytes = await IDB.get(existing.id);
      if (bytes && bytes.length > 0) { await printRecord(existing, bytes); goHome(); return; }
    }
    const id = Date.now();
    const sizes = size.split('|').map(function (s) { return s.trim(); });
    const bytes = await generatePdf(sizes, kind === 'barcode');
    const rec = {
      id: id, size: size, file: fileNameForSize(size), kind: kind,
      created: id, prints: 0, lastPrinted: 0, bytesLen: bytes.length
    };
    await IDB.put(id, bytes);
    records.unshift(rec);
    Store.set('records', records);
    const uq = Store.get('unique_sizes', []);
    if (uq.indexOf(n) < 0) { uq.push(n); Store.set('unique_sizes', uq); }
    await printRecord(rec, bytes);
  } catch (e) {
    toast(t('err_create_fail', (e && e.message) || e));
  }
  goHome();
}

async function printRecord(rec, bytes) {
  rec.prints = (rec.prints || 0) + 1;
  rec.lastPrinted = Date.now();
  Store.set('records', Store.get('records', []));
  Store.set('total_printed', Store.get('total_printed', 0) + 1);
  renderAll();
  /* Web mapping of the Android print dialog: open the PDF in a hidden
     frame and invoke the browser print dialog. */
  const blob = new Blob([bytes], { type: 'application/pdf' });
  const url = URL.createObjectURL(blob);
  try {
    const frame = document.getElementById('print-frame');
    await new Promise(function (res, rej) {
      const to = setTimeout(function () { rej(new Error('print-timeout')); }, 15000);
      frame.onload = function () { clearTimeout(to); res(); };
      frame.src = url;
    });
    frame.contentWindow.focus();
    frame.contentWindow.print();
  } catch (e) {
    window.open(url, '_blank');
  }
  setTimeout(function () { URL.revokeObjectURL(url); }, 60000);
}

/* ---------------- startup: dedupe + cleanup (mirror AppStore) ---------------- */
async function consolidate(records) {
  const map = new Map();
  for (const r of records) {
    const n = strictKey(r.size) + '|' + (r.kind || 'text');
    if (!map.has(n)) { map.set(n, r); continue; }
    const k = map.get(n);
    const winner = ((r.prints || 0) > (k.prints || 0)) ? r : k;
    const loser = (winner === r) ? k : r;
    winner.prints = (winner.prints || 0) + (loser.prints || 0);
    winner.lastPrinted = Math.max(winner.lastPrinted || 0, loser.lastPrinted || 0);
    winner.file = fileNameForSize(winner.size);
    try { await IDB.del(loser.id); } catch (e) {}
    map.set(n, winner);
  }
  return Array.from(map.values());
}

async function cleanupOld(days) {
  const cutoff = Date.now() - days * 86400000;
  const records = Store.get('records', []);
  const kept = [];
  let removed = 0;
  for (const r of records) {
    const ts = r.lastPrinted || r.created;
    if (ts < cutoff) {
      try { await IDB.del(r.id); } catch (e) {}
      removed++;
    } else kept.push(r);
  }
  Store.set('records', kept);
  return removed;
}

async function startup() {
  Store.load();
  try { await IDB.open(); } catch (e) { /* storage unavailable: app still runs */ }
  let records = await consolidate(Store.get('records', []));
  Store.set('records', records);
  if (Store.get('automatic_cleanup', 'true') === 'true') await cleanupOld(7);
}

/* ---------------- toast + dialog ---------------- */
let toastTimer = null;
function toast(msg) {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(function () { el.classList.add('hidden'); }, 2600);
}

function closeDialog() {
  document.getElementById('dlg-backdrop').classList.add('hidden');
}
function showDialog(opts) {
  /* opts: {title, msg, options:[{label, sub, checked, later, onPick}], buttons:[{label, primary, onClick}]} */
  const bd = document.getElementById('dlg-backdrop');
  document.getElementById('dlg-title').textContent = opts.title || '';
  const msgEl = document.getElementById('dlg-msg');
  msgEl.textContent = opts.msg || '';
  msgEl.style.display = opts.msg ? '' : 'none';
  const list = document.getElementById('dlg-list');
  list.innerHTML = '';
  (opts.options || []).forEach(function (o) {
    const b = document.createElement('button');
    b.className = 'opt' + (o.later ? ' later' : '');
    const left = document.createElement('span');
    left.textContent = o.label;
    if (o.sub) {
      const s = document.createElement('span');
      s.className = 'sub2'; s.textContent = o.sub; left.appendChild(s);
    }
    b.appendChild(left);
    const right = document.createElement('span');
    if (o.later) { right.className = 'tag'; right.textContent = ''; }
    else if (o.checked) { right.className = 'chk'; right.textContent = '\u2713'; }
    b.appendChild(right);
    if (!o.later) b.addEventListener('click', function () { closeDialog(); o.onPick(); });
    list.appendChild(b);
  });
  const btns = document.getElementById('dlg-btns');
  btns.innerHTML = '';
  (opts.buttons || [{ label: t('btn_cancel') }]).forEach(function (bb) {
    const b = document.createElement('button');
    b.textContent = bb.label;
    if (bb.primary) b.className = 'go';
    b.addEventListener('click', function () { closeDialog(); if (bb.onClick) bb.onClick(); });
    btns.appendChild(b);
  });
  bd.classList.remove('hidden');
}
document.addEventListener('DOMContentLoaded', function () {
  document.getElementById('dlg-backdrop').addEventListener('click', function (e) {
    if (e.target.id === 'dlg-backdrop') closeDialog();
  });
});

/* ---------------- PWA install ---------------- */
let deferredPrompt = null;
window.addEventListener('beforeinstallprompt', function (e) {
  e.preventDefault();
  deferredPrompt = e;
  if (typeof renderAll === 'function') renderAll();
});
window.addEventListener('appinstalled', function () {
  deferredPrompt = null;
  toast(t('install_done'));
  if (typeof renderAll === 'function') renderAll();
});
function isIos() {
  return /iphone|ipad|ipod/i.test(navigator.userAgent || '');
}
function isStandalone() {
  return (window.matchMedia && window.matchMedia('(display-mode: standalone)').matches) ||
    window.navigator.standalone === true;
}
function installActionable() {
  /* Always offer it in the browser. The installed app opens straight
     into the app, where the row hides itself. */
  return !isStandalone();
}
async function doInstall() {
  if (deferredPrompt) {
    deferredPrompt.prompt();
    try {
      const choice = await deferredPrompt.userChoice;
      if (choice && choice.outcome === 'accepted') { deferredPrompt = null; renderAll(); }
    } catch (e) {}
  } else if (isIos()) {
    showDialog({ title: t('install_app_title'), msg: t('install_ios_msg') });
  } else {
    showDialog({ title: t('install_app_title'), msg: t('install_manual_msg') });
  }
}

/* ---------------- theme ---------------- */
function applyTheme() {
  const m = Store.get('mode', 'system');
  let dark;
  if (m === 'dark') dark = true;
  else if (m === 'light') dark = false;
  else dark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light');
  const mc = document.querySelector('meta[name="theme-color"]');
  if (mc) mc.setAttribute('content', dark ? '#060404' : '#FCF5F2');
}

/* ---------------- navigation ---------------- */
let currentView = 'home';
function goHome() { showView('home'); }

function showView(v, push) {
  currentView = v;
  const pager = document.getElementById('pager');
  const vs = document.getElementById('view-settings');
  const vi = document.getElementById('view-info');
  vs.classList.add('hidden'); vi.classList.add('hidden');
  document.getElementById('dock').style.display = '';
  if (v === 'insights') pager.style.transform = 'translateX(-100%)';
  else pager.style.transform = 'translateX(0)';
  if (v === 'settings') { vs.classList.remove('hidden'); document.getElementById('dock').style.display = 'none'; }
  if (v === 'info') { vi.classList.remove('hidden'); document.getElementById('dock').style.display = 'none'; }
  document.getElementById('tab-create').classList.toggle('on', v === 'home');
  document.getElementById('tab-insights').classList.toggle('on', v === 'insights');
  /* No history pushes here (v1.3.2): view changes are internal state.
     The old per-navigation pushes made the history stack grow and drain
     unevenly (some paths re-pushed, some did not), so a back press could
     occasionally run past the app's entries and close it. Browser
     history is now only the single re-arming trap in onPopState. The
     push argument is kept for call-site compatibility and ignored. */
}

let exitArmedAt = 0;
let exitingApp = false;
function tryClose() {
  exitingApp = true;
  window.close();
  /* If the browser refuses (tab not opened by script), nothing else
     happens. Recorded in NOT-PORTED.md. */
}
/* The back trap (v1.3.2): a sacrificial history entry sits above the
   base entry so a system back press pops the trap and popstate fires,
   instead of the browser leaving the app. Two kinds of arm:
   - provisional: pushed at startup and after each back press. Android
     Chrome's history manipulation intervention can SKIP entries that
     were pushed without a user gesture, so a provisional trap alone is
     not reliable (field report: back closed the app from any menu, the
     Ask dialog never appeared; it worked better before 1.3, when most
     entries happened to be pushed from taps).
   - gesture-armed: pushed from a real pointerdown or keydown, which
     Chrome cannot skip. trapNeedsArm tracks this: after any back press
     the trap counts as spent until the user's next tap or keypress
     arms a fresh non-skippable one. That next gesture always comes
     before the next deliberate action (even a swipe starts with a
     pointerdown), so in practice the top entry is a gesture trap
     whenever back matters.
   Only an intentional exit (tryClose) leaves the trap disarmed. */
let trapNeedsArm = true;
function pushTrap() {
  try { history.pushState({ pantser: 'trap' }, ''); } catch (e) {}
}
function armTrap() {
  pushTrap();
  trapNeedsArm = true;
}
function ensureTrapFromGesture() {
  if (!trapNeedsArm) return;
  pushTrap();
  trapNeedsArm = false;
}
function onPopState() {
  exitingApp = false;
  onBackPressed();
  if (!exitingApp) armTrap();
}
function onBackPressed() {
  /* Belt and braces (v1.3.1): if Insights is what is actually on screen,
     back goes home even if currentView somehow disagrees. */
  const pagerEl = document.getElementById('pager');
  const insightsOnScreen = pagerEl && pagerEl.style.transform.indexOf('-100%') >= 0;
  if (currentView === 'settings' || currentView === 'info' || currentView === 'insights' || (currentView === 'home' && insightsOnScreen)) { showView('home'); return; }
  const b = Store.get('back_behavior', 'ask');
  if (b === 'ask') {
    showDialog({
      title: t('dialog_exit_title'), msg: t('dialog_exit_msg'),
      buttons: [{ label: t('btn_cancel') }, { label: t('btn_exit'), primary: true, onClick: tryClose }]
    });
  } else if (b === 'one_press') { tryClose(); }
  else if (b === 'two_press') {
    const now = Date.now();
    if (now - exitArmedAt < 2000) { exitArmedAt = 0; tryClose(); }
    else { exitArmedAt = now; toast(t('toast_press_again_exit')); }
  }
  /* 'disable' and the non-exit paths need no history work here:
     onPopState re-arms the trap after every back press. */
}

/* small swipe pager with dots */
function makePager(pagerEl, dotsEl) {
  const pages = pagerEl.children.length;
  let idx = 0, sx = 0;
  function draw() {
    pagerEl.style.transform = 'translateX(-' + (idx * 100) + '%)';
    if (dotsEl) {
      dotsEl.innerHTML = '';
      for (let i = 0; i < pages; i++) {
        const d = document.createElement('span');
        if (i === idx) d.className = 'on';
        dotsEl.appendChild(d);
      }
    }
  }
  pagerEl.addEventListener('touchstart', function (e) { sx = e.touches[0].clientX; }, { passive: true });
  pagerEl.addEventListener('touchend', function (e) {
    const dx = e.changedTouches[0].clientX - sx;
    if (dx < -60 && idx < pages - 1) idx++;
    else if (dx > 60 && idx > 0) idx--;
    draw();
  }, { passive: true });
  draw();
  return { go: function (i) { idx = Math.max(0, Math.min(pages - 1, i)); draw(); } };
}

/* ---------------- static strings ---------------- */
function applyStrings() {
  document.getElementById('topbar-title').textContent = t('app_name');
  document.getElementById('tab-create').textContent = t('generate_pdf');
  document.getElementById('tab-insights').textContent = t('insights_title');
  document.title = t('app_name');
  document.documentElement.lang = langCode();
}

/* ---------------- home ---------------- */
/* Session drafts (v1.3.0): what is typed on the home screen is kept in
   sessionStorage, so it survives switching to Settings, Insights or Info
   (and the renders those cause) and even a page reload, but a fresh app
   launch starts blank. Pressing Create Label clears the active draft. */
let advMem = [];
let classicMem = '';
let tbMem = ['', ''];
try {
  classicMem = sessionStorage.getItem('draft_classic') || '';
  advMem = JSON.parse(sessionStorage.getItem('draft_adv') || '[]');
  tbMem = JSON.parse(sessionStorage.getItem('draft_tb') || '["",""]');
  if (!Array.isArray(advMem)) advMem = [];
  if (!Array.isArray(tbMem) || tbMem.length !== 2) tbMem = ['', ''];
} catch (e) {}
function saveDrafts() {
  try {
    sessionStorage.setItem('draft_classic', classicMem);
    sessionStorage.setItem('draft_adv', JSON.stringify(advMem));
    sessionStorage.setItem('draft_tb', JSON.stringify(tbMem));
  } catch (e) {}
}
function seedTbFromClassic() {
  if (tbMem[0] || tbMem[1]) return;
  const m = /^(\d{1,2})\s*x\s*(\d{1,2})$/i.exec((classicMem || '').trim());
  if (m) { tbMem = [m[1], m[2]]; }
}
function syncClassicFromTb() {
  classicMem = (tbMem[0] && tbMem[1]) ? tbMem[0] + ' x ' + tbMem[1] : (tbMem[0] || tbMem[1] || '');
}

function renderHome() {
  document.getElementById('home-prompt').textContent = t('prompt_what_size');
  document.getElementById('home-layout').textContent = t('using_layout', Store.get('layout', '4x15'));
  document.getElementById('home-privacy').textContent = t('privacy_note');
  const wrap = document.getElementById('home-inputs');
  wrap.innerHTML = '';
  const advanced = Store.get('advanced_mode', 'false') === 'true';
  const cols = layoutColsRows()[0];
  const jumpBtn = document.getElementById('btn-jump');

  const twoBox = !advanced && Store.get('two_box', 'false') === 'true';
  if (!advanced && !twoBox) {
    wrap.classList.remove('row');
    const inp = document.createElement('input');
    inp.type = 'text'; inp.id = 'classic-input';
    inp.placeholder = t('hint_example'); inp.value = classicMem;
    inp.setAttribute('aria-label', t('prompt_what_size'));
    inp.addEventListener('input', function () { classicMem = inp.value; saveDrafts(); });
    inp.addEventListener('keydown', function (e) { if (e.key === 'Enter') doCreateClassic(); });
    wrap.appendChild(inp);
    jumpBtn.classList.add('hidden');
  } else if (twoBox) {
    wrap.classList.remove('row');
    seedTbFromClassic();
    const row = document.createElement('div');
    row.className = 'two-box';
    const boxes = [];
    for (let bi = 0; bi < 2; bi++) {
      const binp = document.createElement('input');
      binp.type = 'text'; binp.id = 'tb-input-' + bi;
      binp.inputMode = 'numeric'; binp.setAttribute('pattern', '[0-9]*');
      binp.setAttribute('autocomplete', 'off');
      /* No maxlength on purpose (v1.3.1): a digit typed into a full box
         must reach the input handler so it can be forwarded to the next
         box, instead of being silently swallowed by the character cap. */
      binp.value = tbMem[bi] || '';
      binp.placeholder = bi === 0 ? '32' : '30';
      binp.setAttribute('aria-label', t('hint_example'));
      boxes.push(binp);
    }
    boxes.forEach(function (el, index) {
      el.addEventListener('input', function () {
        let digits = el.value.replace(/\D/g, '');
        if (index === 0 && digits.length > 2) {
          /* First box is full (for example after backspace jumped back to
             it): forward the extra digit into the second box and move
             focus there, like typing had just continued across (v1.3.1). */
          const extra = digits.slice(2);
          digits = digits.slice(0, 2);
          el.value = digits; tbMem[0] = digits;
          const next = boxes[1];
          next.value = (next.value + extra).slice(0, 2);
          tbMem[1] = next.value;
          syncClassicFromTb(); saveDrafts();
          next.focus();
          try { next.setSelectionRange(next.value.length, next.value.length); } catch (err) {}
          return;
        }
        el.value = digits.slice(0, 2);
        tbMem[index] = el.value;
        syncClassicFromTb(); saveDrafts();
        if (index === 0 && el.value.length === 2) {
          boxes[1].focus();
          try { boxes[1].setSelectionRange(boxes[1].value.length, boxes[1].value.length); } catch (e) {}
        }
      });
      el.addEventListener('click', function () {
        /* Tapping a box clears just that box. A programmatic focus from
           the auto-advance does not fire click, so it never clears. */
        if (el.value) { el.value = ''; tbMem[index] = ''; syncClassicFromTb(); saveDrafts(); }
      });
      el.addEventListener('keydown', function (e) {
        if (e.key === 'Backspace' && el.value === '' && index > 0) {
          e.preventDefault();
          const prev = boxes[index - 1];
          prev.focus();
          try { prev.setSelectionRange(prev.value.length, prev.value.length); } catch (err) {}
        }
        if (e.key === 'Enter') doCreateTwoBox();
      });
    });
    row.appendChild(boxes[0]);
    const xSep = document.createElement('span');
    xSep.className = 'two-box-x'; xSep.textContent = 'x';
    row.appendChild(xSep);
    row.appendChild(boxes[1]);
    wrap.appendChild(row);
    jumpBtn.classList.add('hidden');
  } else {
    wrap.classList.add('row');
    while (advMem.length < cols) advMem.push('');
    advMem.length = cols;
    const inputs = [];
    for (let i = 0; i < cols; i++) {
      const inp = document.createElement('input');
      inp.type = 'text'; inp.value = advMem[i] || '';
      inp.placeholder = t('label_column_x', i + 1);
      inp.setAttribute('aria-label', t('label_column_x', i + 1));
      (function (index, el) {
        el.addEventListener('input', function () { advMem[index] = el.value; saveDrafts(); refreshAsterisks(); });
        el.addEventListener('keydown', function (e) {
          if (e.key === 'Backspace' && el.value === '' && index > 0) {
            e.preventDefault();
            const prev = inputs[index - 1];
            prev.focus();
            prev.setSelectionRange(prev.value.length, prev.value.length);
          }
          if (e.key === 'Enter') doCreateAdvanced();
        });
      })(i, inp);
      inputs.push(inp);
      wrap.appendChild(inp);
    }
    const firstSkipped = function () {
      for (let i = 0; i < cols; i++) {
        if (advMem[i].trim() !== '') continue;
        for (let j = i + 1; j < cols; j++) if (advMem[j].trim() !== '') return i;
      }
      return -1;
    };
    const refreshAsterisks = function () {
      let skipped = false;
      for (let i = 0; i < cols; i++) {
        const blank = advMem[i].trim() === '';
        let laterFilled = false;
        for (let j = i + 1; j < cols; j++) if (advMem[j].trim() !== '') laterFilled = true;
        inputs[i].placeholder = t('label_column_x', i + 1) + ((blank && laterFilled) ? ' *' : '');
        if (blank && laterFilled) skipped = true;
      }
      jumpBtn.classList.toggle('hidden', !skipped);
    };
    window.__refreshAsterisks = refreshAsterisks;
    jumpBtn.textContent = t('jump_to_previous_unsaved');
    jumpBtn.onclick = function () {
      const i = firstSkipped();
      if (i >= 0) inputs[i].focus();
    };
    refreshAsterisks();
  }

  const cb = document.getElementById('btn-create');
  cb.textContent = t('create_label_btn');
  cb.setAttribute('aria-label', t('generate_pdf'));
  cb.onclick = function () { advanced ? doCreateAdvanced() : (twoBox ? doCreateTwoBox() : doCreateClassic()); };
  renderTray();
}

function doCreateClassic() {
  const el = document.getElementById('classic-input');
  const v = el ? el.value : classicMem;
  /* Pressing Create clears the draft right away (v1.3.0): the app cannot
     know whether anything truly printed, and the size lands in the quick
     print tray / history, so it is one tap away if it is needed again. */
  classicMem = ''; tbMem = ['', '']; saveDrafts();
  if (el) el.value = '';
  createLabel(v);
}
function doCreateTwoBox() {
  const a = tbMem[0] || '', b = tbMem[1] || '';
  tbMem = ['', '']; classicMem = ''; saveDrafts();
  document.querySelectorAll('.two-box input').forEach(function (el) { el.value = ''; });
  if (!a || !b) { toast(t('err_empty_size')); return; }
  createLabel(a + ' x ' + b);
}
function doCreateAdvanced() {
  const cols = layoutColsRows()[0];
  const vals = [];
  for (let i = 0; i < cols; i++) vals.push((advMem[i] || '').trim());
  advMem = advMem.map(function () { return ''; });
  saveDrafts();
  document.querySelectorAll('#home-inputs input').forEach(function (el) { el.value = ''; });
  if (vals.some(function (v) { return v === ''; })) { toast(t('err_invalid_column')); return; }
  createLabel(vals.join('|'));
}

/* ---------------- quick-print tray ---------------- */
function renderTray() {
  document.getElementById('tray-title').textContent = t('quick_print_title');
  const list = document.getElementById('tray-list');
  list.innerHTML = '';
  const seen = new Set();
  let recs = Store.get('records', []).filter(function (r) {
    if (!(r.prints > 0)) return false;
    const n = strictKey(r.size) + '|' + (r.kind || 'text');
    if (seen.has(n)) return false;
    seen.add(n);
    return true;
  });
  const sort = Store.get('quick_print_sort', 'last');
  recs.sort(function (a, b) {
    return sort === 'freq' ? ((b.prints || 0) - (a.prints || 0)) : ((b.lastPrinted || 0) - (a.lastPrinted || 0));
  });
  recs = recs.slice(0, 8);
  if (!recs.length) {
    const e = document.createElement('div');
    e.className = 'empty'; e.textContent = t('no_recent_labels');
    list.appendChild(e);
    return;
  }
  recs.forEach(function (r) {
    const row = document.createElement('div');
    row.className = 'tray-row';
    const name = document.createElement('span');
    name.textContent = r.size.split('|').join(' | ') +
      (r.kind === 'barcode' ? '  ·  ' + t('label_barcode_tag') : '');
    const n = document.createElement('span');
    n.className = 'n'; n.textContent = t('print_count_text', r.prints || 0);
    row.appendChild(name); row.appendChild(n);
    row.addEventListener('click', function () { createLabel(r.size, r.kind || 'text'); });
    list.appendChild(row);
  });
}

/* ---------------- insights ---------------- */
function fmtBytes(b) {
  if (b < 1024) return b + ' B';
  if (b < 1048576) return Math.floor(b / 1024) + ' KB';
  return Math.floor(b / 1048576) + ' MB';
}
function renderInsights() {
  document.getElementById('ins-title').textContent = t('insights_title');
  document.getElementById('ins-sub').textContent = t('insights_sub');
  const cards = document.getElementById('ins-cards');
  cards.innerHTML = '';
  const records = Store.get('records', []);
  const stats = [
    [t('stat_total_printed'), t('stat_total_printed_desc'), Store.get('total_printed', 0)],
    [t('stat_unique_pdfs'), t('stat_unique_pdfs_desc'), Store.get('unique_sizes', []).length],
    [t('stat_saved_pdfs'), t('stat_saved_pdfs_desc'), records.length]
  ];
  stats.forEach(function (s) {
    const c = document.createElement('div');
    c.className = 'card';
    const k = document.createElement('div'); k.className = 'k'; k.textContent = s[0];
    const d = document.createElement('div'); d.className = 'd'; d.textContent = s[1];
    const v = document.createElement('div'); v.className = 'v'; v.textContent = s[2];
    c.appendChild(k); c.appendChild(d); c.appendChild(v);
    cards.appendChild(c);
  });
  document.getElementById('ins-most').textContent = t('most_printed_title');
  const list = document.getElementById('ins-list');
  list.innerHTML = '';
  const groups = {};
  records.forEach(function (r) {
    groups[r.size] = (groups[r.size] || 0) + (r.prints || 0);
  });
  const top = Object.keys(groups).sort(function (a, b) { return groups[b] - groups[a]; }).slice(0, 5);
  if (!top.length) {
    const e = document.createElement('div');
    e.className = 'empty'; e.textContent = t('no_history');
    list.appendChild(e);
  } else {
    top.forEach(function (size, i) {
      const row = document.createElement('div');
      row.className = 'hist-row';
      row.textContent = t('history_item_format', i + 1, size.split('|').join(' | '), groups[size]);
      list.appendChild(row);
    });
  }
  const total = records.reduce(function (a, r) { return a + (r.bytesLen || 0); }, 0);
  document.getElementById('ins-storage').textContent = t('storage_used', fmtBytes(total));
}

/* ---------------- updates ----------------
   The service worker already installs new versions by itself
   (skipWaiting + clients.claim), but the browser only looks for them
   on its own lazy schedule. This button forces the check now; once a
   new worker has installed, the button becomes Restart to update and
   simply reloads, which serves the fresh shell from the new cache. */
let updState = 'idle'; /* idle | checking | ready */
let updLatest = null;

function renderUpdate() {
  const btn = document.getElementById('btn-update');
  const st = document.getElementById('update-status');
  if (!btn) return;
  if (updState === 'checking') {
    btn.disabled = true;
    btn.textContent = t('upd_checking');
  } else if (updState === 'ready') {
    btn.disabled = false;
    btn.textContent = t('upd_restart');
    if (st) st.textContent = t('upd_available') + (updLatest ? ' (v' + updLatest + ')' : '');
  } else {
    btn.disabled = false;
    btn.textContent = t('upd_check');
  }
}

async function checkForUpdates() {
  const st = document.getElementById('update-status');
  if (!('serviceWorker' in navigator)) {
    if (st) st.textContent = t('upd_unsupported');
    return;
  }
  updState = 'checking';
  renderUpdate();
  if (st) st.textContent = '';
  let netFailed = false;
  try {
    const reg = await navigator.serviceWorker.getRegistration();
    if (!reg) throw new Error('no registration');
    /* Read the live version straight from the served app.js, bypassing
       every cache, so the answer is about the server, not about what
       happens to be stored on this device. */
    let latest = null;
    try {
      const res = await fetch('js/app.js?u=' + Date.now(), { cache: 'no-store' });
      if (res && res.ok) {
        const txt = await res.text();
        const m = txt.match(/APP_VERSION = '([^']+)'/);
        if (m) latest = m[1];
      } else {
        netFailed = true;
      }
    } catch (e) {
      netFailed = true;
    }
    await reg.update();
    /* If a new worker appeared, wait until it has installed (it then
       activates itself) so the restart lands on the new version. */
    const w = reg.installing || reg.waiting;
    if (w && w.state !== 'installed' && w.state !== 'activated') {
      await new Promise(function (resolve) {
        const to = setTimeout(resolve, 8000);
        const done = function () { clearTimeout(to); resolve(); };
        w.addEventListener('statechange', function () {
          if (w.state === 'installed' || w.state === 'activated') done();
        });
        if (w.state === 'installed' || w.state === 'activated') done();
      });
    }
    if ((latest && latest !== APP_VERSION) || w) {
      updLatest = latest;
      updState = 'ready';
      renderUpdate();
    } else if (netFailed) {
      updState = 'idle';
      renderUpdate();
      if (st) st.textContent = t('upd_failed');
    } else {
      updState = 'idle';
      renderUpdate();
      if (st) st.textContent = t('upd_latest') + ' (v' + APP_VERSION + ')';
    }
  } catch (e) {
    updState = 'idle';
    renderUpdate();
    if (st) st.textContent = t('upd_failed');
  }
}

function onUpdateButton() {
  if (updState === 'ready') {
    try { location.reload(); } catch (e) {}
    return;
  }
  if (updState !== 'checking') checkForUpdates();
}

/* ---------------- info ---------------- */
function renderInfo() {
  const set = function (id, key) { document.getElementById(id).textContent = t(key); };
  set('info-t1', 'manual_title'); set('info-b1a', 'manual_welcome');
  set('info-t2', 'manual_concept_title'); set('info-b2', 'manual_concept_body');
  set('info-t3', 'manual_steps_title'); set('info-b3', 'manual_steps_body');
  set('info-t4', 'manual_settings_title'); set('info-b4', 'manual_settings_body');
  renderUpdate();
}

/* ---------------- settings ---------------- */
function setRow(title, sub, valueText, onClick) {
  const b = document.createElement('button');
  b.className = 'set-row';
  const left = document.createElement('span');
  const tt = document.createElement('span'); tt.className = 't'; tt.textContent = title;
  left.appendChild(tt);
  if (sub) { const s = document.createElement('span'); s.className = 's'; s.textContent = sub; left.appendChild(s); }
  b.appendChild(left);
  if (valueText !== undefined && valueText !== null) {
    const v = document.createElement('span'); v.className = 'v'; v.textContent = valueText;
    b.appendChild(v);
  }
  if (onClick) b.addEventListener('click', onClick);
  return b;
}
function switchRow(title, sub, checked, onToggle) {
  const b = document.createElement('div');
  b.className = 'set-row'; b.style.cursor = 'default';
  const left = document.createElement('span');
  const tt = document.createElement('span'); tt.className = 't'; tt.textContent = title;
  left.appendChild(tt);
  const s = document.createElement('span'); s.className = 's'; s.textContent = sub; left.appendChild(s);
  b.appendChild(left);
  const sw = document.createElement('label'); sw.className = 'switch';
  const inp = document.createElement('input'); inp.type = 'checkbox'; inp.checked = checked;
  const tr = document.createElement('span'); tr.className = 'tr';
  sw.appendChild(inp); sw.appendChild(tr);
  inp.addEventListener('change', function () { onToggle(inp.checked); });
  b.appendChild(sw);
  return b;
}
function pickDialog(title, items, current, onPick) {
  showDialog({
    title: title,
    options: items.map(function (it) {
      return {
        label: it.label, sub: it.sub, checked: it.key === current,
        later: !!it.later,
        onPick: function () { onPick(it.key); }
      };
    })
  });
}

const PALETTES = [
  { key: 'EMBER_NIGHT', name: 'Crimson Dusk' },
  { key: 'ROYAL_GRAPE', name: 'Midnight Purple', later: true },
  { key: 'SPRING_PASTEL', name: 'Garden Meadow', later: true },
  { key: 'LAVENDER_CREAM', name: 'Petal Soft', later: true },
  { key: 'BAUHAUS', name: 'Bold Primary', later: true },
  { key: 'TERMINAL', name: 'Hacker Green', later: true },
  { key: 'DOPAMINE', name: 'Neon Pop', later: true }
];
const STYLES = [
  { key: 'modern', tkey: 'style_modern' },
  { key: 'retro', tkey: 'style_retro', later: true },
  { key: 'bauhaus', tkey: 'style_bauhaus', later: true },
  { key: 'terminal', tkey: 'style_terminal', later: true },
  { key: 'dopamine', tkey: 'style_dopamine', later: true }
];
const LAYOUTS = ['4x15', '3x10', '4x20', '2x10'];

function renderSettings() {
  /* utilities */
  document.getElementById('set-util-title').textContent = t('section_utilities');
  const u = document.getElementById('set-util-list');
  u.innerHTML = '';
  if (installActionable()) {
    u.appendChild(setRow(t('install_app_title'), t('install_app_sub'), null, doInstall));
  }
  const sortVal = Store.get('quick_print_sort', 'last') === 'freq' ? t('sort_freq_used') : t('sort_last_used');
  u.appendChild(setRow(t('setting_sort_format'), null, sortVal, function () {
    pickDialog(t('setting_sort_format'), [
      { key: 'last', label: t('sort_last_used') },
      { key: 'freq', label: t('sort_freq_used') }
    ], Store.get('quick_print_sort', 'last'), function (k) { Store.set('quick_print_sort', k); renderAll(); });
  }));
  const bb = Store.get('back_behavior', 'ask');
  const bbLabel = { ask: t('back_behavior_ask'), one_press: t('back_behavior_one_press'), two_press: t('back_behavior_two_press'), disable: t('back_behavior_disable') }[bb];
  u.appendChild(setRow(t('setting_back_behavior'), null, bbLabel, function () {
    pickDialog(t('setting_back_behavior'), [
      { key: 'ask', label: t('back_behavior_ask') },
      { key: 'one_press', label: t('back_behavior_one_press') },
      { key: 'two_press', label: t('back_behavior_two_press') },
      { key: 'disable', label: t('back_behavior_disable') }
    ], bb, function (k) { Store.set('back_behavior', k); renderAll(); });
  }));
  const lg = Store.get('lang', 'system');
  const lgLabel = lg === 'en' ? t('lang_en') : lg === 'es' ? t('lang_es') : t('mode_system');
  u.appendChild(setRow(t('setting_language'), null, lgLabel, function () {
    pickDialog(t('setting_language'), [
      { key: 'system', label: t('mode_system') },
      { key: 'en', label: t('lang_en') },
      { key: 'es', label: t('lang_es') }
    ], lg, function (k) { Store.set('lang', k); applyStrings(); renderAll(); });
  }));
  const adv = Store.get('advanced_mode', 'false') === 'true';
  const cols = layoutColsRows()[0];
  const pmLabel = adv ? t('print_mode_advanced', cols, cols) : t('print_mode_classic');
  u.appendChild(setRow(t('setting_print_mode'), null, pmLabel, function () {
    pickDialog(t('setting_print_mode'), [
      { key: 'false', label: t('print_mode_classic'), sub: t('print_mode_classic_sub') },
      { key: 'true', label: t('print_mode_advanced'), sub: t('print_mode_advanced_sub', cols, cols) }
    ], Store.get('advanced_mode', 'false'), function (k) { Store.set('advanced_mode', k); renderAll(); });
  }));
  u.appendChild(switchRow(t('setting_two_box'), t('setting_two_box_sub'),
    Store.get('two_box', 'false') === 'true',
    function (on) { Store.set('two_box', on ? 'true' : 'false'); renderAll(); }));
  u.appendChild(switchRow(t('setting_barcode'), t('setting_barcode_sub'),
    Store.get('barcode', 'false') === 'true',
    function (on) { Store.set('barcode', on ? 'true' : 'false'); renderAll(); }));
  const layout = Store.get('layout', '4x15');
  u.appendChild(setRow(t('setting_layout'), null, layout.replace('x', ' x '), function () {
    pickDialog(t('setting_layout'), LAYOUTS.map(function (l) {
      return { key: l, label: l.replace('x', ' x ') };
    }), layout, function (k) { Store.set('layout', k); renderAll(); });
  }));
  u.appendChild(setRow(t('setting_export'), t('setting_export_sub'), null, showExportDialog));

  /* storage */
  document.getElementById('set-storage-title').textContent = t('section_storage');
  const s = document.getElementById('set-storage-list');
  s.innerHTML = '';
  s.appendChild(switchRow(t('setting_cleanup'), t('setting_cleanup_sub'),
    Store.get('automatic_cleanup', 'true') === 'true',
    async function (on) {
      Store.set('automatic_cleanup', on ? 'true' : 'false');
      if (on) await cleanupOld(7);
      renderAll();
    }));
  s.appendChild(setRow(t('setting_delete_cache'), t('setting_delete_cache_sub'), null, function () {
    /* Web has no temp files; see NOT-PORTED.md. */
    toast(t('toast_cache_cleared'));
  }));
  s.appendChild(setRow(t('setting_delete_old'), t('setting_delete_old_sub'), null, function () {
    pickDialog(t('setting_delete_old'), [
      { key: '7', label: t('delete_7_days') },
      { key: '30', label: t('delete_30_days') },
      { key: '90', label: t('delete_90_days') },
      { key: 'all', label: t('delete_all') }
    ], null, async function (k) {
      if (k === 'all') {
        const recs = Store.get('records', []);
        for (const r of recs) { try { await IDB.del(r.id); } catch (e) {} }
        Store.set('records', recs.length ? [] : []);
        toast(t('toast_deleted', recs.length, recs.length === 1 ? '' : 's'));
      } else {
        const n = await cleanupOld(parseInt(k, 10));
        toast(t('toast_deleted', n, n === 1 ? '' : 's'));
      }
      renderAll();
    });
  }));
  s.appendChild(setRow(t('setting_reset'), t('setting_reset_sub'), null, function () {
    showDialog({
      title: t('dialog_reset_title'), msg: t('dialog_reset_msg'),
      buttons: [
        { label: t('btn_cancel') },
        { label: t('btn_reset'), primary: true, onClick: function () {
          Store.set('total_printed', 0);
          Store.set('unique_sizes', []);
          const recs = Store.get('records', []);
          recs.forEach(function (r) { r.prints = 0; r.lastPrinted = 0; });
          Store.set('records', recs);
          toast(t('toast_stats_reset'));
          renderAll();
        } }
      ]
    });
  }));

  /* appearance */
  document.getElementById('set-appear-title').textContent = t('section_appearance');
  const a = document.getElementById('set-appear-list');
  a.innerHTML = '';
  const mode = Store.get('mode', 'system');
  const modeLabel = mode === 'light' ? t('mode_light') : mode === 'dark' ? t('mode_dark') : t('mode_system');
  a.appendChild(setRow(t('setting_theme_mode'), null, modeLabel, function () {
    pickDialog(t('setting_theme_mode'), [
      { key: 'system', label: t('mode_system') },
      { key: 'light', label: t('mode_light') },
      { key: 'dark', label: t('mode_dark') }
    ], mode, function (k) { Store.set('mode', k); applyTheme(); renderAll(); });
  }));
  const pal = PALETTES.find(function (p) { return p.key === Store.get('palette', 'EMBER_NIGHT'); }) || PALETTES[0];
  a.appendChild(setRow(t('setting_theme_palette'), null, pal.name, function () {
    pickDialog(t('setting_theme_palette'), PALETTES.map(function (p) {
      return { key: p.key, label: p.name, later: p.later };
    }), Store.get('palette', 'EMBER_NIGHT'), function () {});
  }));
  const sty = STYLES.find(function (x) { return x.key === Store.get('ui_style', 'modern'); }) || STYLES[0];
  a.appendChild(setRow(t('setting_theme_style'), null, t(sty.tkey), function () {
    pickDialog(t('setting_theme_style'), STYLES.map(function (x) {
      return { key: x.key, label: t(x.tkey), later: x.later };
    }), Store.get('ui_style', 'modern'), function () {});
  }));
  a.appendChild(scaleRow());
}

function showExportDialog() {
  const records = Store.get('records', []);
  if (!records.length) { toast(t('toast_no_pdfs')); return; }
  showDialog({
    title: t('setting_export'),
    options: records.map(function (r) {
      const d = new Date(r.created).toLocaleDateString(langCode() === 'es' ? 'es' : 'en');
      return {
        label: r.size.split('|').join(' | ') + ' x ' + d,
        onPick: function () { exportPdf(r); }
      };
    })
  });
}

async function exportPdf(rec) {
  try {
    const bytes = await IDB.get(rec.id);
    if (!bytes || !bytes.length) { toast(t('toast_export_failed', 'empty')); return; }
    const blob = new Blob([bytes], { type: 'application/pdf' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = rec.file || fileNameForSize(rec.size);
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(function () { URL.revokeObjectURL(a.href); }, 60000);
    toast(t('toast_exported'));
  } catch (e) {
    toast(t('toast_export_failed', (e && e.message) || e));
  }
}

/* ---------------- UI scale (v1.3.0) ---------------- */
/* Same approach as the JeansMixing web app: a root font-size scale, so
   every rem in the stylesheet grows or shrinks and the layout reflows.
   CSS zoom is never used; it shrinks the box, not the layout. */
function uiScaleVal() {
  let v = parseInt(Store.get('ui_scale', '100'), 10);
  if (isNaN(v)) v = 100;
  return Math.min(200, Math.max(50, v));
}
function applyScale() {
  document.documentElement.style.setProperty('--scale', String(uiScaleVal() / 100));
}
function scaleRow() {
  const b = document.createElement('div');
  b.className = 'set-row'; b.style.cursor = 'default';
  const left = document.createElement('span');
  const tt = document.createElement('span'); tt.className = 't'; tt.textContent = t('setting_ui_scale');
  left.appendChild(tt);
  const s = document.createElement('span'); s.className = 's'; s.textContent = t('setting_ui_scale_sub');
  left.appendChild(s);
  b.appendChild(left);
  const st = document.createElement('div'); st.className = 'stepper';
  const down = document.createElement('button'); down.type = 'button'; down.textContent = '\u2212';
  down.setAttribute('aria-label', 'smaller');
  const val = document.createElement('span'); val.className = 'sv'; val.textContent = uiScaleVal() + '%';
  const up = document.createElement('button'); up.type = 'button'; up.textContent = '+';
  up.setAttribute('aria-label', 'bigger');
  const bump = function (d) {
    Store.set('ui_scale', String(Math.min(200, Math.max(50, uiScaleVal() + d))));
    applyScale(); renderAll();
  };
  down.addEventListener('click', function () { bump(-5); });
  up.addEventListener('click', function () { bump(5); });
  st.appendChild(down); st.appendChild(val); st.appendChild(up);
  b.appendChild(st);
  return b;
}

/* ---------------- render all + init ---------------- */
function renderAll() {
  applyStrings();
  applyScale();
  renderHome();
  renderInsights();
  renderSettings();
  renderInfo();
}

async function init() {
  await startup();
  applyTheme();
  if (window.matchMedia) {
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function () {
      if (Store.get('mode', 'system') === 'system') applyTheme();
    });
  }
  applyScale();
  renderAll();
  /* The main pager is NOT a makePager (v1.3.1): showView owns its
     transform and currentView. Two controllers could desync (swiping to
     Insights moved the pager visually while back handling trusted the
     other controller's state, and back exited the app). makePager below
     remains only for the Settings and Info dot pagers. */
  makePager(document.getElementById('settings-pager'), document.getElementById('settings-dots'));
  makePager(document.getElementById('info-pager'), document.getElementById('info-dots'));

  document.getElementById('tab-create').addEventListener('click', function () {
    if (currentView === 'home') goHome(); else showView('home');
  });
  document.getElementById('tab-insights').addEventListener('click', function () {
    if (currentView === 'insights') goHome(); else showView('insights');
  });
  document.getElementById('btn-settings').addEventListener('click', function () {
    if (currentView === 'settings') goHome(); else showView('settings');
  });
  document.getElementById('btn-info').addEventListener('click', function () {
    if (currentView === 'info') goHome(); else showView('info');
  });

  /* swipe between home and insights */
  const pager = document.getElementById('pager');
  let sx = 0;
  pager.addEventListener('touchstart', function (e) { sx = e.touches[0].clientX; }, { passive: true });
  pager.addEventListener('touchend', function (e) {
    if (currentView !== 'home' && currentView !== 'insights') return;
    const dx = e.changedTouches[0].clientX - sx;
    if (dx < -60 && currentView === 'home') showView('insights');
    else if (dx > 60 && currentView === 'insights') showView('home');
  }, { passive: true });

  document.getElementById('info-version').textContent = 'v' + APP_VERSION;
  document.getElementById('btn-update').addEventListener('click', onUpdateButton);
  renderUpdate();
  try { history.replaceState({ pantser: 'base' }, ''); } catch (e) {}
  armTrap();
  window.addEventListener('popstate', onPopState);
  document.addEventListener('pointerdown', ensureTrapFromGesture, true);
  document.addEventListener('keydown', ensureTrapFromGesture, true);

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('service-worker.js').catch(function () {});
  }
  showView('home', false);
}

document.addEventListener('DOMContentLoaded', init);
