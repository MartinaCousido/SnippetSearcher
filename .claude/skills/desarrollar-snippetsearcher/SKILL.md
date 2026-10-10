---
name: desarrollar-snippetsearcher
description: Usar cuando se pide implementar una user story, escribir, modificar o refactorizar código, crear un servicio o agregar tests en SnippetSearcher, incluso si hay apuro o se pide avanzar sin frenar.
---

# Desarrollar en SnippetSearcher

## Principio

El código se construye solo sobre decisiones registradas en el repo. Una decisión que falta se le pregunta a Martina **antes** de escribir código, no se deduce ni se deja "documentada como supuesto".

## Regla de corte

```
SI LA TAREA NECESITA ALGO NO DEFINIDO → NO SE ESCRIBE CÓDIGO QUE DEPENDA DE ESO
```

"Definido" = escrito como decisión en un `.md` del repo (README → **Decisiones técnicas**, user stories, diagramas). **Pendiente**, **A confirmar**, "referencia" o una pista indirecta (un `.gitignore`, un ejemplo en un checklist, el stack de otro proyecto) **no** es una decisión.

Aplica aunque Martina pida avanzar sin frenar: la instrucción de avanzar no es una decisión de stack ni de contrato.

## Proceso

1. **Leer** `CLAUDE.md`, `README.md` completo, la user story en `docs/` (los PDFs se leen con la herramienta Read), `docs/diagramas/`, [checklist de corrección](../corregir-prs/checklist-proyecto.md) y el código existente relacionado. Si un documento no se puede leer, decirlo en la entrega.
2. **Listar lo que la tarea necesita**: stack, librerías, endpoints propios, contratos con otros servicios, formatos, límites. Marcar cada ítem como *definido (dónde)* o *no definido*.
3. **Si hay algo no definido**: detenerse y entregar a Martina, sin código escrito, una lista de decisiones con, para cada una: qué hace falta decidir, por qué bloquea, 2–3 opciones y una recomendación. Se puede avanzar solo en lo que no depende de esas decisiones (y decir cuál es).
4. **Cuando Martina decide**: registrar la decisión en README → **Decisiones técnicas** (en un commit `docs`) y recién ahí implementar.
5. **Rama** `feature/us-<n>-<descripcion>` desde `dev`.
6. **Implementar el mínimo** que cumple las notas de la user story. Nada de otras US "de paso".
7. **Tests** del comportamiento nuevo, incluidos errores y permisos. Correr build y tests; reportar el resultado real.
8. **Commits** con el formato de `CLAUDE.md`.
9. **Autocorrección**: aplicar la skill `corregir-prs` sobre `dev...HEAD` y corregir todo 🔴 y 🟠.
10. **Entregar** a Martina: resumen, rama, commits, resultado de tests, autocorrección y descripción del PR (Qué / Por qué / Cómo / Impacto / Cómo probarlo). Push y PR hacia `dev` solo si ella lo confirma. Martina corrige; Claude no aprueba, y mergea solo si ella se lo pide.

## Justificaciones que no valen

| Justificación | Realidad |
|---|---|
| "El `.gitignore` / el checklist / PrintScript usa Kotlin, así que el stack es Kotlin" | Es una pista, no una decisión. El README dice Pendiente. |
| "Hay apuro, avanzo y pregunto al final" | Preguntar al final obliga a rehacer el código. Preguntar primero es más rápido. |
| "Dejo los supuestos documentados en el PR / README" | Documentar un invento no lo vuelve una decisión, y en el README parece definido. |
| "Lo hago configurable, así no importa el contrato" | El formato del request y la respuesta sigue siendo inventado. |
| "Es el estándar de la industria" | El proyecto decide; el estándar entra como recomendación en la pregunta. |
| "El README no es práctico acá, lo cambio" | Desviarse de la arquitectura es una decisión de Martina. Proponerlo, no hacerlo. |

## Señales de alarma: frenar

- Estás por elegir una versión, framework o librería que no figura en **Decisiones técnicas**.
- Estás escribiendo la URL o el formato de un servicio que no existe en el repo.
- Estás escribiendo "supuse…", "asumí…" o "a confirmar" en código o docs.
- El diff pasa de una decena de archivos para una sola user story.
