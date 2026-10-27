import { describe, expect, it, vi } from "vitest";
import { ApiError, checkSlug, postJson, type ClientDeps } from "./api-client";

const json = (status: number, body?: unknown, headers: Record<string, string> = {}) =>
  new Response(body === undefined ? null : JSON.stringify(body), { status, headers });

/** Fake browser: a cookie jar that GET /api/auth/csrf fills, and a scripted fetch. */
function setup(responses: Response[], jar: Record<string, string> = {}) {
  const calls: { url: string; init?: RequestInit }[] = [];
  const queue = [...responses];
  const deps: ClientDeps = {
    getCookie: (name) => jar[name],
    fetch: vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      calls.push({ url, init });
      if (url === "/api/auth/csrf") {
        jar["XSRF-TOKEN"] = `token-${calls.filter((c) => c.url === url).length}`;
        return new Response(null, { status: 204 });
      }
      return queue.shift() ?? json(500);
    }) as typeof fetch,
  };
  return { deps, calls, jar };
}

const headerOf = (call: { init?: RequestInit }, name: string) =>
  (call.init?.headers as Record<string, string>)[name];

describe("postJson", () => {
  it("fetches the CSRF cookie first, then sends it in X-XSRF-TOKEN", async () => {
    const { deps, calls } = setup([json(201, { email: "a@b.co", slug: "abc" })]);
    const me = await postJson("/api/auth/signup", { email: "a@b.co" }, deps);
    expect(me).toEqual({ email: "a@b.co", slug: "abc" });
    expect(calls.map((c) => c.url)).toEqual(["/api/auth/csrf", "/api/auth/signup"]);
    expect(headerOf(calls[1], "X-XSRF-TOKEN")).toBe("token-1");
    expect(calls[1].init?.method).toBe("POST");
    expect(calls[1].init?.credentials).toBe("same-origin");
  });

  it("reuses an existing cookie without calling /csrf", async () => {
    const { deps, calls } = setup([json(204)], { "XSRF-TOKEN": "cached" });
    await postJson("/api/auth/logout", undefined, deps);
    expect(calls.map((c) => c.url)).toEqual(["/api/auth/logout"]);
    expect(headerOf(calls[0], "X-XSRF-TOKEN")).toBe("cached");
  });

  it("retries once with a fresh token after a 403", async () => {
    const { deps, calls } = setup([json(403), json(200, { email: "a@b.co", slug: null })], {
      "XSRF-TOKEN": "stale",
    });
    await postJson("/api/auth/login", {}, deps);
    expect(calls.map((c) => c.url)).toEqual([
      "/api/auth/login",
      "/api/auth/csrf",
      "/api/auth/login",
    ]);
    expect(headerOf(calls[2], "X-XSRF-TOKEN")).toBe("token-1");
  });

  it("throws ApiError with the code and Retry-After", async () => {
    const { deps } = setup([json(429, { error: "TOO_MANY_ATTEMPTS" }, { "Retry-After": "120" })]);
    const failure = await postJson("/api/auth/login", {}, deps).catch((e: unknown) => e);
    expect(failure).toBeInstanceOf(ApiError);
    expect(failure).toMatchObject({
      status: 429,
      code: "TOO_MANY_ATTEMPTS",
      retryAfterSeconds: 120,
    });
  });

  it("throws ApiError without a code when the body is not JSON", async () => {
    const { deps } = setup([new Response("<html>", { status: 502 })], { "XSRF-TOKEN": "t" });
    await expect(postJson("/api/auth/login", {}, deps)).rejects.toMatchObject({
      status: 502,
      code: undefined,
    });
  });
});

describe("checkSlug", () => {
  it("URL-encodes the slug in the path and does not need CSRF", async () => {
    const { deps, calls } = setup([
      json(200, { slug: "a/b", available: false, reason: "INVALID_CHARACTERS" }),
    ]);
    const answer = await checkSlug("a/b", deps);
    expect(calls.map((c) => c.url)).toEqual(["/api/slugs/a%2Fb/availability"]);
    expect(answer.reason).toBe("INVALID_CHARACTERS");
  });
});
