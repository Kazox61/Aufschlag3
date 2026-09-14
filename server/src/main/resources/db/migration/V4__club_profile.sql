-- Club profile fields shown/edited on the admin app's "Verein" settings page.
-- Distinct from clubs.settings (jsonb ClubSettings): plain display/contact data, own columns.

ALTER TABLE clubs
    ADD COLUMN address       text,
    ADD COLUMN contact_email text,
    ADD COLUMN phone         text,
    ADD COLUMN website       text,
    ADD COLUMN logo_url      text;
