-- What the profile editor (S6, FR-05) lets the owner set. All are optional except the theme, which
-- starts on the free default. The length limits repeat the domain rules, so bad data cannot get in
-- outside the API. avatar_key is only the object key in storage (ADR 0009), never a URL or a file name.
ALTER TABLE profiles
    ADD COLUMN display_name VARCHAR(40),
    ADD COLUMN bio          VARCHAR(160),
    ADD COLUMN avatar_key   VARCHAR(80),
    ADD COLUMN theme_id     VARCHAR(30) NOT NULL DEFAULT 'noite';

ALTER TABLE profiles
    ADD CONSTRAINT profiles_display_name_not_blank CHECK (display_name IS NULL OR btrim(display_name) <> ''),
    ADD CONSTRAINT profiles_avatar_key_format CHECK (avatar_key IS NULL OR avatar_key ~ '^avatars/[0-9a-f-]{36}\.jpg$');
