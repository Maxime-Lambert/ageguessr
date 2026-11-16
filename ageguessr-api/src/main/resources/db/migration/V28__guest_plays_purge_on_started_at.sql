-- La purge des parties invitees se fait desormais sur le moment ou la manche a ete jouee
-- (started_at) et non plus sur le jour du defi : un jour passe rattrape depuis le
-- calendrier est garde 7 jours comme celui du jour. L'index suit la nouvelle requete.
DROP INDEX IF EXISTS game_plays_guest_date_idx;
CREATE INDEX game_plays_guest_started_idx ON game_plays (started_at) WHERE guest_id IS NOT NULL;
