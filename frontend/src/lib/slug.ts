/** Same normalization as the backend (Slug.normalize): trim and lowercase. */
export function normalizeSlug(raw: string): string {
  return raw.trim().toLowerCase();
}

/** Trailing-edge debounce. `cancel()` drops a pending call. */
export function debounce<A extends unknown[]>(fn: (...args: A) => void, waitMs: number) {
  let timer: ReturnType<typeof setTimeout> | undefined;
  const debounced = (...args: A) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), waitMs);
  };
  debounced.cancel = () => clearTimeout(timer);
  return debounced;
}
