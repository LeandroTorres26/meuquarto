import { ProviderTags } from "@/components/linked/ProviderTags";
import { broughtCaption, type ProviderInfo } from "@/lib/linked-accounts";

/** A platform whose provider does not exist yet: honest "Em breve", no input. */
export function SoonCard({ provider }: { provider: ProviderInfo }) {
  const id = `${provider.id.toLowerCase()}-soon`;
  return (
    <div className="region region--line" role="group" aria-labelledby={`${id}-h`}>
      <div className="row row--between">
        <h3 id={`${id}-h`} className="t-h3">
          {provider.name}
        </h3>
        <ProviderTags provider={provider} />
      </div>
      <p className="t-cap">{broughtCaption(provider)}</p>
      <button type="button" className="mq-btn mq-btn--secondary mq-btn--sm self-start" disabled>
        Em breve
      </button>
    </div>
  );
}
