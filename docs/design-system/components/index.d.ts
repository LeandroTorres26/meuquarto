/** meuquarto components. All styling comes from components/bundle.css and the CSS variables in tokens.css. */
declare namespace Meuquarto {
  type Category = 'movie' | 'series' | 'anime' | 'manga' | 'book' | 'game' | 'music';

  interface HighlightCardProps {
    /** Stable id of the work. Drives the resting angle and the default fixing. */
    id: string | number;
    category: Category;
    /** Shown below the cover, 2 lines max with an ellipsis. */
    title: string;
    year?: string | number;
    /** Volume number for manga and anime; printed in the subline, never on the cover. */
    volume?: string | number;
    /** Cover image url. Empty: the "no cover" state. */
    cover?: string;
    /** One handwritten line, about 28 characters. */
    note?: string;
    href?: string;
    /** Defaults to a value derived from id. */
    fix?: 'tape' | 'pin' | 'none';
    /** Hide the category sticker (default true). */
    showTag?: boolean;
    loading?: boolean;
    /** The cover image failed to load. */
    error?: boolean;
    disabled?: boolean;
    /** Force a state for documentation. */
    state?: 'hover' | 'focus';
  }
  interface HighlightSlotProps { category: Category; onClick?: () => void; label?: string }
  interface CategoryTagProps { category: Category; count?: number | string }

  const HighlightCard: (props: HighlightCardProps) => JSX.Element;
  const HighlightSlot: (props: HighlightSlotProps) => JSX.Element;
  const HighlightGrid: (props: { children?: any }) => JSX.Element;
  const CategoryTag: (props: CategoryTagProps) => JSX.Element;
  const Icon: (props: { name: string }) => JSX.Element;
  const CATEGORIES: Record<Category, { label: string; icon: string; shape: string }>;
  /** Deterministic factor in [-1,-0.36] or [0.36,1]; angle = factor * tilt-max. */
  function tiltFactor(id: string | number): number;
  function fixKind(id: string | number): 'tape' | 'pin';
  function hash(id: string | number): number;
}
