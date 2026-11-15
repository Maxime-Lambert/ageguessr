-- Purge des comptes inactifs (plans/active-plan.md, RGPD : limitation de la conservation).
-- last_active_at : derniere connexion ou dernier renouvellement de session. Une session
-- vivante se renouvelle au moins tous les 90 jours, inutile de l'ecrire a chaque requete.
-- Comptes existants : leur derniere session connue, sinon leur creation.
ALTER TABLE users ADD COLUMN last_active_at TIMESTAMPTZ;

UPDATE users u
SET last_active_at = GREATEST(
    u.created_at,
    COALESCE((SELECT max(r.created_at) FROM refresh_tokens r WHERE r.user_id = u.id), u.created_at));

ALTER TABLE users
    ALTER COLUMN last_active_at SET NOT NULL,
    ALTER COLUMN last_active_at SET DEFAULT now();

-- Date de l'email qui annonce la suppression pour inactivite, remise a NULL par la
-- prochaine activite. La suppression attend 30 jours apres lui.
ALTER TABLE users ADD COLUMN inactivity_warned_at TIMESTAMPTZ;
