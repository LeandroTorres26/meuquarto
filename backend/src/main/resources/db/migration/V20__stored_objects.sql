-- Files kept in the database (avatars). The key is chosen by the code, never by a user.
CREATE TABLE stored_objects (
    key          VARCHAR(100) PRIMARY KEY,
    content      BYTEA        NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
