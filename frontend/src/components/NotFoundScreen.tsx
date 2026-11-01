import Link from "next/link";
import { ProfileTopBar } from "@/components/profile/ProfileTopBar";

/**
 * S16 (docs/design/telas/404.html). One screen for both 404s: `profile` is an address nobody lives
 * at (unknown, reserved or malformed slug, which the API answers alike), `route` is any other path.
 */
export function NotFoundScreen({ kind }: { kind: "profile" | "route" }) {
  return (
    <div className="page">
      <ProfileTopBar />
      <main className="wrap sections">
        <div className="grid" style={{ paddingTop: "var(--page-top)", alignItems: "center" }}>
          <div
            className="span-all t-span-4 d-span-5"
            aria-hidden="true"
            style={{ position: "relative", height: 300, borderBottom: "6px solid var(--border)" }}
          >
            <Frame left="4%" top={40} width="30%" ratio="2 / 3" rotate={-2} />
            <Frame left="40%" top={16} width="26%" ratio="2 / 3" rotate={1.4} />
            <Frame left="70%" top={150} width="26%" ratio="1 / 1" rotate={-1.2} />
            <p className="mq-slip" style={{ left: "6%", right: "auto", bottom: 40 }}>
              volto já
            </p>
          </div>
          <div
            className="span-all t-span-4 d-span-6 stack"
            style={{ gap: "var(--l3)", gridColumnEnd: -1 }}
          >
            {kind === "profile" ? <ProfileCopy /> : <RouteCopy />}
          </div>
        </div>
      </main>
    </div>
  );
}

function Frame(props: { left: string; top: number; width: string; ratio: string; rotate: number }) {
  return (
    <div
      style={{
        position: "absolute",
        left: props.left,
        top: props.top,
        width: props.width,
        aspectRatio: props.ratio,
        border: "2px dashed var(--border-strong)",
        borderRadius: "var(--radius-sm)",
        transform: `rotate(${props.rotate}deg)`,
      }}
    />
  );
}

function ProfileCopy() {
  return (
    <>
      <div className="pair-2">
        <h1 className="t-h1">Ninguém mora aqui ainda</h1>
        <p className="t-lead muted measure-s">
          Não existe perfil nesse endereço. Pode ser que ele tenha mudado, ou que esteja livre para
          você.
        </p>
      </div>
      <div className="actions">
        <Link href="/cadastro" prefetch={false} className="mq-btn mq-btn--primary">
          Usar esse endereço
        </Link>
        <Link href="/" className="mq-btn mq-btn--secondary">
          Ir para o início
        </Link>
      </div>
    </>
  );
}

function RouteCopy() {
  return (
    <>
      <div className="pair-2">
        <h1 className="t-h1">Essa página não existe</h1>
        <p className="t-lead muted measure-s">
          O endereço que você abriu não leva a lugar nenhum. Pode ter mudado de lugar ou ter sido
          digitado errado.
        </p>
      </div>
      <div className="actions">
        <Link href="/" className="mq-btn mq-btn--primary">
          Ir para o início
        </Link>
        <Link href="/cadastro" prefetch={false} className="mq-btn mq-btn--secondary">
          Crie o seu
        </Link>
      </div>
    </>
  );
}
