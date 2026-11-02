"use client";

import dynamic from "next/dynamic";
import type { RoomSection } from "./objects/buildRoom";

// The room is loaded only when this page is opened, as its own chunk: the profile (S4) never pays
// for it. It cannot render on the server (it measures the window and reads the input model), so the
// server sends this placeholder and the scene takes over in the browser.
const RoomScene = dynamic(() => import("./RoomScene"), {
  ssr: false,
  loading: () => (
    <div className="page room" aria-busy="true">
      <p className="t-sm" role="status" style={{ padding: "var(--l4)" }}>
        Carregando o quarto…
      </p>
    </div>
  ),
});

export function RoomLoader(props: { slug: string; ownerName: string; sections: RoomSection[] }) {
  return <RoomScene {...props} />;
}
