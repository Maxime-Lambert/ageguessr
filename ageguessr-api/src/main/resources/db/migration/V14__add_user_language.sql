-- Langue du compte (plans/active-plan.md) : celle de ses emails, et du site une fois
-- connecte. NULL = jamais choisie (comptes crees avant V14) : le front enregistre alors la
-- langue de la page, venue du navigateur, et d'ici la les emails partent en anglais.
ALTER TABLE users ADD COLUMN language VARCHAR(2);

ALTER TABLE users ADD CONSTRAINT users_language CHECK (language IS NULL OR language IN ('EN', 'FR'));
