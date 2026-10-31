"use client";

import { Check, Clock, RefreshCw, TriangleAlert } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ProviderTags } from "@/components/linked/ProviderTags";
import { UnlinkDialog } from "@/components/linked/UnlinkDialog";
import { useNow } from "@/components/linked/useNow";
import { ApiError, syncAccount, unlinkAccount } from "@/lib/api-client";
import {
  NETWORK_MESSAGE,
  nowMs,
  broughtCaption,
  cooldownMinutes,
  cooldownUntilFromRetryAfter,
  minutesUntil,
  providerInfo,
  relativeTimePtBr,
  syncErrorMessage,
  syncSuccessMessage,
  waitLabel,
  type LinkedAccount,
} from "@/lib/linked-accounts";

type Outcome = { kind: "ok" | "bad"; message: string };

/** S9, a linked account: status, "Sincronizar agora" with the BR-03 cooldown, and "Desvincular" (FR-16). */
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
  const failed = account.status === "ERROR" || outcome?.kind === "bad";
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
      <div className="row row--between">
        <h3 id={headingId} className="t-h3">
          {provider.name}
        </h3>
        <ProviderTags provider={provider} />
      </div>
      <div className="pair">
        <p className="t-body">{account.username}</p>
        <p className="t-cap">{broughtCaption(provider)}</p>
        {failed ? (
          <p className="status status--bad">
            <TriangleAlert className="icon" aria-hidden="true" />
            {outcome?.kind === "bad" ? outcome.message : stored}
          </p>
        ) : (
          <p className="status status--ok">
            <Check className="icon" aria-hidden="true" />
            {syncedLabel}
          </p>
        )}
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
      </div>
      <div className="actions">
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
        sending={unlinking}
        error={unlinkError}
        onCancel={() => setConfirming(false)}
        onConfirm={unlink}
      />
    </article>
  );
}
