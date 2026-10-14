import "server-only";

export type ApiHealth = "UP" | "DOWN";

// Server-side only: the API address never reaches the browser bundle.
const apiUrl = () => process.env.API_URL ?? "http://localhost:8080";

export async function getApiHealth(): Promise<ApiHealth> {
  try {
    const res = await fetch(`${apiUrl()}/api/health`, {
      cache: "no-store",
      signal: AbortSignal.timeout(3000),
    });
    if (!res.ok) return "DOWN";
    const body: { status?: string } = await res.json();
    return body.status === "UP" ? "UP" : "DOWN";
  } catch {
    return "DOWN";
  }
}
