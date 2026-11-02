import { PlatformMark } from "@/components/PlatformMark";
import { ProviderTags } from "@/components/linked/ProviderTags";
import type { ProviderInfo } from "@/lib/linked-accounts";

/** First row of every S9 card (ADR 0023): the platform's mark and name, and the category stickers it feeds. */
export function ProviderHead({
  provider,
  headingId,
}: {
  provider: ProviderInfo;
  headingId: string;
}) {
  return (
    <div className="row row--between">
      <h3 id={headingId} className="t-h3 platform-name">
        <PlatformMark platform={provider.id} large />
        {provider.name}
      </h3>
      <ProviderTags provider={provider} />
    </div>
  );
}
