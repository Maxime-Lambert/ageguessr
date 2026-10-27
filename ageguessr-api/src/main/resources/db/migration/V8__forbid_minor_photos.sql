-- Aucune photo d'une personne mineure dans le jeu, meme si elle est adulte aujourd'hui
-- (decision du 2026-10-26, plans/active-plan.md). L'ingestion les rejette deja
-- (PhotoEvaluator.MIN_AGE) ; cette contrainte est le filet, quelle que soit la facon
-- dont une photo est inseree. Les images partent avec leurs photos (ON DELETE CASCADE
-- vers person_photo_images). Aucune ligne en prod, l'ingestion n'y a jamais tourne.
DELETE FROM person_photos WHERE age_at_photo < 18;

ALTER TABLE person_photos DROP CONSTRAINT person_photos_age_range;
ALTER TABLE person_photos ADD CONSTRAINT person_photos_age_range CHECK (age_at_photo BETWEEN 18 AND 120);
