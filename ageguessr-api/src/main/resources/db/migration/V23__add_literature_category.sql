-- Categorie Litterature (feature/literature-hints) : parutions d'oeuvres et grands prix
-- litteraires, 7e categorie d'indice. Les contraintes qui listent les categories
-- l'acceptent ; VARCHAR(16) suffit ('LITERATURE' fait 10 caracteres).

ALTER TABLE hints
    DROP CONSTRAINT hints_category,
    ADD CONSTRAINT hints_category CHECK (
        category IN ('MUSIC', 'FILM', 'SPORT', 'SCIENCE', 'WORLD', 'CELEBRITIES', 'LITERATURE')
    );

ALTER TABLE game_plays
    DROP CONSTRAINT game_plays_hint_categories,
    ADD CONSTRAINT game_plays_hint_categories CHECK (
        hint_categories <@ ARRAY['MUSIC', 'FILM', 'SPORT', 'SCIENCE', 'WORLD', 'CELEBRITIES', 'LITERATURE']::VARCHAR(16)[]
    );

ALTER TABLE free_series_plays
    DROP CONSTRAINT free_series_plays_hint_categories,
    ADD CONSTRAINT free_series_plays_hint_categories CHECK (
        hint_categories <@ ARRAY['MUSIC', 'FILM', 'SPORT', 'SCIENCE', 'WORLD', 'CELEBRITIES', 'LITERATURE']::VARCHAR(16)[]
    );
