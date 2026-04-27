// OculiX Reporter — Phase 1 JS.
// Pure vanilla, no dep. Collapses tests by default, opens failed ones,
// enables lightbox zoom on screenshots, and sidebar navigation.
(function () {
  'use strict';

  // --- Collapse / expand tests ---
  document.querySelectorAll('.test-header').forEach(function (h) {
    h.addEventListener('click', function () {
      var card = h.closest('.test');
      card.classList.toggle('open');
    });
  });

  // Auto-open failed/error tests on load.
  document.querySelectorAll('.test').forEach(function (c) {
    var o = c.getAttribute('data-outcome');
    if (o === 'failed' || o === 'error') c.classList.add('open');
  });

  // --- Sidebar filter ---
  var navLinks = document.querySelectorAll('.sidebar-nav a[data-filter]');
  navLinks.forEach(function (a) {
    a.addEventListener('click', function (e) {
      e.preventDefault();
      var filter = a.getAttribute('data-filter');
      navLinks.forEach(function (l) { l.classList.remove('active'); });
      a.classList.add('active');
      document.querySelectorAll('.test').forEach(function (c) {
        if (filter === 'all' || c.getAttribute('data-outcome') === filter) {
          c.style.display = '';
        } else {
          c.style.display = 'none';
        }
      });
    });
  });

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

  // --- Test detail modal (triggered from Slowest tests rows) ---
  var modal = document.querySelector('.test-modal-overlay');
  if (modal) {
    var modalBody = modal.querySelector('.test-modal-body');
    var modalClose = modal.querySelector('.test-modal-close');

    function openTestModal(idx) {
      var article = document.querySelector('article.test[data-test-index="' + idx + '"]');
      if (!article) return;
      var clone = article.cloneNode(true);
      clone.classList.add('open');
      modalBody.innerHTML = '';
      modalBody.appendChild(clone);
      modal.hidden = false;
      document.body.style.overflow = 'hidden';
    }
    function closeTestModal() {
      modal.hidden = true;
      modalBody.innerHTML = '';
      document.body.style.overflow = '';
    }

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
})();
