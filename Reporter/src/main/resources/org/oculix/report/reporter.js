// OculiX Reporter — vanilla JS, no dep.
(function () {
  'use strict';

  var testsContainer = document.querySelector('.tests');
  var allTests = Array.prototype.slice.call(document.querySelectorAll('.test'));

  // --- Combined filter (outcome + search) ---
  var activeOutcome = 'all';
  var searchQuery = '';
  function applyFilters() {
    var q = searchQuery.toLowerCase();
    allTests.forEach(function (c) {
      var matchOutcome = activeOutcome === 'all' || c.getAttribute('data-outcome') === activeOutcome;
      var matchSearch = !q || c.getAttribute('data-name').toLowerCase().indexOf(q) !== -1;
      c.style.display = (matchOutcome && matchSearch) ? '' : 'none';
    });
  }

  // --- Collapse / expand a single test ---
  document.querySelectorAll('.test-header').forEach(function (h) {
    h.addEventListener('click', function (e) {
      // Don't toggle when clicking the permalink anchor
      if (e.target.closest('.test-permalink')) return;
      h.closest('.test').classList.toggle('open');
    });
  });

  // Auto-open failed/error tests on load.
  allTests.forEach(function (c) {
    var o = c.getAttribute('data-outcome');
    if (o === 'failed' || o === 'error') c.classList.add('open');
  });

  // --- Sidebar outcome filter ---
  var navLinks = document.querySelectorAll('.sidebar-nav a[data-filter]');
  navLinks.forEach(function (a) {
    a.addEventListener('click', function (e) {
      e.preventDefault();
      activeOutcome = a.getAttribute('data-filter');
      navLinks.forEach(function (l) { l.classList.remove('active'); });
      a.classList.add('active');
      applyFilters();
    });
  });

  // --- Sidebar search ---
  var search = document.getElementById('sidebar-search');
  if (search) {
    search.addEventListener('input', function () {
      searchQuery = search.value;
      applyFilters();
    });
  }

  // --- Sort buttons ---
  var sortState = { key: null, dir: 1 }; // dir: 1 asc, -1 desc
  document.querySelectorAll('.sort-btn').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var key = btn.getAttribute('data-sort');
      if (sortState.key === key) {
        sortState.dir = -sortState.dir;
      } else {
        sortState.key = key;
        sortState.dir = (key === 'duration') ? -1 : 1; // duration: longest first
      }
      document.querySelectorAll('.sort-btn').forEach(function (b) {
        b.classList.remove('active');
        b.removeAttribute('data-direction');
      });
      btn.classList.add('active');
      btn.setAttribute('data-direction', sortState.dir > 0 ? '↑' : '↓');
      sortTests();
    });
  });
  function sortTests() {
    if (!testsContainer || !sortState.key) return;
    var arr = Array.prototype.slice.call(testsContainer.querySelectorAll('.test'));
    arr.sort(function (a, b) {
      var av, bv;
      if (sortState.key === 'duration') {
        av = parseInt(a.getAttribute('data-duration-ms'), 10) || 0;
        bv = parseInt(b.getAttribute('data-duration-ms'), 10) || 0;
        return (av - bv) * sortState.dir;
      }
      if (sortState.key === 'name') {
        av = (a.getAttribute('data-name') || '').toLowerCase();
        bv = (b.getAttribute('data-name') || '').toLowerCase();
      } else { // outcome
        av = a.getAttribute('data-outcome') || '';
        bv = b.getAttribute('data-outcome') || '';
      }
      if (av < bv) return -1 * sortState.dir;
      if (av > bv) return  1 * sortState.dir;
      return 0;
    });
    arr.forEach(function (el) { testsContainer.appendChild(el); });
  }

  // --- Expand all / Collapse all ---
  document.querySelectorAll('.action-btn').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var action = btn.getAttribute('data-action');
      allTests.forEach(function (c) {
        if (action === 'expand-all') c.classList.add('open');
        else if (action === 'collapse-all') c.classList.remove('open');
      });
    });
  });

  // --- Copy stack trace / error message ---
  document.querySelectorAll('.copy-btn').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var pre = btn.parentElement.querySelector('pre');
      if (!pre) return;
      var text = pre.textContent || '';
      var done = function () {
        var orig = btn.textContent;
        btn.textContent = 'Copied';
        btn.classList.add('copied');
        setTimeout(function () {
          btn.textContent = orig;
          btn.classList.remove('copied');
        }, 1200);
      };
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(done, function () { fallbackCopy(text, done); });
      } else {
        fallbackCopy(text, done);
      }
    });
  });
  function fallbackCopy(text, cb) {
    var ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand('copy'); cb(); } catch (e) {}
    document.body.removeChild(ta);
  }

  // --- Permalink: jump to anchor on load + flash highlight ---
  function handleHash() {
    var hash = window.location.hash;
    if (!hash || hash.indexOf('#test-') !== 0) return;
    var el = document.getElementById(hash.substring(1));
    if (!el) return;
    el.classList.add('open');
    el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    el.classList.remove('target-flash');
    void el.offsetWidth; // restart animation
    el.classList.add('target-flash');
  }
  window.addEventListener('hashchange', handleHash);
  if (window.location.hash) setTimeout(handleHash, 50);

  // --- Lightbox for screenshots ---
  var lightbox = document.createElement('div');
  lightbox.className = 'lightbox';
  var lbImg = document.createElement('img');
  lightbox.appendChild(lbImg);
  document.body.appendChild(lightbox);
  lightbox.addEventListener('click', function () { lightbox.classList.remove('open'); });
  document.querySelectorAll('.step-shot img').forEach(function (i) {
    i.addEventListener('click', function () {
      lbImg.src = i.src;
      lightbox.classList.add('open');
    });
  });

  // --- Test detail modal (triggered from Slowest tests + trend rows) ---
  var modal = document.querySelector('.test-modal-overlay');
  function openTestModal(idx) {
    if (!modal) return;
    var article = document.querySelector('article.test[data-test-index="' + idx + '"]');
    if (!article) return;
    var clone = article.cloneNode(true);
    clone.classList.add('open');
    var body = modal.querySelector('.test-modal-body');
    body.innerHTML = '';
    body.appendChild(clone);
    modal.hidden = false;
    document.body.style.overflow = 'hidden';
  }
  function closeTestModal() {
    if (!modal) return;
    modal.hidden = true;
    modal.querySelector('.test-modal-body').innerHTML = '';
    document.body.style.overflow = '';
  }
  if (modal) {
    var modalClose = modal.querySelector('.test-modal-close');
    document.querySelectorAll('.slowest-row').forEach(function (row) {
      row.addEventListener('click', function () {
        openTestModal(row.getAttribute('data-test-index'));
      });
      row.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          openTestModal(row.getAttribute('data-test-index'));
        }
      });
    });
    modal.addEventListener('click', function (e) {
      if (e.target === modal) closeTestModal();
    });
    modalClose.addEventListener('click', closeTestModal);
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && !modal.hidden) closeTestModal();
    });
  }

  // --- Trend pills: toggle detail panels + filter test list to impacted tests ---
  var trendActive = null; // 'regressions' | 'new-passes' | 'flaky' | null
  function applyTrendFilter() {
    if (!trendActive) {
      // remove trend filter, restore baseline filtering
      allTests.forEach(function (c) { delete c.dataset.trendHide; });
      applyFilters();
      return;
    }
    var panel = document.querySelector('.trends-details[data-trend="' + trendActive + '"]');
    if (!panel) return;
    var allowed = {};
    panel.querySelectorAll('.trend-list-row').forEach(function (r) {
      allowed[r.getAttribute('data-test-index')] = true;
    });
    allTests.forEach(function (c) {
      if (allowed[c.getAttribute('data-test-index')]) {
        delete c.dataset.trendHide;
      } else {
        c.dataset.trendHide = '1';
      }
    });
    // Apply combined visibility (search + outcome + trend)
    var q = (search ? search.value : '').toLowerCase();
    allTests.forEach(function (c) {
      var matchOutcome = activeOutcome === 'all' || c.getAttribute('data-outcome') === activeOutcome;
      var matchSearch = !q || c.getAttribute('data-name').toLowerCase().indexOf(q) !== -1;
      var hiddenByTrend = c.dataset.trendHide === '1';
      c.style.display = (matchOutcome && matchSearch && !hiddenByTrend) ? '' : 'none';
    });
  }

  document.querySelectorAll('.trend-pill').forEach(function (pill) {
    pill.addEventListener('click', function () {
      var key = pill.getAttribute('data-trend');
      var panel = document.querySelector('.trends-details[data-trend="' + key + '"]');
      var willOpen = pill.classList.contains('active') ? false : true;
      // close all other pills + panels first
      document.querySelectorAll('.trend-pill').forEach(function (p) { p.classList.remove('active'); });
      document.querySelectorAll('.trends-details').forEach(function (p) { p.hidden = true; });
      if (willOpen) {
        pill.classList.add('active');
        if (panel) panel.hidden = false;
        trendActive = key;
      } else {
        trendActive = null;
      }
      applyTrendFilter();
    });
  });

  // Trend list rows open the test in the modal
  document.querySelectorAll('.trend-list-row').forEach(function (row) {
    row.addEventListener('click', function () {
      openTestModal(row.getAttribute('data-test-index'));
    });
    row.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        openTestModal(row.getAttribute('data-test-index'));
      }
    });
  });
})();
