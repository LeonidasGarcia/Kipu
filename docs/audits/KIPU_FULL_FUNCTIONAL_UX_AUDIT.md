# Auditoría funcional y UX de Kipu — Informe de QA en dispositivo

**Fecha:** 28/09/2026 (America/Lima)  
**Dispositivo:** Samsung Galaxy S24+ — SM-S926B — R5CX10W7CZD  
**Paquete / base local:** com.kipu.app — Room kipu.db, esquema 15  
**Estado:** **INCOMPLETA** — se probaron y conciliaron varios flujos críticos, pero quedan escenarios y contratos sin evidencia para declarar cierre integral.  
**Cambios:** se actualizó este informe y se conservaron evidencias de QA. No se modificó código de producto ni se hicieron commits. El árbol contenía otros archivos modificados/sin seguimiento; esta auditoría no los alteró.

## 1. Resumen ejecutivo

Se recorrieron flujos de cuentas, tarjetas, ledger, historial, filtros, pagos, transferencias, cuotas, archivado y ajustes con datos mock en el Samsung. La mayoría de las operaciones locales se conciliaron entre UI, Room y ledger. No se reprodujo hoy el crash al guardar gasto/compra con tarjeta: la compra en cuotas de S/ 100 y tres compras de prueba se guardaron y aparecieron en historial. AGY atribuyó las trazas antiguas de gastos con tarjeta a un mapper previo que perdía `operationKind`; el árbol actual contiene mapper unificado y regresión Android para `CARD_PURCHASE` (BUG-MAP-01, resuelto en el árbol; la suite no se ejecutó). Sí se confirmó otro crash vigente al abrir Avisos.

Hallazgos principales:

1. **BUG-NAV-01 P1 confirmado:** la campana «Notificaciones» termina el proceso de Kipu tres veces. El NavHost que se ejecuta en `MainActivity` no registra `notifications/center`; el callback intenta navegar a esa ruta ausente. No atribuir este crash al guardado de gastos con tarjeta.
2. **BUG-FIN-01/02/03 P2:** el importe de pago de tarjeta precargado con separador de miles se rechaza; Efectivo se ofrece como fuente pero el repositorio lo rechaza; y la segunda corrección del saldo inicial revierte el asiento equivocado.
3. **BUG-FIN-04 P2 confirmado:** el alta de tarjeta falla en 2/4 y 3/4 porque `AccountsViewModel` genera un `UserId` aleatorio y el repositorio lo compara con el propietario activo. No se crea tarjeta ni se modifica Room.
4. **HU-10:** se comprobaron visualmente las utilizaciones 50%, 80% y 100% con compras locales y se compensaron los importes para restaurar los saldos. Los avisos, su deduplicación/rearme y la disponibilidad con permiso denegado requieren sincronización confirmada con backend; quedan incompletos, no se afirma que deban aparecer en modo offline.
5. **UX-06 P3 observado:** al abrir Historial se llegó a mostrar brevemente «Sin movimientos registrados» y la jerarquía de accesibilidad un instante después ya contenía los movimientos de Room. La inicialización del estado de owner explica una emisión temporal vacía; no hubo evidencia de pérdida de datos.

La fixture financiera quedó restaurada: activos líquidos S/ 3,893.78; Amex archivada con deuda S/ 500.00; BCP archivada con deuda cero; 2/4 instrumentos. El recorrido HU-10 deja deliberadamente cinco transacciones de QA y seis asientos netos de ledger en el historial; todas siguen PENDING localmente. El cierre Room tiene 31 transacciones y 41 asientos. No hubo sincronización de backend.

No se modificó código de producto ni se ejecutaron cambios de datos en backend. El estado integral permanece **INCOMPLETO** por los bugs abiertos y la cobertura/sincronización pendiente.

## 2. Método, límites y evidencia

- Se inspeccionó la aplicación abierta en el Samsung; las cuentas y transacciones eran mockups de QA y se autorizó archivado/creación de datos de prueba.
- Cada operación financiera completada se contrastó entre UI, Room, transacción y ledger.
- AGY actuó como revisor independiente. Se separan hechos runtime de hallazgos estáticos.
- Room refleja persistencia local, no sincronización exitosa con backend. Estados PENDING/FAILED se reportan aparte.
- El error al crear la tarjeta se atribuye al mismatch de propietario entre ViewModel y sesión activa (BUG-FIN-04); no se atribuye al límite Free.
- Tras el último crash de navegación, el proceso de Kipu salió al launcher. La reconexión solicitada todavía no reaparecía en `adb devices` en el cierre de esta edición; no se ejecutaron nuevas mutaciones a partir de ese momento.

### Evidencia principal

| Evidencia | Contenido |
|---|---|
| [00-room-initial.txt](evidence/00-room-initial.txt), [00-initial.png](evidence/00-initial.png) | Baseline Room/UI, cuentas, categorías, instrumentos y sincronización. |
| [12-room-after-full-payment.txt](evidence/12-room-after-full-payment.txt), [12-pay-after.png](evidence/12-pay-after.png) | Pago total BCP y estado posterior. |
| [25-room-post-transfer.txt](evidence/25-room-post-transfer.txt), [26-transfer-filter.png](evidence/26-transfer-filter.png) | Transferencia reconciliada y filtro del historial. |
| [17-new-movement.png](evidence/17-new-movement.png) | Selector de categorías plano. |
| [39-room-after-credit-purchase.txt](evidence/39-room-after-credit-purchase.txt), [45-room-after-partial-payment.txt](evidence/45-room-after-partial-payment.txt) | Compra en cuotas y pago parcial. |
| [58-total-prefill-confirmed.png](evidence/58-total-prefill-confirmed.png), [59-room-after-total-prefill-payment.txt](evidence/59-room-after-total-prefill-payment.txt), [66-manual-payment-recovered.png](evidence/66-manual-payment-recovered.png) | Primer fallo de prefill y recuperación manual. |
| [77-second-prefill-confirmation-error.png](evidence/77-second-prefill-confirmation-error.png), [78-room-after-second-prefill-failure.txt](evidence/78-room-after-second-prefill-failure.txt) | Segundo fallo independiente y estado Room sin pago. |
| [80-second-source-type-rejected.png](evidence/80-second-source-type-rejected.png), [81-payment-invalid-source-dismissed.png](evidence/81-payment-invalid-source-dismissed.png), [85-second-payment-recovered.png](evidence/85-second-payment-recovered.png) | Fuente Efectivo rechazada, diálogo bloqueado y recuperación. |
| [93-add-fourth-instrument-saved.png](evidence/93-add-fourth-instrument-saved.png), [94-instrument-create-session-error.png](evidence/94-instrument-create-session-error.png) | La captura 93 contiene el toast owner/session; la 94 muestra dashboard 3/4 tras el rechazo. |
| [108-duplicate-test-first-saved.png](evidence/108-duplicate-test-first-saved.png), [112-room-after-duplicate-dismissed.txt](evidence/112-room-after-duplicate-dismissed.txt), [118-exact-duplicate-warning.png](evidence/118-exact-duplicate-warning.png), [119-duplicate-under60-result.png](evidence/119-duplicate-under60-result.png) | Flujo de similitud; límite temporal exacto inconcluso. |
| [123-bcp-archive-dialog.png](evidence/123-bcp-archive-dialog.png), [124-bcp-after-archive.png](evidence/124-bcp-after-archive.png), [125-history-after-card-archive.png](evidence/125-history-after-card-archive.png), [126-room-final-audit-state.txt](evidence/126-room-final-audit-state.txt) | Archivado sin borrar historial y estado local final. |
| [130-transfer-destination-list.png](evidence/130-transfer-destination-list.png), [134-transfer-insufficient-result.png](evidence/134-transfer-insufficient-result.png), [135-room-after-insufficient-transfer.txt](evidence/135-room-after-insufficient-transfer.txt) | El origen no aparece como destino; transferencia S/ 2,000 a saldo negativo, persistida y conciliada. |
| [139-cleanup-transfer-ready.png](evidence/139-cleanup-transfer-ready.png), [140-cleanup-transfer-saved.png](evidence/140-cleanup-transfer-saved.png), [143-transfer-doubletap-ready.png](evidence/143-transfer-doubletap-ready.png), [144-transfer-doubletap-result.png](evidence/144-transfer-doubletap-result.png), [149-room-final-after-qa.txt](evidence/149-room-final-after-qa.txt) | Transferencias compensatorias, doble toque de S/ 0.01 y estado Room intermedio. |
| [154-cash-alias-edited.png](evidence/154-cash-alias-edited.png), [156-cash-alias-restored.png](evidence/156-cash-alias-restored.png), [161-add-cash-result.png](evidence/161-add-cash-result.png), [165-qa-cash-archived.png](evidence/165-qa-cash-archived.png), [178-add-savings-saved.png](evidence/178-add-savings-saved.png), [181-qa-savings-archived.png](evidence/181-qa-savings-archived.png) | Alias editado/restaurado; altas y archivados de cuentas CASH/SAVINGS. |
| [167-initial-balance-correction-dialog.png](evidence/167-initial-balance-correction-dialog.png), [170-room-after-balance-correction/kipu.db](evidence/170-room-after-balance-correction/kipu.db), [171-initial-balance-restoration-ready.png](evidence/171-initial-balance-restoration-ready.png), [173-room-after-restoration-attempt/kipu.db](evidence/173-room-after-restoration-attempt/kipu.db) | Reproducción de correcciones consecutivas del saldo inicial; la segunda no restablece el saldo esperado. |
| [182-initial-balance-cleanup-ready.png](evidence/182-initial-balance-cleanup-ready.png), [183-initial-balance-cleanup-result.png](evidence/183-initial-balance-cleanup-result.png), [184-room-final-after-all-tests.txt](evidence/184-room-final-after-all-tests.txt) | Compensación QA para restaurar saldo y snapshot Room final (26 transacciones, 35 asientos). |
| [186-settings.png](evidence/186-settings.png), [191-settings-private-retest.png](evidence/191-settings-private-retest.png), [192-home-private-retest.png](evidence/192-home-private-retest.png), [193-settings-private-persisted.png](evidence/193-settings-private-persisted.png), [199-settings-toggle-left.png](evidence/199-settings-toggle-left.png), [200-final-dashboard-visible.png](evidence/200-final-dashboard-visible.png) | HU-04: máscara de importes, persistencia al navegar/reabrir y restauración del valor original. |
| [207-credit-form.png](evidence/207-credit-form.png), [208-credit-form-bottom.png](evidence/208-credit-form-bottom.png), [210-credit-product-selected.png](evidence/210-credit-product-selected.png) | HU-11/HU-08: BCP expande Amex mientras Visa está activa; al seleccionar Amex se sincronizan preview, alias y red. Formulario no guardado. |
| [223-credit-form-open.png](evidence/223-credit-form-open.png), [224-credit-form-state.xml](evidence/224-credit-form-state.xml), [225-credit-product-details.png](evidence/225-credit-product-details.png), [226-final-dashboard.png](evidence/226-final-dashboard.png) | Revisión read-only HU-11: selector Amex sincroniza la red; el formulario solo muestra aviso genérico TEA y se cerró sin guardar. Dashboard final confirmado. |
| [212-card-detail.png](evidence/212-card-detail.png), [214-card-reactivated.png](evidence/214-card-reactivated.png), [215-card-active-detail.png](evidence/215-card-active-detail.png), [219-archive-reactivated-dialog.xml](evidence/219-archive-reactivated-dialog.xml), [220-restored-dashboard.png](evidence/220-restored-dashboard.png) | HU-11: reactivación temporal para abrir tarifario y archivado restaurado; cupo final Free 2/4, Amex archivada y deuda S/ 500.00. |
| [216-rate-catalog.png](evidence/216-rate-catalog.png), [217-rate-catalog-retry.png](evidence/217-rate-catalog-retry.png), [221-live-room-final-hu11.txt](evidence/221-live-room-final-hu11.txt), [222-rate-catalog-diagnostics.txt](evidence/222-rate-catalog-diagnostics.txt) | HU-11: fecha 24/09/2026 y advertencia visibles; no cargan filas de tasas ni tras reintento. Causa remota no determinada; Room de cierre sin cambios financieros; outboxes locales separados. |
| [230-credit-form-top.png](evidence/230-credit-form-top.png), [232-credit-card-save-ready.png](evidence/232-credit-card-save-ready.png), [233-qa-hu10-card-create.png](evidence/233-qa-hu10-card-create.png) | HU-08: el alta de tarjeta falla también con 2/4 instrumentos; error de owner/session, sin alta en Room. |
| [239-reactivation-result.png](evidence/239-reactivation-result.png), [249-hu10-purchase-result.xml](evidence/249-hu10-purchase-result.xml), [250-room-after-hu10-50.txt](evidence/250-room-after-hu10-50.txt), [261-hu10-debt-80.png](evidence/261-hu10-debt-80.png), [263-hu10-debt-100.png](evidence/263-hu10-debt-100.png) | HU-10: compras locales y saldos de utilización 50/80/100%. |
| [251-hu10-notifications-screen.png](evidence/251-hu10-notifications-screen.png), [251-hu10-notifications-screen.xml](evidence/251-hu10-notifications-screen.xml), [253-app-reopened.png](evidence/253-app-reopened.png) | El fotograma inicial muestra estado vacío; el volcado posterior ya contiene transacciones. Se clasifica UX-06 transitorio, sin pérdida confirmada. |
| [269-compensation-income-saved.png](evidence/269-compensation-income-saved.png), [276-payment-compensation-result.png](evidence/276-payment-compensation-result.png), [279-hu10-card-archived.png](evidence/279-hu10-card-archived.png), [280-room-final-hu10.txt](evidence/280-room-final-hu10.txt) | Compensación financiera final y estado de Room/outboxes tras HU-10. |
| [281-notification-center-open.png](evidence/281-notification-center-open.png), [283-notification-route-crash.txt](evidence/283-notification-route-crash.txt) | La campana produce tres crashes; la última evidencia visual es el launcher. |

