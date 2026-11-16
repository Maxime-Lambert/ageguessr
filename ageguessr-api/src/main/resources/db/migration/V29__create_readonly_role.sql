-- Rôle en lecture seule pour consulter la base depuis un client SQL (DBeaver, par un tunnel
-- SSH : voir docs/decisions/architecture.md, « Accès à la base de prod »).
-- LOGIN sans mot de passe : aucune connexion réseau n'aboutit tant que l'admin ne l'a pas
-- défini à la main sur le serveur (\password ageguessr_readonly). Jamais de mot de passe ici.
-- Un rôle est global au serveur PostgreSQL : idempotent, pour les bases qui le partagent.
DO $$
DECLARE
    hashed RECORD;
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'ageguessr_readonly') THEN
        CREATE ROLE ageguessr_readonly LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT;
    END IF;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO ageguessr_readonly', current_database());
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO ageguessr_readonly', current_schema());
    EXECUTE format('GRANT SELECT ON ALL TABLES IN SCHEMA %I TO ageguessr_readonly', current_schema());

    -- Les hachages (mot de passe, jetons) ne servent à rien dans une consultation et ne
    -- doivent pas finir dans un cache de DBeaver : sur chaque table qui en a, la lecture
    -- porte sur toutes les autres colonnes seulement. PostgreSQL ne sait pas retirer une
    -- colonne d'un droit de table : on retire le droit de table, on donne les colonnes.
    -- Une colonne ajoutée plus tard à ces tables n'est pas lisible (SELECT * refusé) : c'est
    -- voulu, on l'ajoute alors ici par une nouvelle migration si elle n'est pas sensible.
    FOR hashed IN
        SELECT table_name,
               string_agg(quote_ident(column_name), ', ' ORDER BY ordinal_position)
                   FILTER (WHERE column_name NOT LIKE '%\_hash') AS readable
        FROM information_schema.columns
        WHERE table_schema = current_schema()
        GROUP BY table_name
        HAVING bool_or(column_name LIKE '%\_hash')
    LOOP
        EXECUTE format('REVOKE SELECT ON %I FROM ageguessr_readonly', hashed.table_name);
        EXECUTE format('GRANT SELECT (%s) ON %I TO ageguessr_readonly', hashed.readable, hashed.table_name);
    END LOOP;

    -- Les tables créées plus tard par les migrations, c'est-à-dire par le rôle qui exécute
    -- Flyway (current_user). Une future table avec un hachage devra le retirer elle-même.
    EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA %I GRANT SELECT ON TABLES TO ageguessr_readonly',
                   current_user, current_schema());
END
$$;
