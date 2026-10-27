"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { logOut } from "@/lib/api-client";

/** Sign out: POST with CSRF, then back to S3. */
export function LogoutButton() {
  const router = useRouter();
  const [sending, setSending] = useState(false);
  const [failed, setFailed] = useState(false);

  async function onClick() {
    setSending(true);
    setFailed(false);
    try {
      await logOut();
      router.replace("/entrar");
    } catch {
      setFailed(true);
      setSending(false);
    }
  }

  return (
    <>
      <button
        type="button"
        className="mq-btn mq-btn--secondary"
        onClick={onClick}
        disabled={sending}
      >
        Sair
      </button>
      <p className="t-sm" role="status">
        {failed ? "Não foi possível sair agora. Tente de novo." : ""}
      </p>
    </>
  );
}
