"use client";

import { Search } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, useTransition, type FormEvent } from "react";
import { listingHref, MAX_QUERY_LENGTH, ORDERS, type Order } from "@/lib/category-listing";
import type { Category } from "@/lib/profile-view";

const TYPING_DELAY_MS = 350;

/**
 * Search and sort of the category listing (S18). The state lives in the URL (`?q=&ordem=`), so the
 * server renders each combination and a link can be shared. It is a plain GET form: without
 * JavaScript, Enter in the field and the radios still work through the submit button; with it,
 * typing (after a short pause) and changing the order replace the URL without a history entry.
 */
export function CategoryTools({
  slug,
  category,
  q,
  ordem,
}: {
  slug: string;
  category: Category;
  q: string;
  ordem: Order;
}) {
  const router = useRouter();
  const [, startTransition] = useTransition();
  const [text, setText] = useState(q);
  const [order, setOrder] = useState<Order>(ordem);
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const action = listingHref(slug, category, {});

  // The URL changed from outside (back button, "Limpar busca"): follow it.
  const [seen, setSeen] = useState({ q, ordem });
  if (seen.q !== q || seen.ordem !== ordem) {
    setSeen({ q, ordem });
    setText(q);
    setOrder(ordem);
  }

  useEffect(() => () => clearTimeout(timer.current), []);

  function go(nextText: string, nextOrder: Order) {
    clearTimeout(timer.current);
    const href = listingHref(slug, category, { q: nextText.trim(), ordem: nextOrder });
    startTransition(() => router.replace(href, { scroll: false }));
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    go(text, order);
  }

  return (
    <form className="cat-tools" action={action} method="get" role="search" onSubmit={onSubmit}>
      <div className="search">
        <label htmlFor="q" className="sr">
          Buscar por título
        </label>
        <Search className="icon" aria-hidden="true" />
        <input
          id="q"
          name="q"
          className="input"
          type="search"
          placeholder="Buscar por título"
          autoComplete="off"
          maxLength={MAX_QUERY_LENGTH}
          value={text}
          onChange={(event) => {
            const value = event.target.value;
            setText(value);
            clearTimeout(timer.current);
            timer.current = setTimeout(() => go(value, order), TYPING_DELAY_MS);
          }}
        />
      </div>
      <fieldset className="seg">
        <legend className="sr">Ordenar por</legend>
        {ORDERS.map((option) => (
          <label key={option.value}>
            <input
              type="radio"
              name="ordem"
              value={option.value}
              checked={order === option.value}
              onChange={() => {
                setOrder(option.value);
                go(text, option.value);
              }}
            />
            {option.label}
          </label>
        ))}
      </fieldset>
      <noscript>
        <button type="submit" className="mq-btn mq-btn--secondary">
          Buscar
        </button>
      </noscript>
    </form>
  );
}
