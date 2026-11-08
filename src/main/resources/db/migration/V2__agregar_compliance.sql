-- Estado de lint del snippet, con los valores de la UI: pending, failed,
-- not-compliant o compliant. Empieza en pending hasta que se lintea (#23).
ALTER TABLE snippets ADD COLUMN compliance VARCHAR(20) NOT NULL DEFAULT 'pending';
