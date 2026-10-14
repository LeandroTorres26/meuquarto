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

  interface ButtonProps {
    variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
    size?: 'md' | 'sm';
    /** Icon name from Meuquarto.Icon. */
    icon?: string;
    /** Icon only; then aria-label is required. */
    iconOnly?: boolean;
    'aria-label'?: string;
    /** Keeps the width, shows a spinner, blocks clicks and sets aria-busy. */
    loading?: boolean;
    /** Use the label to say why ("Aguarde 15 min"). */
    disabled?: boolean;
    /** Renders a link instead of a button. */
    href?: string;
    type?: 'button' | 'submit';
    onClick?: () => void;
    className?: string;
    /** Force a state for documentation. */
    state?: 'hover' | 'focus';
    children?: any;
  }
  interface NowCardProps {
    /** Assistindo, Lendo, Ouvindo, Jogando. */
    verb: string;
    title?: string;
    /** Source and time, or the error text when state is 'error'. */
    status?: string;
    cover?: string;
    /** Square cover (music). */
    square?: boolean;
    /** Show the accent lamp (synced or pinned). Default true. */
    lamp?: boolean;
    /** Standalone box; inside a list keep it false. */
    card?: boolean;
    state?: 'default' | 'empty' | 'loading' | 'error';
  }
  interface ToastProps {
    tone?: 'success' | 'error';
    /** Bold status word. Defaults to "Pronto." or "Erro.". */
    title?: string;
    /** Error toasts stay until closed. */
    onClose?: () => void;
    children?: any;
  }
  interface LimitUpsellProps {
    used?: number;
    limit?: number;
    title?: string;
    /** One sentence on what the pack gives. */
    children?: any;
    href?: string;
    cta?: string;
  }

  const Button: (props: ButtonProps) => JSX.Element;
  const NowCard: (props: NowCardProps) => JSX.Element;
  const Toast: (props: ToastProps) => JSX.Element;
  const LimitUpsell: (props: LimitUpsellProps) => JSX.Element;
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
