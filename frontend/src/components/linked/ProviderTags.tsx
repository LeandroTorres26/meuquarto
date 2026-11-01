import { CategoryTag } from "@/components/profile/CategoryTag";
import type { ProviderInfo } from "@/lib/linked-accounts";

/** The category stickers of a platform (what it feeds), as in the S9 card header. */
export function ProviderTags({ provider }: { provider: ProviderInfo }) {
  return (
    <span className="row">
      {provider.categories.map((category) => (
        <CategoryTag key={category} category={category} />
      ))}
    </span>
  );
}
