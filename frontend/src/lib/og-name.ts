/**
 * Font size (px) for the heading on the share image: the big condensed face fits about 14
 * characters on a line at the top size, so longer text steps down. The image clamps whatever is
 * left after the last step to two lines.
 */
export function nameFontSize(name: string): number {
  const length = [...name.trim()].length;
  if (length <= 14) return 176;
  if (length <= 20) return 128;
  if (length <= 25) return 96;
  return 80;
}
