-- Amis (plans/active-plan.md, Semaine 6). Une demande et une amitie sont la meme paire a
-- deux statuts : PENDING (demande envoyee par requester_id a addressee_id), puis ACCEPTED.
-- Refuser ou annuler une demande, retirer un ami : la ligne est supprimee, sans trace.
--
-- ON DELETE CASCADE des deux cotes : supprimer un compte supprime ses amities et ses
-- demandes, ses amis ne le voient plus.
CREATE TABLE friendships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    addressee_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at TIMESTAMPTZ,
    CONSTRAINT friendships_not_self CHECK (requester_id <> addressee_id),
    CONSTRAINT friendships_status CHECK (status IN ('PENDING', 'ACCEPTED')),
    CONSTRAINT friendships_accepted_at CHECK ((status = 'ACCEPTED') = (accepted_at IS NOT NULL))
);

-- Une seule relation par paire, quel que soit son sens : A -> B puis B -> A est refuse, meme
-- sur deux clics simultanes. LEAST/GREATEST rangent la paire dans un ordre fixe.
CREATE UNIQUE INDEX friendships_pair_unique_idx
    ON friendships (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id));

-- Les relations d'un compte, dans un sens comme dans l'autre (la paire ci-dessus sert deja
-- pour la colonne de tete quand elle est la plus petite, pas dans tous les cas).
CREATE INDEX friendships_requester_idx ON friendships (requester_id);
CREATE INDEX friendships_addressee_idx ON friendships (addressee_id);

-- Lien d'invitation personnel : un code par compte, cree a la premiere lecture, remplace a
-- la demande (l'ancien lien ne marche plus). L'ouvrir et accepter cree directement une
-- amitie : partager le lien vaut consentement de son proprietaire.
CREATE TABLE friend_invites (
    user_id UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    code VARCHAR(8) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