### Clasificación de capacidades por sprint (backlog oficial HU-01–HU-60)

La asignación de sprints se contrastó con `02_Kipu_V4.2_Product_Backlog.md` (tabla del backlog EP-APS/EP-CTA/EP-CCO/EP-MOV/EP-PLA, líneas 22–68 y 151–170). `NO_EJECUTADO` significa que no hay evidencia de runtime suficiente y **no** afirma ausencia de implementación. No se marca `NO_IMPLEMENTADO` sin evidencia positiva de que una capacidad cerrada falte.

| Sprint | HU | Estado de auditoría | Alcance y evidencia/límite |
|---|---|---|---|
| S1 | HU-01 Registro e inicio de sesión | NO_EJECUTADO — excluido | El prompt prohíbe registro/login/logout; la sesión ya estaba activa. No es un veredicto de implementación. |
| S1 | HU-02 Recuperación y ciclo de sesión | NO_EJECUTADO — excluido | Recuperación y login/logout fuera del alcance autorizado. |
| S1 | HU-03 Desbloqueo local con biometría | NO_EJECUTADO | No se suspendió ni bloqueó la app para conservar la sesión de QA; no se afirma ausencia. |
| S1 | HU-04 Preferencias y privacidad de saldos | INCOMPLETO — caso de ocultamiento PASS | El switch persistió tras navegar/reabrir; dashboard enmascaró y luego volvió a mostrar importes al restaurar el valor original. No se verificó tipografía tabular (tnum). Evidencia 186–193 y 199–200. |
| S1 | HU-05 Permisos y fuentes de automatización | NO_EJECUTADO | No se concedieron/denegaron permisos ni se probó el modo manual con permisos denegados. |
| S1 | HU-52 Elegir Free o prueba Premium | NO_EJECUTADO — compra excluida | El dashboard muestra Plan Free 2/4; no se recorrió onboarding/selección de trial y no se inició compra. |
| S2 | HU-07 Administración de cuentas | INCOMPLETO + BUG-FIN-03 | Alta CASH/SAVINGS, alias, archivo y corrección del saldo inicial probados. Faltan reactivación, edición de institución/titular, alta con apertura no cero y sync; correcciones repetidas fallan. |
| S2 | HU-08 Registro y vínculo de tarjetas | BUG-FIN-04 P2 + INCOMPLETO | El alta falla por mismatch determinista de UserId en el ViewModel/repositorio, probado en 2/4 y 3/4 sin mutación. El resto del CRUD y los vínculos siguen incompletos. |
| S2 | HU-14 Categorías y subcategorías | UX-01 + INCOMPLETO | `parent_id` conserva la jerarquía, pero el selector las presenta como chips planos; CRUD y ciclo de vida de categorías no se recorrieron. |
| S2 | HU-15 Comercios y servicios | INCOMPLETO | El comercio se ve separado del selector de categorías en la captura del movimiento; alta/edición/archivo y búsqueda de catálogo no se verificaron. |
| S2 | HU-18 Registro manual de movimientos | INCOMPLETO | Se registraron gastos y transferencias propias; pagos y compras se cubren bajo HU-12/13. No se probó de forma aislada ingreso manual ni la matriz completa de validaciones. |
| S2 | HU-19 Saldos inmediatos y atomicidad | INCOMPLETO | En operaciones locales probadas, Room/ledger/UI conciliaron; doble toque produjo una sola transferencia. Falta fallo parcial, reinicio y replay/sync de backend. |
| S2 | HU-23 Detección de duplicados | INCOMPLETO | La similitud observada concuerda con el contrato; límite temporal exacto de cinco minutos carece de timestamps reproducibles. |
| S2 | HU-57 Cupos, selección y downgrade | INCOMPLETO | Se observó el contador Free 2/4↔3/4; no se probó límite máximo, selección de recursos al exceder cupo ni downgrade. El error al crear tarjeta no se atribuye al cupo. |

**Sprint 3 activo — capacidades revisadas o limitadas:** HU-09 línea/deuda/disponible: PASS en saldo/límite y exclusión del activo líquido; HU-10 utilización local: 50/80/100% PASS, avisos remotos INCOMPLETOS por ausencia de sincronización confirmada; HU-11 catálogo y tasas: INCOMPLETO, formulario/producto inspeccionado y selección Amex sincronizada, pero el tarifario no cargó productos ni tras reintento y no se guardó TEA; HU-12 pago: BUG-FIN-01/02 y BUG-UI-01, con recuperaciones exitosas total/parcial; HU-13 compra/cuotas: PASS para S/ 100 en tres cuotas; HU-53/54/56 compra/verificación/cancelación: PENDIENTE_SPRINT_ACTIVO, no se lanzó Google Play Billing ni compra externa por exclusión expresa. HU-55 cierra en S5 y está FUERA_DEL_SPRINT.

## 3. Fixture inicial

| Elemento | Estado |
|---|---|
| Efectivo | Saldo derivado S/ 20.00. |
| Sueldo | Ahorros; saldo derivado S/ 9,975.00. |
| BCP Visa crédito 7548 | Límite S/ 10,000.00; deuda S/ 4,000.00; disponible S/ 6,000.00. |
| Amex 1234 | Archivada, deuda S/ 500.00; no se cuenta como activo líquido. |
| Activos líquidos | S/ 9,995.00, suma de Efectivo y Sueldo. |
| Categorías | Raíz Suscripciones; hijas HBO y Crunchyroll con parent_id correcto. |
| Límite de instrumentos | Dashboard mostraba 3/4 activos. |
| Sincronización | Room contenía un gasto FAILED_PERMANENT / ACCOUNT_NOT_FOUND y outboxes ERROR/PENDING. Causa no atribuida. |

Los saldos corrientes se derivan de ledger/proyección local, no del saldo inicial.

## 4. Hallazgos

### Matriz de hallazgos por severidad

| Prioridad | Hallazgos clasificados |
|---|---|
| P0 | Ninguno confirmado. |
| P1 | BUG-NAV-01: función central de Avisos no disponible; la campana cierra la app de forma reproducible. No se interpreta como descarte global de otros P1. |
| P2 | BUG-FIN-01, BUG-FIN-02, BUG-FIN-03, BUG-FIN-04, BUG-UI-01, UX-01; SPEC-CTA-01 (contradicción normativa sobre pago de tarjeta archivada); BUG-BACK-01 es candidato estático no reproducido. |
| P3 | UX-02, UX-03, UX-04 (distintivo de tarjeta archivada en resumen; no BUG de visibilidad), UX-05 (familia Amex expandida con Visa activa), UX-06 (estado vacío transitorio en Historial), INCOMPLETO-03 (tarifario sin filas; síntoma no bloqueante, HU-11.5 pendiente, causa no determinada), INCOMPLETO-04 (avisos HU-10 no verificados sin sync remoto). |
| Sin severidad asignada | INCOMPLETO-01 (ACCOUNT_NOT_FOUND): evidencia runtime disponible, causa del registro/sync no atribuida. |

Cada ficha siguiente detalla pantalla/HU, pasos, observado/esperado, impacto, evidencia y recomendación.

### BUG-FIN-01 — El monto agrupado generado por el diálogo se rechaza al confirmar

