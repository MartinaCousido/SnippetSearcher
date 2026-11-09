-- Con qué versión de la librería del lenguaje se analizó cada snippet (#93).
-- null: todavía no se analizó con una versión conocida (los de antes de este
-- cambio). Cuando cambia la versión, se vuelven a validar, lintear y formatear.
ALTER TABLE snippets ADD COLUMN analyzed_with VARCHAR(50);
