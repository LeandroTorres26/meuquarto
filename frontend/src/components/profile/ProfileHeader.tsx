import { countLabel, visibleSections, type PublicProfileData } from "@/lib/profile-view";
import { CategoryTag } from "./CategoryTag";
import { CopyLinkButton } from "./CopyLinkButton";

/** Public host shown in the address line; the copy button uses the real origin of the page. */
const PUBLIC_HOST = "meuquarto.com.br";

/**
 * ProfileHeader (design system): taped polaroid avatar (an initial when there is no photo), slug, name and
 * bio. The photo caption and the handwritten tagline of the prototype are not built: the profile has no
 * data for them (docs/data-model.md). The stickers show how many works each category holds. "Ver quarto" is left out until the room exists.
 */
export function ProfileHeader({ profile }: { profile: PublicProfileData }) {
  const sections = visibleSections(profile.categories);
  return (
    <header className="mq-profile">
      <div className="mq-polaroid">
        <span className="mq-tape" aria-hidden="true" />
        {profile.avatarUrl ? (
          // eslint-disable-next-line @next/next/no-img-element -- same-origin avatar already resized to 512 px by the API
          <img
            className="mq-art"
            src={profile.avatarUrl}
            alt={`Foto de ${profile.displayName}`}
            width={512}
            height={512}
          />
        ) : (
          <div
            className="mq-art mq-art--initial"
            role="img"
            aria-label={`${profile.displayName}, sem foto`}
          >
            {Array.from(profile.displayName)[0]?.toUpperCase() ?? "?"}
          </div>
        )}
      </div>
      <div style={{ minWidth: 0 }}>
        <p className="mq-profile__slug">
          {PUBLIC_HOST}/{profile.slug}
        </p>
        <h1 className="mq-profile__name">{profile.displayName}</h1>
        {profile.bio ? <p className="mq-profile__bio bio-text">{profile.bio}</p> : null}
        {sections.length > 0 ? (
          <ul className="mq-profile__tags" aria-label="Itens por categoria">
            {sections.map((section) => (
              <li key={section.category}>
                <CategoryTag category={section.category} count={section.total} />
                <span className="sr"> ({countLabel(section.category, section.total)})</span>
              </li>
            ))}
          </ul>
        ) : null}
        <div className="mq-profile__actions">
          <CopyLinkButton slug={profile.slug} />
        </div>
      </div>
    </header>
  );
}
