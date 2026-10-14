/* S17 on the public profile: any link to ?item=<id> opens the item dialog. The id stays in the URL, so the
   browser back button, Esc, the close button and a click on the dimmed backdrop all close it the same way. */
(function () {
  var dlg = document.getElementById('item-dlg');
  var opener = null, pushed = false;
  var closeBtn = '<button type="button" class="ib dialog__close" aria-label="Fechar" data-close><svg class="icon" aria-hidden="true"><use href="icons.svg#x"/></svg></button>';

  function current() { return new URLSearchParams(location.search).get('item'); }

  function sync() {
    var id = current();
    if (!id) { if (dlg.open) { dlg.close(); if (opener) opener.focus(); } return; }
    dlg.innerHTML = closeBtn + '<div class="dialog__body detail-wrap">' + MQItem.render(id) + '</div>';
    if (!dlg.open) dlg.showModal();
    dlg.querySelector('.dialog__close').focus();
  }

  function requestClose() {
    if (pushed) { history.back(); return; }        /* opened by a click: going back closes it */
    var q = new URLSearchParams(location.search);    /* opened from a shared link: drop the param */
    q.delete('item'); q.delete('estado');
    history.replaceState(null, '', location.pathname + (q.toString() ? '?' + q : ''));
    sync();
  }

  document.addEventListener('click', function (e) {
    var a = e.target.closest('a[href^="?item="]');
    if (!a || e.metaKey || e.ctrlKey || e.shiftKey) return;
    e.preventDefault();
    opener = a;
    history.pushState({ item: true }, '', a.getAttribute('href'));
    pushed = true;
    sync();
  });
  dlg.addEventListener('click', function (e) {
    if (e.target === dlg || e.target.closest('[data-close]')) requestClose();   /* e.target === dlg: the backdrop */
  });
  dlg.addEventListener('cancel', function (e) { e.preventDefault(); requestClose(); });   /* Esc */
  addEventListener('popstate', function (e) { pushed = !!(e.state && e.state.item); sync(); });

  sync();
})();
