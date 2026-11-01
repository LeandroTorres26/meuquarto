HighlightGrid lays out HighlightCards as a wall of objects that never causes horizontal scroll.

Provide HighlightCards as children (`<Meuquarto.HighlightGrid>`, or `ul.mq-grid` with one item per cell). Columns are `repeat(auto-fill, minmax(140px, 1fr))` with `space-8` between rows and `space-4` between columns (`space-6` from 720px). The grid is a size container.

Mobile (360px): 2 columns, and a 24px gutter (`space-6`) on every side. The gutter is what absorbs everything that sticks out of a card box: sticker (8px left), slip (10px right), the ribbon bookmark and manga pages that hang past the cover, and the rotation. Nothing is clipped and nothing scrolls sideways; do not add `overflow: hidden` to the grid or to the page. Below 480px the rotation shrinks to 60%.

Align cells to the top (`align-items: start`): captions have different heights because titles take one or two lines. Keep a category grouped in its own list when you can; mixed grids are fine.
