// Platforms that appear as links on the public profile (S4) and as cards in the panel (S9), ADR 0023.
// Pure helpers: no React, no fetch.

/** In the fixed order the profile lists them (FR-12a). MyAnimeList is not linkable yet (issue #126). */
export const PLATFORM_IDS = [
  "LETTERBOXD",
  "ANILIST",
  "MYANIMELIST",
  "LASTFM",
  "STEAM",
  "GOODREADS",
  "BACKLOGGD",
  "TRAKT",
  "STORYGRAPH",
] as const;

export type PlatformId = (typeof PLATFORM_IDS)[number];

export const PLATFORM_NAMES: Record<PlatformId, string> = {
  LETTERBOXD: "Letterboxd",
  ANILIST: "AniList",
  MYANIMELIST: "MyAnimeList",
  LASTFM: "Last.fm",
  STEAM: "Steam",
  GOODREADS: "Goodreads",
  BACKLOGGD: "Backloggd",
  TRAKT: "Trakt",
  STORYGRAPH: "StoryGraph",
};

/** `platforms` of GET /api/public/profiles/{slug}: the id and the profile URL the server built (FR-12a). */
export type PublicPlatformLink = { platform: string; url: string };

export const isPlatformId = (value: string): value is PlatformId =>
  (PLATFORM_IDS as readonly string[]).includes(value);

/** The CSS modifier of the mark tile: `mq-mark--letterboxd`. */
export const markClass = (platform: PlatformId): string => `mq-mark--${platform.toLowerCase()}`;

/** Only absolute https links are ever rendered; the server builds them, this is the second lock. */
export const isHttpsUrl = (value: string): boolean => {
  try {
    return new URL(value).protocol === "https:";
  } catch {
    return false;
  }
};

export type ShownPlatformLink = { platform: PlatformId; name: string; url: string };

/** The links the header can show: a known platform and an https URL, in the order the API sent them. */
export function shownPlatformLinks(
  links: readonly PublicPlatformLink[] | null | undefined,
): ShownPlatformLink[] {
  const shown: ShownPlatformLink[] = [];
  for (const link of links ?? []) {
    if (isPlatformId(link.platform) && isHttpsUrl(link.url)) {
      shown.push({ platform: link.platform, name: PLATFORM_NAMES[link.platform], url: link.url });
    }
  }
  return shown;
}