**Prioridad:** P2, BUG confirmado en runtime.  
**Pantalla / HU:** Modal Pagar tarjeta, HU-12.  
**Pasos:** seleccionar Total con deuda S/ 1,060.00 o S/ 1,000.00 y confirmar sin editar el importe; repetir escribiendo manualmente el mismo valor sin coma.  
**Flujo:** pagar tarjeta.

En el primer caso, el diálogo precargó S/ 1,060.00; confirmar 1,060.00 sin editar mostró “Ingresa un monto mayor a cero”. Al volver a escribir 1060.00 sin coma, el pago funcionó. En una segunda deuda de S/ 1,000.00, el prefill 1,000.00 falló de nuevo; 1000.00 sin coma sí funcionó. Los intentos fallidos no escribieron pago ni asientos parciales en Room.

**Esperado:** todo valor producido por la propia UI y la acción Total debe poder parsearse; los errores deben describir una causa real.  
**Impacto:** bloquea la confirmación de pagos desde el importe mostrado y fuerza un workaround manual.  
**Evidencia:** [58-total-prefill-confirmed.png](evidence/58-total-prefill-confirmed.png), [59-room-after-total-prefill-payment.txt](evidence/59-room-after-total-prefill-payment.txt), [77-second-prefill-confirmation-error.png](evidence/77-second-prefill-confirmation-error.png), [78-room-after-second-prefill-failure.txt](evidence/78-room-after-second-prefill-failure.txt), [66-manual-payment-recovered.png](evidence/66-manual-payment-recovered.png), [85-second-payment-recovered.png](evidence/85-second-payment-recovered.png).  
**AGY:** identificó la causa concreta: el parser reemplaza la coma por punto, transformando 1,060.00 en 1.060.00; la expresión amountPattern lo rechaza y devuelve null. Dos reproducciones runtime lo confirman.  
**Severidad:** P2 porque bloquea un flujo primario, pero no se observó pérdida/corrupción de datos ni crash; el rechazo deja Room intacto y hay workaround manual con un formato sin separador.  
**Corrección/regresión:** unificar formatter y parser, sin tratar separador de miles como decimal ambiguo. Probar 1,060.00, 1,000.00, 1000.00, decimales/locales y Total; una prueba UI debe verificar un único CARD_PAYMENT con ledger balanceado.

### BUG-FIN-02 — Efectivo aparece como origen válido, pero el repositorio lo rechaza

**Prioridad:** P2, BUG confirmado en runtime.  
**Pantalla / HU:** Selector de origen del modal Pagar tarjeta, HU-12.  
**Pasos:** elegir Efectivo con saldo positivo y confirmar un pago inferior al saldo; comparar la lista elegible con el resultado del comando.  
**Observado:** se seleccionó Efectivo con saldo S/ 1,020.00 para pagar S/ 1,000.00; la app mostró “Only an active bank or savings account can pay a credit card”. No hubo mutación en Room.  
**Esperado:** el selector solo debe ofrecer cuentas que el comando acepte, de acuerdo con el contrato de dominio; o debe aceptarlas de punta a punta.  
**Impacto:** la UI induce al usuario a un intento imposible.  
**Evidencia:** [80-second-source-type-rejected.png](evidence/80-second-source-type-rejected.png), [81-payment-invalid-source-dismissed.png](evidence/81-payment-invalid-source-dismissed.png), [78-room-after-second-prefill-failure.txt](evidence/78-room-after-second-prefill-failure.txt).  
**AGY:** PayCardDialog.kt:53-56 permite cuentas activas con saldo positivo; OfflineFirstFinancialInstrumentsRepository.kt:833-835 restringe a BANK/SAVINGS. La RPC allocate_credit_payment_v1, en la migración 20260925022130_s3_credit_canonical_purchase_payment.sql:710, rechaza cuentas CREDIT_LIABILITY y admite otras cuentas líquidas. El prototipo de HU-12 describe efectivo, ahorros y billeteras. Hay una contradicción tripartita entre UI, repositorio y backend.  
**Corrección:** acordar criterio único para UI/dominio/repositorio/RPC. Probar CASH, BANK, SAVINGS, archivadas/inactivas e insuficientes.

### BUG-FIN-03 — La corrección repetida del saldo inicial acumula un saldo incorrecto

**Prioridad:** P2, BUG confirmado en runtime y contrastado por AGY.  
**Pantalla / HU:** Detalle de cuenta → Corregir saldo inicial, HU-07.  
**Pasos:** sobre apertura S/ 20.00 y saldo actual S/ 1,018.78, corregir a S/ 21.00 y luego a S/ 20.00; leer el saldo y ledger después de cada confirmación.  
**Regla de negocio:** RN-10 de EP-CTA establece que la apertura es atómica e inmutable y que sus correcciones deben ser ajustes auditables. El modelo de datos deriva saldos de movimientos y exige conservar hechos financieros append-only mediante ajustes/reversiones relacionados (`specs/002-ep-cta-cuentas-tarjetas/spec.md:262`; `specs/002-ep-cta-cuentas-tarjetas/data-model.md:10,17`).

**Reproducción:** con Efectivo en S/ 1,018.78 y saldo de apertura original S/ 20.00, se corrigió la apertura a S/ 21.00: saldo pasó correctamente a S/ 1,019.78. Luego se indicó S/ 20.00 para devolverla al valor previo. La UI confirmó “Saldo inicial corregido”, pero el saldo permaneció en S/ 1,019.78, S/ 1.00 por encima de lo esperado. No se sobrescribió el historial; Room añadió asientos, pero la cadena compensatoria fue incorrecta.

**Causa raíz corroborada por AGY:** `OfflineFirstFinancialInstrumentsRepository.kt:218-269` vuelve a buscar la apertura original por `opening_account_id` en cada corrección y revierte siempre el importe original, sin revertir el ajuste vigente anterior. `account.initial_balance_minor_units` queda obsoleto y la pantalla vuelve a precargarlo (`AccountDetailScreen.kt:345`). AGY señaló que `record_opening_adjustment` en `supabase/migrations/20260921000000_ep_cta_baseline.sql:557-581` requiere la misma revisión. La corrección de QA a S/ 19.00 fue solo una compensación para devolver el saldo derivado al baseline; no es una solución funcional.

**Impacto:** saldo derivado y actividad dejan de representar el saldo inicial objetivo después de correcciones sucesivas; un usuario puede aceptar un mensaje de éxito y conservar un saldo incorrecto. No se perdió el historial.  
**Evidencia:** [167-initial-balance-correction-dialog.png](evidence/167-initial-balance-correction-dialog.png), [170-room-after-balance-correction/kipu.db](evidence/170-room-after-balance-correction/kipu.db), [171-initial-balance-restoration-ready.png](evidence/171-initial-balance-restoration-ready.png), [172-initial-balance-restored.png](evidence/172-initial-balance-restored.png), [173-room-after-restoration-attempt/kipu.db](evidence/173-room-after-restoration-attempt/kipu.db), [184-room-final-after-all-tests.txt](evidence/184-room-final-after-all-tests.txt).  
**Regresión requerida:** partiendo de apertura S/ 20.00 y movimientos posteriores que dejan S/ 1,018.78, corregir a S/ 21.00 debe dejar S/ 1,019.78; corregir después a S/ 20.00 debe volver exactamente a S/ 1,018.78; corregir a S/ 25.00 debe dejar S/ 1,023.78. Verificar que la cadena append-only tenga una sola corrección vigente y que los saldos UI/Room/backend coincidan tras sync. Cubrir idempotencia y repetición en Room y RPC.

### BUG-UI-01 — El diálogo no sale de “Procesando...” después del rechazo

**Prioridad:** P2, BUG confirmado en runtime.  
**Pantalla / HU:** Modal Pagar tarjeta y error del ViewModel, HU-12.  
**Pasos:** provocar el rechazo del origen Efectivo y observar el diálogo/toast sin cerrarlo.  
**Observado:** tras el error de fuente, el botón y diálogo quedaron bloqueados hasta cancelar; el toast estaba en inglés.  
**Esperado:** todo error debe finalizar la carga, explicar el problema en español y permitir corregir/reintentar.  
**Evidencia:** [80-second-source-type-rejected.png](evidence/80-second-source-type-rejected.png), [81-payment-invalid-source-dismissed.png](evidence/81-payment-invalid-source-dismissed.png).  
**AGY:** PayCardDialog.kt:200-210 activa isSubmitting; AccountsViewModel.kt:480-490 emite error sin callback que restablezca el estado local.  
**Regresión:** forzar error de dominio, comprobar salida de carga, mensaje localizado y reintento habilitado.

### BUG-BACK-01 — account_id opcional podría ser rechazado por la RPC

**Prioridad:** P2 candidato estático; no reproducido en runtime.  
**Pantalla / HU:** Alta de tarjeta de crédito → RPC register_card_v1, HU-08.  
**Pasos:** pendiente: con sesión válida, contrastar llamadas account_id omitido, null y explícito en backend de desarrollo.  
**Hallazgo AGY:** supabase/migrations/20260928110000_credit_card_pull_projection.sql:440-445 compara account_id de forma que NULL podría resultar distinto al id esperado; remote-api.openapi.yaml:358 declara account_id opcional.  
**Límite:** la creación de tarjeta falló primero con “Credit card owner does not match the active session”; no se aisló la llamada RPC ni account_id nulo.  
**Siguiente paso:** contrastar contrato y semántica autorizada; test SQL para omitido, null y válido en backend de desarrollo. Mantener separado del fallo runtime owner/session.

### UX-01 — El selector no comunica jerarquía de categorías

**Prioridad:** P2, UX; datos Room correctos.  
**Pantalla / HU:** Selector de categoría en Nuevo movimiento, HU-14/HU-18.  
**Pasos:** abrir el registro de gasto y comparar chips raíz/subcategoría con campo de comercio.  
Suscripciones, HBO y Crunchyroll aparecen como chips equivalentes, sin agrupación, sangría o disclosure. Room conserva parent_id. Comercio aparece separado y no se observó mezcla con categoría.

**Evaluación objetiva frente al patrón progressive disclosure:** el selector actual requiere un toque para elegir raíz o hija, pero alinea visualmente Suscripciones, HBO y Crunchyroll como pares; con más categorías, la lista horizontal reduce escaneabilidad y dificulta reconocer el `parent_id`. El campo de comercio está separado visualmente y no se observó mezcla semántica con categoría. Un bottom sheet con raíces y chevrons que expanden hijas añade un toque para elegir subcategoría, pero comunica agrupación, reduce chips simultáneos y permite colapsar ramas; elegir una raíz puede seguir siendo una acción directa. La referencia resuelve jerarquía/carga cognitiva, a costa de un paso adicional para hijas; se recomienda validar con las categorías reales y no copiar su estética.  
**Impacto:** se pierde contexto y aumenta el riesgo de elegir el nivel equivocado.  
**Evidencia:** [17-new-movement.png](evidence/17-new-movement.png), [00-room-initial.txt](evidence/00-room-initial.txt).  
**AGY:** confirmó que es presentación, no corrupción de datos; referencia QuickMovementViewModel.kt:105.  
**Mejora:** listar raíces y expandir hijas bajo demanda, permitiendo seleccionar explícitamente también la raíz.

