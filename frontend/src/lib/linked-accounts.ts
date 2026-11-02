import type { Category } from "@/lib/profile-view";

// S9 (painel-contas.html): pure helpers for the linked accounts screen. No React, no fetch.

export type ProviderId = "LETTERBOXD" | "ANILIST" | "STEAM" | "LASTFM" | "GOODREADS";

export type LinkedAccount = {
  id: number;
  provider: ProviderId;
  username: string;
  status: "ACTIVE" | "ERROR";
  lastSyncedAt: string | null;
  lastError: string | null;
  nextManualSyncAt: string | null;
};

export type SyncResult = {
  status: "SUCCESS" | "FAILED" | "SKIPPED";
  itemsCount: number;
  error: string | null;
};

export type ProviderInfo = {
  id: ProviderId;
  name: string;
  /** Categories the platform feeds (tags on the card). */
  categories: Category[];
  /** What enters the collection (BR-10), the caption the design asks for. */
  brings: string;
  /** Label of the username input. */
  label: string;
  /** Provider-specific hint under the input. */
  hint: string;
  placeholder: string;
  /** False while the backend has no provider for the platform: the card shows "Em breve". */
  available: boolean;
};

export const PROVIDERS: ProviderInfo[] = [
  {
    id: "LETTERBOXD",
    name: "Letterboxd",
    categories: ["MOVIE"],
    brings: "Seu diário recente; o histórico se acumula a partir de agora.",
    label: "Usuário do Letterboxd",
    hint: "Seu nome de usuário no Letterboxd, como aparece em letterboxd.com/nome.",
    placeholder: "seunome",
    available: true,
  },
  {
    id: "ANILIST",
    name: "AniList",
    categories: ["ANIME", "MANGA"],
    brings: "Completos, assistindo, pausados e revendo.",
    label: "Usuário do AniList",
    hint: "Seu nome de usuário no AniList, como aparece em anilist.co/user/nome.",
    placeholder: "seunome",
    available: true,
  },
  {
    id: "STEAM",
    name: "Steam",
    categories: ["GAME"],
    brings: "Jogos com tempo de jogo.",
    label: "Perfil do Steam",
    hint: "A lista de jogos também precisa estar pública.",
    placeholder: "steamcommunity.com/id/seunome",
    available: false,
  },
  {
    id: "LASTFM",
    name: "Last.fm",
    categories: ["MUSIC"],
    brings: "Faixas amadas e álbuns que você mais ouve.",
    label: "Usuário do Last.fm",
    hint: "Seu nome de usuário no Last.fm, como aparece em last.fm/user/nome.",
    placeholder: "seunome",
    available: false,
  },
  {
    id: "GOODREADS",
    name: "Goodreads",
    categories: ["BOOK"],
    brings: "Estantes lidos e lendo.",
    label: "Número do seu perfil no Goodreads",
    hint: "O número que aparece no endereço do seu perfil, em goodreads.com/user/show/12345678-nome. Seu perfil e suas estantes precisam estar públicos.",
    placeholder: "12345678",
    available: true,
  },
];

export const providerInfo = (id: ProviderId): ProviderInfo =>
  PROVIDERS.find((p) => p.id === id) ?? PROVIDERS[0];

/** Providers the user can still link: available and not linked yet. */
export function linkableProviders(accounts: LinkedAccount[]): ProviderInfo[] {
  const linked = new Set(accounts.map((a) => a.provider));
  return PROVIDERS.filter((p) => p.available && !linked.has(p.id));
}

/** "Entra na coleção: …" caption (BR-10) for a platform. */
export const broughtCaption = (p: ProviderInfo): string =>
  `Entra na coleção: ${p.brings.charAt(0).toLowerCase()}${p.brings.slice(1)}`;

/** Providers shown as "Em breve" (no backend provider yet). */
export const comingSoonProviders = (): ProviderInfo[] => PROVIDERS.filter((p) => !p.available);

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

const plural = (n: number, one: string, many: string) => `${n} ${n === 1 ? one : many}`;

