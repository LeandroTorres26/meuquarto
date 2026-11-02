import { ProviderHead } from "@/components/linked/ProviderHead";
import { SyncNote } from "@/components/linked/SyncNote";
import type { ProviderInfo } from "@/lib/linked-accounts";

/** A platform whose provider does not exist yet: same card, honest "Em breve" action, no input. */
export function SoonCard({ provider }: { provider: ProviderInfo }) {
  const id = `${provider.id.toLowerCase()}-soon`;
  return (
    <div className="region region--line" role="group" aria-labelledby={`${id}-h`}>
      <ProviderHead provider={provider} headingId={`${id}-h`} />
      <SyncNote provider={provider} />
      <button
        type="button"
        className="mq-btn mq-btn--secondary mq-btn--sm self-start region__act"
        disabled
      >
        Em breve
      </button>
    </div>
  );
}