### UX-02 — Historial titula el pago de tarjeta como “Transferencia”

**Prioridad:** P3. CARD_PAYMENT conserva clasificación contable correcta, pero el título genérico oculta la intención.

**Pantalla / HU:** Lista Historial de movimientos, HU-12/HU-22.  
**Pasos:** registrar un pago de tarjeta y abrir su fila en el historial.  

**Esperado:** “Pago de tarjeta”, manteniendo subtítulo origen → tarjeta.  
**Evidencia:** [14-history-after-payment.png](evidence/14-history-after-payment.png), [12-room-after-full-payment.txt](evidence/12-room-after-full-payment.txt).  
**AGY:** MovementHistoryScreen.kt:307-320 usa el fallback TRANSFER para el título; el prototipo respalda una etiqueta específica.

### UX-03 — El historial mantiene el filtro Gasto al volver de una transferencia

**Prioridad:** P3. Tras guardar, la pantalla conservó Gasto; parecía que la transferencia había desaparecido. Se encontró en Transferencia y Room.  
**Pantalla / HU:** Historial y filtros de movimientos, HU-18/HU-22.  
**Pasos:** guardar una transferencia iniciada desde el filtro Gasto y observar el filtro activo y la lista resultante.  
**Evidencia:** [23-transfer-saved.png](evidence/23-transfer-saved.png), [26-transfer-filter.png](evidence/26-transfer-filter.png), [25-room-post-transfer.txt](evidence/25-room-post-transfer.txt).  
**AGY:** MovementHistoryScreen.kt:182-236 mantiene el filtro. Agregar confirmación/contexto visible.

### SPEC-CTA-01 — Fuentes de EP-CTA discrepan sobre pagar una tarjeta archivada con deuda

**Prioridad:** P2, discrepancia de especificación/contratos; no BUG de runtime confirmado.  
**Pantalla / HU:** Detalle de tarjeta archivada y flujo Pagar tarjeta, HU-07.5, HU-08, HU-12 y dependencia HU-57.  
**Pasos:** abrir la AMEX archivada con deuda S/ 500.00; observar el banner y acciones; contrastar el estado Room con plan, HU/modelo, repositorio y RPC. No se envió ni confirmó pago.  
**Observado:** Room tiene `is_archived=1`. El dashboard retiene la tarjeta por su pasivo; el detalle avisa que debe reactivarse para liquidar deuda y solo ofrece “Reactivar tarjeta”. AGY verificó que `OfflineFirstFinancialInstrumentsRepository.kt:845` exige `!isArchived` y `allocate_credit_payment_v1` devuelve `CREDIT_CARD_UNAVAILABLE` si la tarjeta está archivada (`supabase/migrations/20260925022130_s3_credit_canonical_purchase_payment.sql:704-706`).  
**Esperado según fuentes en conflicto:** `specs/002-ep-cta-cuentas-tarjetas/plan.md:188` dice que la tarjeta archivada con deuda sigue visible y puede recibir pagos; HU-07.5 (`spec.md:113`) dice que deja de admitir operaciones nuevas, y el modelo (`data-model.md:19`) dice que archivar conserva historia/libera cupo y reactivar vuelve a consumirlo.  
**Impacto:** hay dos comportamientos contradictorios documentados. Con el contrato ejecutado, el usuario debe reactivar la tarjeta antes de amortizar; si el plan Free ya agotó sus cupos, puede requerir archivar otro instrumento para liberar uno.  
**Evidencia:** [201-amex-detail.png](evidence/201-amex-detail.png), [200-final-dashboard-visible.png](evidence/200-final-dashboard-visible.png), [184-room-final-after-all-tests.txt](evidence/184-room-final-after-all-tests.txt).  
**Dictamen AGY:** la retención visual con deuda es conforme; la ruta actual es coherente entre UI/repositorio/RPC con la regla HU-07.5, pero contradice `plan.md:188`. Resolver la fuente normativa antes de cambiar código o clasificarlo como defecto de producto.  
**Recomendación:** decidir explícitamente si permitir pagos sin reactivar o actualizar plan/HU/modelo/copy para exigir reactivación; añadir regresión con cupo Free completo en el contrato elegido.

### UX-04 — El resumen no etiqueta como archivada una tarjeta retenida por deuda

**Prioridad:** P3, mejora UX, separada de SPEC-CTA-01.  
**Pantalla / HU:** Dashboard, resumen de Tarjetas de Crédito, HU-07/HU-08.  
**Pasos:** observar AMEX archivada con deuda en el dashboard y abrir su detalle.  
**Observado:** la tarjeta se presenta en el resumen como una tarjeta ordinaria; al abrirla, el detalle sí comunica “Este instrumento está archivado”.  
**Esperado:** conservar visible el pasivo, con señal secundaria que explique su estado archivado.  
**Impacto:** sin distintivo, el usuario puede dudar de si la acción de archivo surtió efecto antes de entrar al detalle.  
**Evidencia:** [200-final-dashboard-visible.png](evidence/200-final-dashboard-visible.png), [201-amex-detail.png](evidence/201-amex-detail.png), [184-room-final-after-all-tests.txt](evidence/184-room-final-after-all-tests.txt).  
**Recomendación:** considerar badge “Archivada · deuda pendiente”; prioridad P3 porque el detalle aclara el estado y la retención del resumen está prescrita.

### UX-05 — El catálogo abre una familia Amex mientras Visa aparece seleccionada

**Prioridad:** P3, fricción de presentación; no se observó inconsistencia financiera al seleccionar un producto.  
**Pantalla / HU:** Formulario Nuevo instrumento → Tarjeta de crédito / HU-08 y HU-11.  
**Pasos:** abrir Nuevo instrumento, seleccionar Tarjeta de crédito y dejar BCP; observar la familia expandida y el selector de red antes de elegir un diseño; luego seleccionar Amex Clásica LATAM Pass.  
**Observado:** el catálogo dependiente de BCP expande American Express LATAM Pass mientras el chip Visa está activo y el preview aún es genérico. Al seleccionar Amex, preview, alias y red cambian juntos a Amex. Salir atrás sin guardar no alteró Room.  
**Esperado:** que familia expandida y red seleccionada representen un estado inicial coherente, o que la relación de ambos controles sea evidente.  
**Impacto:** puede interpretarse que Amex es el producto seleccionado o que el catálogo no coincide con el emisor/red, aunque la selección explícita posterior se sincroniza correctamente.  
**Evidencia:** [207-credit-form.png](evidence/207-credit-form.png), [208-credit-form-bottom.png](evidence/208-credit-form-bottom.png), [210-credit-product-selected.png](evidence/210-credit-product-selected.png).  
**Dictamen AGY:** BCP tiene productos Amex válidos en su catálogo; al tocar el diseño, el código actualiza `network`, alias y preset. No se observó riesgo de persistencia en el flujo inspeccionado.  
**Recomendación:** expandir por defecto la familia Visa correspondiente a la red inicial o filtrar familias por red; mantener familias alternativas accesibles explícitamente.

### INCOMPLETO-03 — El tarifario HU-11 no muestra productos tras la carga ni el reintento

**Clasificación:** PENDIENTE_SPRINT_ACTIVO / INCOMPLETO; prioridad P3 informativa por la degradación no bloqueante. No es BUG confirmado: no se obtuvo status HTTP/body y la especificación permite que la indisponibilidad del snapshot no bloquee cuentas, tarjetas ni TEA personal (`spec.md:372`).  
**Pantalla / HU:** Detalle de tarjeta activa → Tasas referenciales y TEA personal, HU-11.  
**Pasos:** reactivar temporalmente una tarjeta archivada, abrir Tasas referenciales y TEA personal y observar el listado; pulsar Reintentar una vez.  
**Observado:** se muestra la advertencia de uso informativo y “Tasa referencial al 24/09/2026”, seguida de “No pudimos cargar las tasas referenciales”; tras Reintentar el mismo estado permanece y no hay filas para Todos, Soles (PEN) ni Dólares (USD). No se guardó TEA. En el formulario de alta, el producto Amex seleccionado muestra solo una advertencia genérica, sin tasa/fuente/fecha por producto: cuando la lista remota está vacía, el código usa `CardStylePresets` local solo para diseño y `ReferencialTeaCard` no se renderiza al no existir referencia asociada. La UI orienta a revisar conexión, pero no se capturó status/body que identifique red, endpoint, autenticación o población del catálogo.  
**Esperado:** cuando el catálogo está disponible, HU-11.1/11.5 requieren consultar 44 productos (18 BCP, 10 BBVA y 16 Interbank), con moneda/alcance, rango, fuente, advertencias y fecha del snapshot. La especificación también exige que la indisponibilidad no bloquee cuentas, tarjetas ni el acceso a TEA personal.  
**Impacto:** el usuario no puede consultar la referencia que respalda nuevas simulaciones; HU-11.1/11.5 no se puede certificar en runtime.  
**Evidencia:** [216-rate-catalog.png](evidence/216-rate-catalog.png), [217-rate-catalog-retry.png](evidence/217-rate-catalog-retry.png), [222-rate-catalog-diagnostics.txt](evidence/222-rate-catalog-diagnostics.txt), estado Room final en [221-live-room-final-hu11.txt](evidence/221-live-room-final-hu11.txt).  
**Dictamen cruzado:** AGY revisó `spec.md:372`: la indisponibilidad del snapshot no bloquea cuentas, tarjetas ni la TEA personal. La pantalla conserva el disclaimer, el estado de error y la entrada de TEA; el runtime no mostró crash ni bloqueo de las demás acciones. Tras confrontar que no hay status/body ni conectividad validada del endpoint, AGY retiró la clasificación inicial de BUG P2 y acordó PENDIENTE_SPRINT_ACTIVO / INCOMPLETO, P3 informativo.  
**Siguiente paso:** confirmar la respuesta del endpoint y el poblamiento de las 44 filas antes del cierre S3; verificar filtros y datos fuente sin inferir ahora si la indisponibilidad se debe a red, autenticación, servicio o conteo. La evidencia actual no certifica HU-11.1/11.2 ni HU-11.5; tampoco se probaron guardado de TEA válida (11.3) o rechazo de inválida (11.4).

### INCOMPLETO-01 — ACCOUNT_NOT_FOUND en outbox

