-- The "forgot my password" e-mail flow was removed (FR-01a dropped); its token table is no longer used.
DROP TABLE password_reset_tokens;
