# SnippetSearcher — reglas para Claude

Estas reglas aplican a toda tarea en este repo. Para escribir o modificar código, cargar además la skill `desarrollar-snippetsearcher`.

## Antes de actuar

- Leer el contexto del proyecto: `README.md` (en especial **Decisiones técnicas** y **Flujo de trabajo**), `docs/` (consigna y user stories), `docs/diagramas/` y todo otro `.md` del repo relacionado con la tarea.
- Seguir lo definido en esos archivos. Si algo que la tarea necesita no está definido, o figura como **Pendiente** o **A confirmar**, preguntar a Martina antes de decidir. No inventar stack, versiones, endpoints, contratos entre servicios ni reglas de negocio.

## Git

- Nunca commitear directo a `main` ni a `dev`. Cada tarea va en `feature/us-<n>-<descripcion>` (o `docs/`, `fix/`…) creada desde `dev`.
- Commits en español: `tipo(scope): resumen` en imperativo, máximo 72 caracteres. Tipos: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`. Scope = módulo (`snippets`, `permisos`, `printscript`, `storage`, `ui`, `infra`, `docs`). El cuerpo explica el porqué; el pie referencia la user story (`Refs: US-1`). Un cambio lógico por commit.
- Pushear y abrir PRs solo cuando Martina lo pida o lo confirme.

## PRs

- Los PRs van hacia `dev`. Lo aprobado en `dev` pasa a `main` con un PR `dev` → `main`.
- **Las correcciones de los PRs las hace Martina Cousido.** Claude no aprueba ni mergea PRs.
- Antes de proponer un PR, Claude hace una autocorrección con la skill `corregir-prs` y arregla lo que encuentre.
