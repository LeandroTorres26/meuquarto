/* S17 Detalhes do item. Data and markup shared by perfil.html (dialog) and quarto.html (room panel).
   In the app the item comes from the API and the links are the stored external URLs; here the data is a fixed
   table and the links are platform searches. State lives in the URL: ?item=<id>, plus &estado=carregando to see
   the loading slot. An id that is not in the table shows the "saiu do quarto" state. */
(function () {
  var OWNER = 'Leandro';
  var CAT = { movie: ['Filme', 'clapperboard', 'poster'], series: ['Série', 'tv', 'poster'], anime: ['Anime', 'sparkles', 'dvd'], manga: ['Mangá', 'book-copy', 'manga'], book: ['Livro', 'book-open', 'book'], game: ['Game', 'gamepad-2', 'game'], music: ['Música', 'disc-3', 'vinyl'] };
  /* [activity platform, only for synced items; catalog] */
  var SRC = { movie: ['Letterboxd', 'TMDB'], series: [null, 'TMDB'], anime: ['AniList', 'AniList'], manga: ['AniList', 'AniList'], book: ['Goodreads', 'Open Library'], game: ['Steam', 'IGDB'], music: ['Last.fm', 'Deezer'] };
  var PLAT = {
    'Letterboxd': ['no', function (q, c) { return 'https://letterboxd.com/search/' + q + '/'; }],
    'TMDB': ['no', function (q) { return 'https://www.themoviedb.org/search?query=' + q; }],
    'AniList': ['no', function (q, c) { return 'https://anilist.co/search/' + (c === 'manga' ? 'manga' : 'anime') + '?search=' + q; }],
    'Goodreads': ['no', function (q) { return 'https://www.goodreads.com/search?q=' + q; }],
    'Open Library': ['na', function (q) { return 'https://openlibrary.org/search?q=' + q; }],
    'Steam': ['na', function (q) { return 'https://store.steampowered.com/search/?term=' + q; }],
    'IGDB': ['no', function (q) { return 'https://www.igdb.com/search?q=' + q; }],
    'Last.fm': ['no', function (q) { return 'https://www.last.fm/search?q=' + q; }],
    'Deezer': ['na', function (q) { return 'https://www.deezer.com/search/' + q; }]
  };
  /* id: [category, title, year, subtitle, cover, tilt factor, tape or pin, owner note, origin ('' = chosen by hand)] */
  var ROWS = {
    'perfect-days': ['movie', 'Perfect Days', 2023, 'de Wim Wenders', 1, 0.883, 't', 'assisti 3 vezes. o final no carro me pega toda vez', 'Assistido em 11 out. 2026 no Letterboxd'],
    'frieren': ['anime', 'Frieren', 2023, 'estúdio Madhouse', 12, 0.516, 'p', '', 'Assistindo agora no AniList'],
    'clube-da-esquina': ['music', 'Clube da Esquina', 1972, 'de Milton Nascimento e Lô Borges', 11, -0.88, 'p', 'repeat o mês todo', 'Ouvindo agora no Last.fm'],
    'outer-wilds': ['game', 'Outer Wilds', 2019, 'da Mobius Digital', 3, -0.844, 't', '', ''],
    'paris-texas': ['movie', 'Paris, Texas', 1984, 'de Wim Wenders', 9, -0.373, 't', '', 'Assistido em 20 set. 2026 no Letterboxd'],
    'central-do-brasil': ['movie', 'Central do Brasil', 1998, 'de Walter Salles', 2, -0.618, 'p', 'chorei no final', ''],
    'aftersun': ['movie', 'Aftersun', 2022, 'de Charlotte Wells', 5, -0.987, 'p', '', 'Assistido em 2 ago. 2026 no Letterboxd'],
    'cidade-de-deus': ['movie', 'Cidade de Deus', 2002, 'de Fernando Meirelles e Kátia Lund', 4, -0.682, 't', '', ''],
    'twin-peaks': ['series', 'Twin Peaks', 1990, 'de David Lynch e Mark Frost', 7, -0.603, 'p', '', ''],
    'sintonia': ['series', 'Sintonia', 2019, 'de KondZilla', 3, 0.592, 't', '', ''],
    'familia-soprano': ['series', 'Família Soprano', 1999, 'de David Chase', 6, 0.545, 'p', '', ''],
    'fleabag': ['series', 'Fleabag', 2016, 'de Phoebe Waller-Bridge', 9, -0.833, 'p', 'temporada 2', ''],
    'cowboy-bebop': ['anime', 'Cowboy Bebop', 1998, 'estúdio Sunrise', 7, 0.426, 't', 'see you space cowboy', 'Assistido em 14 jul. 2026 no AniList'],
    'mob-psycho-100': ['anime', 'Mob Psycho 100', 2016, 'estúdio Bones', 10, -0.381, 'p', '', ''],
    'neon-genesis-evangelion': ['anime', 'Neon Genesis Evangelion', 1995, 'estúdio Gainax', 4, 0.657, 'p', '', 'Assistido em 5 mar. 2026 no AniList'],
    'mushishi': ['anime', 'Mushishi', 2005, 'estúdio Artland', 5, 0.36, 'p', '', ''],
    'vagabond': ['manga', 'Vagabond', 1998, 'vol. 12, de Takehiko Inoue', 5, -0.597, 't', '', 'Lido em 9 out. 2026 no AniList'],
    'oyasumi-punpun': ['manga', 'Oyasumi Punpun', 2007, 'vol. 1, de Inio Asano', 2, -0.491, 't', '', ''],
    'monster': ['manga', 'Monster', 1994, 'vol. 3, de Naoki Urasawa', 3, -0.637, 'p', '', 'Lido em 1 set. 2026 no AniList'],
    'yotsuba': ['manga', 'Yotsuba&!', 2003, 'vol. 5, de Kiyohiko Azuma', 9, 0.691, 'p', '', ''],
    'torto-arado': ['book', 'Torto Arado', 2019, 'de Itamar Vieira Junior', 10, -0.996, 'p', '', 'Lendo agora no Goodreads'],
    'grande-sertao-veredas': ['book', 'Grande Sertão: Veredas', 1956, 'de João Guimarães Rosa', 9, -0.804, 't', 'nonada', ''],
    'dom-casmurro': ['book', 'Dom Casmurro', 1899, 'de Machado de Assis', 6, -0.953, 'p', '', 'Lido em 18 jun. 2026 no Goodreads'],
    'a-hora-da-estrela': ['book', 'A Hora da Estrela', 1977, 'de Clarice Lispector', 12, -0.533, 'p', '', ''],
    'elis-e-tom': ['music', 'Elis & Tom', 1974, 'de Elis Regina e Tom Jobim', 6, 0.619, 'p', '', 'Ouvido em 12 out. 2026 no Last.fm'],
    'sobrevivendo-no-inferno': ['music', 'Sobrevivendo no Inferno', 1997, 'dos Racionais MC\'s', 4, -0.532, 'p', '', ''],
    'acabou-chorare': ['music', 'Acabou Chorare', 1972, 'dos Novos Baianos', 2, -0.521, 'p', '', 'Ouvido em 30 set. 2026 no Last.fm'],
    'da-lama-ao-caos': ['music', 'Da Lama ao Caos', 1994, 'de Chico Science & Nação Zumbi', 10, -0.832, 't', '', ''],
    'hollow-knight': ['game', 'Hollow Knight', 2017, 'da Team Cherry', 5, -0.374, 't', '', 'Jogado em 22 ago. 2026 na Steam'],
    'celeste': ['game', 'Celeste', 2018, 'da Maddy Makes Games', 1, 0.685, 't', 'todos os morangos', 'Jogado em 4 out. 2026 na Steam'],
    'disco-elysium': ['game', 'Disco Elysium', 2019, 'da ZA/UM', 7, -0.518, 'p', '', ''],
    'hades': ['game', 'Hades', 2020, 'da Supergiant Games', 12, -0.366, 'p', '', '']
  };

  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  function icon(n) { return '<svg class="icon" aria-hidden="true"><use href="icons.svg#' + n + '"/></svg>'; }
  function cover(n) { return 'covers/c' + String(n).padStart(2, '0') + '.svg'; }

  function get(id) {
    var r = ROWS[id]; if (!r) return null;
    return { id: id, cat: r[0], title: r[1], year: r[2], sub: r[3], cover: cover(r[4]), tilt: r[5], fix: r[6], note: r[7], origin: r[8] };
  }

  /* cover in the media's own shape; the only place with tilt, sticker and tape or pin */
  function coverHtml(it, loading, preview) {
    var c = CAT[it.cat], shape = c[2];
    var cls = shape === 'book' || shape === 'manga' ? 'mq-item--' + shape + ' mq-item--volume' : 'mq-item--' + shape;
    var lead = { dvd: '<span class="mq-spine" aria-hidden="true"></span>', vinyl: '<span class="mq-record" aria-hidden="true"></span>', game: '<span class="mq-gamebar" aria-hidden="true"><svg class="mq-icon icon"><use href="icons.svg#gamepad-2"/></svg></span>' }[shape] || '';
    var inner = loading
      ? (shape === 'vinyl' ? '' : lead) + '<span class="mq-art"></span>'
      : lead + '<img class="mq-art" src="' + it.cover + '" alt="">' +
        '<span class="mq-tag mq-tag--' + it.cat + '">' + icon(c[1]).replace('class="icon"', 'class="mq-icon icon"') + c[0] + '</span>' +
        (it.fix === 't' ? '<span class="mq-tape" aria-hidden="true"></span>' : '<span class="mq-pin" aria-hidden="true"></span>');
    /* music: the 30s Deezer preview plays from a button on the sleeve itself */
    var play = shape === 'vinyl' && !loading && preview
      ? '<button type="button" class="detail__play" data-preview aria-label="Ouvir prévia de 30 segundos de ' + esc(it.title) + '">' +
        '<svg class="detail__ring" viewBox="0 0 56 56" aria-hidden="true"><circle cx="28" cy="28" r="26"/></svg>' + icon('play') + '</button>'
      : '';
    return '<div class="detail__cover' + (shape === 'vinyl' ? ' detail__cover--wide' : '') + '"' + (loading ? ' aria-hidden="true"' : '') + '>' +
      '<div class="mq-item ' + cls + (loading ? ' mq-item--loading' : '') + '" style="--tilt-f:' + (loading ? 0 : it.tilt) + '"><figure class="mq-item__cover">' + inner + '</figure>' + play + '</div>' +
      (play ? '<p class="t-cap detail__credit">Prévia de 30 segundos, pela Deezer.</p>' : '') + '</div>';
  }

  function links(it) {
    var src = SRC[it.cat], out = [], q = encodeURIComponent(it.title);
    if (it.origin && src[0]) out.push(src[0]);
    if (out.indexOf(src[1]) < 0) out.push(src[1]);
    return out.map(function (p) {
      return '<a class="mq-btn mq-btn--secondary" href="' + PLAT[p][1](q, it.cat) + '" target="_blank" rel="noopener noreferrer">Ver ' + PLAT[p][0] + ' ' + p + icon('external-link') + '<span class="sr"> (abre em nova aba)</span></a>';
    }).join('');
  }

  /* opts.h: heading tag; opts.hid: heading id; opts.preview: false hides the music preview; opts.back: label of the gone state's button */
  function body(it, opts) {
    opts = opts || {};
    var h = opts.h || 'h2', hid = opts.hid || 'item-h';
    return '<article class="detail">' + coverHtml(it, false, opts.preview !== false) +
      '<div class="detail__info">' +
        '<div class="detail__head"><' + h + ' id="' + hid + '" class="detail__title">' + esc(it.title) + '</' + h + '><p class="detail__meta">' + it.year + (it.sub ? ', ' + esc(it.sub) : '') + '</p></div>' +
        '<div class="detail__owner">' +
          (it.note ? '<p class="detail__note"><span class="sr">Nota de ' + OWNER + ': </span>' + esc(it.note) + '</p>' : '') +
          '<p class="t-cap">' + (it.origin || 'Escolhido à mão') + '</p>' +
        '</div>' +
        '<div class="detail__links">' + links(it) + '</div>' +
      '</div></article>';
  }

  /* loading: the slot keeps the real proportion of the medium, known from the card that was clicked */
  function loading(it, opts) {
    opts = opts || {};
    var h = opts.h || 'h2', hid = opts.hid || 'item-h';
    return '<article class="detail detail--loading" aria-busy="true">' + coverHtml(it, true) +
      '<div class="detail__info"><div class="detail__head"><' + h + ' id="' + hid + '" class="sr">Carregando item</' + h + '><span class="mq-sk is-title"></span><span class="mq-sk" style="width:45%"></span></div>' +
      '<span class="mq-sk" style="width:60%"></span><div class="detail__actions"><span class="mq-sk is-btn"></span></div></div></article>';
  }

  function gone(opts) {
    opts = opts || {};
    var h = opts.h || 'h2', hid = opts.hid || 'item-h';
    return '<div class="detail-gone">' + icon('image-off') + '<div class="pair-2"><' + h + ' id="' + hid + '" class="t-h3">Esse item saiu do quarto.</' + h + '><p class="t-sm">' + OWNER + ' tirou ele dos destaques.</p></div>' +
      '<button type="button" class="mq-btn mq-btn--secondary" data-close>' + (opts.back || 'Voltar ao perfil') + '</button></div>';
  }

  /* picks the right state for an id: ?estado=carregando, missing item, or the item */
  function render(id, opts) {
    var it = get(id), est = new URLSearchParams(location.search).get('estado');
    if (!it) return gone(opts);
    return est === 'carregando' ? loading(it, opts) : body(it, opts);
  }

  /* music preview: no audio in the prototype; the record spins out and the ring fills in 30s, then it stops */
  function setPlaying(b, on) {
    var item = b.closest('.mq-item');
    item.classList.toggle('is-playing', on);
    b.setAttribute('aria-label', (on ? 'Pausar prévia de ' : 'Ouvir prévia de 30 segundos de ') + item.closest('.detail').querySelector('.detail__title').textContent);
    b.querySelector('use').setAttribute('href', 'icons.svg#' + (on ? 'pause' : 'play'));
  }
  document.addEventListener('click', function (e) {
    var b = e.target.closest('[data-preview]'); if (b) setPlaying(b, !b.closest('.mq-item').classList.contains('is-playing'));
  });
  document.addEventListener('animationend', function (e) {
    if (e.target.classList && e.target.classList.contains('detail__ring')) setPlaying(e.target.closest('.detail__play'), false);
  }, true);
  window.MQItem = { get: get, render: render, body: body, loading: loading, gone: gone };
})();
