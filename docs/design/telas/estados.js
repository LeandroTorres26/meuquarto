/* Prototype states. ?estado=<name> shows the elements whose data-estado lists that name and hides the others.
   Elements with data-tab="<name>" switch to that state on click and get aria-selected. The default comes from
   data-estado-padrao on <body>. Only for these static prototypes; in the app the state comes from data. */
(function () {
  function apply(s) {
    document.querySelectorAll('[data-estado]').forEach(function (el) {
      el.hidden = el.getAttribute('data-estado').split(' ').indexOf(s) < 0;
    });
    document.querySelectorAll('[data-tab]').forEach(function (el) {
      el.setAttribute('aria-selected', String(el.getAttribute('data-tab') === s));
    });
  }
  var start = new URLSearchParams(location.search).get('estado') || document.body.getAttribute('data-estado-padrao');
  apply(start);
  document.addEventListener('click', function (e) {
    var t = e.target.closest('[data-tab]');
    if (!t) return;
    var s = t.getAttribute('data-tab');
    history.replaceState(null, '', '?estado=' + s);
    apply(s);
  });
})();
