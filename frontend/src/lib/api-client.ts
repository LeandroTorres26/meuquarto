// Browser-side API client. Talks only to this origin (/api/*, proxied to the backend by proxy.ts),
// so it needs no API address and holds no secrets.

import { catalogSearchPath } from "./collection";
import type {
  CatalogSearch,
  CollectionItem,
  CollectionPage,
  Highlight,
  HighlightList,
  HighlightOverview,
} from "@/lib/collection";
import type { LinkedAccount, ProviderId, SyncResult } from "@/lib/linked-accounts";
import type { Category } from "@/lib/profile-view";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    readonly retryAfterSeconds?: number,
  ) {
    super(code ?? `HTTP ${status}`);
  }
}

export type ClientDeps = {
  fetch: typeof fetch;
  /** Value of a cookie readable from JavaScript, or undefined. */
  getCookie: (name: string) => string | undefined;
};

const defaultDeps = (): ClientDeps => ({
  fetch: (...args) => fetch(...args),
  getCookie: (name) => {
    for (const part of document.cookie.split(";")) {
      const [key, ...rest] = part.trim().split("=");
      if (key === name) return decodeURIComponent(rest.join("="));
    }
    return undefined;
  },
});

const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";

/** The CSRF token, fetching GET /api/auth/csrf first when the cookie is not there yet (or `refresh`). */
export async function ensureCsrfToken(deps: ClientDeps, refresh = false): Promise<string> {
  const existing = refresh ? undefined : deps.getCookie(CSRF_COOKIE);
  if (existing) return existing;
  const res = await deps.fetch("/api/auth/csrf", { credentials: "same-origin", cache: "no-store" });
  if (!res.ok) throw new ApiError(res.status, undefined);
  const token = deps.getCookie(CSRF_COOKIE);
  if (!token) throw new ApiError(res.status, "CSRF_UNAVAILABLE");
  return token;
}

async function toError(res: Response): Promise<ApiError> {
  let code: string | undefined;
  try {
    const body: { error?: unknown } = await res.json();
    if (typeof body.error === "string") code = body.error;
  } catch {
    // no JSON body
  }
  const retry = Number(res.headers.get("Retry-After"));
  return new ApiError(res.status, code, Number.isFinite(retry) && retry > 0 ? retry : undefined);
}

type Method = "POST" | "PUT" | "PATCH" | "DELETE";

/** A state-changing request with the CSRF header. Retries once with a fresh token if the API answers 403. */
async function sendRaw<T>(
  method: Method,
  path: string,
  body: unknown,
  deps: ClientDeps,
): Promise<{ status: number; data: T }> {
  for (let attempt = 0; attempt < 2; attempt++) {
    const token = await ensureCsrfToken(deps, attempt > 0);
    const res = await deps.fetch(path, {
      method,
      credentials: "same-origin",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        [CSRF_HEADER]: token,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (res.status === 403 && attempt === 0) continue;
    if (!res.ok) throw await toError(res);
    return {
      status: res.status,
      data: res.status === 204 ? (undefined as T) : ((await res.json()) as T),
    };
  }
  throw new ApiError(403, undefined);
}

const sendJson = async <T>(
  method: Method,
  path: string,
  body: unknown,
  deps: ClientDeps,
): Promise<T> => (await sendRaw<T>(method, path, body, deps)).data;

/** POST JSON with the CSRF header. Retries once with a fresh token if the API answers 403. */
export const postJson = <T = void>(
  path: string,
  body?: unknown,
  deps: ClientDeps = defaultDeps(),
): Promise<T> => sendJson<T>("POST", path, body, deps);

/** PUT JSON with the CSRF header (same retry as postJson). */
export const putJson = <T = void>(
  path: string,
  body?: unknown,
  deps: ClientDeps = defaultDeps(),
): Promise<T> => sendJson<T>("PUT", path, body, deps);

/** PATCH JSON with the CSRF header (same retry as postJson). */
export const patchJson = <T = void>(
  path: string,
  body?: unknown,
  deps: ClientDeps = defaultDeps(),
): Promise<T> => sendJson<T>("PATCH", path, body, deps);

/** DELETE with the CSRF header (same retry as postJson). */
export const deleteRequest = (path: string, deps: ClientDeps = defaultDeps()): Promise<void> =>
  sendJson<void>("DELETE", path, undefined, deps);

export async function getJson<T>(path: string, deps: ClientDeps = defaultDeps()): Promise<T> {
  const res = await deps.fetch(path, {
    credentials: "same-origin",
    headers: { Accept: "application/json" },
    cache: "no-store",
  });
  if (!res.ok) throw await toError(res);
  return (await res.json()) as T;
}

export type Me = { email: string; slug: string | null };
export type SlugCheck = { slug: string; available: boolean; reason: string | null };

export const signUp = (input: { email: string; password: string; slug: string }) =>
  postJson<Me>("/api/auth/signup", input);
export const logIn = (input: { email: string; password: string }) =>
  postJson<Me>("/api/auth/login", input);
export const logOut = () => postJson("/api/auth/logout");
export const checkSlug = (slug: string, deps?: ClientDeps) =>
  getJson<SlugCheck>(`/api/slugs/${encodeURIComponent(slug)}/availability`, deps);

export const changePassword = (input: { currentPassword: string; newPassword: string }) =>
  postJson("/api/account/password", input);
export const deleteAccount = (input: { password: string }) =>
  postJson("/api/account/delete", input);

export const linkAccount = (input: { provider: ProviderId; username: string }) =>
  postJson<LinkedAccount>("/api/linked-accounts", input);
export const unlinkAccount = (id: number) =>
  deleteRequest(`/api/linked-accounts/${encodeURIComponent(String(id))}`);
export const syncAccount = (id: number) =>
  postJson<SyncResult>(`/api/linked-accounts/${encodeURIComponent(String(id))}/sync`);

// Collection, highlights and catalog (S7, S8). Ids are numbers from our own API; they still go through
// encodeURIComponent like every path segment.
const seg = (id: number) => encodeURIComponent(String(id));

export const searchCatalog = (category: Category, term: string, deps?: ClientDeps) =>
  getJson<CatalogSearch>(catalogSearchPath(category, term), deps);

/** `created` is false when the work was already in the collection (the API answers 200 instead of 201). */
export async function addToCollection(
  input: { category: Category; externalId: string },
  deps: ClientDeps = defaultDeps(),
): Promise<{ created: boolean; item: CollectionItem }> {
  const { status, data } = await sendRaw<CollectionItem>("POST", "/api/collection", input, deps);
  return { created: status === 201, item: data };
}
export const getCollection = (path: string, deps?: ClientDeps) =>
  getJson<CollectionPage>(path, deps);
export const updateCollectionItem = (
  id: number,
  change: { note?: string; hidden?: boolean },
  deps?: ClientDeps,
) => patchJson<CollectionItem>(`/api/collection/${seg(id)}`, change, deps);
export const removeCollectionItem = (id: number) => deleteRequest(`/api/collection/${seg(id)}`);

export const getHighlights = (deps?: ClientDeps) =>
  getJson<HighlightOverview>("/api/highlights", deps);
export const addHighlight = (input: { category: Category | null; collectionItemId: number }) =>
  postJson<Highlight>("/api/highlights", input);
export const removeHighlight = (id: number) => deleteRequest(`/api/highlights/${seg(id)}`);
export const reorderHighlights = (input: { category: Category | null; ids: number[] }) =>
  putJson<HighlightList>("/api/highlights/order", input);
