-- Parties jouees (plans/active-plan.md) : une ligne par joueur et par manche, creee au 1er
-- essai. Pas de ligne = manche pas commencee. L'etat est garde en base et pas dans le
-- navigateur : l'age ne doit jamais y arriver avant la reponse, et le serveur fait foi pour
-- les points.
--
-- Le joueur est soit un compte (user_id, supprime avec lui), soit un invite (guest_id, l'id
-- aleatoire du cookie ageguessr_guest, purge 7 jours apres la partie).
CREATE TABLE game_plays (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    game_date DATE NOT NULL,
    position SMALLINT NOT NULL,
    user_id UUID REFERENCES users (id) ON DELETE CASCADE,
    guest_id UUID,
    -- Langue de l'indice et du nom revele, fixee au 1er essai pour toute la manche.
    language VARCHAR(2) NOT NULL,
    first_guess INT NOT NULL,
    -- Points stockes a l'essai, pas recalcules : regler la formule ne reecrit pas le passe.
    first_points INT NOT NULL,
    hint_category VARCHAR(16),
    -- SET NULL : un indice n'est jamais supprime aujourd'hui, mais s'il l'etait, la partie
    -- resterait, sans indice a reafficher.
    hint_id UUID REFERENCES hints (id) ON DELETE SET NULL,
    second_guess INT,
    second_points INT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Pose au 2e essai, ou des le 1er quand il est exact (manche a 1 000 sans indice).
    finished_at TIMESTAMPTZ,
    -- Verrou optimiste (@Version) : deux onglets qui jouent la meme etape en meme temps,
    -- le second recoit un 409 au lieu d'ecraser le premier.
    version INT NOT NULL DEFAULT 0,
    FOREIGN KEY (game_date, position) REFERENCES daily_rounds (game_date, position),
    CONSTRAINT game_plays_one_player CHECK (num_nonnulls(user_id, guest_id) = 1),
    CONSTRAINT game_plays_language CHECK (language IN ('EN', 'FR')),
    CONSTRAINT game_plays_guess_range CHECK (
        first_guess BETWEEN 18 AND 100 AND (second_guess IS NULL OR second_guess BETWEEN 18 AND 100)
    ),
    CONSTRAINT game_plays_points_range CHECK (
        first_points BETWEEN 0 AND 500 AND (second_points IS NULL OR second_points BETWEEN 0 AND 500)
    ),
    CONSTRAINT game_plays_hint_category CHECK (
        hint_category IS NULL
        OR hint_category IN ('MUSIC', 'FILM', 'SPORT', 'SCIENCE', 'WORLD', 'CELEBRITIES')
    ),
    -- Un 2e essai a toujours ses points, et termine la manche.
    CONSTRAINT game_plays_second_guess_complete CHECK (
        (second_guess IS NULL AND second_points IS NULL)
        OR (second_guess IS NOT NULL AND second_points IS NOT NULL AND finished_at IS NOT NULL)
    )
);

-- Un seul essai par joueur et par manche, meme sur un double clic ou deux onglets. Pas
-- besoin d'index partiels : Postgres ne compare jamais deux NULL dans un index unique, donc
-- les lignes invitees (user_id NULL) ne se genent pas entre elles. L'ordre (joueur, date)
-- sert aussi a lire la partie du jour d'un joueur.
CREATE UNIQUE INDEX game_plays_user_round_unique_idx ON game_plays (user_id, game_date, position);
CREATE UNIQUE INDEX game_plays_guest_round_unique_idx ON game_plays (guest_id, game_date, position);

-- La purge quotidienne supprime les lignes invitees les plus anciennes.
CREATE INDEX game_plays_guest_date_idx ON game_plays (game_date) WHERE guest_id IS NOT NULL;
