---
name: corregir-prs
description: Usar cuando se pide corregir, revisar o hacer code review de un PR, pull request, rama o diff de SnippetSearcher, o decidir si un cambio se puede mergear.
---

# Corregir PRs de SnippetSearcher

## Principio

Una corrección vale por su veredicto: dice si el PR se puede mergear y, si no, exactamente qué cambiar. Todo hallazgo se verifica contra el código real antes de reportarlo.

Las correcciones las firma **Martina Cousido**. El reporte es **solo local**: se muestra en la conversación y nunca se publica en GitHub (ni comentarios, ni reviews, ni approve).

## 1. Juntar contexto

| Fuente | Cómo |
|---|---|
| PR en GitHub | `gh pr view <n>`, `gh pr diff <n>`, `gh pr view <n> --json commits,files,baseRefName` |
| Rama local | `git diff main...<rama>`, `git log main..<rama>` |
| User story | Número en la descripción del PR → `docs/Snippet Searcher - Casos de uso.pdf` |
| Arquitectura | README (Módulos, Dependencias) y `docs/diagramas/` |
| Reglas del proyecto | [checklist-proyecto.md](checklist-proyecto.md) |
| Código alrededor | Quién llama a lo que cambió y qué otros servicios lo consumen |

Si el PR no referencia una user story, ese es un hallazgo 🟠.

## 2. Revisar

Recorrer [checklist-proyecto.md](checklist-proyecto.md) completo. Correr build y tests si el proyecto lo permite y reportar el resultado real.

## 3. Asignar severidad

| Severidad | Criterio | Bloquea |
|---|---|---|
| 🔴 Bloqueante | Bug, agujero de seguridad o de permisos, secreto commiteado, pérdida de datos, contrato roto entre servicios, no compila, no cumple una nota de la US | Sí |
| 🟠 Debe corregirse | Viola una regla de arquitectura del checklist, faltan tests del comportamiento nuevo, la descripción del PR no coincide con el diff | Sí, salvo que se acuerde postergarlo con un issue creado |
| 🟡 Sugerencia | Mejora de diseño o legibilidad | No |
| ⚪ Nit | Detalle menor que un linter no detecta | No |

Las reglas de arquitectura del README son decisiones ya tomadas: violarlas es 🟠 como mínimo, no un tema "a discutir". Lo que detecta un linter o formatter no se reporta.

## 4. Reporte

El reporte tiene exactamente estas partes, en este orden:

```markdown
# Corrección PR #<n>: <título>

**Revisión:** Martina Cousido · **Fecha:** <AAAA-MM-DD> · **Autor del PR:** <autor>

## Veredicto: <✅ Aprobar | 🟡 Aprobar con sugerencias | 🔴 Cambios requeridos antes de mergear>
<Dos líneas: por qué.>

## Cumplimiento de la user story (US-<n>)
- [x] <nota de la US cumplida>
- [ ] <nota de la US no cumplida> → ver hallazgo <id>

## Hallazgos
### 🔴 Bloqueantes
**B1. <título>** — `ruta/archivo.kt:<línea>`
- **Problema:** <qué pasa>
- **Escenario:** <entrada o estado concreto → resultado incorrecto>
- **Corrección:** <cambio concreto, con código si ayuda>

### 🟠 Debe corregirse
(mismo formato, ids D1, D2…)
### 🟡 Sugerencias
(ids S1…)
### ⚪ Nits
(ids N1…)

## Build y tests
<Comando ejecutado y resultado, o "No ejecutado: <motivo>".>

## Qué no se revisó
<Lo que quedó fuera y por qué.>
```

Las secciones de severidad vacías se escriben con "Ninguno". El veredicto es 🔴 si hay al menos un 🔴 o un 🟠 sin issue acordado.

## Re-corrección

Si el autor dice "ya lo corregí", verificar en el código **cada** hallazgo anterior por su id (✅ resuelto / ❌ pendiente) y revisar el diff nuevo con el mismo proceso.