**Clasificación:** INCOMPLETO, no contar como bug confirmado. Un gasto HBO estaba en FAILED_PERMANENT / ACCOUNT_NOT_FOUND con icono de error; la causa puede ser dato legado, cuenta o sesión.  
**Pantalla / HU:** Historial de gastos / sincronización de movimiento, HU-18/HU-19.  
**Pasos:** revisar la fila HBO/Sueldo con el estado de error y contrastar id de cuenta, usuario y outbox; no reintentar destructivamente antes de identificar la causa.  
**Evidencia:** [00-room-initial.txt](evidence/00-room-initial.txt), [15-history-expense-filter.png](evidence/15-history-expense-filter.png).  
**Siguiente paso:** validar usuario, cuenta, sesión, payload y contrato de sincronización antes de reintentar o alterar el movimiento.

### BUG-FIN-04 — El alta de tarjeta genera un propietario distinto a la sesión activa

**Prioridad:** P2, bug confirmado en runtime y causa raíz contrastada por AGY.  
**Pantalla / HU:** Formulario Nuevo instrumento → tarjeta de crédito, HU-08.  
**Reproducción:** se intentó guardar una tarjeta de prueba con 3/4 instrumentos activos y se repitió con 2/4. Ambos intentos mostraron “Credit card owner does not match the active session”; no se creó tarjeta ni pasivo asociado en Room.  
**Causa raíz:** `AccountsViewModel.registerCreditCard` crea la tarjeta con `UserId.generate()` (`AccountsViewModel.kt:441-443`). `OfflineFirstFinancialInstrumentsRepository.registerCreditCard` resuelve el owner vigente mediante `currentUserId()` y rechaza si `card.userId.value != userId` (`OfflineFirstFinancialInstrumentsRepository.kt:455-458`). La identidad aleatoria nunca coincide con la sesión en condiciones normales. No es un error de cuota ni evidencia de sesión expirada.  
**Impacto:** el flujo de registro de tarjeta no puede completar el alta desde la UI y bloquea la HU-08.  
**Evidencia:** [91-add-credit-filled.png](evidence/91-add-credit-filled.png), [93-add-fourth-instrument-saved.png](evidence/93-add-fourth-instrument-saved.png), [94-instrument-create-session-error.png](evidence/94-instrument-create-session-error.png), [230-credit-form-top.png](evidence/230-credit-form-top.png), [232-credit-card-save-ready.png](evidence/232-credit-card-save-ready.png), [233-qa-hu10-card-create.png](evidence/233-qa-hu10-card-create.png).  
**AGY:** corroboró la generación aleatoria en el ViewModel y la comparación estricta en el repositorio; descartó que el cupo 2/4 vs. 3/4 explique el resultado.  
**Corrección/regresión:** asignar al modelo el `verifiedUserId` del owner activo o hacer que el repositorio asigne esa identidad en el límite de persistencia. Con sesión válida, probar alta de tarjeta en 2/4 y 3/4; exigir una fila de tarjeta y una de pasivo del mismo userId, estado/outbox consistente y ausencia de escrituras parciales si falla una restricción. No inferir éxito por callback de UI solamente.

### BUG-NAV-01 — La campana de notificaciones termina la aplicación

**Prioridad:** P1, función central no disponible y crash confirmado tres veces en runtime.  
**Pantalla / HU:** dashboard → centro de notificaciones.  
**Pasos:** desde el dashboard, activar la acción accesible «Notificaciones».  
**Observado:** a las 13:42:47, 13:45:35 y 13:57:33 del 28/09/2026, Android registró `IllegalArgumentException: Navigation destination that matches route notifications/center cannot be found in the navigation graph`; `ApplicationExitInfo` clasifica las tres salidas como `APP CRASH(EXCEPTION)`. La app vuelve al launcher. El stack pasa por `navigateToNotifications` y el callback de `accountsDestinations`.  
**Causa raíz:** el `NavHost` activo se crea directamente en `MainActivity.kt:163-204` y no registra `notificationDestinations`; la campana navega a `notifications/center` (`AccountsNavigation.kt:76`, `NotificationsNavigation.kt:15-18`). El builder auxiliar `KipuNavHost.kt:28` sí lo registra, pero no es el que construye la actividad activa.  
**Impacto:** el usuario no puede abrir Avisos y un toque cierra la app. No se atribuye a guardar un gasto con tarjeta: las compras de prueba se guardaron y el stack identifica la navegación de la campana.  
**Evidencia:** [283-notification-route-crash.txt](evidence/283-notification-route-crash.txt) contiene tres excepciones y el contraste del grafo; [281-notification-center-open.png](evidence/281-notification-center-open.png) muestra el launcher después de la última.  
**Nota de atribución:** `282-app-exit-diagnostics.txt` también conserva crashes `Transfer requires a destination account` fechados 24/09, no ocurridos durante esta secuencia. En la base QA actual, las cinco filas `CARD_PAYMENT` son `type=TRANSFER`, `operation_kind=CARD_PAYMENT` y destino líquido nulo; el modelo permite ese caso explícitamente (`MovementModels.kt:105-110`). Esa traza histórica no se usa para atribuir un crash a los pagos de tarjeta de hoy.  
**Corrección/regresión:** registrar `notificationDestinations(navController)` en el NavHost efectivamente usado y añadir prueba de grafo/UI que pulse «Notificaciones», verifique la ruta `notifications/center`/pantalla Avisos y asegure que el proceso siga vivo.

### UX-06 — Historial muestra un estado vacío durante la resolución del owner

**Prioridad:** P3, discrepancia transitoria observada una vez.  
**Observado:** el fotograma [251-hu10-notifications-screen.png](evidence/251-hu10-notifications-screen.png) muestra «Sin movimientos registrados», mientras que el volcado de accesibilidad [251-hu10-notifications-screen.xml](evidence/251-hu10-notifications-screen.xml) y la reapertura [253-app-reopened.png](evidence/253-app-reopened.png) ya contienen las transacciones locales. No se observó pérdida ni borrado.  
**Causa probable contrastada:** `MovementHistoryViewModel.kt:29` inicializa `isLoading=true`, pero el flujo convierte un owner todavía no `Available` en `flowOf(emptyList())` (líneas 48-55); `combine` publica entonces `isLoading=false` y lista vacía antes de que se resuelva el owner.  
**Recomendación/regresión:** mantener estado de carga hasta confirmar owner y primera emisión Room, diferenciando “sin owner”, “sin movimientos” y “cargando”. Capturar la UI desde cold start y verificar que nunca muestre el empty state antes del primer resultado. Se clasifica como flash UX, no como error de persistencia.

### INCOMPLETO-04 — Los avisos HU-10 dependen de la sincronización remota no disponible

**Estado:** cobertura E2E incompleta; no es bug confirmado del modo offline.  
**Probado:** deuda local de Amex pasó de S/ 500 a S/ 25,000 (50%), S/ 40,000 (80%) y S/ 50,000 (100% del límite S/ 50,000). La UI reflejó los tres porcentajes; las compras quedaron `PENDING`. No se observó aviso y Room reportó cero notificaciones.  
**Contrato y límite:** AGY confirmó que la RPC backend emite los cruces al confirmar/sincronizar la compra; el cliente local solo consulta avisos remotos. El dispositivo no tenía conexión/sync confirmado, así que no se puede concluir que backend, deduplicación, rearme o permisos funcionen o fallen. El toque al centro de avisos también está bloqueado por BUG-NAV-01.  
**Evidencia:** [249-hu10-purchase-result.xml](evidence/249-hu10-purchase-result.xml), [250-room-after-hu10-50.txt](evidence/250-room-after-hu10-50.txt), [261-hu10-debt-80.png](evidence/261-hu10-debt-80.png), [263-hu10-debt-100.png](evidence/263-hu10-debt-100.png), [280-room-final-hu10.txt](evidence/280-room-final-hu10.txt).  
**Siguiente prueba:** conectar un entorno de prueba backend, sincronizar un cruce 50/80/100, verificar fila/evento e inbox in-app con permiso Android concedido/denegado y cubrir deduplicación/rearme. No reutilizar compras externas ni presentar datos PENDING como notificación backend.

### BUG-MAP-01 — Crash histórico al cargar compras con tarjeta (resuelto en el árbol actual)

**Estado:** identificado en salidas antiguas del 24/09 y 27/09; la causa está corregida en el árbol de trabajo actual y no se reprodujo en los flujos de hoy. No se cuenta como bug abierto.
**Causa raíz contrastada por AGY:** un mapper anterior de `TransactionEntity` a `Transaction` omitía `operationKind` (y metadatos de tarjeta). Una compra `EXPENSE/CARD_PURCHASE`, cuyo `sourceAccountId` es nulo por diseño, llegaba al modelo como gasto estándar y disparaba `Expense requires a source account`. De forma análoga, un pago `TRANSFER/CARD_PAYMENT` sin destino líquido se interpretaba como transferencia estándar y podía disparar `Transfer requires a destination account`.
**Estado actual:** `MovementLocalDataSource` y el repositorio usan `TransactionEntity.toDomain()` en `TransactionMappers.kt:8-29`, que preserva `cardId`, `operationKind` e `installmentCount`. Existe una prueba Android `creditPurchasePersistsAndMapsFromObservedRoomFlowWithLiabilityLedgerOnly` en `MovementLocalDataSourceTest.kt:146-258`, con aserciones de esos campos. La prueba automatizada no se ejecutó en esta auditoría; en runtime, las compras con tarjeta se guardaron y aparecieron en historial sin ese crash.
**Evidencia:** [282-app-exit-diagnostics.txt](evidence/282-app-exit-diagnostics.txt) contiene esas excepciones con fechas anteriores; [39-room-after-credit-purchase.txt](evidence/39-room-after-credit-purchase.txt) y [264-history-at-100.png](evidence/264-history-at-100.png) muestran compras actuales conciliadas y listadas.
**Regresión pendiente:** conservar/ejecutar la prueba existente para `CARD_PURCHASE` y añadir caso equivalente de `CARD_PAYMENT`; el primero debe mapearse con `sourceAccountId=null`, `cardId` y `operationKind=CARD_PURCHASE`, aparecer en flujo Room/historial y no cerrar la app.

### PASS-TRANSFER-01 — Transferencia con saldo líquido negativo, cuenta destino distinta y doble toque

**Saldo insuficiente:** partiendo de Efectivo S/ 1,018.78 y Sueldo S/ 2,875.00, se guardó transferencia Efectivo → Sueldo de S/ 2,000.00. Room creó un TRANSFER y dos ledger entries: efectivo −200,000 minor, sueldo +200,000. Los saldos quedaron −S/ 981.22 y S/ 4,875.00; activos líquidos permanecieron S/ 3,893.78. AGY contrastó HU-18/HU-19 (02_Kipu_V4.2_Product_Backlog.md:1213-1236, 1276-1281), contrato register-transaction-v1:44-52 y Constitución I; ninguno requiere saldo no-negativo. Resultado **PASS**, no bug.

