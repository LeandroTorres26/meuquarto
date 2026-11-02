import { OwnerPlatformsInvite } from "@/components/SessionSlots";
import { PlatformMark } from "@/components/PlatformMark";
import { shownPlatformLinks, type PublicPlatformLink } from "@/lib/platforms";

/**
 * The owner's profiles on other platforms (FR-12a, ADR 0023), after the two actions of the header. The
 * URLs come from the API, built on the server from the username and a fixed template per platform; the
 * links are the same for every viewer and open in a new tab. With none linked, the owner (only) is
 * invited to link some; visitors and other users see nothing.
 */
export function PlatformLinks({
  ownerSlug,
  displayName,
  platforms,
}: {
  ownerSlug: string;
  displayName: string;
  platforms: readonly PublicPlatformLink[];
}) {
  const links = shownPlatformLinks(platforms);
  if (links.length === 0) return <OwnerPlatformsInvite ownerSlug={ownerSlug} />;
  return (
    <nav aria-label={`${displayName} em outras plataformas`}>
      <ul className="mq-profile__links">
        {links.map((link) => (
          <li key={link.platform}>
            <a className="mq-link" href={link.url} target="_blank" rel="noopener noreferrer">
              <PlatformMark platform={link.platform} />
              {link.name}
              <span className="sr"> (abre em nova aba)</span>
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}
