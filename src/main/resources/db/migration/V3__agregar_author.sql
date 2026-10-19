-- Quién creó el snippet (su owner): el userId de Auth0. La UI lo muestra en el
-- listado. Los snippets que ya existían los creó el usuario fijo dev-user.
ALTER TABLE snippets ADD COLUMN author VARCHAR(255) NOT NULL DEFAULT 'dev-user';
ALTER TABLE snippets ALTER COLUMN author DROP DEFAULT;
