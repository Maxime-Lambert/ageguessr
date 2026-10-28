-- Motif d'exclusion d'une personne (plans/done/editorial-line.md) : MANUAL pour le
-- bouton de la page de curation (demandes de retrait, ligne editoriale appliquee a la
-- main), EDITORIAL_LINE pour les criteres automatiques de la ligne editoriale. Les
-- distinguer permet de revoir les exclusions automatiques si les criteres changent,
-- sans jamais toucher a une demande de retrait.
ALTER TABLE people ADD COLUMN exclusion_reason VARCHAR(16);

UPDATE people SET exclusion_reason = 'MANUAL' WHERE excluded;

-- IS NOT NULL explicite : un CHECK qui s'evalue a NULL passe, et NULL IN (...) vaut
-- NULL, donc une exclusion sans motif serait acceptee sans lui.
ALTER TABLE people ADD CONSTRAINT people_exclusion_reason_check CHECK (
    (excluded AND exclusion_reason IS NOT NULL AND exclusion_reason IN ('MANUAL', 'EDITORIAL_LINE'))
    OR (NOT excluded AND exclusion_reason IS NULL)
);
