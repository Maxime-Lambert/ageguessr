-- Partie du jour (plans/done/daily-draw.md) : les photos tirees pour une date, les memes pour
-- tous les joueurs. La date est celle de minuit UTC.
--
-- photo_id en ON DELETE SET NULL, pas CASCADE : exclure une personne supprime ses photos
-- (trigger V4), et la manche doit rester pour que l'historique et les scores du jour ne
-- perdent pas une position. Il ne reste alors que la date et la position, plus rien sur
-- la personne (droit a l'effacement).
--
-- Une manche est neutralisee (photo retiree pour tous) quand photo_id est NULL ou que la
-- photo n'est plus APPROVED. C'est calcule a la lecture, pas stocke : people n'a pas a
-- prevenir game, et un rejet annule en curation retablit la manche.
CREATE TABLE daily_rounds (
    game_date DATE NOT NULL,
    position SMALLINT NOT NULL,
    photo_id UUID REFERENCES person_photos (id) ON DELETE SET NULL,
    drawn_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (game_date, position),
    CONSTRAINT daily_rounds_position_positive CHECK (position >= 1)
);

-- La regle des 3 ans lit la derniere date jouee de chaque photo, et le SET NULL cherche
-- les manches d'une photo supprimee.
CREATE INDEX daily_rounds_photo_idx ON daily_rounds (photo_id);

-- Filet : jamais deux fois la meme photo dans une partie. Partiel, car plusieurs manches
-- d'un meme jour peuvent avoir perdu leur photo.
CREATE UNIQUE INDEX daily_rounds_date_photo_unique_idx
    ON daily_rounds (game_date, photo_id) WHERE photo_id IS NOT NULL;
