import { countLabel, visibleSections, type PublicProfileData } from "@/lib/profile-view";
import { CategoryTag } from "./CategoryTag";
import { CopyLinkButton } from "./CopyLinkButton";

/** Public host shown in the address line; the copy button uses the real origin of the page. */
const PUBLIC_HOST = "meuquarto.com.br";

/**
 * ProfileHeader (design system) without avatar, tagline and bio: the API does not carry them yet.
 * The stickers show how many works each category holds. "Ver quarto" is left out until the room exists.
 */
export function ProfileHeader({ profile }: { profile: PublicProfileData }) {
  const sections = visibleSections(profile.categories);
  return (
    <header className="mq-profile mq-profile--solo">
      <div style={{ minWidth: 0 }}>
        <p className="mq-profile__slug">
          {PUBLIC_HOST}/{profile.slug}
        </p>
        <h1 className="mq-profile__name">{profile.displayName}</h1>
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
