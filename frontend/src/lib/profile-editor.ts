// Profile editor (S6): types, limits, messages and the avatar file check. Pure; unit tested.

import { collectionMessage } from "./collection";
import { slugReasonMessage } from "./auth-errors";

/** GET /api/profile. `avatarUrl` is null without avatar; `availableThemes` are the ids the user may apply. */
export type OwnProfile = {
  slug: string;
  displayName: string;
  bio: string;
  avatarUrl: string | null;
  theme: string;
  availableThemes: string[];
};

export type ProfileChange = { displayName: string; bio: string; slug: string; theme: string };

// Same limits as the backend (DisplayName, Bio, AvatarProcessor); the server is the one that enforces them.
export const NAME_MAX = 40;
export const BIO_MAX = 160;
export const AVATAR_MAX_BYTES = 2 * 1024 * 1024;
export const AVATAR_TYPES = ["image/jpeg", "image/png"];

/** Free themes shown in the picker, in the design's order. */
export const THEME_LABELS: Record<string, string> = { noite: "Noite", dia: "Dia" };

/** Characters of a text as the server counts them (code points, so an emoji is one). */
export const charCount = (text: string): number => Array.from(text).length;

export type EditorField = "name" | "bio" | "slug" | "theme" | "avatar" | "form";
export type EditorError = { field: EditorField; message: string };

const BY_CODE: Record<string, EditorError> = {
  DISPLAY_NAME_REQUIRED: { field: "name", message: "Escreva um nome." },
  DISPLAY_NAME_TOO_LONG: {
    field: "name",
    message: `O nome pode ter no máximo ${NAME_MAX} caracteres.`,
  },
  DISPLAY_NAME_INVALID: {
    field: "name",
    message: "O nome não pode ter quebra de linha nem caracteres de controle.",
  },
  BIO_TOO_LONG: { field: "bio", message: `A bio pode ter no máximo ${BIO_MAX} caracteres.` },
  BIO_INVALID: { field: "bio", message: "A bio tem caracteres que não podemos guardar." },
  THEME_UNKNOWN: { field: "theme", message: "Esse tema não existe." },
  THEME_LOCKED: { field: "theme", message: "Esse tema ainda não está liberado para você." },
  SLUG_TOO_SHORT: { field: "slug", message: "O endereço precisa ter pelo menos 3 caracteres." },
  SLUG_TOO_LONG: { field: "slug", message: "O endereço pode ter no máximo 30 caracteres." },
  SLUG_INVALID_CHARACTERS: { field: "slug", message: "Use só letras minúsculas, números e hífen." },
  SLUG_INVALID_HYPHEN: {
    field: "slug",
    message: "O hífen não pode ficar no começo, no fim nem aparecer duas vezes seguidas.",
  },
  SLUG_RESERVED: { field: "slug", message: "Esse endereço é reservado. Escolha outro." },
  SLUG_TAKEN: { field: "slug", message: "Esse endereço já está em uso." },
  AVATAR_EMPTY: { field: "avatar", message: "Escolha uma imagem." },
  AVATAR_TOO_LARGE: { field: "avatar", message: "A imagem passa de 2 MB. Escolha uma menor." },
  AVATAR_UNSUPPORTED_TYPE: {
    field: "avatar",
    message:
      "Esse arquivo não é uma imagem JPG ou PNG. Outros formatos, como WebP e GIF, não são aceitos.",
  },
  AVATAR_DIMENSIONS_TOO_LARGE: {
    field: "avatar",
    message:
      "A imagem é grande demais (mais de 4096 pixels de largura ou altura). Reduza e tente de novo.",
  },
  AVATAR_UNREADABLE: {
    field: "avatar",
    message: "Não conseguimos ler essa imagem. Ela pode estar corrompida; tente outra foto.",
  },
  STORAGE_UNAVAILABLE: {
    field: "avatar",
    message: "Não foi possível guardar a foto agora. Tente de novo em instantes.",
  },
};

/** Maps an API error code (plus status and Retry-After) to the field and the pt-BR text. */
export function profileMessage(
  code: string | undefined,
  status?: number,
  retryAfterSeconds?: number,
): EditorError {
  if (code && BY_CODE[code]) return BY_CODE[code];
  return { field: "form", message: collectionMessage(code, status, retryAfterSeconds) };
}

/** Why a slug state coming from the availability check should be shown as an error, in pt-BR. */
export const slugProblem = (reason: string | null | undefined): string => slugReasonMessage(reason);

/** Quick check in the browser before uploading. The server decides by content; this only saves a round trip. */
export function avatarFileProblem(file: { size: number; type: string }): string | null {
  if (file.size === 0) return BY_CODE.AVATAR_EMPTY.message;
  if (file.size > AVATAR_MAX_BYTES) return BY_CODE.AVATAR_TOO_LARGE.message;
  if (!AVATAR_TYPES.includes(file.type)) return BY_CODE.AVATAR_UNSUPPORTED_TYPE.message;
  return null;
}
