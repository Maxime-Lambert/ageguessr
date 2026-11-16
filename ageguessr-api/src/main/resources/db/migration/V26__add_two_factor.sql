-- Double authentification TOTP (plans/active-plan.md), facultative.
-- totp_secret : le secret de l'app d'authentification, chiffre (AES-256-GCM, cle
-- TOTP_ENCRYPTION_KEY), base64. En attente tant que totp_enabled_at est NULL (le joueur
-- n'a pas encore saisi un premier code).
-- totp_last_step : le dernier pas de 30 s accepte, pour refuser un code deja utilise.
ALTER TABLE users
    ADD COLUMN totp_secret TEXT,
    ADD COLUMN totp_enabled_at TIMESTAMPTZ,
    ADD COLUMN totp_last_step BIGINT,
    ADD CONSTRAINT users_totp_enabled_has_secret CHECK (totp_enabled_at IS NULL OR totp_secret IS NOT NULL);
