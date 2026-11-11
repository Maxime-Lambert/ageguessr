-- Le lien d'invitation personnel est retire (retour produit du 2026-11-11) : on devient
-- ami uniquement par une demande par pseudo, acceptee explicitement. Les codes existants
-- ne servent plus a rien ; les amities deja creees par un lien sont gardees (friendships).
DROP TABLE friend_invites;
