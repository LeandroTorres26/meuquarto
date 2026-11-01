// Copies the design system CSS from docs/ into src/styles/ (the Docker build context is frontend/,
// so the app cannot read docs/). Run `npm run sync:design` whenever the sources in docs/ change.
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const out = join(root, "frontend", "src", "styles");

const files = [
  ["docs/design-system/assets/Code/meuquarto.tokens.css", "meuquarto.tokens.css"],
  ["docs/design-system/components/bundle.css", "components.css"],
  ["docs/design/telas/site.css", "site.css"],
];

mkdirSync(out, { recursive: true });
for (const [source, target] of files) {
  const header = `/* GENERATED COPY of ${source}. Do not edit here: change the source and run \`npm run sync:design\`. */\n`;
  writeFileSync(join(out, target), header + readFileSync(join(root, source), "utf8"));
}

// Decorative covers used by the sign-up and sign-in art panel and the landing page shelf.
const covers = join(root, "frontend", "public", "covers");
mkdirSync(covers, { recursive: true });
for (const name of ["c01", "c02", "c06", "c07", "c09", "c10", "c11", "c12"]) {
  copyFileSync(join(root, "docs/design/telas/covers", `${name}.svg`), join(covers, `${name}.svg`));
}
