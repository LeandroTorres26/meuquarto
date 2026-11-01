import { isIP } from "node:net";

/**
 * Headers the web app sends to the API when it proxies /api/* (ADR 0017).
 *
 * The API only believes X-Forwarded-For from a trusted peer (the web container), so whatever this
 * function puts there becomes the client address behind the per-IP rate limits. A browser can send
 * any X-Forwarded-* it likes, so every incoming value is dropped first and only rebuilt when the
 * deployment says a trusted reverse proxy (Caddy) sits in front and overwrites them.
 */
const FORWARDED = [
  "x-forwarded-for",
  "x-forwarded-proto",
  "x-forwarded-port",
  "x-forwarded-host",
  "forwarded",
  "x-real-ip",
];

/** TRUST_PROXY_HEADERS=true means a reverse proxy we control sets X-Forwarded-For/Proto. */
export const trustProxyHeaders = (value: string | undefined) =>
  value?.trim().toLowerCase() === "true";

/**
 * The client address a single trusted proxy saw: the right-most X-Forwarded-For entry, which that
 * proxy appended (anything to its left was supplied by the client). Null when it is not an IP.
 */
export function clientAddress(xForwardedFor: string | null): string | null {
  const last = xForwardedFor?.split(",").pop()?.trim() ?? "";
  return isIP(last) ? last : null;
}

export function apiRequestHeaders(incoming: Headers, trusted: boolean): Headers {
  const headers = new Headers(incoming);
  const address = trusted ? clientAddress(incoming.get("x-forwarded-for")) : null;
  const proto = trusted
    ? incoming.get("x-forwarded-proto")?.split(",").pop()?.trim().toLowerCase()
    : undefined;

  for (const name of FORWARDED) headers.delete(name);
  if (address) headers.set("x-forwarded-for", address);
  if (proto === "http" || proto === "https") headers.set("x-forwarded-proto", proto);
  return headers;
}
