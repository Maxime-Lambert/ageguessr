-- Trace de la curation : qui a pris la derniere decision sur une photo, et quand.
-- ON DELETE SET NULL : si l'administrateur supprime son compte, l'historique reste,
-- anonymise, au lieu de bloquer la suppression ou d'effacer des photos validees.
ALTER TABLE person_photos ADD COLUMN reviewed_at TIMESTAMPTZ;
ALTER TABLE person_photos ADD COLUMN reviewed_by UUID REFERENCES users (id) ON DELETE SET NULL;

-- La file de curation lit les photos en attente.
CREATE INDEX person_photos_pending_idx ON person_photos (person_id) WHERE review_status = 'PENDING';
