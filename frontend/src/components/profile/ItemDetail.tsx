import { ExternalLink, ImageOff } from "lucide-react";
import type { CSSProperties, ReactNode } from "react";
import {
  CATEGORY_META,
  linkLabel,
  originCaption,
  safeLinkUrl,
  shapeClasses,
  tiltFactor,
  type Category,
  type PublicItemDetail,
} from "@/lib/profile-view";
import { ItemCover } from "./ItemCover";

const HEADING_ID = "item-h";

function CoverSlot({ category, children }: { category: Category; children: ReactNode }) {
  const meta = CATEGORY_META[category];
  return (
    <div className={`detail__cover${meta.shape === "vinyl" ? " detail__cover--wide" : ""}`}>
      {children}
    </div>
  );
}

/** S17 body once the item arrived: cover, title, year, note on paper, origin, external links. */
export function ItemDetailBody({
  detail,
  ownerName,
}: {
  detail: PublicItemDetail;
  ownerName: string;
}) {
  const { item } = detail;
  const meta = CATEGORY_META[item.category];
  const links = detail.links.flatMap((link) => {
    const href = safeLinkUrl(link.url);
    return href ? [{ provider: link.provider, href }] : [];
  });
  return (
    <article className="detail">
      <CoverSlot category={item.category}>
        <div
          className={`mq-item ${shapeClasses(meta.shape)}`}
          style={{ "--tilt-f": tiltFactor(item.ref) } as CSSProperties}
        >
          <ItemCover item={item} alt={`Capa de ${item.title}`} eager showTag />
        </div>
      </CoverSlot>
      <div className="detail__info">
        <div className="detail__head">
          <h2 id={HEADING_ID} className="detail__title">
            {item.title}
          </h2>
          {item.year ? <p className="detail__meta">{item.year}</p> : null}
        </div>
        <div className="detail__owner">
          {item.note ? (
            <p className="detail__note">
              <span className="sr">Nota de {ownerName}: </span>
              {item.note}
            </p>
          ) : null}
          <p className="t-cap">{originCaption(detail.origin)}</p>
        </div>
        {links.length > 0 ? (
          <div className="detail__links">
            {links.map((link) => (
              <a
                key={link.provider}
                className="mq-btn mq-btn--secondary"
                href={link.href}
                target="_blank"
                rel="noopener noreferrer"
              >
                {linkLabel(link.provider)}
                <ExternalLink className="icon" aria-hidden="true" />
                <span className="sr"> (abre em nova aba)</span>
              </a>
            ))}
          </div>
        ) : null}
      </div>
    </article>
  );
}

/** Loading: the cover slot already has the medium's proportion when known from the clicked card. */
export function ItemDetailLoading({ category }: { category: Category | null }) {
  const shown: Category = category ?? "MOVIE";
  const meta = CATEGORY_META[shown];
  return (
    <article className="detail detail--loading" aria-busy="true">
      <CoverSlot category={shown}>
        <div
          className={`mq-item mq-item--loading ${shapeClasses(meta.shape)}`}
          style={{ "--tilt-f": 0 } as CSSProperties}
          aria-hidden="true"
        >
          <figure className="mq-item__cover">
            {meta.shape === "dvd" ? <span className="mq-spine" /> : null}
            <span className="mq-art" />
          </figure>
        </div>
      </CoverSlot>
      <div className="detail__info">
        <div className="detail__head">
          <h2 id={HEADING_ID} className="sr">
            Carregando item
          </h2>
          <span className="mq-sk is-title" />
          <span className="mq-sk" style={{ width: "45%" }} />
        </div>
        <span className="mq-sk" style={{ width: "60%" }} />
        <div className="detail__actions">
          <span className="mq-sk is-btn" />
        </div>
      </div>
    </article>
  );
}

/** The item is not in the profile (anymore), or the reference is not valid. */
export function ItemDetailGone({ ownerName, onClose }: { ownerName: string; onClose: () => void }) {
  return (
    <div className="detail-gone">
      <ImageOff className="icon" aria-hidden="true" />
      <div className="pair-2">
        <h2 id={HEADING_ID} className="t-h3">
          Esse item saiu do quarto.
        </h2>
        <p className="t-sm">{ownerName} tirou ele dos destaques.</p>
      </div>
      <button type="button" className="mq-btn mq-btn--secondary" onClick={onClose}>
        Voltar ao perfil
      </button>
    </div>
  );
}

/** The request failed for another reason (network, rate limit): say so and offer to try again. */
export function ItemDetailError({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="detail-gone">
      <ImageOff className="icon" aria-hidden="true" />
      <div className="pair-2">
        <h2 id={HEADING_ID} className="t-h3">
          Não foi possível abrir esse item.
        </h2>
        <p className="t-sm">Confira sua conexão e tente de novo.</p>
      </div>
      <button type="button" className="mq-btn mq-btn--secondary" onClick={onRetry}>
        Tentar de novo
      </button>
    </div>
  );
}
