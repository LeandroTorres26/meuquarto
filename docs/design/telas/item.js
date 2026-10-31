/* S17 Detalhes do item. Data and markup shared by perfil.html (dialog) and quarto.html (room panel).
   In the app the item comes from the API and the links are the stored external URLs; here the data is a fixed
   table and the links are platform searches. State lives in the URL: ?item=<id>, plus &estado=carregando to see
   the loading slot. An id that is not in the table shows the "saiu do quarto" state. */
(function () {
  var OWNER = 'Leandro';
  var CAT = { movie: ['Filme', 'clapperboard', 'poster'], series: ['Série', 'tv', 'poster'], anime: ['Anime', 'sparkles', 'poster'], manga: ['Mangá', 'book-copy', 'manga'], book: ['Livro', 'book-open', 'book'], game: ['Game', 'gamepad-2', 'game'], music: ['Música', 'disc-3', 'vinyl'] };
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
  /* id: [category, title, year, subtitle, cover, tilt factor, former tape or pin (unused), owner note, origin ('' = chosen by hand)] */
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

  /* ---------- collection: every item of a category (sync + manual, no limit); highlights are the items marked in it ----------
     Order = most recent first (the "Recentes" sort). Items not on the profile get their tilt and cover from their id,
     and an origin from their place in the list (series are always by hand). */
  function slug(t) { return t.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/&/g, 'e').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, ''); }
  function hash(s) { var h = 2166136261; for (var i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return h >>> 0; }
  var MONTHS = ['jan.', 'fev.', 'mar.', 'abr.', 'maio', 'jun.', 'jul.', 'ago.', 'set.', 'out.', 'nov.', 'dez.'];
  var VERB = { movie: 'Assistido', anime: 'Assistido', manga: 'Lido', book: 'Lido', game: 'Jogado', music: 'Ouvido' };
  var EXTRA = {
    movie: [['Ainda Estou Aqui', 2024, 'de Walter Salles'], ['Retratos Fantasmas', 2023, 'de Kleber Mendonça Filho'], ['Past Lives', 2023, 'de Celine Song'], ['Bacurau', 2019, 'de Kleber Mendonça Filho e Juliano Dornelles'], ['Drive My Car', 2021, 'de Ryusuke Hamaguchi'], ['Asteroid City', 2023, 'de Wes Anderson'], ['Parasita', 2019, 'de Bong Joon-ho'], ['Moonlight', 2016, 'de Barry Jenkins'], ['Que Horas Ela Volta?', 2015, 'de Anna Muylaert'], ['O Som ao Redor', 2012, 'de Kleber Mendonça Filho'], ['Amor à Flor da Pele', 2000, 'de Wong Kar-wai']],
    series: [['The Bear', 2022, 'de Christopher Storer'], ['Severance', 2022, 'de Dan Erickson'], ['Succession', 2018, 'de Jesse Armstrong'], ['Cidade Invisível', 2021, 'de Carlos Saldanha']],
    anime: [['Dandadan', 2024, 'estúdio Science SARU'], ['Bocchi the Rock!', 2022, 'estúdio CloverWorks'], ['Chainsaw Man', 2022, 'estúdio MAPPA'], ['Odd Taxi', 2021, 'estúdio OLM'], ['Sonny Boy', 2021, 'estúdio Madhouse'], ['Spy x Family', 2022, 'estúdio Wit e CloverWorks'], ['Vinland Saga', 2019, 'estúdio Wit'], ['Made in Abyss', 2017, 'estúdio Kinema Citrus'], ['March Comes in Like a Lion', 2016, 'estúdio Shaft'], ['Erased', 2016, 'estúdio A-1 Pictures'], ['Haikyu!!', 2014, 'estúdio Production I.G'], ['Ping Pong the Animation', 2014, 'estúdio Tatsunoko'], ['Hunter x Hunter', 2011, 'estúdio Madhouse'], ['Steins;Gate', 2011, 'estúdio White Fox'], ['The Tatami Galaxy', 2010, 'estúdio Madhouse'], ['Fullmetal Alchemist: Brotherhood', 2009, 'estúdio Bones'], ['Monogatari', 2009, 'estúdio Shaft'], ['Kaiji', 2007, 'estúdio Madhouse'], ['Samurai Champloo', 2004, 'estúdio Manglobe']],
    manga: [],
    book: [['O Avesso da Pele', 2020, 'de Jeferson Tenório'], ['Ensaio sobre a Cegueira', 1995, 'de José Saramago'], ['Cem Anos de Solidão', 1967, 'de Gabriel García Márquez'], ['A Paixão segundo G.H.', 1964, 'de Clarice Lispector'], ['Quarto de Despejo', 1960, 'de Carolina Maria de Jesus'], ['Vidas Secas', 1938, 'de Graciliano Ramos'], ['Capitães da Areia', 1937, 'de Jorge Amado'], ['Memórias Póstumas de Brás Cubas', 1881, 'de Machado de Assis']],
    music: [['AmarElo', 2019, 'de Emicida'], ['Bluesman', 2018, 'de Baco Exu do Blues'], ['Cartola', 1976, 'de Cartola'], ['Racional', 1975, 'de Tim Maia'], ['A Tábua de Esmeralda', 1974, 'de Jorge Ben'], ['Loki?', 1974, 'de Arnaldo Baptista'], ['Krig-ha, Bandolo!', 1973, 'de Raul Seixas'], ['Secos & Molhados', 1973, 'dos Secos & Molhados'], ['Transa', 1972, 'de Caetano Veloso'], ['Construção', 1971, 'de Chico Buarque'], ['Tropicália ou Panis et Circencis', 1968, 'de vários artistas']],
    game: [['Baldur\'s Gate 3', 2023, 'da Larian Studios'], ['Tunic', 2022, 'de Andrew Shouldice'], ['Inscryption', 2021, 'da Daniel Mullins Games'], ['Return of the Obra Dinn', 2018, 'de Lucas Pope'], ['The Legend of Zelda: Breath of the Wild', 2017, 'da Nintendo'], ['Stardew Valley', 2016, 'da ConcernedApe'], ['Undertale', 2015, 'de Toby Fox']]
  };
  /* category pages: slug, category, plural label, unit for counts ("24 animes"), profile highlights in wall order */
  var CATS = [
    { slug: 'filmes', cat: 'movie', label: 'Filmes', unit: ['filme', 'filmes'], hl: ['paris-texas', 'central-do-brasil', 'aftersun', 'cidade-de-deus'] },
    { slug: 'series', cat: 'series', label: 'Séries', unit: ['série', 'séries'], hl: ['twin-peaks', 'sintonia', 'familia-soprano', 'fleabag'] },
    { slug: 'animes', cat: 'anime', label: 'Animes', unit: ['anime', 'animes'], hl: ['cowboy-bebop', 'mob-psycho-100', 'neon-genesis-evangelion', 'mushishi'] },
    { slug: 'mangas', cat: 'manga', label: 'Mangás', unit: ['mangá', 'mangás'], hl: ['vagabond', 'oyasumi-punpun', 'monster', 'yotsuba'] },
    { slug: 'livros', cat: 'book', label: 'Livros', unit: ['livro', 'livros'], hl: ['torto-arado', 'grande-sertao-veredas', 'dom-casmurro', 'a-hora-da-estrela'] },
    { slug: 'musica', cat: 'music', label: 'Música', unit: ['álbum', 'álbuns'], hl: ['elis-e-tom', 'sobrevivendo-no-inferno', 'acabou-chorare', 'da-lama-ao-caos'] },
    { slug: 'games', cat: 'game', label: 'Games', unit: ['game', 'games'], hl: ['hollow-knight', 'celeste', 'disco-elysium', 'hades'] }
  ];
  var FAVORITES = ['perfect-days', 'frieren', 'clube-da-esquina', 'outer-wilds'];
  var COLL = {};
  (function () {
    var today = new Date(2026, 9, 13);
    CATS.forEach(function (c) {
      var mine = Object.keys(ROWS).filter(function (id) { return ROWS[id][0] === c.cat; });
      var extra = EXTRA[c.cat].map(function (e) { return slug(e[0]); });
      /* interleave: the profile's items sit among the others, so "Recentes" is not just the highlights first */
      var list = [], a = mine.slice(), b = extra.slice();
      while (a.length || b.length) { if (b.length) list.push(b.shift()); if (a.length) list.push(a.shift()); if (b.length && list.length % 3 === 0) list.push(b.shift()); }
      EXTRA[c.cat].forEach(function (e, i) {
        var id = slug(e[0]), h = hash(id), pos = list.indexOf(id), synced = SRC[c.cat][0] && i % 3 !== 2;
        var d = new Date(today); d.setDate(d.getDate() - 4 - pos * 9);
        var origin = synced ? VERB[c.cat] + ' em ' + d.getDate() + ' ' + MONTHS[d.getMonth()] + ' ' + d.getFullYear() + (SRC[c.cat][0] === 'Steam' ? ' na ' : ' no ') + SRC[c.cat][0] : '';
        var cv = [1, 2, 3, 4, 5, 6, 7, 9, 10, 11, 12][h % 11], t = ((h >>> 8) % 1000) / 1000 * 2 - 1;
        ROWS[id] = [c.cat, e[0], e[1], e[2], cv, (t < 0 ? -1 : 1) * Math.max(0.36, Math.abs(t)), (h >>> 4) % 2 ? 't' : 'p', '', origin];
      });
      COLL[c.slug] = list;
    });
  })();
  function category(s) { return CATS.filter(function (c) { return c.slug === s; })[0] || null; }

  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;'); }
  function icon(n) { return '<svg class="icon" aria-hidden="true"><use href="icons.svg#' + n + '"/></svg>'; }
  function cover(n) { return 'covers/c' + String(n).padStart(2, '0') + '.svg'; }

  function get(id) {
    var r = ROWS[id]; if (!r) return null;
    return { id: id, cat: r[0], title: r[1], year: r[2], sub: r[3], cover: cover(r[4]), tilt: r[5], note: r[7], origin: r[8] };
  }

  /* Finish variant of the work (design system README, "Media finish"): same FNV-1a hash as the tilt, mixed (fmix32) so neighbouring ids differ.
     Mirrors Meuquarto.finish in docs/design-system/components/bundle.js; the prototype passes the shape, not the category. */
  function hash(id) { var s = String(id), x = 2166136261; for (var i = 0; i < s.length; i++) { x ^= s.charCodeAt(i); x = Math.imul(x, 16777619) >>> 0; } return x >>> 0; }
  function mix(x) { x ^= x >>> 16; x = Math.imul(x, 0x85ebca6b); x ^= x >>> 13; x = Math.imul(x, 0xc2b2ae35); x ^= x >>> 16; return x >>> 0; }
  var VARIANT = { poster: 'peel', vinyl: 'worn', book: 'ribbon', manga: 'gloss' }, CORNERS = ['br', 'bl', 'tr'];
  function finish(id, shape) {
    var kind = VARIANT[shape], x = mix(hash(id));
    if (!kind || (x & 4095) % 3 !== 0) return null;
    if (kind !== 'peel') return { kind: kind };
    return { kind: kind, corner: CORNERS[((x >>> 12) & 15) % 3], size: Math.round((14 + 6 * ((x >>> 16) & 15) / 15) * 10) / 10 };
  }
  /* classes and inline style that carry the variant; the ribbon is the only extra element */
  function finishAttrs(fin) {
    if (!fin) return { cls: '', style: '', lead: '' };
    if (fin.kind === 'peel') return { cls: ' mq-item--peel mq-item--peel-' + fin.corner, style: ';--peel-size:' + fin.size + '%', lead: '' };
    return { cls: ' mq-item--' + fin.kind, style: '', lead: fin.kind === 'ribbon' ? '<span class="mq-ribbon" aria-hidden="true"></span>' : '' };
  }

  /* cover in the media's own shape; the only place with tilt, sticker and the medium's finish variant */
  function coverHtml(it, loading, preview) {
    var c = CAT[it.cat], shape = c[2];
    var cls = shape === 'book' ? 'mq-item--' + shape + ' mq-item--volume' : 'mq-item--' + shape;
    var fa = finishAttrs(loading ? null : finish(it.id, shape));
    var lead = { vinyl: '<span class="mq-record" aria-hidden="true"></span>', game: '<span class="mq-gamebar" aria-hidden="true"><svg class="mq-icon icon"><use href="icons.svg#gamepad-2"/></svg></span>' }[shape] || '';
    lead += fa.lead;
    var inner = loading
      ? (shape === 'vinyl' ? '' : lead) + '<span class="mq-art"></span>'
      : lead + '<img class="mq-art" src="' + it.cover + '" alt="">' +
        '<span class="mq-tag mq-tag--' + it.cat + '">' + icon(c[1]).replace('class="icon"', 'class="mq-icon icon"') + c[0] + '</span>';
    /* music: the 30s Deezer preview plays from a button on the sleeve itself */
    var play = shape === 'vinyl' && !loading && preview
      ? '<button type="button" class="detail__play" data-preview aria-label="Ouvir prévia de 30 segundos de ' + esc(it.title) + '">' +
        '<svg class="detail__ring" viewBox="0 0 56 56" aria-hidden="true"><circle cx="28" cy="28" r="26"/></svg>' + icon('play') + '</button>'
      : '';
    return '<div class="detail__cover' + (shape === 'vinyl' ? ' detail__cover--wide' : '') + '"' + (loading ? ' aria-hidden="true"' : '') + '>' +
      '<div class="mq-item ' + cls + fa.cls + (loading ? ' mq-item--loading' : '') + '" style="--tilt-f:' + (loading ? 0 : it.tilt) + fa.style + '"><figure class="mq-item__cover">' + inner + '</figure>' + play + '</div>' +
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
  /* a HighlightCard (static markup) for grids: the shape of the medium, finish variant from the id, optional sticker and "Destaque" seal */
  function card(id, opts) {
    opts = opts || {};
    var it = get(id), c = CAT[it.cat], shape = c[2];
    var cls = shape === 'book' ? 'mq-item--' + shape + ' mq-item--volume' : 'mq-item--' + shape;
    var fa = finishAttrs(finish(it.id, shape));
    var lead = { vinyl: '<span class="mq-record" aria-hidden="true"></span>', game: '<span class="mq-gamebar" aria-hidden="true"><svg class="mq-icon icon"><use href="icons.svg#gamepad-2"/></svg></span>' }[shape] || '';
    lead += fa.lead;
    var hl = opts.highlight ? '<span class="hl-seal"><svg class="icon" aria-hidden="true"><use href="icons.svg#star"/></svg>Destaque</span>' : '';
    return '<article class="mq-item ' + cls + fa.cls + '" style="--tilt-f:' + it.tilt + fa.style + '"><a class="mq-item__link" href="' + (opts.href || '?item=' + id) + '" aria-label="' + esc(it.title) + ', ' + it.year + (opts.highlight ? ', destaque' : '') + '">' +
      '<figure class="mq-item__cover">' + lead + '<img class="mq-art" src="' + it.cover + '" alt="" loading="lazy">' +
      (opts.tag ? '<span class="mq-tag mq-tag--' + it.cat + '">' + icon(c[1]).replace('class="icon"', 'class="mq-icon icon"') + c[0] + '</span>' : '') +
      '</figure>' +
      '<div class="mq-item__meta"><h3 class="mq-item__title">' + esc(it.title) + '</h3><p class="mq-item__sub">' + it.year + '</p>' + hl + '</div></a></article>';
  }

  window.MQItem = { get: get, render: render, body: body, loading: loading, gone: gone, card: card, slug: slug,
    cats: CATS, category: category, favorites: FAVORITES, platform: function (cat) { return SRC[cat]; },
    collection: function (s) { return (COLL[s] || []).slice(); },
    isHighlight: function (s, id) { var c = category(s); return !!c && c.hl.indexOf(id) >= 0; } };
})();
