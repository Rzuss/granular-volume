/* Which answer engine sent this visit, carried onto the Google Play links.
   Play Console can only split installs by the utm_source in the store link. A visitor who
   arrives from an AI assistant keeps utm_source=site, so every engine looked the same.
   Here the engine is read once (from the referrer, or from the utm_source the engine added
   to our own URL), kept for the visit, and written into the Play links as site-<engine>.
   Nothing leaves the page: no cookie, no request, only the links on this page change. */
(function () {
  var ENGINES = [
    ['chatgpt', /(^|\.)chatgpt\.com$|(^|\.)openai\.com$/],
    ['gemini', /^gemini\.google\.com$|^bard\.google\.com$/],
    ['perplexity', /(^|\.)perplexity\.ai$/],
    ['copilot', /(^|\.)copilot\.microsoft\.com$|(^|\.)copilot\.com$/],
    ['claude', /(^|\.)claude\.ai$/],
    ['grok', /(^|\.)grok\.com$/],
    ['meta', /(^|\.)meta\.ai$/],
    ['deepseek', /(^|\.)deepseek\.com$/],
    ['mistral', /(^|\.)mistral\.ai$/],
    ['you', /(^|\.)you\.com$/]
  ];
  var KEY = 'gv_engine';

  function engineOf(host) {
    host = (host || '').toLowerCase().replace(/^www\./, '');
    for (var i = 0; i < ENGINES.length; i++) if (ENGINES[i][1].test(host)) return ENGINES[i][0];
    return '';
  }

  function detect() {
    var found = '';
    try {
      var q = new URLSearchParams(location.search).get('utm_source');
      if (q) found = engineOf(q.replace(/^https?:\/\//, '').split('/')[0]);
    } catch (e) {}
    if (!found && document.referrer) {
      try { found = engineOf(new URL(document.referrer).hostname); } catch (e) {}
    }
    try {
      if (found) sessionStorage.setItem(KEY, found);
      else found = sessionStorage.getItem(KEY) || '';
    } catch (e) {}
    return found;
  }

  function tagLinks(engine) {
    var links = document.querySelectorAll('a[href*="play.google.com/store/apps/details?id=granularvolume.com"]');
    for (var i = 0; i < links.length; i++) {
      var a = links[i];
      a.href = a.href.replace('utm_source%3Dsite%26', 'utm_source%3Dsite-' + engine + '%26');
      var ev = a.getAttribute('data-goatcounter-click');
      if (ev && ev.indexOf('play-') === 0) a.setAttribute('data-goatcounter-click', ev + '-' + engine);
    }
  }

  var engine = detect();
  if (!engine) return;
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', function () { tagLinks(engine); });
  else tagLinks(engine);
})();
