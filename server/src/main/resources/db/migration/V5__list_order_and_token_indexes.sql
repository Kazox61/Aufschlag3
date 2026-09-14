-- Keyset pagination orders every list by (created_at, id) — a random UUIDv4 alone has no
-- meaningful order. The composite indexes cover both the tenant filter and the sort, which
-- makes the old single-column prefixes redundant.
CREATE INDEX idx_memberships_club_created ON memberships (club_id, created_at, id);
CREATE INDEX idx_memberships_user_created ON memberships (user_id, created_at, id);
CREATE INDEX idx_courts_club_created      ON courts (club_id, created_at, id);
CREATE INDEX idx_clubs_created            ON clubs (created_at, id);
DROP INDEX idx_memberships_club_id;
DROP INDEX idx_memberships_user_id;
DROP INDEX idx_courts_club_id;

-- Hot booking queries: the per-user open-bookings count and the upcoming list both filter on
-- (user_id, status = ACTIVE, ends_at > now) — a partial index keeps cancelled history out.
CREATE INDEX idx_bookings_user_open ON bookings (user_id, ends_at) WHERE status = 'ACTIVE';
DROP INDEX idx_bookings_user_id;

-- Family revocation only touches live rows.
CREATE INDEX idx_refresh_tokens_family_live ON refresh_tokens (family_id) WHERE revoked_at IS NULL;
DROP INDEX idx_refresh_tokens_family_id;

-- Expired-token pruning (TokenMaintenance) deletes by expires_at; an expired successor may be
-- referenced by a not-yet-expired predecessor's replaced_by_id if the TTL was shortened in
-- between — clear the pointer instead of failing the whole maintenance pass.
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
CREATE INDEX idx_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);
ALTER TABLE refresh_tokens
    DROP CONSTRAINT refresh_tokens_replaced_by_id_fkey,
    ADD CONSTRAINT refresh_tokens_replaced_by_id_fkey
        FOREIGN KEY (replaced_by_id) REFERENCES refresh_tokens ON DELETE SET NULL;
