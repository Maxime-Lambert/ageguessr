-- Plusieurs pistes par manche (feature/better-hints-gameplay) : le joueur choisit N
-- categories (app.game.hints.per-round, 3 par defaut) au lieu d'une. Les colonnes d'un seul
-- indice deviennent des tableaux, dans les deux tables de parties (meme RoundProgress
-- embarque, memes colonnes).
--
-- Pourquoi des tableaux plutot qu'une table fille : la liste fait au plus 6 elements, est
-- ecrite une fois et lue en bloc avec la manche. On perd la cle etrangere vers hints (un
-- tableau ne peut pas en porter) : un indice n'est jamais supprime (un retrait passe par
-- REJECTED) et le jeu ignore deja un id inconnu ou rejete.
--
-- hint_categories et hint_ids vont ensemble, dans le meme ordre : l'id peut etre NULL dans
-- le tableau (indice supprime sous l'ancien ON DELETE SET NULL), la longueur reste la meme.
-- reported_hint_categories remplace hint_reported : le signalement se fait par indice.

ALTER TABLE game_plays
    ADD COLUMN hint_categories VARCHAR(16)[] NOT NULL DEFAULT '{}',
    ADD COLUMN hint_ids UUID[] NOT NULL DEFAULT '{}',
    ADD COLUMN reported_hint_categories VARCHAR(16)[] NOT NULL DEFAULT '{}';

UPDATE game_plays
SET hint_categories = ARRAY[hint_category],
    hint_ids = ARRAY[hint_id],
    reported_hint_categories = CASE WHEN hint_reported THEN ARRAY[hint_category] ELSE '{}' END
WHERE hint_category IS NOT NULL;

ALTER TABLE game_plays
    DROP CONSTRAINT game_plays_hint_category,
    DROP COLUMN hint_category,
    DROP COLUMN hint_id,
    DROP COLUMN hint_reported,
    ADD CONSTRAINT game_plays_hint_categories CHECK (
        hint_categories <@ ARRAY['MUSIC', 'FILM', 'SPORT', 'SCIENCE', 'WORLD', 'CELEBRITIES']::VARCHAR(16)[]
    ),
    ADD CONSTRAINT game_plays_hint_ids_match CHECK (cardinality(hint_ids) = cardinality(hint_categories)),
    ADD CONSTRAINT game_plays_reported_hints CHECK (reported_hint_categories <@ hint_categories);

ALTER TABLE free_series_plays
    ADD COLUMN hint_categories VARCHAR(16)[] NOT NULL DEFAULT '{}',
    ADD COLUMN hint_ids UUID[] NOT NULL DEFAULT '{}',
    ADD COLUMN reported_hint_categories VARCHAR(16)[] NOT NULL DEFAULT '{}';

UPDATE free_series_plays
SET hint_categories = ARRAY[hint_category],
    hint_ids = ARRAY[hint_id],
    reported_hint_categories = CASE WHEN hint_reported THEN ARRAY[hint_category] ELSE '{}' END
WHERE hint_category IS NOT NULL;

ALTER TABLE free_series_plays
    DROP CONSTRAINT free_series_plays_hint_category,
    DROP COLUMN hint_category,
    DROP COLUMN hint_id,
    DROP COLUMN hint_reported,
    ADD CONSTRAINT free_series_plays_hint_categories CHECK (
        hint_categories <@ ARRAY['MUSIC', 'FILM', 'SPORT', 'SCIENCE', 'WORLD', 'CELEBRITIES']::VARCHAR(16)[]
    ),
    ADD CONSTRAINT free_series_plays_hint_ids_match CHECK (cardinality(hint_ids) = cardinality(hint_categories)),
    ADD CONSTRAINT free_series_plays_reported_hints CHECK (reported_hint_categories <@ hint_categories);
