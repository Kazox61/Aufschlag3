-- Clubs & memberships schema per docs/milestone-1-auth.md §2.
-- Conventions: UUID PKs, timestamptz (UTC), enums as text + CHECK,
-- created_at/updated_at on all tables (updated_at maintained by the V1 trigger fn).

CREATE TABLE clubs (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        text NOT NULL,
    slug        text UNIQUE NOT NULL,
    plan        text NOT NULL DEFAULT 'FREE_BETA',
    status      text NOT NULL DEFAULT 'TRIAL'
                CHECK (status IN ('TRIAL', 'ACTIVE', 'SUSPENDED', 'ARCHIVED')),
    timezone    text NOT NULL DEFAULT 'Europe/Berlin',
    settings    jsonb NOT NULL DEFAULT '{}', -- serialized ClubSettings from /core; the
                                              -- @Serializable class is the schema, validated
                                              -- on every write
    billing_ref text,                        -- future Stripe customer id
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TRIGGER clubs_set_updated_at
    BEFORE UPDATE ON clubs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE memberships (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid NOT NULL REFERENCES users ON DELETE CASCADE, -- GDPR delete
    club_id          uuid NOT NULL REFERENCES clubs,
    role             text NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    status           text NOT NULL
                     CHECK (status IN ('PENDING', 'ACTIVE', 'PAUSED', 'SUSPENDED', 'ENDED')),
    application_data jsonb,                 -- Mitgliedsantrag answers
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now()
);

-- PARTIAL unique index, NOT a plain UNIQUE(user_id, club_id): a plain unique constraint
-- would permanently block ex-members (ENDED row) from re-applying. ENDED rows stay as
-- history; a new PENDING row is allowed once the old one is ENDED.
CREATE UNIQUE INDEX idx_memberships_active_unique ON memberships (user_id, club_id)
    WHERE status != 'ENDED';

CREATE INDEX idx_memberships_club_id ON memberships (club_id);
CREATE INDEX idx_memberships_user_id ON memberships (user_id);

CREATE TRIGGER memberships_set_updated_at
    BEFORE UPDATE ON memberships
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
