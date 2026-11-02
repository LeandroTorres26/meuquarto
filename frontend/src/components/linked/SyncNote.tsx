import { Link as LinkIcon, RefreshCw } from "lucide-react";
import { syncNote, type ProviderInfo } from "@/lib/linked-accounts";

/**
 * Third row of every S9 card: "Atualiza sozinho." with what the platform brings into the collection, or
 * "Só o link." when its list is not read (ADR 0023). Never promises more than the platform allows.
 */
export function SyncNote({ provider }: { provider: ProviderInfo }) {
  const { lead, text } = syncNote(provider);
  const Icon = provider.syncs ? RefreshCw : LinkIcon;
  return (
    <p className="t-cap sync-note region__note">
      <Icon className="icon" aria-hidden="true" />
      <span>
        <strong>{lead}</strong> {text}
      </span>
    </p>
  );
}
