-- Metadatos de cada snippet. El código no se guarda acá: vive en el Snippet
-- Storage (Asset Service), y content_key es la clave de ese archivo.
CREATE TABLE snippets (
    id          UUID         PRIMARY KEY, -- identifica el snippet
    name        VARCHAR(255) NOT NULL,
    description TEXT         NOT NULL DEFAULT '', -- opcional
    language    VARCHAR(50)  NOT NULL,
    version     VARCHAR(20)  NOT NULL,
    content_key VARCHAR(255) NOT NULL UNIQUE -- clave del codigo en el Asset Service
);