**Origen igual a destino:** con Efectivo como origen, el selector solo ofreció Sueldo como destino. El contrato register-transaction-v1:48, RegisterTransactionValidator.kt:47-49 y el RPC register_transaction_v1 (20260923120000_ep_mov_sync_transactions.sql:162-170) rechazan source=destination. El rechazo directo del validador/backend no se forzó desde UI porque el selector ya excluyó el origen; resultado de UI **PASS**, bypass directo pendiente.

**Doble toque:** dos taps rápidos sobre Guardar en transferencia de S/ 0.01 produjeron una sola fila y dos asientos en Room (source −1, destination +1 minor). Resultado **PASS** para esa reproducción.

Se hicieron dos transferencias compensatorias para restaurar Efectivo/Sueldo a los saldos anteriores al test; se registran y concilian en la tabla financiera. Evidencia: [129-transfer-mode.png](evidence/129-transfer-mode.png), [130-transfer-destination-list.png](evidence/130-transfer-destination-list.png), [134-transfer-insufficient-result.png](evidence/134-transfer-insufficient-result.png), [135-room-after-insufficient-transfer.txt](evidence/135-room-after-insufficient-transfer.txt), [139-cleanup-transfer-ready.png](evidence/139-cleanup-transfer-ready.png), [144-transfer-doubletap-result.png](evidence/144-transfer-doubletap-result.png), [149-room-final-after-qa.txt](evidence/149-room-final-after-qa.txt).

### PASS — Similitud de movimientos (HU-23); ventana exacta pendiente

AGY confirmó que la similitud local usa usuario, cuenta origen, tipo, importe, moneda y fecha en ventana de cinco minutos; el comercio no es dimensión obligatoria. La advertencia aunque varíe el texto del comercio es intencional. Cancelar no mostró escritura parcial; al confirmar movimientos considerados distintos se conservaron identidades independientes.

No se certifica el umbral exacto de cinco minutos: las capturas del último intento no contienen timestamps reproducibles. Aunque Room final contiene dos QA_DUP_CASE de S/ 0.11, eso no basta para demostrar hora ni alerta de cada intento. El caso temporal queda INCOMPLETO, no BUG.

Fuentes cruzadas por AGY: backlog HU-23; specs/004-ep-mov-movimientos-ledger/contracts/sync-and-deduplication.md; contracts/ui-contract.md; MovementDao.kt:36-45; RegisterTransaction.kt:21-36.

## 5. Conciliación financiera UI ↔ Room ↔ ledger

Room almacena los importes en céntimos (minor units). Los fallos de confirmación no produjeron asientos.

| Operación | Antes | Esperado | Resultado observado en Room/UI | Delta / veredicto |
|---|---|---|---|---|
| Pago total inicial | Sueldo 9,975.00; efectivo 20.00; BCP deuda 4,000.00/disponible 6,000.00; activos 9,995.00. | Pagar 4,000.00: sueldo 5,975.00; deuda 0; disponible 10,000.00; activos 5,995.00. | TRANSFER/CARD_PAYMENT 400,000; ledger SOURCE −400,000 y LIABILITY +400,000; allocation 400,000; cuota PAID. | Δ S/ 0.00, PASS. |
| Transferencia Sueldo → Efectivo | Sueldo 5,975.00; efectivo 20.00; activos 5,995.00. | Mover 1,000.00: sueldo 4,975.00; efectivo 1,020.00; activos iguales. | Una TRANSFER 100,000; SOURCE −100,000 y DESTINATION +100,000; aparece en Transferencia, no Gasto/Ingreso. | Δ S/ 0.00, PASS. |
| Transferencia con saldo negativo | Efectivo 1,018.78; Sueldo 2,875.00; activos 3,893.78. | Transferir 2,000.00 Efectivo → Sueldo; negativo permitido según HU-18/19. | Una TRANSFER; Efectivo −981.22, Sueldo 4,875.00; ledger −200,000/+200,000. | Δ S/ 0.00; PASS según contrato. |
| Transferencia compensatoria de QA | Efectivo −981.22; Sueldo 4,875.00. | Transferir 2,000.00 Sueldo → Efectivo para restaurar fixture. | Una TRANSFER; Efectivo 1,018.78, Sueldo 2,875.00; suma líquida 3,893.78. | Δ S/ 0.00; PASS. |
| Doble tap + compensación centavo | Efectivo 1,018.78; Sueldo 2,875.00. | Dos taps sobre S/ 0.01; esperado máximo una escritura; luego revertir para restaurar fixture. | Room contiene una fila −1/+1 minor del primer envío; compensación agrega una fila opuesta; saldo vuelve al baseline. | Una escritura ante doble tap; Δ neto final S/ 0.00; PASS. |
| Compra BCP en 3 cuotas | Deuda cero; activos 5,995.00. | Comprar 100.00 sin reducir dinero líquido. | EXPENSE/CARD_PURCHASE, origen null; cuotas 33.34 + 33.33 + 33.33; pasivo −10,000 minor. | Δ S/ 0.00, PASS. |
| Pago parcial | Sueldo 4,975.00; efectivo 1,020.00; deuda 100.00. | Pagar 40.00: sueldo 4,935.00; deuda 60.00; activos 5,955.00. | CARD_PAYMENT; SOURCE −4,000 y LIABILITY +4,000 minor; allocations 33.34 a cuota 1 y 6.66 a cuota 2; estados PAID/PARTIAL/PENDING. | Δ S/ 0.00, PASS. |
| Prefill primer caso | Nueva compra deja deuda 1,060.00; prefill 1,060.00. | Rechazo no debe mutar Room. | Error; no se creó pago. Escribir 1060.00 permitió pagar exactamente 1,060.00 y dejar deuda cero. | Δ S/ 0.00 durante error; bug confirmado; recuperación PASS. |
| Prefill y origen CASH segundo caso | Deuda 1,000.00; efectivo 1,020.00. | Fallos no deben mutar Room. | Prefill 1,000.00 y fuente Efectivo rechazados. Snapshot: 12 transacciones, 3 CARD_PAYMENT antes de la recuperación; sin pago nuevo de 1,000.00. | Δ S/ 0.00 durante ambos errores. |
| Recuperación segundo pago | Sueldo 3,875.00; efectivo 1,020.00; BCP debe 1,000.00. | Pagar desde Sueldo: sueldo 2,875.00; deuda cero. | CARD_PAYMENT 100,000 minor con ledger fuente/pasivo consistente. | Δ S/ 0.00, PASS. |
| Gastos de QA | Efectivo 1,020.00. | HBO 1.00 y QA_DUP_CASE 0.11 dos veces: −1.22 en efectivo. | Room final: efectivo 1,018.78; Sueldo 2,875.00; BCP 0; Amex −500.00. | Δ S/ 0.00 en saldo final. |
| Ciclo de umbrales HU-10 | Amex S/ 500; línea S/ 50,000. | Registrar compras QA por S/ 24,500 + 15,000 + 10,000 y luego compensar con ingreso y pago por S/ 49,500. | La UI mostró 50%, 80% y 100%; Room final: efectivo S/ 1,018.78; Sueldo S/ 2,875.00; deuda Amex S/ 500.00. Se agregaron 5 transacciones y 6 ledger entries frente al snapshot previo; las cinco están PENDING. | Δ balances líquidos/deuda S/ 0.00 tras compensar; avisos backend no verificados. Evidencia 239–280. |
| Corrección inicial S/ 20 → S/ 21 | Efectivo S/ 1,018.78; apertura original S/ 20.00. | Nueva apertura efectiva S/ 21.00; efectivo S/ 1,019.78. | Room agregó reversión −2,000 y ajuste+2,100 minor; saldo UI S/ 1,019.78. | Δ esperado +S/ 1.00; PASS aislado. |
| Corrección siguiente S/ 21 → S/ 20 | Efectivo S/ 1,019.78. | Volver exactamente a S/ 1,018.78. | UI reportó éxito, pero el repositorio volvió a revertir la apertura original y el saldo siguió S/ 1,019.78; ajuste neto de esta operación S/ 0.00. | S/ 1.00 sobre objetivo; BUG-FIN-03 confirmado. |
| Compensación de QA (S/ 19) | Efectivo S/ 1,019.78 tras el bug. | Restaurar solo la fixture al saldo base S/ 1,018.78. | Se ingresó S/ 19.00; saldo derivado volvió a S/ 1,018.78. Esta compensación no valida la regla de producto y queda registrada como QA. | Δ S/ 0.00 vs baseline; fixture restaurada. |
| Alta, alias y archivo de cuentas QA | Efectivo y Sueldo en estado base; 2/4 activos. | Crear CASH cero y SAVINGS cero; editar/restaurar alias; archivar ambos y volver a 2/4. | Las altas mostraron 3/4 al agregar SAVINGS, archivo regresó a 2/4; importes de apertura cero y sin ledger. Alias QA_CASH se reflejó y luego fue restaurado. | Saldos líquidos Δ S/ 0.00; PASS local. |
| Archivar BCP | Sin deuda; historial presente. | Archivar sin borrar movimientos ni alterar saldos. | BCP is_archived=1; el snapshot intermedio de esa etapa conserva 16 transacciones y 21 ledger entries; historial sigue accesible. | Δ S/ 0.00, PASS. |

Saldo líquido final: S/ 3,893.78 (efectivo 1,018.78 + Sueldo 2,875.00). La Amex archivada mantiene pasivo S/ 500.00; BCP archivada queda en cero.

El snapshot global posterior a todas las pruebas contiene 31 transacciones y 41 ledger entries (incremento de cinco transacciones y seis asientos por el ciclo HU-10). El outbox local conserva 9 filas PENDING y 1 FAILED_PERMANENT; instrument_sync_outbox conserva 32 PENDING y 1 ERROR; sync_outbox tiene 1 IN_FLIGHT; app_notifications contiene 0 filas. La conciliación financiera es local y no demuestra confirmación del backend.

## 6. Otras comprobaciones

