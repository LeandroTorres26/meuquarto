"use client";

import { Check, Clock, ExternalLink, RefreshCw, TriangleAlert } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ProviderHead } from "@/components/linked/ProviderHead";
import { SyncNote } from "@/components/linked/SyncNote";
import { UnlinkDialog } from "@/components/linked/UnlinkDialog";
import { useNow } from "@/components/linked/useNow";
import { ApiError, syncAccount, unlinkAccount } from "@/lib/api-client";
import { isHttpsUrl } from "@/lib/platforms";
import {
  NETWORK_MESSAGE,
  nowMs,
  cooldownMinutes,
  cooldownUntilFromRetryAfter,
  minutesUntil,
  profileLinkText,
  providerInfo,
  relativeTimePtBr,
  syncErrorMessage,
  syncSuccessMessage,
  waitLabel,
  type LinkedAccount,
} from "@/lib/linked-accounts";

type Outcome = { kind: "ok" | "bad"; message: string };

/**
 * S9, a linked account (ADR 0023): the link to the profile on the platform, then, where the platform's list
 * is read, the status and "Sincronizar agora" with the BR-03 cooldown; always "Desvincular" (FR-16). A
 * link-only platform has no status and no sync button.
 */
export function ConnectedCard({
  account,
  slug,
  serverNow,
}: {
  account: LinkedAccount;
  slug: string | null;
  serverNow: number;
}) {
  const router = useRouter();
  const provider = providerInfo(account.provider);
  const now = useNow(serverNow);
  const [syncing, setSyncing] = useState(false);
  const [outcome, setOutcome] = useState<Outcome | null>(null);
  /** Cooldown learned from a 429 Retry-After; the server value (nextManualSyncAt) covers the rest. */
  const [localUntil, setLocalUntil] = useState(0);
  const [confirming, setConfirming] = useState(false);
  const [unlinking, setUnlinking] = useState(false);
  const [unlinkError, setUnlinkError] = useState<string | null>(null);

  const wait = Math.max(
    cooldownMinutes(account.nextManualSyncAt, now),
    minutesUntil(localUntil, now),
  );
  const failed = account.syncs && (account.status === "ERROR" || outcome?.kind === "bad");
  const headingId = `conta-${account.id}-h`;

  async function sync() {
    if (syncing) return;
    setSyncing(true);
    setOutcome(null);
    try {
      const result = await syncAccount(account.id);
      setOutcome(
        result.status === "SUCCESS"
          ? { kind: "ok", message: syncSuccessMessage(result.itemsCount) }
          : { kind: "bad", message: syncErrorMessage(result.error, provider.name) },
      );
    } catch (e) {
      if (e instanceof ApiError && e.status === 429) {
        setLocalUntil(cooldownUntilFromRetryAfter(e.retryAfterSeconds, nowMs()));
        setOutcome({
          kind: "bad",
          message:
            "Essa conta foi atualizada há pouco. Tente de novo quando o tempo de espera acabar.",
        });
      } else if (e instanceof ApiError && e.status === 404) {
        setOutcome({
          kind: "bad",
          message: syncErrorMessage("LINKED_ACCOUNT_NOT_FOUND", provider.name),
        });
      } else {
        setOutcome({
          kind: "bad",
          message:
            e instanceof ApiError ? syncErrorMessage(undefined, provider.name) : NETWORK_MESSAGE,
        });
      }
    } finally {
      setSyncing(false);
      router.refresh(); // status, last sync and the cooldown come from the server
    }
  }

  async function unlink() {
    if (unlinking) return;
    setUnlinking(true);
    setUnlinkError(null);
    try {
      await unlinkAccount(account.id);
      setConfirming(false);
      router.refresh();
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) {
        setConfirming(false); // already gone
        router.refresh();
      } else {
        setUnlinkError(
          e instanceof ApiError
            ? "Não conseguimos desvincular agora. Tente de novo em instantes."
            : NETWORK_MESSAGE,
        );
      }
    } finally {
      setUnlinking(false);
    }
  }

  const stored =
    account.status === "ERROR" ? syncErrorMessage(account.lastError, provider.name) : null;
  const syncedLabel = account.lastSyncedAt
    ? `Sincronizado ${relativeTimePtBr(account.lastSyncedAt, now)}`
    : "Ainda não sincronizado. Sincronize agora ou espere a atualização automática.";

  return (
    <article className={`region${failed ? " region--danger" : ""}`} aria-labelledby={headingId}>
      <ProviderHead provider={provider} headingId={headingId} />
      <div className="pair">
        {account.profileUrl && isHttpsUrl(account.profileUrl) ? (
          <a href={account.profileUrl} target="_blank" rel="noopener noreferrer" className="t-body">
            {profileLinkText(account.profileUrl)}
            <ExternalLink className="icon icon--inline" aria-hidden="true" />
            <span className="sr"> (abre em nova aba)</span>
          </a>
        ) : (
          <p className="t-body">{account.username}</p>
        )}
        {!account.syncs ? null : failed ? (
          <p className="status status--bad">
            <TriangleAlert className="icon" aria-hidden="true" />
            {outcome?.kind === "bad" ? outcome.message : stored}
          </p>
        ) : (
          <p className={`status${account.lastSyncedAt ? " status--ok" : ""}`}>
            {account.lastSyncedAt && <Check className="icon" aria-hidden="true" />}
            {syncedLabel}
          </p>
        )}
        {account.syncs && (
          <p
            role="status"
            aria-live="polite"
            className={syncing || outcome?.kind === "ok" ? "status" : undefined}
          >
            {syncing ? (
              <>
                <span className="mq-spinner" aria-hidden="true" style={{ marginTop: 2 }} />
                Buscando sua lista no {provider.name}…
              </>
            ) : outcome?.kind === "ok" ? (
              <>
                {outcome.message}. {slug && <Link href={`/${slug}`}>Ver meu perfil</Link>}
              </>
            ) : null}
          </p>
        )}
      </div>
      <SyncNote provider={provider} />
      <div className="actions region__act">
        {account.syncs && (
          <button
            type="button"
            className="mq-btn mq-btn--secondary mq-btn--sm"
            onClick={sync}
            disabled={syncing || wait > 0}
            aria-busy={syncing}
          >
            {syncing ? (
              <>
                <span className="mq-spinner" aria-hidden="true" />
                Sincronizando…
              </>
            ) : wait > 0 ? (
              <>
                <Clock className="icon" aria-hidden="true" />
                {waitLabel(wait)}
              </>
            ) : (
              <>
                <RefreshCw className="icon" aria-hidden="true" />
                {failed ? "Tentar agora" : "Sincronizar agora"}
              </>
            )}
          </button>
        )}
        <button
          type="button"
          className="mq-btn mq-btn--ghost mq-btn--sm"
          onClick={() => {
            setUnlinkError(null);
            setConfirming(true);
          }}
          disabled={syncing}
        >
          Desvincular
        </button>
      </div>
      <UnlinkDialog
        open={confirming}
        providerName={provider.name}
        syncs={account.syncs}
        sending={unlinking}
        error={unlinkError}
        onCancel={() => setConfirming(false)}
        onConfirm={unlink}
      />
    </article>
  );
}
