import { Suspense } from "react";
import { connection } from "next/server";
import { getApiHealth } from "@/lib/api";

async function ApiStatus() {
  await connection();
  const health = await getApiHealth();
  return <p role="status">API: {health === "UP" ? "online" : "indisponível"}</p>;
}

export default function Home() {
  return (
    <main>
      <h1>meuquarto</h1>
      <Suspense fallback={<p>Verificando a API…</p>}>
        <ApiStatus />
      </Suspense>
    </main>
  );
}
