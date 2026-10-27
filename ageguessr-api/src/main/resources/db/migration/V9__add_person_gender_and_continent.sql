-- Equilibre du pool entre hommes et femmes et entre continents (plans/active-plan.md).
-- Valeurs lues sur Wikidata a chaque ingestion : P21 pour le genre, P27 puis P30 pour le
-- continent de nationalite (pas l'origine ethnique, P172 : donnee sensible, art. 9 RGPD).
-- Nullables : Wikidata ne dit pas toujours, et les personnes deja ingerees sont remplies
-- a leur prochain passage.
ALTER TABLE people ADD COLUMN gender VARCHAR(16);
ALTER TABLE people ADD COLUMN continent VARCHAR(16);

ALTER TABLE people ADD CONSTRAINT people_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER'));
ALTER TABLE people ADD CONSTRAINT people_continent
    CHECK (continent IN ('AFRICA', 'ASIA', 'EUROPE', 'NORTH_AMERICA', 'OCEANIA', 'SOUTH_AMERICA'));
