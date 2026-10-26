-- Role applicatif. Aucun endpoint ne le modifie : un administrateur est designe a la
-- main en SQL (UPDATE users SET role = 'ADMIN' WHERE email = ...), ce qui ne laisse
-- aucune surface d'attaque pour s'auto-promouvoir.
ALTER TABLE users ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD CONSTRAINT users_role CHECK (role IN ('USER', 'ADMIN'));
