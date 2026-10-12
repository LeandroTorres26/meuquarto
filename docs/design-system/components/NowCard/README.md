NowCard is the "Agora" block entry: one item per category with the lamp dot, a verb, the source and the sync status.

Provide the category verb (Assistindo, Lendo, Ouvindo, Jogando), the item title, a thumbnail and the status text. Show the accent lamp only for items that came from sync or were pinned; a hidden item renders nothing. Failed sync shows the status line in `danger` with the word "Erro", never color alone.

Do: keep the status in `body-sm`. Do not: call external providers while rendering; read the cached read model.
