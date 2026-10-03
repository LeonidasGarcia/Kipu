# Contract: consulta de historial y acceso S4 — T061

**Estado**: frontera del consumidor EP-MOV definida; productor HU-58/HU-59 EP-PLA pendiente de implementación/integración. **Trazabilidad**: HU-22/58/59; FR-016–018/027/032; P15/P31; P3/B1.

## Consulta y propietario

HistoryQuery básica: texto opcional, fromInclusive/toExclusive opcionales y conjunto de tipos Gasto/Ingreso/Transferencia. Texto busca campos visibles de comercio/categoría/nota; normalización no altera hechos. Fechas de UI se convierten con zoneId explícita y fin exclusivo; validar inicio<=fin, no invertir silenciosamente. La consulta de fuente local cubre todos los datos disponibles, sin limitar Free al mes actual.

AdvancedCriteria añade conjuntos de cuenta, tarjeta, categoría y comercio, límites de amount_minor, source y estado financiero/sync identificados por separado. Un criterio avanzado presente requiere capacidad avanzada incluso si es el único. Criterios se combinan por intersección; IDs dentro de cada conjunto por pertenencia, sin duplicar filas. Validar rango monetario y currency explícita cuando haya comparación de montos; no comparar monedas distintas como equivalentes.

El propietario se deriva de LocalAccess/sesión antes de consulta; no aceptar un owner de deep link. Consultar referencias archivadas/bloqueadas y VOIDED como historia propia. Privacidad enmascara presentación, no cambia el SQL ni los valores.

## Policy port del consumidor

Entrada: propietario activo, capacidad ADVANCED_MOVEMENT_FILTERS, evidencia efectiva y temporal proporcionada por EP-PLA y versión de política. EP-MOV no fabrica evidencia ni firma/renueva concesiones; configuración comercial y callbacks de Play no son inputs de autoridad.

Resultado conceptual:

| Decisión | Semántica |
|---|---|
| ALLOWED | Identifica propietario, capacidad, versión y validez comprobada en esta evaluación; no cachear indefinidamente |
| PREMIUM_REQUIRED | Free sin entitlement verificado; ofrecer consulta básica |
| REVALIDATION_REQUIRED | Concesión caducada, ausente, inválida o sin continuidad temporal demostrable; pedir reconexión sin bloquear Free |
| NOT_AUTHORIZED | Sesión/propietario inválido; no revelar contenido |

FeatureAccessPolicy existente de EP-PLA debe evolucionar conservando FREE_CORE/FREE_LIMITED y semántica de cuotas. El contrato histórico `specs/012-ep-pla-planes-monetizacion/contracts/feature-access-policy.md` es la referencia de integración, no prueba de soporte HU-59.

## Concesión y reloj

- Límite `notAfter = min(verifiedServerTime + 72 horas, knownEntitlementEnd)`; Lifetime usa el primer término. Concesión firmada/verificada, ligada a usuario/dispositivo/política; el cliente no puede editar notAfter para ganar tiempo.
- Permitir solo si `trustedNow < notAfter`; igualdad o mayor exige revalidación. Expiración comercial en 12 h corta antes de 72 h.
- Durante el mismo boot, productor combina tiempo de servidor verificado con elapsedRealtime (incluye suspensión) y ancla monotónica persistida. No usar currentTimeMillis para prolongar vigencia.
- Tras reboot, no asumir continuidad de elapsedRealtime ni derivarla de fecha civil manipulable. Si no existe evidencia segura de continuidad, REVALIDATION_REQUIRED hasta respuesta remota verificada. Reinstalación/restore/dispositivo diferente tampoco renuevan la concesión.
- Revalidación temporalmente fallida no concede nueva ventana ni revoca una concesión previa aún demostrablemente válida; al límite o sin evidencia, deniega Premium. Resultado remoto negativo/revocado actualiza acceso a Free sin borrar historia.
- Registro/edición/anulación manual estándar, sync y consulta básica continúan disponibles para sesión local previamente autenticada, respetando elegibilidad/integridad; no poner app en solo lectura.

## Ejecución, cursor y cambio de acceso

Autorizar antes del DAO al aplicar consulta, abrir deep link, cargar cada página y refrescar. Cursor opaco local incluye fecha efectiva+ID y fingerprint de criterios/propietario; otro propietario o criterios distintos invalida cursor. Orden descendente por fecha y ID; cambios de filtros, commit/revisión del dataset o renovación/revocación de acceso reinician desde primera página. No reutilizar resultados avanzados precalculados tras denegación.

Al denegar, no ejecutar subconjunto avanzado. Ofrecer alternativa conservando texto/fechas/tipos y explicar criterios retirados; selección guardada se conserva. Limpiar elimina criterios activos y reinicia cursor, sin borrar historia. Estado vacío distingue ausencia de movimientos de ausencia de coincidencias.

Revalidar también un enlace creado durante Premium; ID ajeno devuelve mensaje neutro. Guardar/restaurar state del panel no es autorización. Todas las superficies consumen el mismo caso de uso protegido; UI oculta/explica, dominio impide bypass.

## Matriz temporal requerida

| Evidencia | Instante/caso | Consulta avanzada | Básica / manual |
|---|---|---|---|
| Verificada, mismo usuario/boot | notAfter-1 ms | ALLOWED | Permitidos |
| Verificada | exactamente notAfter | REVALIDATION_REQUIRED | Permitidos |
| Verificada | notAfter+1 ms | REVALIDATION_REQUIRED | Permitidos |
| Comercial termina antes | exactamente fin comercial | REVALIDATION_REQUIRED | Permitidos |
| Lifetime | 72 h desde validación | REVALIDATION_REQUIRED | Permitidos |
| Reloj civil atrasado/adelantado | ancla monotónica vigente/caducada | Depende del tiempo confiable, no del civil | Permitidos |
| Reboot sin continuidad confiable | cualquier reloj civil | REVALIDATION_REQUIRED | Permitidos |
| Evidencia ajena/restaurada/manipulada | cualquiera | Denegar | Solo datos de sesión propia |
| Red recuperada, verificación válida | nueva concesión verificada | ALLOWED según capacidad | Sync con identidades originales |

## Responsabilidades y gates

EP-MOV implementa query/cursor, revalidación y alternativa Free (T079–T083); T061 deja definida esta interfaz desde requisitos aprobados. EP-PLA implementa evidencia, evaluación temporal, firma/validación y revalidación remota en HU-58/59. La interfaz permite dobles controlados para pruebas de consumidor, no falsos entitlements en release.

T084 permanece bloqueada hasta productor integrado, contrato compatible y pruebas de reloj/reinicio/restore en dispositivo más proveedor cuando aplique. T063 revisa el diseño de consumo; no marca HU-58/59 completas ni fuerza su implementación dentro de EP-MOV. Ningún acuerdo con otro equipo o prueba de proveedor se presume realizado.
