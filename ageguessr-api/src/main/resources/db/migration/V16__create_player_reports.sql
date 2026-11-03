-- Signalement des photos et des indices par les joueurs (plans/active-plan.md), depuis
-- l'ecran de resultat d'une manche terminee.
--
-- Les signalements sont anonymes : ni utilisateur, ni invite, ni IP. Ce qui empeche un
-- joueur de signaler dix fois la meme photo est une marque sur sa manche jouee, deja liee
-- a lui et purgee avec elle. Aucun signalement ne retire quoi que ce soit tout seul :
-- l'admin decide.

ALTER TABLE game_plays
    ADD COLUMN photo_reported BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN hint_reported BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE free_series_plays
    ADD COLUMN photo_reported BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN hint_reported BOOLEAN NOT NULL DEFAULT false;

-- OPEN tant que l'admin n'a rien fait ; RESOLVED quand il a pris une decision sur la cible
-- (rejet, correction), DISMISSED quand il la garde telle quelle. CASCADE : exclure une
-- personne supprime ses photos, donc leurs signalements.
CREATE TABLE photo_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    photo_id UUID NOT NULL REFERENCES person_photos (id) ON DELETE CASCADE,
    reason VARCHAR(24) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,
    CONSTRAINT photo_reports_reason CHECK (
        reason IN ('TOO_SMALL', 'NOT_IDENTIFIABLE', 'WRONG_PERSON', 'LOOKS_MINOR', 'DISTURBING_PERSON', 'OTHER')
    ),
    CONSTRAINT photo_reports_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT photo_reports_closed CHECK ((status = 'OPEN') = (closed_at IS NULL))
);

CREATE INDEX photo_reports_open_idx ON photo_reports (photo_id) WHERE status = 'OPEN';

CREATE TABLE hint_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hint_id UUID NOT NULL REFERENCES hints (id) ON DELETE CASCADE,
    reason VARCHAR(24) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,
    CONSTRAINT hint_reports_reason CHECK (
        reason IN ('WRONG_YEAR', 'FALSE', 'UNCLEAR', 'DISTURBING', 'OTHER')
    ),
    CONSTRAINT hint_reports_status CHECK (status IN ('OPEN', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT hint_reports_closed CHECK ((status = 'OPEN') = (closed_at IS NULL))
);

CREATE INDEX hint_reports_open_idx ON hint_reports (hint_id) WHERE status = 'OPEN';