/** "agora há pouco", "há 3 minutos", "há 2 horas", "há 1 dia"… from an ISO instant. */
export function relativeTimePtBr(iso: string, now: number): string {
  const diff = Math.max(0, now - new Date(iso).getTime());
  if (Number.isNaN(diff)) return "";
  if (diff < MINUTE) return "agora há pouco";
  if (diff < HOUR) return `há ${plural(Math.floor(diff / MINUTE), "minuto", "minutos")}`;
  if (diff < DAY) return `há ${plural(Math.floor(diff / HOUR), "hora", "horas")}`;
  return `há ${plural(Math.floor(diff / DAY), "dia", "dias")}`;
}

/** Whole minutes (rounded up) until the manual sync is allowed again; 0 when it already is. */
export function cooldownMinutes(nextManualSyncAt: string | null, now: number): number {
  if (!nextManualSyncAt) return 0;
  const left = new Date(nextManualSyncAt).getTime() - now;
  return Number.isFinite(left) && left > 0 ? Math.ceil(left / MINUTE) : 0;
}

/** The instant (ms) a Retry-After in seconds ends, counted from `now`; 0 when there is none. */
export const cooldownUntilFromRetryAfter = (seconds: number | undefined, now: number): number =>
  seconds && seconds > 0 ? now + seconds * 1000 : 0;

/** Minutes left until `until` (ms epoch); 0 when it has passed. */
export function minutesUntil(until: number, now: number): number {
  return until > now ? Math.ceil((until - now) / MINUTE) : 0;
}

export const waitLabel = (minutes: number) => `Aguarde ${minutes} min`;

/** Text for a code the sync stored in `lastError` or returned in a sync result. */
export function syncErrorMessage(code: string | null | undefined, providerName: string): string {
  switch (code) {
    case "ACCOUNT_NOT_FOUND_ON_PROVIDER":
      return `Não encontramos esse perfil no ${providerName}. Ele pode ter sido apagado ou ficado privado. O que já está no perfil continua lá.`;
    case "PROVIDER_UNAVAILABLE":
      return `O ${providerName} não respondeu agora. Tentamos de novo em breve; o que já está no perfil continua lá.`;
    case "LINKED_ACCOUNT_NOT_FOUND":
      return "Essa conta já foi desvinculada.";
    default:
      return `Não conseguimos atualizar com o ${providerName} agora. Tentamos de novo em breve; o que já está no perfil continua lá.`;
  }
}

/** "12 obras sincronizadas" / "Tudo em dia…" for a successful sync. */
export function syncSuccessMessage(itemsCount: number): string {
  if (itemsCount <= 0) return "Tudo em dia. Não havia nada novo para adicionar.";
  return itemsCount === 1 ? "1 obra sincronizada" : `${itemsCount} obras sincronizadas`;
}

export type LinkErrorField = "username" | "form";

/** Error of POST /api/linked-accounts (or any call) as inline text. */
export function linkErrorMessage(
  code: string | undefined,
  status: number | undefined,
  providerName: string,
  retryAfterSeconds?: number,
): { field: LinkErrorField; message: string } {
  if (status === 429) {
    const minutes = retryAfterSeconds ? Math.max(1, Math.ceil(retryAfterSeconds / 60)) : null;
    return {
      field: "form",
      message: minutes
        ? `Muitas tentativas. Tente de novo em ${plural(minutes, "minuto", "minutos")}.`
        : "Muitas tentativas. Tente de novo em alguns minutos.",
    };
  }
  switch (code) {
    case "INVALID_USERNAME":
    case "INVALID_REQUEST":
      return {
        field: "username",
        message: `Esse nome de usuário não é válido no ${providerName}. Confira como ele aparece na URL do seu perfil.`,
      };
    case "ACCOUNT_NOT_FOUND_ON_PROVIDER":
      return {
        field: "username",
        message: "Esse perfil não existe ou está privado. Confira o nome na URL do seu perfil.",
      };
    case "ALREADY_LINKED":
      return { field: "form", message: `Você já vinculou uma conta do ${providerName}.` };
    case "PROVIDER_UNAVAILABLE":
      return {
        field: "form",
        message: `Não conseguimos falar com o ${providerName} agora. Tente de novo em instantes.`,
      };
    default:
      return {
        field: "form",
        message: "Algo deu errado do nosso lado. Tente de novo em instantes.",
      };
  }
}

export const NETWORK_MESSAGE =
  "Não foi possível falar com o servidor. Confira sua conexão e tente de novo.";

/** The current time in ms; a function of its own so components do not call Date.now during render. */
export const nowMs = (): number => Date.now();
