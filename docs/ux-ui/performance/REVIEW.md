# Revisión de rendimiento y motion — 2026-10-04

Encargo: investigar lentitud y transiciones poco fluidas, optimizar los caminos
existentes y verificar en el Samsung SM-A165M. Referencia funcional: especificaciones
EP-CTA/EP-MOV y brief de mockups aprobados. Esta revisión no redefine las reglas del
ledger, los filtros Premium, los importes ni el aislamiento por usuario.

## Hallazgos y correcciones

| Punto | Evidencia en código | Cambio |
| --- | --- | --- |
| Colores de Calm Emerald duplicados | Cada llamada creaba 28 `animateColorAsState` y su lectura de motion | Un proveedor por árbol de `KipuTheme`; las filas reutilizan los colores animados |
| Observadores del sistema duplicados | Cada llamada a `rememberReducedMotionEnabled` registraba tres settings | Un observador compartido por tema, conservando cambios de accesibilidad en vivo y fallback para previews |
| Identidad de la paleta Material | `target.copy` creaba una instancia nueva en cada recomposici?n | Reutilizar la instancia mientras sus colores no cambien |
| Historial no virtualizado por movimiento | Un item por fecha componía todas sus filas mediante `forEachIndexed` | Items individuales con ID estable y `contentType`, conservando agrupación por fecha |
| Consultas repetidas por fila | `observeItems` ejecutaba `getById` para origen, destino y tarjeta en cada movimiento | Snapshots observables de cuentas/tarjetas del usuario y lookup por ID; conserva referencias archivadas |
| Recomposición durante rotación | Valores animados leídos en la composición de dashboard, categorías y catálogo | Lectura en `graphicsLayer`, limitada al dibujo del icono |
| Doble animación de tamaño | El catálogo combinaba `animateContentSize` exterior con expansión interior | Una expansión interior de 200 ms; flecha de 150 ms |
| Transiciones prolongadas | Navegación secundaria 250 ms y entre pestañas 200 ms | 220 ms y 160 ms respectivamente; selección 150 ms y soporte de movimiento reducido |

Se añadirá el resultado de la verificación y de la comparación en este informe.

## Puntos siguientes, con prioridad

1. La configuración `release.optimization.enable = false` en `app/build.gradle.kts`
   desactiva la optimización final. Activar R8 requiere validar la versión optimizada
   con las librerías que usan reflexión, compra/restauración y acceso local; compilar
   una APK no prueba por sí solo esos flujos. No se cambió el contrato de release aquí.
2. `MovementHistoryViewModel` observa el historial completo y filtra/ordena en memoria.
   El repositorio ya expone `queryHistory` paginado. Integrar esa consulta con el estado
   de pantalla, acceso Premium, resumen completo y refresco de revisiones es el siguiente
   cambio para historiales grandes; no presentar un resumen de la página como total.
3. `ObserveFinancialDashboard` abre un flow de balance/deuda por cuenta/tarjeta y
   vuelve a filtrar tarjetas de débito por cuenta. Medir con un dataset aislado grande
   antes de reemplazar esas proyecciones por lecturas agrupadas.
4. Añadir Macrobenchmark y Baseline Profiles sobre una variante optimizada y aislada.
   Las mediciones de desarrollo descritas abajo orientan la comparación, no sustituyen
   ese benchmark ni el análisis por trazas de cada camino lento.

## Método

`scripts/performance/profile-ui.py` ejecuta tres repeticiones del mismo recorrido
ADB: 12 cambios entre Dinero y Movimientos, cuatro aperturas/cierres de registro y
ocho swipes de dashboard. Calienta ambos destinos, reinicia `gfxinfo` por escenario,
mantiene los datos existentes y no registra movimientos ni modifica cuentas.
Los JSON contienen frames, janky frames, percentiles y contador de UI lenta.

Dispositivo: Samsung SM-A165M, resolución 1080 × 2340, fuente 0.9, APK debug.
Los percentiles de `gfxinfo` son histogramas discretizados; los cambios de proceso,
temperatura, GC y compilación JIT introducen variación. No equiparar estos números
con latencias de consultas ni con rendimiento de release.

Referencias primarias: [fases de Compose](https://developer.android.com/develop/ui/compose/performance/phases),
[buenas prácticas](https://developer.android.com/develop/ui/compose/performance/bestpractices)
y [medición en release](https://developer.android.com/develop/ui/compose/performance).