| Área | Resultado y límite |
|---|---|
| Historial | Se probaron Todos, Gasto, Ingreso, Transferencia y búsqueda. Los pagos no duplican egresos y las transferencias no se cuentan como ingreso/gasto. No se cubrieron todas las combinaciones fecha/categoría. Evidencia: [14-history-after-payment.png](evidence/14-history-after-payment.png), [26-transfer-filter.png](evidence/26-transfer-filter.png), [27-income-filter.png](evidence/27-income-filter.png), [28-history-search.png](evidence/28-history-search.png), [29-history-search-results.png](evidence/29-history-search-results.png). |
| Cuotas | La suma 33.34 + 33.33 + 33.33 es S/ 100.00. Evidencia: [37-credit-purchase-simulation.png](evidence/37-credit-purchase-simulation.png), [39-room-after-credit-purchase.txt](evidence/39-room-after-credit-purchase.txt). |
| Dashboard | Activos líquidos excluyen líneas de crédito. Evidencia: [13-dashboard-after-payment.png](evidence/13-dashboard-after-payment.png). |
| Privacidad de saldos (HU-04) | Switch de Ajustes persistió tras navegar/reabrir; el dashboard enmascaró importes y los mostró de nuevo al restaurar apagado. No hubo cambio de saldo. No se midió tnum. Evidencia: [186-settings.png](evidence/186-settings.png), [191-settings-private-retest.png](evidence/191-settings-private-retest.png), [192-home-private-retest.png](evidence/192-home-private-retest.png), [193-settings-private-persisted.png](evidence/193-settings-private-persisted.png), [199-settings-toggle-left.png](evidence/199-settings-toggle-left.png), [200-final-dashboard-visible.png](evidence/200-final-dashboard-visible.png). |
| CRUD de cuentas | Alias actualizado QA_CASH y restaurado; cuenta CASH de prueba y cuenta SAVINGS de prueba creadas con apertura cero, archivadas y reflejadas en el contador 2/4↔3/4. Los saldos no cambiaron. No se probó reactivar ni cambiar institución/titular de una cuenta. Evidencia: [154-cash-alias-edited.png](evidence/154-cash-alias-edited.png), [156-cash-alias-restored.png](evidence/156-cash-alias-restored.png), [161-add-cash-result.png](evidence/161-add-cash-result.png), [165-qa-cash-archived.png](evidence/165-qa-cash-archived.png), [178-add-savings-saved.png](evidence/178-add-savings-saved.png), [181-qa-savings-archived.png](evidence/181-qa-savings-archived.png). |
| Archivado | Confirmación indica que historial se conserva; las compras BCP se consultan tras archivar. AMEX archivada con deuda sigue visible por regla de negocio y el detalle informa su estado; la posibilidad de pagar directamente es discrepancia documental SPEC-CTA-01. Evidencia: [123-bcp-archive-dialog.png](evidence/123-bcp-archive-dialog.png), [125-history-after-card-archive.png](evidence/125-history-after-card-archive.png), [126-room-final-audit-state.txt](evidence/126-room-final-audit-state.txt), [201-amex-detail.png](evidence/201-amex-detail.png). |
| Catálogo/tasas (HU-11) | En el formulario se pudo seleccionar un producto Amex válido para BCP y la red/preview/alias se sincronizaron; el formulario muestra solo advertencia genérica de TEA, sin datos por producto. El tarifario desde una tarjeta activa mostró fecha y advertencia, pero no productos; Reintentar no cambió el resultado. No se guardó TEA. HU-11.1/11.2/11.5 no quedan certificadas; HU-11.3/11.4 no se probaron. Ver UX-05 e INCOMPLETO-03. Evidencia: [207-credit-form.png](evidence/207-credit-form.png), [208-credit-form-bottom.png](evidence/208-credit-form-bottom.png), [210-credit-product-selected.png](evidence/210-credit-product-selected.png), [225-credit-product-details.png](evidence/225-credit-product-details.png), [216-rate-catalog.png](evidence/216-rate-catalog.png), [217-rate-catalog-retry.png](evidence/217-rate-catalog-retry.png), [222-rate-catalog-diagnostics.txt](evidence/222-rate-catalog-diagnostics.txt). |
| Reactivación de cierre | Amex se reactivó temporalmente para abrir HU-11 y se archivó de nuevo; el dashboard volvió a Free 2/4, AMEX archivada, deuda S/ 500.00 y activos líquidos S/ 3,893.78. No hubo transacciones ni asientos nuevos. Las mutaciones dejan trabajo local pendiente; no se lanzó sincronización. Evidencia: [214-card-reactivated.png](evidence/214-card-reactivated.png), [220-restored-dashboard.png](evidence/220-restored-dashboard.png), [221-live-room-final-hu11.txt](evidence/221-live-room-final-hu11.txt). |
| Crash | No observado en los flujos ejecutados; no descarta fallos en rutas no recorridas, reinicio o concurrencia. |

## 7. Revisión cruzada Codex ↔ AGY

| Tema | Conclusión | Límite |
|---|---|---|
| Prefill agrupado | Consenso: P2 reproducido dos veces; UI/parsing no son compatibles. | Los fallos no alteraron Room. |
| Origen CASH | Consenso: la UI lo ofrece y el repositorio lo rechaza; reglas de cliente/backend difieren. | Confirmar contrato para decidir si CASH se acepta o se excluye antes del envío. |
| Estado Procesando | Consenso: P2; el error no restablece estado del diálogo. | Bloqueo observado hasta cancelar. |
| Categorías | Consenso: UX P2; datos correctos, jerarquía visual ausente. | Vista limitada a las categorías disponibles. |
| HU-04 privacidad | PASS del caso de ocultamiento/restauración: switch persistente y máscara observable en dashboard. | No se verificó el requisito de tipografía tabular (tnum); la HU queda INCOMPLETA a nivel de cobertura. |
| HU-10 umbrales | La UI y la deuda local reflejaron 50/80/100%; avisos backend INCOMPLETOS. | Las compras quedaron PENDING y no se validó sincronización con RPC, evento remoto, deduplicación, rearme ni permiso denegado. AGY confirma que el evento es backend-side; la falta de aviso offline no se clasifica como bug. |
| HU-11 catálogo y TEA | Parcial: se inspeccionó el selector y la pantalla tarifaria; producto Amex/network se sincronizan en selección. | Tarifario sin filas tras reintento; el formulario muestra advertencia genérica, no tasa/fuente por producto. No se verificaron los 44 productos ni advertencias fuente. TEA válida/inválida no probada y no guardada. Servicio/status no determinados. |
| Matriz S1/S2 | AGY clasificó 18 criterios PASS, 1 BUG (HU-07 corrección repetida), 1 NO_IMPLEMENTADO y 11 NO_EJECUTADO dentro de 14 HUs/81 puntos. | Codex mantiene UX-01 como hallazgo UX, no NO_IMPLEMENTADO: HU-14 acepta crear una subcategoría como segundo nivel, pero no exige en Gherkin el patrón visual de disclosure; el modelo `parent_id` y la selección operan. HU-01/02 y los casos no probados se conservan como excluidos/NO_EJECUTADO, no como ausencia inferida. |
| Tarjeta archivada con deuda | Visibilidad retenida es PASS según `plan.md:188`, `data-model.md:21` y `ObserveInstrumentsUseCases.kt:118-120`; Room conserva `is_archived=1`. `plan.md:188` permite pago directo; HU-07.5/modelo/repositorio/RPC bloquean mutaciones mientras siga archivada (SPEC-CTA-01). | Se confrontó el detalle runtime [201-amex-detail.png](evidence/201-amex-detail.png): la UI indica reactivación y oculta Pagar; repositorio `OfflineFirstFinancialInstrumentsRepository.kt:845` y RPC `20260925022130_s3_credit_canonical_purchase_payment.sql:704-706` lo rechazan. Alinear criterio normativo antes de corregir. Badge ausente en el resumen: UX-04 P3. |
| Selector de producto HU-08/HU-11 | AGY confirma que la familia es filtrada por banco; BCP puede emitir Amex y la selección sincroniza red, alias y preset, PASS para selección/registro de HU-08. | Runtime coincide con [207-credit-form.png](evidence/207-credit-form.png) y [210-credit-product-selected.png](evidence/210-credit-product-selected.png): UX-05 P3 por Amex expandida con Visa activa antes de elegir; sin mutación Room. Cuando el catálogo de tasas no llega, el fallback local conserva solo diseños y no muestra TEA por producto (ver INCOMPLETO-03). |
| Tarifario HU-11 | AGY retiró su clasificación inicial BUG P2 tras confrontar la evidencia con `PROMPT_ORCA_QA_AUDIT.md:37` y `spec.md:372`: no se requiere fallback Room y la indisponibilidad no bloquea cuentas, tarjetas ni TEA personal. Acuerdo: PENDIENTE_SPRINT_ACTIVO/INCOMPLETO P3 informativo, no bug confirmado. | Runtime [216-rate-catalog.png](evidence/216-rate-catalog.png) / [217-rate-catalog-retry.png](evidence/217-rate-catalog-retry.png) muestra fecha/disclaimer pero error sin filas; Android y logcat no revelaron status/body ni causa. [225-credit-product-details.png](evidence/225-credit-product-details.png) tiene solo aviso genérico. HU-11.5 pendiente; HU-11.1/11.2 no certificadas y HU-11.3/11.4 sin prueba funcional. |
| Alertas HU-10 | AGY confirma que la RPC backend emite cruces al sincronizar compra; deuda local 50/80/100% comprobada. | La compra PENDING no permite validar alertas, deduplicación, rearme ni permisos. Ver INCOMPLETO-04; no se reporta falta de aviso offline como defecto. |
| Título del pago | Consenso: UX P3; clase contable correcta. | Solo mejora semántica del historial. |
| Duplicados | Consenso: alerta conforme al contrato; comercio no obligatorio. | Umbral exacto de cinco minutos no certificado. |
| account_id omitido | AGY: riesgo estático P2 candidato en RPC/OpenAPI. | La RPC no se alcanzó en runtime. |
| HU-08 alta de tarjeta | AGY corrobora que `AccountsViewModel` genera `UserId.generate()` y el repositorio compara con `currentUserId()`; el mismatch hace fallar antes de Room. | Reproducido con 3/4 y 2/4 activos. BUG-FIN-04 P2; identidad del owner no debe generarse aleatoriamente en el ViewModel. |
| Mapper de compras con tarjeta | AGY atribuyó los crashes de 09-24/09-27 a pérdida de `operationKind` en el mapper histórico; el mapper actual conserva metadatos y el test Android cubre CARD_PURCHASE. | BUG-MAP-01 queda resuelto en árbol; la prueba automatizada no se ejecutó y falta caso de CARD_PAYMENT. |
| Ruta Avisos | Logcat registra tres crashes 09-28 en `notifications/center`; el grafo activo de MainActivity no registra ese destino. | BUG-NAV-01 P1 confirmado según el criterio de función central no disponible; el crash antiguo de Transfer (09-24) queda separado y no se asigna a estos CARD_PAYMENT. AGY confirmó la atribución al grafo activo. |
| Historial inicial | Captura del empty state y XML posterior con datos; ViewModel puede emitir empty mientras owner no está Available. | UX-06 P3 transitorio, sin pérdida de Room confirmada; revisar loading/owner gate. |
| Transferencia negativa | Guardada Efectivo → Sueldo por encima del saldo; conservó activos según doble entrada. | PASS conforme a HU-18/19; contratos no exigen saldo mínimo. |
| Corrección de apertura repetida | AGY confirmó discrepancia entre ajuste vigente y reversión de la apertura original; P2. | BUG-FIN-03 reproducido con Room/UI; regresión objetivo S/20→21→20→25 especificada en sección 4. |
| Origen = destino | UI no ofrece el origen en la lista destino; dominio y RPC contienen validación explícita. | PASS de UI; camino de bypass directo no ejecutado. |
| Doble tap | Dos taps rápidos sobre Guardar generaron un solo movimiento S/ 0.01 en Room. | PASS para el intento reproducido; no cubre concurrencia de backend/offline replay. |
| Transferencia | Persistida y balanceada por S/ 1,000.00; también se probaron saldo negativo, exclusión del origen y doble toque. | La prueba cubre una sola instancia local; falta replay concurrente/offline y bypass directo de source=destination. |

