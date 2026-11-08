-- Reglas de lint y formateo de cada usuario (US-11, US-14). Se guarda solo lo
-- que el usuario cambió: las reglas que no están acá usan el valor por defecto
-- del catálogo del lenguaje. value es el valor de la regla como texto (NULL en
-- las de sí o no).
CREATE TABLE user_rules (
    user_id   VARCHAR(255) NOT NULL, -- el sub del token de Auth0
    kind      VARCHAR(10)  NOT NULL, -- lint o format
    name      VARCHAR(100) NOT NULL, -- el nombre de la regla en la librería
    is_active BOOLEAN      NOT NULL,
    value     VARCHAR(100),
    PRIMARY KEY (user_id, kind, name)
);
