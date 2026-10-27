export type FieldName = "email" | "password" | "slug" | "form";

export type FieldError = { field: FieldName; message: string };

const BY_CODE: Record<string, FieldError> = {
  INVALID_EMAIL: {
    field: "email",
    message: "Esse e-mail não parece válido. Confira e tente de novo.",
  },
  EMAIL_TAKEN: {
    field: "email",
    message: "Já existe uma conta com esse e-mail. Entre ou use outro.",
  },
  PASSWORD_TOO_SHORT: {
    field: "password",
    message: "A senha precisa ter pelo menos 8 caracteres.",
  },
  PASSWORD_TOO_LONG: { field: "password", message: "A senha pode ter no máximo 128 caracteres." },
  PASSWORD_TOO_COMMON: { field: "password", message: "Essa senha é muito comum. Escolha outra." },
  PASSWORD_SAME_AS_EMAIL: { field: "password", message: "A senha não pode ser igual ao e-mail." },
  SLUG_TOO_SHORT: { field: "slug", message: "O endereço precisa ter pelo menos 3 caracteres." },
  SLUG_TOO_LONG: { field: "slug", message: "O endereço pode ter no máximo 30 caracteres." },
  SLUG_INVALID_CHARACTERS: {
    field: "slug",
    message: "Use só letras minúsculas, números e hífen.",
  },
  SLUG_INVALID_HYPHEN: {
    field: "slug",
    message: "O hífen não pode ficar no começo, no fim nem aparecer duas vezes seguidas.",
  },
  SLUG_RESERVED: { field: "slug", message: "Esse endereço é reservado. Escolha outro." },
  SLUG_TAKEN: { field: "slug", message: "Esse endereço já está em uso." },
  INVALID_REQUEST: { field: "form", message: "Confira os campos e tente de novo." },
  INVALID_CREDENTIALS: {
    field: "form",
    message: "E-mail ou senha não conferem. Confira os dois e tente de novo.",
  },
};

const GENERIC: FieldError = {
  field: "form",
  message: "Algo deu errado do nosso lado. Tente de novo em instantes.",
};

const NETWORK: FieldError = {
  field: "form",
  message: "Não foi possível falar com o servidor. Confira sua conexão e tente de novo.",
};

/** "Tente de novo em X minutos." from a Retry-After value in seconds. */
export function waitMessage(retryAfterSeconds?: number): string {
  if (!retryAfterSeconds || retryAfterSeconds <= 0) {
    return "Muitas tentativas. Tente de novo em alguns minutos.";
  }
  const minutes = Math.max(1, Math.ceil(retryAfterSeconds / 60));
  return `Muitas tentativas. Tente de novo em ${minutes} ${minutes === 1 ? "minuto" : "minutos"}.`;
}

/** Maps a backend `{error}` code (plus status and Retry-After) to the field and the pt-BR text. */
export function messageFor(
  code: string | undefined,
  status?: number,
  retryAfterSeconds?: number,
): FieldError {
  if (code === "TOO_MANY_ATTEMPTS" || code === "TOO_MANY_REQUESTS" || status === 429) {
    return { field: "form", message: waitMessage(retryAfterSeconds) };
  }
  return (code && BY_CODE[code]) || GENERIC;
}

export const networkError = (): FieldError => NETWORK;

/** Slug availability reasons (GET /api/slugs/{slug}/availability) in pt-BR. */
const SLUG_REASONS: Record<string, string> = {
  TOO_SHORT: "Use pelo menos 3 caracteres.",
  TOO_LONG: "Use no máximo 30 caracteres.",
  INVALID_CHARACTERS: "Use só letras minúsculas, números e hífen.",
  INVALID_HYPHEN: "O hífen não pode ficar no começo, no fim nem aparecer duas vezes seguidas.",
  RESERVED: "Esse endereço é reservado. Escolha outro.",
  TAKEN: "Esse endereço já está em uso.",
};

export function slugReasonMessage(reason: string | null | undefined): string {
  return (reason && SLUG_REASONS[reason]) || "Esse endereço não está disponível.";
}
