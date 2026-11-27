/* S17 Detalhes do item. Data and markup shared by perfil.html (dialog) and quarto.html (room panel).
   In the app the item comes from the API and the links are built from the stored ids; here the data is a fixed
   table of works the owner has finished (in-progress ones only where the page means "now"), and the ids below are real
   (checked on each platform on 2026-11-27). A link always opens the work's own page,
   never a search: a platform without an id for the work simply has no link. State lives in the URL: ?item=<id>, plus &estado=carregando to see
   the loading slot. An id that is not in the table shows the "saiu do quarto" state. */
(function () {
  var OWNER = 'Leandro';
  var CAT = { movie: ['Filme', 'clapperboard', 'poster'], series: ['Série', 'tv', 'poster'], anime: ['Anime', 'sparkles', 'poster'], manga: ['Mangá', 'book-copy', 'manga'], book: ['Livro', 'book-open', 'book'], game: ['Game', 'gamepad-2', 'game'], music: ['Música', 'disc-3', 'vinyl'] };
  /* [activity platform, only for synced items; catalog] */
  var SRC = { movie: ['Letterboxd', 'TMDB'], series: [null, 'TMDB'], anime: ['AniList', 'AniList'], manga: ['AniList', 'AniList'], book: ['Goodreads', 'Open Library'], game: ['Steam', 'IGDB'], music: ['Last.fm', 'Deezer'] };
  /* platform: [preposition, page of the work from its ids or null]. Letterboxd has no ids of its own here: letterboxd.com/tmdb/{id}
     redirects to the film. MyAnimeList ids come from AniList (idMal). Last.fm pages are built from the artist and album names. */
  var PLAT = {
    'Letterboxd': ['no', function (x) { return x.tmdb ? 'https://letterboxd.com/tmdb/' + x.tmdb + '/' : null; }],
    'TMDB': ['no', function (x, c) { return x.tmdb ? 'https://www.themoviedb.org/' + (c === 'series' ? 'tv' : 'movie') + '/' + x.tmdb : null; }],
    'AniList': ['no', function (x, c) { return x.anilist ? 'https://anilist.co/' + (c === 'manga' ? 'manga' : 'anime') + '/' + x.anilist : null; }],
    'MyAnimeList': ['no', function (x, c) { return x.mal ? 'https://myanimelist.net/' + (c === 'manga' ? 'manga' : 'anime') + '/' + x.mal : null; }],
    'Open Library': ['na', function (x) { return x.ol ? 'https://openlibrary.org/works/' + x.ol : null; }],
    'Steam': ['na', function (x) { return x.steam ? 'https://store.steampowered.com/app/' + x.steam + '/' : null; }],
    'Last.fm': ['no', function (x) { return x.lastfm ? 'https://www.last.fm/music/' + x.lastfm.map(function (n) { return encodeURIComponent(n).replace(/%20/g, '+'); }).join('/') : null; }]
  };
  /* platforms linked per category, the activity platform first */
  var LINKS = { movie: ['Letterboxd', 'TMDB'], series: ['TMDB'], anime: ['AniList', 'MyAnimeList'], manga: ['AniList', 'MyAnimeList'], book: ['Open Library'], game: ['Steam'], music: ['Last.fm'] };
  var EXT = {
    'perfect-days': { tmdb: 976893 }, 'o-iluminado': { tmdb: 694 }, 'um-sonho-de-liberdade': { tmdb: 278 }, 'vidas-ao-vento': { tmdb: 149870 }, 'o-poderoso-chefao': { tmdb: 238 },
    'breaking-bad': { tmdb: 1396 }, 'dark': { tmdb: 70523 }, 'the-office': { tmdb: 2316 }, 'arcane': { tmdb: 94605 },
    'frieren': { anilist: 154587, mal: 52991 }, 'vinland-saga': { anilist: 101348, mal: 37521 }, 'steins-gate': { anilist: 9253, mal: 9253 }, 'mob-psycho-100': { anilist: 21507, mal: 32182 },
    'hunter-x-hunter': { anilist: 11061, mal: 11061 }, 'delicious-in-dungeon': { anilist: 153518, mal: 52701 },
    'attack-on-titan': { anilist: 53390, mal: 23390 }, 'kaguya-sama-love-is-war': { anilist: 86635, mal: 90125 }, 'horimiya': { anilist: 72451, mal: 42451 }, 'bloom-into-you': { anilist: 86218, mal: 88660 },
    'stardew-valley': { steam: 413150 }, 'portal-2': { steam: 620 }, 'ori-and-the-will-of-the-wisps': { steam: 1057090 }, 'chrono-trigger': { steam: 613830 }, 'the-elder-scrolls-v-skyrim': { steam: 489830 }, 'hades': { steam: 1145360 },
    'dracula': { ol: 'OL85892W' }, 'a-guerra-dos-tronos': { ol: 'OL257943W' }, 'o-hobbit': { ol: 'OL27482W' }, 'o-genio-do-crime': { ol: 'OL4587854W' }, 'aprendiz-de-assassino': { ol: 'OL2707183W' },
    'clube-da-esquina': { lastfm: ['Milton Nascimento', 'Clube da Esquina'] }, 'acabou-chorare': { lastfm: ['Novos Baianos', 'Acabou Chorare'] },
    'meteora': { lastfm: ['Linkin Park', 'Meteora'] }, 'nada-como-um-dia-apos-o-outro-dia': { lastfm: ['Racionais MC\'s', 'Nada Como um Dia Após o Outro Dia'] },
    'imunidade-musical': { lastfm: ['Charlie Brown Jr.', 'Imunidade Musical'] }
  };
  /* id: [category, title, year, subtitle, cover, tilt factor, former tape or pin (unused), owner note, origin ('' = chosen by hand)] */
  var ROWS = {
    'perfect-days': ['movie', 'Perfect Days', 2023, 'de Wim Wenders', 1, 0.883, 't', 'assisti 3 vezes. o final no carro me pega toda vez', 'Assistido em 11 out. 2026 no Letterboxd'],
    'vinland-saga': ['anime', 'Vinland Saga', 2019, 'estúdio Wit', 7, 0.426, 't', '', 'Assistido em 30 jul. 2026 no AniList'],
    'clube-da-esquina': ['music', 'Clube da Esquina', 1972, 'de Milton Nascimento e Lô Borges', 11, -0.88, 'p', 'repeat o mês todo', 'Ouvindo agora no Last.fm'],
    'portal-2': ['game', 'Portal 2', 2011, 'da Valve', 3, -0.844, 't', '', ''],
    'frieren': ['anime', 'Frieren', 2023, 'estúdio Madhouse', 12, 0.516, 'p', '', 'Assistindo agora no AniList'],
    'dracula': ['book', 'Drácula', 1897, 'de Bram Stoker', 10, -0.996, 'p', '', 'Lendo agora no Goodreads'],
    'stardew-valley': ['game', 'Stardew Valley', 2016, 'da ConcernedApe', 4, 0.47, 'p', '', ''],
    'o-iluminado': ['movie', 'O Iluminado', 1980, 'de Stanley Kubrick', 9, -0.373, 't', '', 'Assistido em 20 set. 2026 no Letterboxd'],
    'um-sonho-de-liberdade': ['movie', 'Um Sonho de Liberdade', 1994, 'de Frank Darabont', 2, -0.618, 'p', 'nota 10', ''],
    'vidas-ao-vento': ['movie', 'Vidas ao Vento', 2013, 'de Hayao Miyazaki', 5, -0.987, 'p', '', 'Assistido em 2 ago. 2026 no Letterboxd'],
    'o-poderoso-chefao': ['movie', 'O Poderoso Chefão', 1972, 'de Francis Ford Coppola', 4, -0.682, 't', '', ''],
    'breaking-bad': ['series', 'Breaking Bad', 2008, 'de Vince Gilligan', 7, -0.603, 'p', '', ''],
    'dark': ['series', 'Dark', 2017, 'de Baran bo Odar e Jantje Friese', 3, 0.592, 't', '', ''],
    'the-office': ['series', 'The Office', 2005, 'de Greg Daniels', 6, 0.545, 'p', '', ''],
    'arcane': ['series', 'Arcane', 2021, 'de Christian Linke e Alex Yee', 9, -0.833, 'p', 'temporada 2', ''],
    'steins-gate': ['anime', 'Steins;Gate', 2011, 'estúdio White Fox', 1, 0.44, 't', 'El Psy Kongroo', 'Assistido em 14 jul. 2026 no AniList'],
    'mob-psycho-100': ['anime', 'Mob Psycho 100', 2016, 'estúdio Bones', 10, -0.381, 'p', '', ''],
    'hunter-x-hunter': ['anime', 'Hunter x Hunter', 2011, 'estúdio Madhouse', 4, 0.657, 'p', '', 'Assistido em 5 mar. 2026 no AniList'],
    'delicious-in-dungeon': ['anime', 'Delicious in Dungeon', 2024, 'estúdio Trigger', 5, 0.36, 'p', '', ''],
    'attack-on-titan': ['manga', 'Attack on Titan', 2009, 'vol. 34, de Hajime Isayama', 5, -0.597, 't', '', 'Lido em 9 out. 2026 no AniList'],
    'kaguya-sama-love-is-war': ['manga', 'Kaguya-sama: Love Is War', 2015, 'vol. 1, de Aka Akasaka', 2, -0.491, 't', '', ''],
    'horimiya': ['manga', 'Horimiya', 2011, 'vol. 3, de Hero e Daisuke Hagiwara', 3, -0.637, 'p', '', 'Lido em 1 set. 2026 no AniList'],
    'bloom-into-you': ['manga', 'Bloom Into You', 2015, 'vol. 5, de Nio Nakatani', 9, 0.691, 'p', '', ''],
    'a-guerra-dos-tronos': ['book', 'A Guerra dos Tronos', 1996, 'de George R. R. Martin', 10, -0.62, 'p', '', ''],
    'o-hobbit': ['book', 'O Hobbit', 1937, 'de J. R. R. Tolkien', 9, -0.804, 't', 'nota 10', ''],
    'o-genio-do-crime': ['book', 'O Gênio do Crime', 1969, 'de João Carlos Marinho', 6, -0.953, 'p', '', 'Lido em 18 jun. 2026 no Goodreads'],
    'aprendiz-de-assassino': ['book', 'Aprendiz de Assassino', 1995, 'de Robin Hobb', 12, -0.533, 'p', '', ''],
    'meteora': ['music', 'Meteora', 2003, 'do Linkin Park', 6, 0.619, 'p', '', 'Ouvido em 12 out. 2026 no Last.fm'],
    'nada-como-um-dia-apos-o-outro-dia': ['music', 'Nada Como um Dia Após o Outro Dia', 2002, 'dos Racionais MC\'s', 4, -0.532, 'p', '', ''],
    'acabou-chorare': ['music', 'Acabou Chorare', 1972, 'dos Novos Baianos', 2, -0.521, 'p', '', 'Ouvido em 30 set. 2026 no Last.fm'],
    'imunidade-musical': ['music', 'Imunidade Musical', 2005, 'do Charlie Brown Jr.', 10, -0.832, 't', '', ''],
    'ori-and-the-will-of-the-wisps': ['game', 'Ori and the Will of the Wisps', 2020, 'da Moon Studios', 5, -0.374, 't', '', ''],
    'chrono-trigger': ['game', 'Chrono Trigger', 1995, 'da Square', 1, 0.685, 't', 'nota 10', ''],
    'the-elder-scrolls-v-skyrim': ['game', 'The Elder Scrolls V: Skyrim', 2011, 'da Bethesda Game Studios', 7, -0.518, 'p', '', ''],
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
    movie: [['Valor Sentimental', 2025, 'de Joachim Trier'], ['Duna: Parte Dois', 2024, 'de Denis Villeneuve'], ['Robô Selvagem', 2024, 'de Chris Sanders'], ['A Vida Secreta de Walter Mitty', 2013, 'de Ben Stiller'], ['Onde os Fracos Não Têm Vez', 2007, 'de Joel Coen e Ethan Coen'], ['O Castelo Animado', 2004, 'de Hayao Miyazaki'], ['O Senhor dos Anéis: A Sociedade do Anel', 2001, 'de Peter Jackson'], ['Gênio Indomável', 1997, 'de Gus Van Sant'], ['A Lista de Schindler', 1993, 'de Steven Spielberg'], ['O Silêncio dos Inocentes', 1991, 'de Jonathan Demme'], ['Casablanca', 1942, 'de Michael Curtiz']],
    series: [['The Pitt', 2025, 'de R. Scott Gemmill'], ['A Maldição da Residência Hill', 2018, 'de Mike Flanagan'], ['Demolidor', 2015, 'de Drew Goddard'], ['Avatar: A Lenda de Aang', 2005, 'de Michael Dante DiMartino e Bryan Konietzko']],
    anime: [['Witch Hat Atelier', 2026, 'estúdio Bug Films'], ['You and I Are Polar Opposites', 2026, 'estúdio Lapin Track'], ['The Summer Hikaru Died', 2025, 'estúdio CygamesPictures'], ['Heavenly Delusion', 2023, 'estúdio Production I.G'], ['Summer Time Rendering', 2022, 'estúdio OLM'], ['Mushoku Tensei', 2021, 'estúdio Bind'], ['To Your Eternity', 2021, 'estúdio Brain\'s Base'], ['Jujutsu Kaisen', 2020, 'estúdio MAPPA'], ['Demon Slayer', 2019, 'estúdio ufotable'], ['Erased', 2016, 'estúdio A-1 Pictures'], ['Re:Zero', 2016, 'estúdio White Fox'], ['Yuri!!! on Ice', 2016, 'estúdio MAPPA'], ['Your Lie in April', 2014, 'estúdio A-1 Pictures'], ['Terror in Resonance', 2014, 'estúdio MAPPA'], ['Anohana', 2011, 'estúdio A-1 Pictures'], ['Durarara!!', 2010, 'estúdio Brain\'s Base'], ['Code Geass', 2006, 'estúdio Sunrise'], ['Death Note', 2006, 'estúdio Madhouse'], ['Fullmetal Alchemist', 2003, 'estúdio Bones']],
    manga: [],
    book: [['Guerra Civil', 2012, 'de Stuart Moore'], ['A Dança dos Dragões', 2011, 'de George R. R. Martin'], ['O Festim dos Corvos', 2005, 'de George R. R. Martin'], ['A Tormenta de Espadas', 2000, 'de George R. R. Martin'], ['A Fúria dos Reis', 1998, 'de George R. R. Martin'], ['A Fúria do Assassino', 1997, 'de Robin Hobb'], ['O Assassino do Rei', 1996, 'de Robin Hobb'], ['O Peregrino', 1678, 'de John Bunyan']],
    music: [['Diamantes, Lágrimas e Rostos para Esquecer', 2025, 'de BK'], ['The Gods We Can Touch', 2022, 'de AURORA'], ['O Líder em Movimento', 2020, 'de BK e JXNV$'], ['A Different Kind of Human (Step 2)', 2019, 'de AURORA'], ['Bluesman', 2018, 'de Baco Exu do Blues'], ['All My Demons Greeting Me as a Friend', 2015, 'de AURORA'], ['Minutes to Midnight', 2007, 'do Linkin Park'], ['Abalando a Sua Fábrica', 2001, 'do Charlie Brown Jr.'], ['Clube da Esquina 2', 1978, 'de Milton Nascimento'], ['Alucinação', 1976, 'de Belchior'], ['Cartola', 1976, 'de Cartola']],
    game: [['Hades II', 2025, 'da Supergiant Games'], ['Baldur\'s Gate 3', 2023, 'da Larian Studios'], ['Cyberpunk 2077', 2020, 'da CD Projekt Red'], ['Divinity: Original Sin 2', 2017, 'da Larian Studios'], ['Civilization VI', 2016, 'da Firaxis Games'], ['Portal', 2007, 'da Valve'], ['Age of Empires II', 1999, 'da Ensemble Studios']]
  };
  /* category pages: slug, category, plural label, unit for counts ("24 animes"), profile highlights in wall order */
  var CATS = [
    { slug: 'filmes', cat: 'movie', label: 'Filmes', unit: ['filme', 'filmes'], hl: ['o-iluminado', 'um-sonho-de-liberdade', 'vidas-ao-vento', 'o-poderoso-chefao'] },
    { slug: 'series', cat: 'series', label: 'Séries', unit: ['série', 'séries'], hl: ['breaking-bad', 'dark', 'the-office', 'arcane'] },
    { slug: 'animes', cat: 'anime', label: 'Animes', unit: ['anime', 'animes'], hl: ['steins-gate', 'mob-psycho-100', 'hunter-x-hunter', 'delicious-in-dungeon'] },
    { slug: 'mangas', cat: 'manga', label: 'Mangás', unit: ['mangá', 'mangás'], hl: ['attack-on-titan', 'kaguya-sama-love-is-war', 'horimiya', 'bloom-into-you'] },
    { slug: 'livros', cat: 'book', label: 'Livros', unit: ['livro', 'livros'], hl: ['a-guerra-dos-tronos', 'o-hobbit', 'o-genio-do-crime', 'aprendiz-de-assassino'] },
    { slug: 'musica', cat: 'music', label: 'Música', unit: ['álbum', 'álbuns'], hl: ['meteora', 'nada-como-um-dia-apos-o-outro-dia', 'acabou-chorare', 'imunidade-musical'] },
    { slug: 'games', cat: 'game', label: 'Games', unit: ['game', 'games'], hl: ['ori-and-the-will-of-the-wisps', 'chrono-trigger', 'the-elder-scrolls-v-skyrim', 'hades'] }
  ];
  var FAVORITES = ['perfect-days', 'vinland-saga', 'clube-da-esquina', 'portal-2'];
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
    var x = EXT[it.id] || {};
    return LINKS[it.cat].map(function (p) {
      var url = PLAT[p][1](x, it.cat);
      return url ? '<a class="mq-btn mq-btn--secondary" href="' + url + '" target="_blank" rel="noopener noreferrer">Ver ' + PLAT[p][0] + ' ' + p + icon('external-link') + '<span class="sr"> (abre em nova aba)</span></a>' : '';
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
        (links(it) ? '<div class="detail__links">' + links(it) + '</div>' : '') +
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
