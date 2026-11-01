/* @ds-bundle: {"format":4,"namespace":"Meuquarto","components":[{"name":"HighlightCard"},{"name":"CategoryTag"},{"name":"Button"},{"name":"NowCard"},{"name":"Toast"},{"name":"LimitUpsell"}]} */
(function () {
  var React = window.React;
  var h = React.createElement;

  /* Lucide icons (ISC): inner markup of each 24px svg */
  var ICONS = {"clapperboard":"<path d=\"m12.296 3.464 3.02 3.956\" /> <path d=\"M20.2 6 3 11l-.9-2.4c-.3-1.1.3-2.2 1.3-2.5l13.5-4c1.1-.3 2.2.3 2.5 1.3z\" /> <path d=\"M3 11h18v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z\" /> <path d=\"m6.18 5.276 3.1 3.899\" />","tv":"<path d=\"m17 2-5 5-5-5\" /> <rect width=\"20\" height=\"15\" x=\"2\" y=\"7\" rx=\"2\" />","sparkles":"<path d=\"M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z\" /> <path d=\"M20 2v4\" /> <path d=\"M22 4h-4\" /> <circle cx=\"4\" cy=\"20\" r=\"2\" />","book-copy":"<path d=\"M5 7a2 2 0 0 0-2 2v11\" /> <path d=\"M5.803 18H5a2 2 0 0 0 0 4h9.5a.5.5 0 0 0 .5-.5V21\" /> <path d=\"M9 15V4a2 2 0 0 1 2-2h9.5a.5.5 0 0 1 .5.5v14a.5.5 0 0 1-.5.5H11a2 2 0 0 1 0-4h10\" />","book-open":"<path d=\"M12 5v16\" /> <path d=\"M20.001 19A2 2 0 0022 17V5a2 2 0 00-1.999-2L16 3.002A5 5 0 0012 5a5 5 0 00-4-2H4a2 2 0 00-2 2v12a2 2 0 001.999 2H8a5 5 0 014 2 5 5 0 014-2z\" />","disc-3":"<circle cx=\"12\" cy=\"12\" r=\"10\" /> <path d=\"M6 12c0-1.7.7-3.2 1.8-4.2\" /> <circle cx=\"12\" cy=\"12\" r=\"2\" /> <path d=\"M18 12c0 1.7-.7 3.2-1.8 4.2\" />","gamepad-2":"<line x1=\"6\" x2=\"10\" y1=\"11\" y2=\"11\" /> <line x1=\"8\" x2=\"8\" y1=\"9\" y2=\"13\" /> <line x1=\"15\" x2=\"15.01\" y1=\"12\" y2=\"12\" /> <line x1=\"18\" x2=\"18.01\" y1=\"10\" y2=\"10\" /> <path d=\"M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.01.101-.017.152C2.604 9.416 2 14.456 2 16a3 3 0 0 0 3 3c1 0 1.5-.5 2-1l1.414-1.414A2 2 0 0 1 9.828 16h4.344a2 2 0 0 1 1.414.586L17 18c.5.5 1 1 2 1a3 3 0 0 0 3-3c0-1.545-.604-6.584-.685-7.258-.007-.05-.011-.1-.017-.151A4 4 0 0 0 17.32 5z\" />","image-off":"<line x1=\"2\" x2=\"22\" y1=\"2\" y2=\"22\" /> <path d=\"M10.41 10.41a2 2 0 1 1-2.83-2.83\" /> <line x1=\"13.5\" x2=\"6\" y1=\"13.5\" y2=\"21\" /> <line x1=\"18\" x2=\"21\" y1=\"12\" y2=\"15\" /> <path d=\"M3.59 3.59A1.99 1.99 0 0 0 3 5v14a2 2 0 0 0 2 2h14c.55 0 1.052-.22 1.41-.59\" /> <path d=\"M21 15V5a2 2 0 0 0-2-2H9\" />","plus":"<path d=\"M5 12h14\" /> <path d=\"M12 5v14\" />","copy":"<rect width=\"14\" height=\"14\" x=\"8\" y=\"8\" rx=\"2\" ry=\"2\" /> <path d=\"M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2\" />","door-open":"<path d=\"M10 21H2\" /> <path d=\"M10 3H7a2 2 0 00-2 2v16\" /> <path d=\"M14 12h.01\" /> <path d=\"M19 21V5a2 2 0 00-1.675-1.974l-6.163-1.013A1 1 0 0010 3v18a1 1 0 001.124.992z\" /> <path d=\"M22 21h-3\" />","share-2":"<circle cx=\"18\" cy=\"5\" r=\"3\" /> <circle cx=\"6\" cy=\"12\" r=\"3\" /> <circle cx=\"18\" cy=\"19\" r=\"3\" /> <line x1=\"8.59\" x2=\"15.42\" y1=\"13.51\" y2=\"17.49\" /> <line x1=\"15.41\" x2=\"8.59\" y1=\"6.51\" y2=\"10.49\" />","check":"<path d=\"M20 6 9 17l-5-5\" />","alert":"<path d=\"m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3\" /> <path d=\"M12 9v4\" /> <path d=\"M12 17h.01\" />","x":"<path d=\"M18 6 6 18\" /> <path d=\"m6 6 12 12\" />","refresh":"<path d=\"M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8\" /> <path d=\"M21 3v5h-5\" /> <path d=\"M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16\" /> <path d=\"M8 16H3v5\" />","clock":"<circle cx=\"12\" cy=\"12\" r=\"10\" /> <polyline points=\"12 6 12 12 16 14\" />","upload":"<path d=\"M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4\" /> <polyline points=\"17 8 12 3 7 8\" /> <line x1=\"12\" x2=\"12\" y1=\"3\" y2=\"15\" />"};

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
    anime:  { label: 'Anime',  icon: 'sparkles',     shape: 'poster' },
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
  /* Finish variant (README, "Media finish"). The tilt reads the FNV-1a hash as it is (x % 1000 and bit 10). FNV-1a keeps the
     high bits of neighbouring ids equal, so the finish first runs the hash through a 32-bit mixer (murmur3 fmix32): bits 0-11
     pick about 1 item in 3, bits 12-15 the poster corner, bits 16-19 the poster peel size (14% to 20%).
     Returns null for the clean default and for games; the top-left corner is never used (it holds the sticker). */
  function mix(x) { x ^= x >>> 16; x = Math.imul(x, 0x85ebca6b); x ^= x >>> 13; x = Math.imul(x, 0xc2b2ae35); x ^= x >>> 16; return x >>> 0; }
  var VARIANT = { poster: 'peel', vinyl: 'worn', book: 'ribbon', manga: 'gloss' };
  var CORNERS = ['br', 'bl', 'tr'];
  function finish(id, category) {
    var kind = VARIANT[(CATEGORIES[category] || {}).shape];
    var x = mix(hash(id));
    if (!kind || (x & 4095) % 3 !== 0) return null;
    if (kind !== 'peel') return { kind: kind };
    return { kind: kind, corner: CORNERS[((x >>> 12) & 15) % 3], size: Math.round((14 + 6 * ((x >>> 16) & 15) / 15) * 10) / 10 };
  }

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
     showTag (default true), state ('hover' | 'focus' for documentation) */
  function HighlightCard(props) {
    var cat = CATEGORIES[props.category] || CATEGORIES.movie, shape = cat.shape;
    var loading = !!props.loading, error = !!props.error, empty = !props.cover && !loading && !error;
    var fin = loading || props.disabled ? null : finish(props.id, props.category);
    var cls = 'mq-item mq-item--' + shape + (shape === 'book' ? ' mq-item--volume' : '')
      + (fin ? ' mq-item--' + (fin.kind === 'peel' ? 'peel mq-item--peel-' + fin.corner : fin.kind) : '')
      + (loading ? ' mq-item--loading' : '') + (props.disabled ? ' is-disabled' : '')
      + (props.state === 'hover' ? ' is-hover' : '');
    var style = { '--tilt-f': loading ? 0 : tiltFactor(props.id) };
    if (fin && fin.kind === 'peel') style['--peel-size'] = fin.size + '%';

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
      fin && fin.kind === 'ribbon' ? h('span', { className: 'mq-ribbon', 'aria-hidden': true }) : null,
      art,
      decorate && props.showTag !== false ? h(CategoryTag, { category: props.category }) : null,
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

  /* Button: variant 'primary' | 'secondary' | 'ghost' | 'danger'; size 'md' | 'sm'; icon (name); iconOnly needs aria-label;
     loading keeps the width, shows a spinner and blocks clicks; href renders a link. state 'hover' | 'focus' is for documentation. */
  function Button(props) {
    var v = props.variant || 'primary', loading = !!props.loading, disabled = !!props.disabled || loading;
    var cls = 'mq-btn mq-btn--' + v + (props.size === 'sm' ? ' mq-btn--sm' : '') + (props.iconOnly ? ' mq-btn--icon' : '')
      + (loading ? ' is-loading' : '') + (props.state === 'hover' ? ' is-hover' : '') + (props.state === 'focus' ? ' is-focus' : '')
      + (props.className ? ' ' + props.className : '');
    var content = [
      props.icon ? h(Icon, { key: 'i', name: props.icon }) : null,
      props.iconOnly ? null : props.children,
      loading ? h('span', { key: 's', className: 'mq-spinner', 'aria-hidden': true }) : null
    ];
    var common = { className: cls, 'aria-label': props['aria-label'], 'aria-busy': loading || undefined, onClick: disabled ? undefined : props.onClick };
    if (props.href && !disabled) return h('a', Object.assign({ href: props.href }, common), content);
    return h('button', Object.assign({ type: props.type || 'button', disabled: disabled }, common), content);
  }

  /* NowCard: one "Agora" row. state 'default' | 'empty' | 'loading' | 'error'. The lamp shows only for synced or pinned items. */
  function NowCard(props) {
    var s = props.state || 'default';
    var cls = 'mq-now' + (props.card ? ' mq-now--card' : '') + (s === 'empty' ? ' mq-now--empty' : '') + (s === 'loading' ? ' mq-now--loading' : '');
    var thumbCls = 'mq-now__thumb' + (props.square ? ' mq-now__thumb--sq' : '');
    if (s === 'loading') return h('div', { className: cls, 'aria-busy': true },
      h('span', { className: thumbCls, 'aria-hidden': true }),
      h('div', { className: 'mq-now__body', 'aria-hidden': true }, h('span', { className: 'mq-sk', style: { width: '40%', height: '10px' } }), h('span', { className: 'mq-sk', style: { width: '70%' } })),
      h('span', { className: 'mq-sr' }, 'Carregando'));
    var thumb = s === 'empty' || !props.cover ? h('span', { className: thumbCls, 'aria-hidden': true }) : h('img', { className: thumbCls, src: props.cover, alt: '' });
    var status = s === 'error'
      ? h('p', { className: 'mq-now__status mq-now__status--error' }, h(Icon, { name: 'alert' }), h('span', null, h('strong', null, 'Erro. '), props.status))
      : (props.status ? h('p', { className: 'mq-now__status' }, props.status) : null);
    return h('div', { className: cls }, thumb,
      h('div', { className: 'mq-now__body' },
        h('p', { className: 'mq-now__kicker' }, s !== 'empty' && props.lamp !== false ? h('span', { className: 'mq-now__lamp', 'aria-hidden': true }) : null, props.verb),
        h('p', { className: 'mq-now__title' }, s === 'empty' ? (props.title || 'Nada por aqui agora') : props.title),
        status));
  }

  /* Toast: tone 'success' | 'error'. Starts with a bold status word (title). Success uses role=status and leaves after 5 s;
     error uses role=alert, stays, and has a close button. No other actions inside. */
  function Toast(props) {
    var err = props.tone === 'error';
    return h('div', { className: 'mq-toast' + (err ? ' mq-toast--error' : ''), role: err ? 'alert' : 'status' },
      h(Icon, { name: err ? 'alert' : 'check' }),
      h('p', { className: 'mq-toast__text' }, h('strong', null, props.title || (err ? 'Erro.' : 'Pronto.')), ' ', props.children),
      err && props.onClose !== null ? h('button', { type: 'button', className: 'mq-toast__close', 'aria-label': 'Fechar aviso', onClick: props.onClose }, h(Icon, { name: 'x' })) : null);
  }

  /* LimitUpsell: used of limit slots, a title, one sentence on what the pack gives, and the link to the store. */
  function LimitUpsell(props) {
    var used = props.used == null ? 4 : props.used, limit = props.limit || 4, bars = [];
    for (var i = 0; i < limit; i++) bars.push(h('span', { key: i, className: i < used ? '' : 'is-free' }));
    return h('div', { className: 'mq-upsell' },
      h('p', { className: 'mq-upsell__text' }, h('strong', null, props.title || 'Lista cheia'), props.children),
      h('div', { className: 'mq-upsell__side' },
        h('p', { className: 'mq-slots' }, h('span', { className: 'mq-slots__bar', 'aria-hidden': true }, bars), used + ' de ' + limit),
        h(Button, { variant: 'primary', href: props.href || '#' }, props.cta || 'Ver o pack')));
  }

  window.Meuquarto = {
    Button: Button, NowCard: NowCard, Toast: Toast, LimitUpsell: LimitUpsell,
    HighlightCard: HighlightCard, HighlightSlot: HighlightSlot, HighlightGrid: HighlightGrid, CategoryTag: CategoryTag, Icon: Icon,
    CATEGORIES: CATEGORIES, tiltFactor: tiltFactor, finish: finish, hash: hash
  };
})();
