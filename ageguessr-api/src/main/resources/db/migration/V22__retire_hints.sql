-- Retrait d'un indice (feature/hint-notoriety) : la refonte des sources remplace tous les
-- indices. Un indice retire n'est plus propose a une manche, mais reste affiche la ou il
-- a deja ete montre (historique, manche en cours), et sa decision de relecture reste
-- intacte. REJECTED ne convenait pas : le jeu ne montre que les indices APPROVED, les
-- manches deja jouees auraient perdu leurs indices.
-- Pose par l'import (app.hints.import.retire-absent) sur les cles absentes des fichiers,
-- dans la meme transaction que l'ajout des nouvelles : aucune categorie ne reste vide.
ALTER TABLE hints ADD COLUMN retired_at TIMESTAMPTZ;

-- Le tirage et la couverture ne lisent que les indices valides et non retires.
DROP INDEX hints_approved_year_category_idx;
CREATE INDEX hints_approved_year_category_idx ON hints (year, category)
    WHERE review_status = 'APPROVED' AND retired_at IS NULL;
