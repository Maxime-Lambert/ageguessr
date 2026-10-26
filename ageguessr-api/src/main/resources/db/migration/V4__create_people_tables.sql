-- Personnalites du jeu, ingerees depuis Wikidata (voir plans/done/people-sourcing.md). Les
-- donnees sont publiques mais restent des donnees personnelles : "excluded" sert aux
-- demandes de retrait, une personne exclue n'est plus jamais reingeree ni servie.
CREATE TABLE people (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wikidata_id VARCHAR(16) NOT NULL,
    name_en VARCHAR(255),
    name_fr VARCHAR(255),
    birth_date DATE NOT NULL,
    death_date DATE,
    sitelinks INT NOT NULL,
    excluded BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT people_has_a_name CHECK (name_en IS NOT NULL OR name_fr IS NOT NULL),
    CONSTRAINT people_death_after_birth CHECK (death_date IS NULL OR death_date >= birth_date)
);

CREATE UNIQUE INDEX people_wikidata_id_unique_idx ON people (wikidata_id);

-- Une photo Commons dont l'age de la personne est connu sans ambiguite : la prise de
-- vue est quelque part dans [taken_from, taken_to] (intervalle derive de la precision
-- de la date), et l'age est le meme aux deux bornes.
-- commons_file, source_url et la date ne doivent jamais etre exposes avant la fin d'un
-- round : le nom du fichier contient souvent le nom de la personne. Seul public_id sort
-- de l'API.
CREATE TABLE person_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL REFERENCES people (id) ON DELETE CASCADE,
    commons_file VARCHAR(512) NOT NULL,
    taken_from DATE NOT NULL,
    taken_to DATE NOT NULL,
    age_at_photo INT NOT NULL,
    date_source VARCHAR(32) NOT NULL,
    license_short_name VARCHAR(64) NOT NULL,
    license_url VARCHAR(512),
    author_text VARCHAR(1024),
    source_url VARCHAR(1024) NOT NULL,
    public_id UUID NOT NULL,
    -- Curation manuelle : une photo n'entre dans le jeu qu'une fois APPROVED. Le
    -- spike a montre que ~43 % des photos retenues automatiquement montrent un groupe,
    -- une foule ou personne (plans/done/people-sourcing.md). Une photo REJECTED n'est jamais
    -- reproposee et ne bloque ni son annee ni son age.
    review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT person_photos_review_status CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT person_photos_interval_ordered CHECK (taken_from <= taken_to),
    -- Une date au jour, au mois ou a l'annee tient toujours dans une seule annee : ca
    -- donne un sens a "l'annee de la photo" utilisee par l'index unique plus bas.
    CONSTRAINT person_photos_interval_within_a_year
        CHECK (EXTRACT(YEAR FROM taken_from) = EXTRACT(YEAR FROM taken_to)),
    CONSTRAINT person_photos_age_range CHECK (age_at_photo BETWEEN 0 AND 120)
);

CREATE UNIQUE INDEX person_photos_person_file_unique_idx ON person_photos (person_id, commons_file);
-- Au plus une photo par age et par annee civile pour une meme personne : deux photos au
-- meme age n'apportent rien au jeu. Les deux sont necessaires, une meme annee pouvant
-- couvrir deux ages (avant/apres l'anniversaire) et un meme age deux annees. Index
-- partiels : une photo rejetee a la curation libere son annee et son age.
CREATE UNIQUE INDEX person_photos_person_age_unique_idx
    ON person_photos (person_id, age_at_photo) WHERE review_status <> 'REJECTED';
CREATE UNIQUE INDEX person_photos_person_year_unique_idx
    ON person_photos (person_id, (EXTRACT(YEAR FROM taken_from))) WHERE review_status <> 'REJECTED';
CREATE UNIQUE INDEX person_photos_public_id_unique_idx ON person_photos (public_id);

-- Octets de l'image dans une table a part : Hibernate ne sait pas charger une colonne
-- bytea en lazy sans bytecode enhancement, donc lire une photo chargerait l'image.
CREATE TABLE person_photo_images (
    photo_id UUID PRIMARY KEY REFERENCES person_photos (id) ON DELETE CASCADE,
    content BYTEA NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL
);

-- Droit a l'effacement : exclure une personne (demande de retrait) supprime
-- physiquement ses photos et leurs images (ON DELETE CASCADE vers
-- person_photo_images). La ligne people reste, avec excluded = TRUE, pour que
-- l'ingestion ne la reajoute jamais. Un trigger plutot que du code applicatif : la
-- suppression a lieu quelle que soit la facon dont l'exclusion est posee (SQL a la
-- main aujourd'hui, page de curation demain).
CREATE FUNCTION delete_photos_of_excluded_person() RETURNS TRIGGER AS $$
BEGIN
    DELETE FROM person_photos WHERE person_id = NEW.id;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER people_excluded_deletes_photos
    AFTER INSERT OR UPDATE OF excluded ON people
    FOR EACH ROW
    WHEN (NEW.excluded)
    EXECUTE FUNCTION delete_photos_of_excluded_person();
