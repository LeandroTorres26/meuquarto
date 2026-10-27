// Browser-side API client. Talks only to this origin (/api/*, proxied to the backend by proxy.ts),
// so it needs no API address and holds no secrets.

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

/** POST JSON with the CSRF header. Retries once with a fresh token if the API answers 403. */
export async function postJson<T = void>(
  path: string,
  body?: unknown,
  deps: ClientDeps = defaultDeps(),
): Promise<T> {
  for (let attempt = 0; attempt < 2; attempt++) {
    const token = await ensureCsrfToken(deps, attempt > 0);
    const res = await deps.fetch(path, {
      method: "POST",
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
    return res.status === 204 ? (undefined as T) : ((await res.json()) as T);
  }
  throw new ApiError(403, undefined);
}

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
