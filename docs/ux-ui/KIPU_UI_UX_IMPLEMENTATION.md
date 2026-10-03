# Implementación de la auditoría UI/UX de Kipu

## Estado

**Fases A–E implementadas y verificadas en dispositivo.** La implementación Compose fue delegada a AGY; Codex revisó los diffs, hizo la verificación final de compilación/instalación y recorrió los flujos visuales en un Samsung SM-A165M.

- Rama: `fix/kipu-ui-ux-phases-a-e` (base `9e63572`).
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Compilación e instalación final: `.\gradlew.bat installDebug` — `BUILD SUCCESSFUL`; APK instalado en `RFGL70J9E2T` el 2026-10-03 a las 15:12 (hora de Lima).
- Runtime: Samsung SM-A165M, Android 16 / API 36, pantalla 1080×2340 px, densidad física 450 dpi y override 420 dpi.
- Evidencia runtime y recapturas de la versión instalada final: [`evidence-final/`](./evidence-final/). El índice separa la evidencia recapturada tras la instalación final de las referencias históricas de los mismos recorridos. Las jerarquías XML se validaron y se les retiró `content-desc`.

## Cambios implementados

| Fase | Cambios | Comprobación en runtime |
| :--- | :--- | :--- |
| A — Insets, contraste y feedback | Insets del sistema y de teclado; contraste de selección; copy financiero ligado a datos disponibles; estados empty/loading/error; presentación consistente de signos monetarios. | Dashboard, navegación del sistema, teclado numérico y temas claro/oscuro. |
| B — Filtros y formularios | Filtros básicos primero, opciones Premium progresivas y acciones del sheet fuera del scroll; validación de formularios después de interacción; selección de categoría directa; formularios largos adaptables. | Filtros Free con “Aplicar filtros” y “Cancelar” visibles; gasto sin errores iniciales; teclado y transferencia sin cuenta destino, sin guardar datos. |
| C — Navegación y accesos | Raíces Dinero/Movimientos; Dinero como aterrizaje; Ajustes como destino secundario; acceso al registro rápido; resumen de crédito y fechas configuradas. | Navegación entre raíces y retorno desde Ajustes; barra Compose y navegación de tres botones del sistema. |
| D — Tokens y dinero | Colores derivados del tema Material, escala tipográfica Inter coherente, `MoneyText` sin conversión monetaria a `Double`, componentes comunes y disposición responsive. | Light/Dark, escalado de fuente 0.9 y 1.3, Dashboard, Historial y tarjetas de crédito. |
| E — Motion y reducciones | Tokens de duración/easing, transiciones de navegación y estados; se respeta la escala de animación reducida. | Dashboard e Historial con escalas reducidas; las escalas de animación originales se restauraron al terminar. |

Durante la auditoría final de runtime se corrigieron tres detalles de presentación del Dashboard: acciones de cuenta/registro integradas en el scroll para evitar que cubran encabezados; distribución de chips de estado en filas al aumentar el texto y activar privacidad; y selector de tema apilado a partir de font scale 1.25 para que “Sistema” no se recorte. También se apiló el selector para tamaños grandes. La acción parcialmente visible al borde inferior del viewport inicial de Dashboard a 130% queda completamente accesible al desplazarse; la captura de scroll lo muestra sobre las tarjetas, y al final de la lista hay espacio bajo la última tarjeta.

## Resultado de recorridos

- Dashboard: balance líquido y pasivos separados, límite Free, accesos de acciones, privacidad y recorrido desplazable a 130% de tamaño de fuente.
- Historial: chips rápidos legibles con texto grande y filtros combinables; el sheet Free mantiene sus acciones principales disponibles sin desplazamiento largo y comunica el acceso Premium.
- Formularios: formulario de gasto sin validaciones prematuras; CTA visible con teclado; transferencia explica que se requiere otra cuenta PEN y ofrece el siguiente paso. No se completó ni guardó ningún formulario.
- Instrumentos: se inspeccionaron selectores y estados iniciales de cuenta/débito y tarjeta; no se seleccionó un producto para guardarlo.
- Movimiento anulado existente: detalle muestra `VOIDED`/sin efecto en saldos y revisión; no se modificó.
- Temas, tamaño de fuente y reducción de movimiento: Light/Dark, 0.9/1.3 y escala cero temporal fueron recorridos. Estado final restaurado: font scale `0.9`, escalas de ventana/transición `1.0`, `animator_duration_scale` sin valor (por defecto), tema Kipu oscuro y balances visibles.

No se creó, editó, anuló, borró ni compró ningún movimiento, cuenta o tarjeta; tampoco se limpiaron datos. La app mostró “Cambios pendientes de sincronizar”; no se accionó sincronización manual. La UI mostraba el límite Free en 4/4 y saldo líquido total S/ 95.00 durante la inspección; esos datos no se cambiaron.

## Verificación y límites

- **Ejecutado:** `git diff --check`; compilación e instalación final con `installDebug`; batería manual de navegación, Dashboard, filtros, formularios, detalle de movimiento, temas, escalado de fuente y estados de privacidad en el Samsung.
- **Jerarquías:** 51 archivos XML de evidencia parseados; ninguno inválido; `content-desc` fue retirado y no quedan archivos XML crudos.
- **No ejecutado:** suites unitarias/integración con Gradle. No se revisaron login, registro, recuperación, onboarding de acceso ni TalkBack por exclusión explícita del alcance.
- **Fuera de esta implementación:** CE-04 (paginación y rendimiento con historiales grandes) y CE-05/T110 (procedencia) se mantienen diferidos según la decisión del usuario. No se añadió contrato/campo de procedencia ni se atribuyó origen a movimientos históricos.
- La verificación no acredita sincronización real de outbox, rendimiento a gran escala ni cumplimiento de TalkBack. Contraste estático documentado en la auditoría: 8.85:1 para texto secundario sobre superficie clara y 6.69:1 sobre superficie oscura de tarjeta; esta comprobación estática no sustituye una matriz automatizada de contraste para cada combinación.

## Evidencia

Las capturas finales y sus jerarquías XML sanitizadas están en [`evidence-final/`](./evidence-final/). Los archivos `before_fix_*` preservan capturas de defectos observados antes de los últimos ajustes; el índice distingue esas referencias de la evidencia final. El informe de auditoría previo está en [`KIPU_UI_UX_AUDIT.md`](./KIPU_UI_UX_AUDIT.md).
