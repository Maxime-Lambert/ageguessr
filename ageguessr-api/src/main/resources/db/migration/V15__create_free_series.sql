-- Enquete libre et Defi (plans/active-plan.md) : des series de 5, 10 ou 20 photos, jouees
-- avec les regles de la partie du jour, hors de la partie du jour. Une serie partagee
-- devient un defi : d'autres joueurs la rejoignent par son code et jouent les memes photos.
--
-- Tout est supprime avec la serie (CASCADE), et la purge quotidienne supprime une serie non
-- partagee 7 jours apres sa creation, un defi 30 jours apres son partage. Supprimer un
-- compte supprime ses series (et donc ses defis), et le retire des defis des autres.

-- Une serie, creee par un joueur (compte ou invite, comme game_plays).
CREATE TABLE free_series (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Longueur demandee. La serie peut avoir moins de manches si le pool manque.
    size SMALLINT NOT NULL,
    -- Jour de creation : les indices visent son annee moins l'age, comme pour la partie du
    -- jour avec sa date.
    created_on DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_id UUID REFERENCES users (id) ON DELETE CASCADE,
    guest_id UUID,
    -- Code du defi, pose au partage : 8 caracteres en base32 Crockford (sans I, L, O, U).
    share_code VARCHAR(8),
    shared_at TIMESTAMPTZ,
    CONSTRAINT free_series_one_player CHECK (num_nonnulls(user_id, guest_id) = 1),
    CONSTRAINT free_series_size CHECK (size IN (5, 10, 20)),
    CONSTRAINT free_series_shared CHECK ((share_code IS NULL) = (shared_at IS NULL)),
    CONSTRAINT free_series_share_code CHECK (share_code ~ '^[0-9A-HJKMNP-TV-Z]{8}$')
);

CREATE UNIQUE INDEX free_series_share_code_unique_idx ON free_series (share_code);
-- La serie en cours d'un joueur : la plus recente qu'il a creee.
CREATE INDEX free_series_user_idx ON free_series (user_id, created_at) WHERE user_id IS NOT NULL;
CREATE INDEX free_series_guest_idx ON free_series (guest_id, created_at) WHERE guest_id IS NOT NULL;

-- Les photos d'une serie, dans l'ordre. photo_id en SET NULL comme daily_rounds : exclure
-- une personne neutralise la manche sans casser la serie.
CREATE TABLE free_series_rounds (
    series_id UUID NOT NULL REFERENCES free_series (id) ON DELETE CASCADE,
    position SMALLINT NOT NULL,
    photo_id UUID REFERENCES person_photos (id) ON DELETE SET NULL,
    PRIMARY KEY (series_id, position),
    CONSTRAINT free_series_rounds_position CHECK (position BETWEEN 1 AND 20)
);

CREATE INDEX free_series_rounds_photo_idx ON free_series_rounds (photo_id);
CREATE UNIQUE INDEX free_series_rounds_photo_unique_idx
    ON free_series_rounds (series_id, photo_id) WHERE photo_id IS NOT NULL;

-- Les joueurs d'une serie : le createur, puis ceux qui rejoignent le defi. Une seule
-- tentative par joueur, d'ou les index uniques. Le pseudo est celui d'un invite dans ce
-- defi (un compte affiche son nom d'utilisateur, lu a l'affichage).
CREATE TABLE free_series_players (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    series_id UUID NOT NULL REFERENCES free_series (id) ON DELETE CASCADE,
    user_id UUID REFERENCES users (id) ON DELETE CASCADE,
    guest_id UUID,
    nickname VARCHAR(20),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT free_series_players_one_player CHECK (num_nonnulls(user_id, guest_id) = 1),
    CONSTRAINT free_series_players_nickname_guest_only CHECK (user_id IS NULL OR nickname IS NULL),
    CONSTRAINT free_series_players_nickname_format CHECK (nickname ~ '^[A-Za-z0-9_-]{3,20}$')
);

CREATE UNIQUE INDEX free_series_players_user_unique_idx ON free_series_players (series_id, user_id);
CREATE UNIQUE INDEX free_series_players_guest_unique_idx ON free_series_players (series_id, guest_id);
-- Les photos deja vues par un joueur (tirage) partent de ses series.
CREATE INDEX free_series_players_user_idx ON free_series_players (user_id) WHERE user_id IS NOT NULL;
CREATE INDEX free_series_players_guest_idx ON free_series_players (guest_id) WHERE guest_id IS NOT NULL;

-- Une manche jouee d'une serie : les memes colonnes et les memes regles que game_plays.
CREATE TABLE free_series_plays (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    series_id UUID NOT NULL,
    position SMALLINT NOT NULL,
    user_id UUID REFERENCES users (id) ON DELETE CASCADE,
    guest_id UUID,
    language VARCHAR(2) NOT NULL,
    first_guess INT NOT NULL,
    first_points INT NOT NULL,
    hint_category VARCHAR(16),
    hint_id UUID REFERENCES hints (id) ON DELETE SET NULL,
    second_guess INT,
    second_points INT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at TIMESTAMPTZ,
    version INT NOT NULL DEFAULT 0,
    FOREIGN KEY (series_id, position) REFERENCES free_series_rounds (series_id, position) ON DELETE CASCADE,
    CONSTRAINT free_series_plays_one_player CHECK (num_nonnulls(user_id, guest_id) = 1),
    CONSTRAINT free_series_plays_language CHECK (language IN ('EN', 'FR')),
    CONSTRAINT free_series_plays_guess_range CHECK (
        first_guess BETWEEN 18 AND 100 AND (second_guess IS NULL OR second_guess BETWEEN 18 AND 100)
    ),
    CONSTRAINT free_series_plays_points_range CHECK (
        first_points BETWEEN 0 AND 500 AND (second_points IS NULL OR second_points BETWEEN 0 AND 500)
    ),
    CONSTRAINT free_series_plays_hint_category CHECK (
        hint_category IS NULL
        OR hint_category IN ('MUSIC', 'FILM', 'SPORT', 'SCIENCE', 'WORLD', 'CELEBRITIES')
    ),
    CONSTRAINT free_series_plays_second_guess_complete CHECK (
        (second_guess IS NULL AND second_points IS NULL)
        OR (second_guess IS NOT NULL AND second_points IS NOT NULL AND finished_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX free_series_plays_user_round_unique_idx ON free_series_plays (user_id, series_id, position);
CREATE UNIQUE INDEX free_series_plays_guest_round_unique_idx ON free_series_plays (guest_id, series_id, position);
CREATE INDEX free_series_plays_series_idx ON free_series_plays (series_id);
