import type { Metadata } from "next";
import { SignInArt } from "@/components/AuthArt";
import { AuthLayout } from "@/components/AuthLayout";
import { ResetPasswordForm } from "@/components/ResetPasswordForm";

// The address carries the reset token: no referrer (also sent as a header, see next.config.ts) and
// no indexing. The token is removed from the address by ResetPasswordForm right after load.
export const metadata: Metadata = {
  title: "Nova senha | meuquarto",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function ResetPasswordPage() {
  return (
    <AuthLayout
      action={{ href: "/entrar", label: "Entrar" }}
      note="a porta continua aberta"
      art={<SignInArt />}
    >
      <ResetPasswordForm />
    </AuthLayout>
  );
}
