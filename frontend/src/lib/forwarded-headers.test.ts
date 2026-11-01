import { describe, expect, it } from "vitest";
import { apiRequestHeaders, clientAddress, trustProxyHeaders } from "./forwarded-headers";

const spoofed = new Headers({
  "x-forwarded-for": "6.6.6.6, 198.51.100.7",
  "x-forwarded-proto": "https",
  "x-forwarded-host": "evil.example",
  "x-forwarded-port": "1234",
  forwarded: "for=6.6.6.6",
  "x-real-ip": "6.6.6.6",
  cookie: "SESSION=abc",
  "x-xsrf-token": "t",
});

describe("trustProxyHeaders", () => {
  it("is off unless explicitly true", () => {
    expect(trustProxyHeaders(undefined)).toBe(false);
    expect(trustProxyHeaders("")).toBe(false);
    expect(trustProxyHeaders("1")).toBe(false);
    expect(trustProxyHeaders(" TRUE ")).toBe(true);
  });
});

describe("clientAddress", () => {
  it("takes the entry the trusted proxy appended, not what the client claimed", () => {
    expect(clientAddress("6.6.6.6, 198.51.100.7")).toBe("198.51.100.7");
    expect(clientAddress("2001:db8::7")).toBe("2001:db8::7");
  });

  it("rejects missing and malformed values", () => {
    expect(clientAddress(null)).toBeNull();
    expect(clientAddress("")).toBeNull();
    expect(clientAddress("not-an-ip")).toBeNull();
    expect(clientAddress("1.2.3.4, <script>")).toBeNull();
    expect(clientAddress("1.2.3.4:5678")).toBeNull();
  });
});

describe("apiRequestHeaders", () => {
  it("drops every forwarding header when no trusted proxy is configured", () => {
    const headers = apiRequestHeaders(spoofed, false);
    for (const name of [
      "x-forwarded-for",
      "x-forwarded-proto",
      "x-forwarded-host",
      "x-forwarded-port",
      "forwarded",
      "x-real-ip",
    ]) {
      expect(headers.has(name)).toBe(false);
    }
  });

  it("keeps the other headers untouched", () => {
    const headers = apiRequestHeaders(spoofed, true);
    expect(headers.get("cookie")).toBe("SESSION=abc");
    expect(headers.get("x-xsrf-token")).toBe("t");
  });

  it("replaces X-Forwarded-For with a single address behind a trusted proxy", () => {
    const headers = apiRequestHeaders(spoofed, true);
    expect(headers.get("x-forwarded-for")).toBe("198.51.100.7");
    expect(headers.get("x-forwarded-proto")).toBe("https");
  });

  it("never forwards host, port, Forwarded or X-Real-IP", () => {
    const headers = apiRequestHeaders(spoofed, true);
    for (const name of ["x-forwarded-host", "x-forwarded-port", "forwarded", "x-real-ip"]) {
      expect(headers.has(name)).toBe(false);
    }
  });

  it("drops an invalid address or protocol even when trusted", () => {
    const headers = apiRequestHeaders(
      new Headers({ "x-forwarded-for": "garbage", "x-forwarded-proto": "javascript" }),
      true,
    );
    expect(headers.has("x-forwarded-for")).toBe(false);
    expect(headers.has("x-forwarded-proto")).toBe(false);
  });

  it("does not mutate the incoming headers", () => {
    apiRequestHeaders(spoofed, true);
    expect(spoofed.get("x-forwarded-for")).toBe("6.6.6.6, 198.51.100.7");
  });
});
