-- Retrait de la double authentification TOTP (V26), jugee disproportionnee pour
-- l'audience du jeu (voir docs/decisions/architecture.md, « Récupération du compte »).
-- V26 reste en place : elle a ete appliquee en prod, et Flyway refuse de demarrer si
-- une migration appliquee disparait ou change. On annule donc ses effets ici.
ALTER TABLE users
    DROP CONSTRAINT IF EXISTS users_totp_enabled_has_secret,
    DROP COLUMN IF EXISTS totp_secret,
    DROP COLUMN IF EXISTS totp_enabled_at,
    DROP COLUMN IF EXISTS totp_last_step;
