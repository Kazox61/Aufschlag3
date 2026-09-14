-- bookings.club_id is denormalized from courts (see V3). The two single-column FKs guarantee
-- each id exists, but not that they belong together — a booking could reference court A while
-- carrying club B's id and pass every tenant-scoped check as club B's. The composite FK ties
-- them: the referenced (id, club_id) pair must exist on courts. (Postgres requires a unique
-- constraint on the referenced columns; id alone is already the PK, so this is only a formality.)
ALTER TABLE courts ADD CONSTRAINT courts_id_club_id_key UNIQUE (id, club_id);
ALTER TABLE bookings
    ADD CONSTRAINT bookings_court_id_club_id_fkey FOREIGN KEY (court_id, club_id) REFERENCES courts (id, club_id);
