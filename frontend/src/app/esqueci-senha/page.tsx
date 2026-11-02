import type { Metadata } from "next";
import { SignInArt } from "@/components/AuthArt";
import { AuthLayout } from "@/components/AuthLayout";
import { ForgotPasswordForm } from "@/components/ForgotPasswordForm";

export const metadata: Metadata = {
  title: "Esqueci minha senha | meuquarto",
  robots: { index: false, follow: false },
};

export default function ForgotPasswordPage() {
  return (
    <AuthLayout
      action={{ href: "/entrar", label: "Entrar" }}
      note="a porta continua aberta"
      art={<SignInArt />}
    >
      <ForgotPasswordForm />
    </AuthLayout>
  );
}
