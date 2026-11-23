-- Categories Television et Video games (feature/tv-and-video-game-hints, retours testeurs
-- du 2026-11-23) : 8e et 9e categories d'indice. Les contraintes qui listent les
-- categories les acceptent ; VARCHAR(16) suffit ('VIDEO_GAMES' fait 11 caracteres).

ALTER TABLE hints
    DROP CONSTRAINT hints_category,
    ADD CONSTRAINT hints_category CHECK (
        category IN ('MUSIC', 'FILM', 'SPORT', 'SCIENCE', 'WORLD', 'CELEBRITIES', 'LITERATURE', 'TELEVISION',
                     'VIDEO_GAMES')
    );

ALTER TABLE game_plays
    DROP CONSTRAINT game_plays_hint_categories,
    ADD CONSTRAINT game_plays_hint_categories CHECK (
        hint_categories <@ ARRAY['MUSIC', 'FILM', 'SPORT', 'SCIENCE', 'WORLD', 'CELEBRITIES', 'LITERATURE',
                                 'TELEVISION', 'VIDEO_GAMES']::VARCHAR(16)[]
    );

ALTER TABLE free_series_plays
    DROP CONSTRAINT free_series_plays_hint_categories,
    ADD CONSTRAINT free_series_plays_hint_categories CHECK (
        hint_categories <@ ARRAY['MUSIC', 'FILM', 'SPORT', 'SCIENCE', 'WORLD', 'CELEBRITIES', 'LITERATURE',
                                 'TELEVISION', 'VIDEO_GAMES']::VARCHAR(16)[]
    );