## 8. Cobertura pendiente

| Área | Estado | Pendiente |
|---|---|---|
| Cuentas | Alta de CASH y SAVINGS con apertura cero, edición/restauración de alias y archivado de ambas; archivado de tarjeta conserva historial. | Reactivación, edición de institución/titular, apertura no cero en alta, historial de cuentas archivadas y casos de sync. |
| Tarjetas | Compra en cuotas, pago total/parcial, saldo/límite/disponible, 50/80/100% de utilización y archivado verificados en casos testigo; el crash de compra con tarjeta no se reprodujo. | Alertas backend/deduplicación/rearme HU-10; tarifario HU-11 sin filas tras reintento; catálogo completo, TEA y saldo insuficiente; confirmar además la reproducción original del crash al guardar compra con pasos exactos. |
| Ledger | Compra, pagos, transferencia con saldo negativo, doble tap y compensaciones conciliados en los casos ejecutados. | Atomicidad con fallo parcial y reinicio de proceso; bypass directo source=destination. |
| Historial | Filtros básicos, búsqueda y archivado de tarjeta cubiertos; se observó empty flash transitorio (UX-06). | Más combinaciones, fechas y estados vacíos/error; cubrir cold start con owner tardío y transacción CARD_PAYMENT. |
| Duplicados | Similitud/cancelación contrastadas. | Repetir con timestamps capturados dentro/fuera de cinco minutos. |
| Offline/sync | Outbox y estados visibles inspeccionados. | Red controlada, reintentos, idempotencia, worker y conflicto de cuenta. |
| Navegación/ViewModels | BUG-NAV-01 P1: abrir Avisos provoca crash tres veces; UX-06 en Historial. | Corregir e instrumentar prueba de grafo/ruta; cubrir Back, rotación, proceso detenido, errores y concurrencia. |
| E2E | UI → Room → ledger → historial verificado para operaciones descritas. | Automatizar regresiones y caminos negativos. |
| Sprint 3 | HU-09/HU-13 y casos HU-12 recorridos; HU-10 utilización local 50/80/100% verificada, avisos incompletos por falta de sync; HU-11 parcial. En EP-PLA, HU-53/54/56 quedan PENDIENTE_SPRINT_ACTIVO por la exclusión expresa de Billing/compra externa. | Ver matriz S3 de sección 2; no se emite PASS global del sprint. |
| P0/P1 | BUG-NAV-01 P1 confirmado tres veces. No se confirmó P0 por corrupción financiera ni bloqueo general de inicio. | La ruta se reprodujo tres veces, superando la repetición requerida; la cobertura parcial no descarta otros P0/P1. |

## 9. Backlog priorizado

### P1

1. BUG-NAV-01: registrar `notifications/center` en el NavHost activo y cubrir que la campana no cierre la app.

### P2

1. BUG-FIN-04: asignar identidad de sesión verificada en el alta de tarjeta y probar persistencia atómica en 2/4 y 3/4.
2. BUG-FIN-01: unificar formato y parseo del importe; añadir regresiones de formatter/parser y prueba UI de Total.
3. BUG-FIN-02: acordar elegibilidad de origen entre UI, dominio, repositorio y RPC; cubrir tipos de cuenta.
4. BUG-UI-01: restablecer carga en todos los errores, permitir reintento y localizar mensajes.
5. BUG-FIN-03: corregir la cadena de ajustes de saldo inicial para revertir el ajuste efectivo anterior; alinear Room/RPC y añadir regresión de correcciones consecutivas.
6. SPEC-CTA-01: resolver el conflicto `plan.md:188` vs HU-07.5/modelo/RPC sobre pago de tarjetas archivadas; actualizar el contrato elegido y cubrir el caso con cupo Free completo.
7. BUG-BACK-01: validar account_id omitido/null/válido contra contrato antes de cambiar la RPC.
8. UX-01: hacer visible la jerarquía de categorías.
9. INCOMPLETO-01: identificar causa del outbox ACCOUNT_NOT_FOUND con usuario/sesión válidos.

### P3

1. UX-06: conservar skeleton/carga de Historial hasta resolver owner y primera emisión de Room.
2. UX-02: rotular CARD_PAYMENT como “Pago de tarjeta” manteniendo la clasificación contable.
3. UX-03: confirmar el guardado de transferencia y mantener un contexto donde la operación nueva sea visible.
4. UX-04: considerar badge “Archivada · deuda pendiente” en el resumen de tarjeta retenida; el detalle ya aclara el estado.
5. UX-05: alinear la familia de producto expandida con la red inicial en el formulario de tarjeta.
6. INCOMPLETO-03: diagnosticar por qué el tarifario no devuelve filas; verificar datos/filtros antes de certificar HU-11.
7. INCOMPLETO-04: ejecutar HU-10 E2E con sincronización backend, permisos concedidos/denegados, deduplicación y rearme.

### Para completar la auditoría

1. Cerrar CRUD de cuentas e instrumentos con sesión válida.
2. Probar saldo insuficiente, origen/destino iguales, doble toque y reintento tras error.
3. Capturar test determinista de ventana de deduplicación.
4. Recorrer offline/sync con red controlada y conciliar backend/outbox.
5. Completar matriz S1/S2 y escenarios no transaccionales disponibles de S3, sin compras reales.
6. HU-11: diagnosticar por qué el tarifario no carga; contrastar filas y fuentes con el snapshot de 44 productos y validar errores/filtros/TEA antes de aceptar HU-11.
7. HU-10: ya se verificó localmente utilización 50/80/100 y compensación financiera; falta sync backend para cruces, deduplicación, rearme y aviso in-app.
8. Registrar y verificar la regresión de BUG-NAV-01 en grafo/UI.
9. El crash reportado al guardar compra con tarjeta no se reprodujo en los flujos HU-13/HU-10; conseguir pasos y condiciones exactas para diferenciarlo de BUG-NAV-01.
10. Repetir la captura temporal del empty state del Historial con owner y transacciones Room ya conocidos.

## 10. Resultado final

- **Código de producto modificado:** No. No se ejecutaron pruebas automatizadas del proyecto; la validación de esta sesión fue manual en dispositivo y contraste estático/documental con AGY.
- **Crash confirmado:** abrir el centro de notificaciones desde la campana termina el proceso tres veces (BUG-NAV-01 P1); raíz en ruta ausente del NavHost activo. AGY confirmó este resultado por separado.
- **Crash al registrar compra con tarjeta:** no reproducido hoy. Las trazas históricas que correspondían al mapper anterior tienen causa identificada y están corregidas en el árbol (BUG-MAP-01); hay una regresión Android existente para `CARD_PURCHASE`, no ejecutada durante esta auditoría. La compra en cuotas y las tres compras de umbral fueron aceptadas y aparecieron en Room/historial.
- **Bug financiero principal:** importe agrupado del diálogo de pago rechazado dos veces; formato manual permitió recuperación sin escrituras parciales.
- **Bug de fuente de pago:** CASH se ofrece y se rechaza; diálogo queda bloqueado hasta cancelar.
- **Bug adicional de integridad financiera:** correcciones consecutivas del saldo inicial no revierten el ajuste vigente anterior; BUG-FIN-03 P2 confirmado.
- **Crash histórico al leer compras con tarjeta:** pérdida de `operationKind` en el mapper anterior causaba que `CARD_PURCHASE`/`CARD_PAYMENT` se validara como movimiento estándar. BUG-MAP-01 está corregido en el árbol actual; existe prueba Android para compra y el runtime actual no volvió a reproducirlo.
- **Alta de tarjeta:** BUG-FIN-04 P2; el ViewModel genera owner aleatorio y el repositorio lo compara con sesión actual. Reproducido en 2/4 y 3/4; sin mutación Room.
- **HU-10:** UI/local ledger comprobados a 50/80/100%; 5 movimientos y 6 asientos de QA añadidos y compensados. Las compras permanecen PENDING; notificaciones, backend, deduplicación y rearme no están validados.
- **Room al cierre:** 31 transacciones / 41 ledger entries; activos líquidos S/ 3,893.78; efectivo S/ 1,018.78; Sueldo S/ 2,875.00; BCP archivada con deuda cero; Amex archivada con pasivo S/ 500.00; cuentas QA archivadas. Los cinco nuevos movimientos del ciclo HU-10 permanecen en historial como datos mock PENDING; los saldos se restauraron. Outboxes: movement_outbox 9 PENDING/1 FAILED_PERMANENT, instrument_sync_outbox 32 PENDING/1 ERROR, sync_outbox 1 IN_FLIGHT y 0 notificaciones Room. Evidencia: [280-room-final-hu10.txt](evidence/280-room-final-hu10.txt), [279-hu10-card-archived.png](evidence/279-hu10-card-archived.png).
- **Estado de la app al cierre de la captura:** Android launcher después del crash de Notificaciones; el Samsung dejó de estar visible en `adb devices` durante la última reconexión. No se borraron datos ni se intentó forzar sincronización.
- **Contraste AGY:** se obtuvo para finanzas, ownership y contrato HU-10; se solicitó confirmación final del grafo activo tras presentar el crash de navegación. El archivo de evidencia incluye el contraste directo de código.
- **Estado de auditoría:** **INCOMPLETA** hasta corregir BUG-NAV-01 (P1) y BUG-FIN-01/02/03/04, diagnosticar BUG-BACK-01/INCOMPLETO-01 y completar los escenarios de sección 8, especialmente sincronización/avisos HU-10 y tarifario HU-11.
