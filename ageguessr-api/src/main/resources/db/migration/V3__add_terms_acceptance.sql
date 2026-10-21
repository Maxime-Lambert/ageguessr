-- Which version of the Terms each user accepted, and when: proof of acceptance, and the
-- basis for asking users to accept again once the Terms change.
-- Nullable: accounts created before the Terms existed never accepted any version.
ALTER TABLE users ADD COLUMN terms_version VARCHAR(20);
ALTER TABLE users ADD COLUMN terms_accepted_at TIMESTAMPTZ;
