import type { Metadata } from "next";
import { SignInArt } from "@/components/AuthArt";
import { AuthLayout } from "@/components/AuthLayout";
import { LoginForm } from "@/components/LoginForm";

export const metadata: Metadata = { title: "Entrar no meuquarto" };

// Signed-in visitors are redirected to /painel/conta by proxy.ts.
export default function SignInPage() {
  return (
    <AuthLayout
      action={{ href: "/cadastro", label: "Crie o seu" }}
      note="a parede te esperando"
      art={<SignInArt />}
    >
      <LoginForm />
    </AuthLayout>
  );
}
