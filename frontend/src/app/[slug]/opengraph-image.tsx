import { SHARE_IMAGE_SIZE, TAGLINE, shareImage } from "@/lib/og-image";
import { getPublicProfile } from "@/lib/public-profile";

// Share image of a profile and of the pages under it (listing, room): its name and address. An
// unknown or unavailable profile gets the default image.
export const alt = "Perfil no meuquarto";
export const size = SHARE_IMAGE_SIZE;
export const contentType = "image/png";

export default async function Image({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const result = await getPublicProfile(slug);
  if (result.kind !== "ok") return shareImage(TAGLINE);
  return shareImage(result.profile.displayName, `/${result.profile.slug}`);
}
