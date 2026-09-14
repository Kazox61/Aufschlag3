-- Auth schema per docs/milestone-1-auth.md §2.
-- Conventions: UUID PKs, timestamptz (UTC), enums as text + CHECK,
-- created_at/updated_at on all tables (updated_at maintained by trigger).

CREATE EXTENSION IF NOT EXISTS citext;

CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE users (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email             citext UNIQUE NOT NULL,
    name              text NOT NULL,
    email_verified_at timestamptz,        -- NULL until email verification ships;
                                          -- social auto-linking must know verified vs not
    is_super_admin    boolean NOT NULL DEFAULT false,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now()
);

CREATE TRIGGER users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE auth_identities (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid NOT NULL REFERENCES users ON DELETE CASCADE,
    provider      text NOT NULL CHECK (provider IN ('EMAIL', 'GOOGLE', 'APPLE')),
    subject       text NOT NULL,          -- lowercased email | google sub | apple sub;
                                          -- text is case-sensitive: EMAIL subjects MUST be
                                          -- stored trimmed + lowercased
    password_hash text,                   -- only for EMAIL
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (provider, subject),
    UNIQUE (user_id, provider)
);

CREATE INDEX idx_auth_identities_user_id ON auth_identities (user_id);

CREATE TRIGGER auth_identities_set_updated_at
    BEFORE UPDATE ON auth_identities
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE refresh_tokens (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid NOT NULL REFERENCES users ON DELETE CASCADE,
    family_id      uuid NOT NULL,         -- id of the session's first token; reuse detection
                                          -- revokes the whole family in one UPDATE
    token_hash     text UNIQUE NOT NULL,  -- sha256, never the raw token
    expires_at     timestamptz NOT NULL,
    revoked_at     timestamptz,           -- set on rotation, logout, or family kill
    replaced_by_id uuid REFERENCES refresh_tokens,
                                          -- successor after rotation; enables the ~30 s grace
                                          -- window (idempotent client retry returns the same
                                          -- successor pair instead of killing the family)
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);

CREATE TRIGGER refresh_tokens_set_updated_at
    BEFORE UPDATE ON refresh_tokens
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE password_reset_tokens (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL REFERENCES users ON DELETE CASCADE,
    token_hash text UNIQUE NOT NULL,      -- sha256, single-use
    expires_at timestamptz NOT NULL,      -- ~30 min
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_reset_tokens_user_id ON password_reset_tokens (user_id);

CREATE TRIGGER password_reset_tokens_set_updated_at
    BEFORE UPDATE ON password_reset_tokens
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
