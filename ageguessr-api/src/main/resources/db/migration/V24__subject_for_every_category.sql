-- Personne nommee par un indice, dans toutes les categories (feature/literature-hints,
-- revue code-reviewer) : un indice qui cite un auteur, un laureat ou un vainqueur ne doit
-- jamais etre propose sur la photo de cette personne. Le jeu filtre deja sur subject_qid
-- (findEligible), mais seule Celebrites le renseignait : « Parution : Harry Potter —
-- J. K. Rowling » pouvait sortir sur une photo de J. K. Rowling a 32 ans.
-- Celebrites l'exige toujours ; les autres categories l'acceptent.
ALTER TABLE hints
    DROP CONSTRAINT hints_subject_only_for_celebrities,
    ADD CONSTRAINT hints_subject_for_celebrities CHECK (category <> 'CELEBRITIES' OR subject_qid IS NOT NULL);
