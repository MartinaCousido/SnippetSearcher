-- Lint y formateo en segundo plano (#23, US-12, US-15).
-- lint_revision y format_revision suben cada vez que se pide un lint o un
-- formateo nuevo: un resultado de una revisión vieja se descarta.
-- formatted_key es la clave del archivo formateado en el Snippet Storage
-- (NULL si todavía no hay, o si no se pudo formatear).
ALTER TABLE snippets ADD COLUMN lint_revision   INTEGER      NOT NULL DEFAULT 0;
ALTER TABLE snippets ADD COLUMN format_revision INTEGER      NOT NULL DEFAULT 0;
ALTER TABLE snippets ADD COLUMN formatted_key   VARCHAR(255);

-- Las reglas de lint que incumple cada snippet (US-6), del último lint.
CREATE TABLE snippet_lint_violations (
    snippet_id   UUID         NOT NULL REFERENCES snippets (id) ON DELETE CASCADE,
    position     INTEGER      NOT NULL, -- el orden en que las devolvió el linter
    rule         VARCHAR(100) NOT NULL,
    message      TEXT         NOT NULL,
    start_line   INTEGER      NOT NULL,
    start_column INTEGER      NOT NULL,
    end_line     INTEGER      NOT NULL,
    end_column   INTEGER      NOT NULL,
    PRIMARY KEY (snippet_id, position)
);
