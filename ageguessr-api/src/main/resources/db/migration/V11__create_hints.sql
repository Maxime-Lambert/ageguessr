-- Indices du jeu : une information rattachee a une annee, qui permet de calculer l'age
-- sur la photo (annee du jour - age_at_photo). Ils sont extraits hors de l'application
-- (scripts/hints/) et importes par ImportHintsRunner.
-- Sans cle etrangere vers people : un indice depend d'une annee, pas d'une personne, et
-- la categorie Celebrites vient de sa propre extraction Wikidata.
CREATE TABLE hints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category VARCHAR(16) NOT NULL,
    year INT NOT NULL,
    -- ALL : les deux textes. FR ou EN : le texte de ce public seulement. Le minimum de 5
    -- indices se compte par public : indices ALL + indices de ce public.
    audience VARCHAR(8) NOT NULL,
    text_en VARCHAR(512),
    text_fr VARCHAR(512),
    -- Page d'origine, pour l'attribution CC BY-SA a l'ecran de resultat.
    source_url VARCHAR(1024) NOT NULL,
    -- Type de source (classement, categorie...) : retrouve les indices d'une source moins
    -- sure sans les relire tous.
    source_kind VARCHAR(32) NOT NULL,
    -- Celebrites seulement : pour ne jamais citer une personne sur sa propre photo, et
    -- retrouver ses indices en cas de demande de retrait.
    subject_qid VARCHAR(16),
    -- Cle stable de l'indice : une relance de l'import n'ajoute que les nouvelles cles et
    -- ne touche ni une decision de curation, ni un texte corrige a la main.
    dedupe_key VARCHAR(255) NOT NULL,
    -- APPROVED des l'import pour un indice tire d'une page dans sa langue. Seul un indice
    -- traduit reste PENDING jusqu'a sa validation.
    review_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    -- Derniere decision : vide tant qu'aucun admin n'a touche a l'indice. ON DELETE SET
    -- NULL : meme raison que person_photos (V6).
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT hints_category CHECK (
        category IN ('MUSIC', 'FILM', 'SPORT', 'SCIENCE', 'WORLD', 'CELEBRITIES')
    ),
    CONSTRAINT hints_year_range CHECK (year BETWEEN 1900 AND 2099),
    CONSTRAINT hints_review_status CHECK (review_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT hints_audience_texts CHECK (
        (audience = 'ALL' AND text_en IS NOT NULL AND text_fr IS NOT NULL)
        OR (audience = 'FR' AND text_en IS NULL AND text_fr IS NOT NULL)
        OR (audience = 'EN' AND text_en IS NOT NULL AND text_fr IS NULL)
    ),
    CONSTRAINT hints_subject_only_for_celebrities CHECK (
        (category = 'CELEBRITIES' AND subject_qid IS NOT NULL)
        OR (category <> 'CELEBRITIES' AND subject_qid IS NULL)
    )
);

CREATE UNIQUE INDEX hints_dedupe_key_unique_idx ON hints (dedupe_key);
-- La couverture et le tirage lisent les indices valides par annee et categorie.
CREATE INDEX hints_approved_year_category_idx ON hints (year, category) WHERE review_status = 'APPROVED';
-- La page de validation lit les indices en attente.
CREATE INDEX hints_pending_idx ON hints (created_at) WHERE review_status = 'PENDING';
-- Retrouve les indices d'une personne en cas de demande de retrait.
CREATE INDEX hints_subject_qid_idx ON hints (subject_qid) WHERE subject_qid IS NOT NULL;
