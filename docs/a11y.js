/* Granular Volume accessibility panel.
   Part of this site, not a third-party plugin: it loads nothing from anywhere else, sets no cookie,
   and remembers choices only in sessionStorage (this browser tab, until it is closed).
   Without JavaScript the button stays a plain link to the accessibility statement. */
(function () {
  'use strict';

  var LABELS = {
    en: { open: 'Accessibility options', title: 'Accessibility', size: 'Text size', smaller: 'Smaller text', larger: 'Larger text',
          contrast: 'High contrast', underline: 'Underline links', font: 'Readable font', reset: 'Reset', statement: 'Accessibility statement', close: 'Close' },
    de: { open: 'Optionen zur Barrierefreiheit', title: 'Barrierefreiheit', size: 'Textgröße', smaller: 'Text kleiner', larger: 'Text größer',
          contrast: 'Hoher Kontrast', underline: 'Links unterstreichen', font: 'Gut lesbare Schrift', reset: 'Zurücksetzen', statement: 'Erklärung zur Barrierefreiheit', close: 'Schließen' },
    es: { open: 'Opciones de accesibilidad', title: 'Accesibilidad', size: 'Tamaño del texto', smaller: 'Texto más pequeño', larger: 'Texto más grande',
          contrast: 'Alto contraste', underline: 'Subrayar enlaces', font: 'Fuente legible', reset: 'Restablecer', statement: 'Declaración de accesibilidad', close: 'Cerrar' },
    fr: { open: "Options d'accessibilité", title: 'Accessibilité', size: 'Taille du texte', smaller: 'Texte plus petit', larger: 'Texte plus grand',
          contrast: 'Contraste élevé', underline: 'Souligner les liens', font: 'Police lisible', reset: 'Réinitialiser', statement: "Déclaration d'accessibilité", close: 'Fermer' },
    it: { open: 'Opzioni di accessibilità', title: 'Accessibilità', size: 'Dimensione del testo', smaller: 'Testo più piccolo', larger: 'Testo più grande',
          contrast: 'Contrasto elevato', underline: 'Sottolinea i link', font: 'Carattere leggibile', reset: 'Ripristina', statement: 'Dichiarazione di accessibilità', close: 'Chiudi' },
    pt: { open: 'Opções de acessibilidade', title: 'Acessibilidade', size: 'Tamanho do texto', smaller: 'Texto menor', larger: 'Texto maior',
          contrast: 'Alto contraste', underline: 'Sublinhar links', font: 'Fonte legível', reset: 'Redefinir', statement: 'Declaração de acessibilidade', close: 'Fechar' }
  };
  var SIZES = [100, 115, 130, 150];
  var KEY = 'gv-a11y';

  var fab = document.getElementById('gv-a11y-fab');
  if (!fab) return;
  var lang = (document.documentElement.lang || 'en').slice(0, 2);
  var L = LABELS[lang] || LABELS.en;
  var root = document.documentElement;

  var state = { size: 0, contrast: false, underline: false, font: false };
  try {
    var saved = JSON.parse(window.sessionStorage.getItem(KEY) || 'null');
    if (saved && typeof saved === 'object') {
      state.size = Math.max(0, Math.min(SIZES.length - 1, saved.size | 0));
      state.contrast = !!saved.contrast; state.underline = !!saved.underline; state.font = !!saved.font;
    }
  } catch (e) { /* storage unavailable: choices simply are not remembered */ }

  function save() {
    try { window.sessionStorage.setItem(KEY, JSON.stringify(state)); } catch (e) { /* ignore */ }
  }

  function apply() {
    var zoom = SIZES[state.size] / 100;
    Array.prototype.forEach.call(document.body.children, function (el) {
      if (el.classList.contains('gv-a11y') || el.tagName === 'SCRIPT' || el.tagName === 'STYLE') return;
      el.style.zoom = zoom === 1 ? '' : String(zoom);
    });
    root.classList.toggle('gv-hc', state.contrast);
    root.classList.toggle('gv-ul', state.underline);
    root.classList.toggle('gv-rf', state.font);
  }

  function el(tag, attrs, text) {
    var n = document.createElement(tag);
    Object.keys(attrs || {}).forEach(function (k) { n.setAttribute(k, attrs[k]); });
    if (text) n.textContent = text;
    return n;
  }

  // The link becomes a real button that opens the panel.
  var button = el('button', { type: 'button', id: 'gv-a11y-fab', 'class': 'gv-a11y gv-a11y-fab',
    'aria-label': L.open, 'aria-expanded': 'false', 'aria-controls': 'gv-a11y-panel' });
  button.innerHTML = fab.innerHTML;
  var statementHref = fab.getAttribute('href');
  fab.parentNode.replaceChild(button, fab);

  var panel = el('div', { id: 'gv-a11y-panel', 'class': 'gv-a11y gv-a11y-panel', role: 'region', 'aria-label': L.title, hidden: '' });
  panel.appendChild(el('p', { 'class': 'gv-a11y-title' }, L.title));

  var sizeRow = el('div', { 'class': 'gv-a11y-row', role: 'group', 'aria-label': L.size });
  var sizeName = el('span', { 'class': 'gv-a11y-label' }, L.size);
  var sizeValue = el('span', { 'class': 'gv-a11y-value', 'aria-live': 'polite' });
  var smaller = el('button', { type: 'button', 'class': 'gv-a11y-btn gv-a11y-step', 'aria-label': L.smaller }, 'A-');
  var larger = el('button', { type: 'button', 'class': 'gv-a11y-btn gv-a11y-step', 'aria-label': L.larger }, 'A+');
  sizeRow.appendChild(sizeName); sizeRow.appendChild(smaller); sizeRow.appendChild(sizeValue); sizeRow.appendChild(larger);
  panel.appendChild(sizeRow);

  function toggle(name, label) {
    var b = el('button', { type: 'button', 'class': 'gv-a11y-btn gv-a11y-toggle', 'aria-pressed': 'false' }, label);
    b.addEventListener('click', function () { state[name] = !state[name]; refresh(); });
    panel.appendChild(b);
    return b;
  }
  var tContrast = toggle('contrast', L.contrast);
  var tUnderline = toggle('underline', L.underline);
  var tFont = toggle('font', L.font);

  var reset = el('button', { type: 'button', 'class': 'gv-a11y-btn' }, L.reset);
  panel.appendChild(reset);
  var link = el('a', { href: statementHref, 'class': 'gv-a11y-link' }, L.statement);
  panel.appendChild(link);
  var close = el('button', { type: 'button', 'class': 'gv-a11y-btn gv-a11y-close' }, L.close);
  panel.appendChild(close);

  button.parentNode.insertBefore(panel, button.nextSibling);

  function refresh() {
    sizeValue.textContent = SIZES[state.size] + '%';
    smaller.disabled = state.size === 0;
    larger.disabled = state.size === SIZES.length - 1;
    tContrast.setAttribute('aria-pressed', String(state.contrast));
    tUnderline.setAttribute('aria-pressed', String(state.underline));
    tFont.setAttribute('aria-pressed', String(state.font));
    apply();
    save();
  }

  smaller.addEventListener('click', function () { if (state.size > 0) { state.size--; refresh(); } });
  larger.addEventListener('click', function () { if (state.size < SIZES.length - 1) { state.size++; refresh(); } });
  reset.addEventListener('click', function () { state = { size: 0, contrast: false, underline: false, font: false }; refresh(); });

  function openPanel() {
    panel.hidden = false;
    button.setAttribute('aria-expanded', 'true');
    (larger.disabled ? smaller : larger).focus();
  }
  function closePanel(returnFocus) {
    panel.hidden = true;
    button.setAttribute('aria-expanded', 'false');
    if (returnFocus) button.focus();
  }
  button.addEventListener('click', function () { if (panel.hidden) openPanel(); else closePanel(false); });
  close.addEventListener('click', function () { closePanel(true); });
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape' && !panel.hidden) closePanel(true);
  });
  document.addEventListener('click', function (e) {
    if (!panel.hidden && !panel.contains(e.target) && !button.contains(e.target)) closePanel(false);
  });

  // On a phone or tablet the round button sits over the text being read. It slides off the edge while the
  // page scrolls down and comes back on any scroll up, near the top, at the end of the page, and
  // whenever it has keyboard focus or its panel is open.
  var narrow = window.matchMedia ? window.matchMedia('(max-width:1024px)') : null;
  if (narrow) {
    var style = el('style', {});
    style.textContent = '.gv-a11y-fab{transition:transform .2s ease;}' +
      '.gv-a11y-fab.gv-a11y-away{transform:translateX(-72px);}' +
      '.gv-a11y-fab.gv-a11y-away:focus-visible{transform:none;}' +
      '@media (prefers-reduced-motion:reduce){.gv-a11y-fab{transition:none;}}';
    document.head.appendChild(style);
    var lastY = window.pageYOffset;
    window.addEventListener('scroll', function () {
      var y = window.pageYOffset;
      var atEnd = y + window.innerHeight >= document.documentElement.scrollHeight - 80;
      var away = narrow.matches && panel.hidden && y > lastY && y > 200 && !atEnd;
      button.classList.toggle('gv-a11y-away', away);
      lastY = y;
    }, { passive: true });
  }

  refresh();
})();
