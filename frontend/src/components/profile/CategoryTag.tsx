import { CATEGORY_META, type Category } from "@/lib/profile-view";
import { CategoryIcon } from "./CategoryIcon";

/** Category sticker (design system CategoryTag). `count` adds the collection size, as in the profile header. */
export function CategoryTag({ category, count }: { category: Category; count?: number }) {
  const meta = CATEGORY_META[category];
  return (
    <span className={`mq-tag mq-tag--${meta.tag}`}>
      <CategoryIcon category={category} className="mq-icon icon" />
      {meta.sticker}
      {count !== undefined ? <span className="mq-tag__count">{count}</span> : null}
    </span>
  );
}
