import { SHARE_IMAGE_SIZE, TAGLINE, shareImage } from "@/lib/og-image";

// Share image for every page without an owner (landing, legal pages, 404); /{slug} overrides it.
export const alt = "meuquarto: seu gosto num quarto só";
export const size = SHARE_IMAGE_SIZE;
export const contentType = "image/png";

export default function Image() {
  return shareImage(TAGLINE);
}
