-- Pseudo public, choisi a l'inscription et modifiable. Ajoute en trois temps parce que
-- des comptes existent deja en prod : colonne nullable, backfill, puis NOT NULL.
ALTER TABLE users ADD COLUMN username VARCHAR(20);

-- "player-" + 8 premiers caracteres hex de l'UUID : respecte le format impose par
-- l'application (^[A-Za-z0-9_-]{3,20}$) ; collision quasi impossible sur les quelques
-- comptes existants, et l'index unique ci-dessous ferait echouer la migration plutot
-- que de laisser passer un doublon.
UPDATE users SET username = 'player-' || left(replace(id::text, '-', ''), 8) WHERE username IS NULL;

ALTER TABLE users ALTER COLUMN username SET NOT NULL;

-- Meme raison que pour l'email : une contrainte unique simple est sensible a la casse
-- en Postgres et laisserait coexister "Max" et "max".
CREATE UNIQUE INDEX users_username_unique_idx ON users (LOWER(username));

-- Tokens a usage unique envoyes par email pour une action sur le compte
-- (reinitialisation du mot de passe, confirmation de suppression). Une seule table
-- pour les deux : meme cycle de vie, seul "purpose" change, et l'application ne
-- cherche jamais un token sans filtrer sur son purpose.
CREATE TABLE account_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose VARCHAR(32) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX account_tokens_token_hash_unique_idx ON account_tokens (token_hash);
CREATE INDEX account_tokens_user_id_purpose_idx ON account_tokens (user_id, purpose);
