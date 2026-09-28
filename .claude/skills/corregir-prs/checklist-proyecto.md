# Checklist de SnippetSearcher

Reglas que todo PR tiene que respetar. Cuando la cátedra sume requerimientos no funcionales, se agregan acá.

## User story

- [ ] Cada nota de la US que el PR dice implementar está cumplida.
- [ ] Validación de snippets: los errores incluyen **regla, línea y columna** y se responden con **400** (US-1, US-2).
- [ ] Crear y actualizar aplican la misma validación (US-2, US-3, US-4).
- [ ] Metadatos completos: nombre, descripción, lenguaje **y versión** (US-1).

## Seguridad y permisos → 🔴

- [ ] Todo endpoint obtiene el `userId` del **JWT de Auth0** validado, nunca de un header, parámetro o body que manda el cliente.
- [ ] Toda operación sobre un snippet consulta a **Permisos**: solo el owner escribe, borra y comparte; el usuario con acceso compartido solo lee (US-7).
- [ ] Al crear un snippet se registra al owner en Permisos.
- [ ] No hay secretos en el repo (contraseñas, client secrets de Auth0, tokens). Van en variables de entorno y se documentan en `.env.example`.

## Arquitectura → 🟠

- [ ] Snippets depende de la interfaz `Language` y llama por HTTP al servicio del lenguaje; nunca importa la librería de PrintScript.
- [ ] El contenido del snippet se guarda en **Snippet Storage** (bucket); en Postgres van solo metadatos y la referencia.
- [ ] Ningún servicio accede a la base de datos de otro.
- [ ] No hay dependencias circulares entre servicios. Nadie depende de Snippets salvo la UI.

## Contratos y datos → 🔴

- [ ] Si cambia un endpoint o DTO, se actualizaron todos sus consumidores (UI y otros servicios).
- [ ] Los esquemas cambian con migraciones; nunca `ddl-auto: create-drop` ni `create` fuera de tests.
- [ ] Recursos inexistentes → 404; sin permiso → 403; nunca un 500 por `.get()` sobre un Optional o por excepciones sin manejar.

## Procesos asíncronos (US-12, US-15, re-validación por versión del parser)

- [ ] El usuario no espera a que termine el proceso.
- [ ] Tolerante a fallos: retoma desde donde falló o saltea el snippet problemático.
- [ ] Procesar dos veces el mismo mensaje no produce un resultado distinto (idempotencia).

## Tests y documentación → 🟠

- [ ] Hay tests del comportamiento nuevo, incluidos los casos de error y de permisos.
- [ ] La descripción del PR coincide con el diff (si dice "tests incluidos", los tests están).
- [ ] La descripción explica cómo probarlo con pasos reproducibles.
- [ ] Si cambió la arquitectura, se actualizaron el README y `docs/diagramas/`.
