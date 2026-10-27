-- Recherche de personnes insensible aux accents pour la curation ("Pele" trouve
-- "Pele" accentue). Extension fournie avec Postgres et "trusted" depuis PG 13 : le
-- proprietaire de la base peut l'activer sans etre superutilisateur.
CREATE EXTENSION IF NOT EXISTS unaccent;
