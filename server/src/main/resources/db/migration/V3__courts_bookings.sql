-- Courts, price rules & bookings schema per docs/milestone-1-auth.md §2 ("Specced now,
-- migrated in milestone 2"). Conventions: UUID PKs, timestamptz (UTC), enums as text + CHECK,
-- created_at/updated_at on all tables (updated_at maintained by the V1 trigger fn).

CREATE TABLE courts (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id             uuid NOT NULL REFERENCES clubs,
    name                text NOT NULL,
    surface             text NOT NULL
                        CHECK (surface IN ('CLAY', 'HARD', 'GRASS', 'CARPET', 'ARTIFICIAL_TURF')),
    indoor              boolean NOT NULL DEFAULT false,
    active              boolean NOT NULL DEFAULT true,
    slot_minutes        int NOT NULL DEFAULT 60 CHECK (slot_minutes > 0),
                                              -- PER COURT, not per club: paddle is
                                              -- conventionally 90 min vs 60 for tennis
    default_price_cents int NOT NULL DEFAULT 0 CHECK (default_price_cents >= 0),
    member_discount_pct int NOT NULL DEFAULT 100 CHECK (member_discount_pct BETWEEN 0 AND 100),
    paused_discount_pct int NOT NULL DEFAULT 0 CHECK (paused_discount_pct BETWEEN 0 AND 100),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_courts_club_id ON courts (club_id);

CREATE TRIGGER courts_set_updated_at
    BEFORE UPDATE ON courts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE price_rules (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id     uuid NOT NULL REFERENCES courts ON DELETE CASCADE,
    days_of_week jsonb NOT NULL,             -- JSON array of ISO day numbers (1=Mon..7=Sun);
                                              -- jsonb rather than a native int[] to reuse the
                                              -- same kotlinx.serialization-backed column
                                              -- pattern as clubs.settings
    start_time   time NOT NULL,              -- club-local, half-open [start_time, end_time)
    end_time     time NOT NULL,
    valid_from   date,                       -- optional season validity (e.g. Hallensaison)
    valid_to     date,
    price_cents  int NOT NULL CHECK (price_cents >= 0),
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CHECK (end_time > start_time)
    -- Non-overlap (within overlapping validity) is validated in application code; PUT
    -- replaces a court's whole rule set in one transaction (wholesale replace).
);

CREATE INDEX idx_price_rules_court_id ON price_rules (court_id);

CREATE TRIGGER price_rules_set_updated_at
    BEFORE UPDATE ON price_rules
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Needed for the EXCLUDE constraint below: lets a plain equality column (court_id) combine
-- with a range-overlap column (tstzrange) in a single GiST index.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE bookings (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id           uuid NOT NULL REFERENCES clubs,
                                              -- denormalized from court on purpose: every
                                              -- tenant-isolation check and "my bookings across
                                              -- clubs" query works without a join through courts
    court_id          uuid NOT NULL REFERENCES courts,
    user_id           uuid REFERENCES users ON DELETE SET NULL,
                                              -- NULL = admin block or GDPR-anonymized booking
    starts_at         timestamptz NOT NULL,
    ends_at           timestamptz NOT NULL,
    status            text NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED', 'BLOCKED')),
    note              text,                  -- block label ("Training", "Platzpflege");
                                              -- NULL on normal bookings
    base_price_cents  int NOT NULL CHECK (base_price_cents >= 0),
    discount_pct      int NOT NULL CHECK (discount_pct BETWEEN 0 AND 100),
    final_price_cents int NOT NULL CHECK (final_price_cents >= 0),
    payment_status    text NOT NULL DEFAULT 'NONE'
                      CHECK (payment_status IN ('NONE', 'DUE', 'PAID', 'WAIVED')),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CHECK (ends_at > starts_at),
    -- THE core integrity rule: DB-enforced no-double-booking. tstzrange defaults to
    -- half-open '[)', so back-to-back slots do NOT conflict.
    EXCLUDE USING gist (court_id WITH =, tstzrange(starts_at, ends_at) WITH &&)
        WHERE (status IN ('ACTIVE', 'BLOCKED'))
);

CREATE INDEX idx_bookings_club_id ON bookings (club_id);
CREATE INDEX idx_bookings_court_id ON bookings (court_id);
CREATE INDEX idx_bookings_user_id ON bookings (user_id);

CREATE TRIGGER bookings_set_updated_at
    BEFORE UPDATE ON bookings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
