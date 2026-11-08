-- Tests de cada snippet (US-8): una lista ordenada de inputs y otra de outputs
-- esperados. last_result es el resultado de la última corrida automática
-- (US-16): success, fail o NULL si todavía no se corrió. Al borrar un snippet,
-- se borran sus tests.
CREATE TABLE test_cases (
    id          UUID         PRIMARY KEY,
    snippet_id  UUID         NOT NULL REFERENCES snippets (id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    inputs      TEXT[]       NOT NULL DEFAULT '{}',
    outputs     TEXT[]       NOT NULL DEFAULT '{}',
    last_result VARCHAR(10),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now() -- para listarlos en el orden en que se crearon
);

CREATE INDEX test_cases_snippet_id ON test_cases (snippet_id);
