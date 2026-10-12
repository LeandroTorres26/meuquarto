/* @ds-bundle: {"format":4,"namespace":"Meuquarto","components":[{"name":"HighlightCard"},{"name":"CategoryTag"}]} */
(function () {
  var React = window.React;
  var h = React.createElement;

  /* Lucide icons (ISC): inner markup of each 24px svg */
  var ICONS = {"clapperboard":"<path d=\"m12.296 3.464 3.02 3.956\" /> <path d=\"M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3z\" /> <path d=\"M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z\" /> <path d=\"m6.18 5.276 3.1 3.899\" />","tv":"<path d=\"m17 2-5 5-5-5\" /> <rect width=\"20\" height=\"15\" x=\"2\" y=\"7\" rx=\"2\" />","sparkles":"<path d=\"M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z\" /> <path d=\"M20 2v4\" /> <path d=\"M22 4h-4\" /> <circle cx=\"4\" cy=\"20\" r=\"2\" />","book-copy":"<path d=\"M5 7a2 2 0 0 0-2 2v11\" /> <path d=\"M5.803 18H5a2 2 0 0 0 0 4h9.5a.5.5 0 0 0 .5-.5V21\" /> <path d=\"M9 15V4a2 2 0 0 1 2-2h9.5a.5.5 0 0 1 .5.5v14a.5.5 0 0 1-.5.5H11a2 2 0 0 1 0-4h10\" />","book-open":"<path d=\"M12 5v16\" /> <path d=\"M20.001 19A2 2 0 0022 17V5a2 2 0 00-1.999-2L16 3.002A5 5 0 0012 5a5 5 0 00-4-2H4a2 2 0 00-2 2v12a2 2 0 001.999 2H8a5 5 0 014 2 5 5 0 014-2z\" />","disc-3":"<circle cx=\"12\" cy=\"12\" r=\"10\" /> <path d=\"M6 12c0-1.7.7-3.2 1.8-4.2\" /> <circle cx=\"12\" cy=\"12\" r=\"2\" /> <path d=\"M18 12c0 1.7-.7 3.2-1.8 4.2\" />","gamepad-2":"<line x1=\"6\" x2=\"10\" y1=\"11\" y2=\"11\" /> <line x1=\"8\" x2=\"8\" y1=\"9\" y2=\"13\" /> <line x1=\"15\" x2=\"15.01\" y1=\"12\" y2=\"12\" /> <line x1=\"18\" x2=\"18.01\" y1=\"10\" y2=\"10\" /> <path d=\"M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.01.101-.017.152C2.604 9.416 2 14.456 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.414-1.414A2 2 0 0 1 9.828 16h4.344a2 2 0 0 1 1.414.586L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.545-.604-6.584-.685-7.258-.007-.05-.011-.1-.017-.151A4 4 0 0 0 17.32 5z\" />","image-off":"<line x1=\"2\" x2=\"22\" y1=\"2\" y2=\"22\" /> <path d=\"M10.41 10.41a2 2 0 1 1-2.83-2.83\" /> <line x1=\"13.5\" x2=\"6\" y1=\"13.5\" y2=\"21\" /> <line x1=\"18\" x2=\"21\" y1=\"12\" y2=\"15\" /> <path d=\"M3.59 3.59A1.99 1.99 0 0 0 3 5v14a2 2 0 0 0 2 2h14c.55 0 1.052-.22 1.41-.59\" /> <path d=\"M21 15V5a2 2 0 0 0-2-2H9\" />","plus":"<path d=\"M5 12h14\" /> <path d=\"M12 5v14\" />"};

  function Icon(props) {
    return h('svg', {
      className: 'mq-icon', viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor',
      strokeWidth: 2, strokeLinecap: 'round', strokeLinejoin: 'round', 'aria-hidden': true,
      dangerouslySetInnerHTML: { __html: ICONS[props.name] || '' }
    });
  }

  /* category -> label, icon, physical shape */
  var CATEGORIES = {
    movie:  { label: 'Filme',  icon: 'clapperboard', shape: 'poster' },
    series: { label: 'Série',  icon: 'tv',           shape: 'poster' },
    anime:  { label: 'Anime',  icon: 'sparkles',     shape: 'dvd' },
    manga:  { label: 'Mangá',  icon: 'book-copy',    shape: 'manga' },
    book:   { label: 'Livro',  icon: 'book-open',    shape: 'book' },
    game:   { label: 'Game',   icon: 'gamepad-2',    shape: 'game' },
    music:  { label: 'Música', icon: 'disc-3',       shape: 'vinyl' }
  };

  /* Deterministic style from the item id (FNV-1a, 32 bit). Same id, same result, on server and client. */
  var TILT_MIN = 0.9, TILT_MAX = 2.5; /* degrees, mirrors tokens tilt-min / tilt-max */
  function hash(id) {
    var s = String(id), x = 2166136261;
    for (var i = 0; i < s.length; i++) { x ^= s.charCodeAt(i); x = Math.imul(x, 16777619) >>> 0; }
    return x >>> 0;
  }
  function tiltFactor(id) {
    var x = hash(id), u = (x % 1000) / 999;
    var mag = TILT_MIN / TILT_MAX + (1 - TILT_MIN / TILT_MAX) * u;
    var sign = (x >>> 10) & 1 ? 1 : -1;
    return Math.round(sign * mag * 1000) / 1000; /* in [-1,-0.36] or [0.36,1]; angle = factor * tilt-max */
  }
  function fixKind(id) { return (hash(id) >>> 11) & 1 ? 'tape' : 'pin'; }

  function CategoryTag(props) {
    var c = CATEGORIES[props.category] || CATEGORIES.music;
    return h('span', { className: 'mq-tag mq-tag--' + props.category },
      h(Icon, { name: c.icon }), c.label,
      props.count != null ? h('span', { className: 'mq-tag__count' }, props.count) : null);
  }

  function subline(p) {
    var parts = [];
    if (p.year) parts.push(p.year);
    if (p.volume) parts.push('vol. ' + p.volume);
    return parts.join(' · ');
  }

  /* props: id, category, title, year, volume, cover (url), note, href, loading, error, disabled,
     fix ('tape' | 'pin' | 'none'), showTag (default true), state ('hover' | 'focus' for documentation) */
  function HighlightCard(props) {
    var cat = CATEGORIES[props.category] || CATEGORIES.movie, shape = cat.shape;
    var loading = !!props.loading, error = !!props.error, empty = !props.cover && !loading && !error;
    var fix = props.fix || fixKind(props.id);
    var cls = 'mq-item mq-item--' + shape + (shape === 'book' || shape === 'manga' ? ' mq-item--volume' : '')
      + (loading ? ' mq-item--loading' : '') + (props.disabled ? ' is-disabled' : '')
      + (props.state === 'hover' ? ' is-hover' : '');
    var style = { '--tilt-f': loading ? 0 : tiltFactor(props.id) };

    var art;
    if (loading) art = h('div', { className: 'mq-art' });
    else if (error) art = h('div', { className: 'mq-art mq-art--error', role: 'img', 'aria-label': 'Capa indisponível' },
      h(Icon, { name: 'image-off' }), h('span', { className: 'mq-art__msg' }, 'Capa indisponível'));
    else if (empty) art = h('div', { className: 'mq-art mq-art--empty', role: 'img', 'aria-label': 'Sem capa' }, h(Icon, { name: cat.icon }));
    else art = h('img', { className: 'mq-art', src: props.cover, alt: '', loading: 'lazy', width: 200, height: 300 });

    var decorate = !loading && !props.disabled;
    var cover = h('figure', { className: 'mq-item__cover' },
      shape === 'vinyl' ? h('span', { className: 'mq-record', 'aria-hidden': true }) : null,
      shape === 'game' ? h('span', { className: 'mq-gamebar', 'aria-hidden': true }, h(Icon, { name: 'gamepad-2' })) : null,
      shape === 'dvd' ? h('span', { className: 'mq-spine', 'aria-hidden': true }) : null,
      art,
      decorate && props.showTag !== false ? h(CategoryTag, { category: props.category }) : null,
      decorate && fix === 'tape' ? h('span', { className: 'mq-tape', 'aria-hidden': true }) : null,
      decorate && fix === 'pin' ? h('span', { className: 'mq-pin', 'aria-hidden': true }) : null,
      decorate && props.note ? h('p', { className: 'mq-slip' }, props.note) : null);

    var meta = loading
      ? h('div', { className: 'mq-item__meta', 'aria-hidden': true }, h('span', { className: 'mq-sk' }), h('span', { className: 'mq-sk' }))
      : h('div', { className: 'mq-item__meta' },
          h('h3', { className: 'mq-item__title', title: props.title }, props.title),
          subline(props) ? h('p', { className: 'mq-item__sub' }, subline(props)) : null);

    var label = props.title + (props.year ? ' (' + props.year + ')' : '') + ', ' + cat.label;
    var link = h(props.href && !props.disabled ? 'a' : 'div',
      { className: 'mq-item__link' + (props.state === 'focus' ? ' is-focus' : ''), href: props.href && !props.disabled ? props.href : undefined, 'aria-label': loading ? undefined : label },
      cover, meta);
    return h('article', { className: cls, style: style, 'aria-busy': loading || undefined, 'aria-disabled': props.disabled || undefined }, link);
  }

  /* empty shelf slot: props: category, onClick, label */
  function HighlightSlot(props) {
    var cat = CATEGORIES[props.category] || CATEGORIES.movie;
    return h('div', { className: 'mq-item' },
      h('button', { type: 'button', className: 'mq-slot mq-slot--' + cat.shape, onClick: props.onClick },
        h(Icon, { name: 'plus' }), props.label || 'Fixar obra'));
  }

  function HighlightGrid(props) {
    return h('div', { className: 'mq-grid', role: 'list' }, React.Children.map(props.children, function (c) { return h('div', { role: 'listitem' }, c); }));
  }

  window.Meuquarto = {
    HighlightCard: HighlightCard, HighlightSlot: HighlightSlot, HighlightGrid: HighlightGrid, CategoryTag: CategoryTag, Icon: Icon,
    CATEGORIES: CATEGORIES, tiltFactor: tiltFactor, fixKind: fixKind, hash: hash
  };
})();
