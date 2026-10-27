import type { Metadata } from "next";
import { SignUpArt } from "@/components/AuthArt";
import { AuthLayout } from "@/components/AuthLayout";
import { SignupForm } from "@/components/SignupForm";

export const metadata: Metadata = { title: "Criar conta no meuquarto" };

// Signed-in visitors are redirected to /painel/conta by proxy.ts.
export default function SignUpPage() {
  return (
    <AuthLayout
      action={{ href: "/entrar", label: "Entrar" }}
      note="meu quarto, minhas regras"
      art={<SignUpArt />}
    >
      <SignupForm />
    </AuthLayout>
  );
}
