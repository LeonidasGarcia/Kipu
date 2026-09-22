# Preguntas para terminar EP-MOV

## Decisiones resueltas para Sprint 2 (2026-09-22)

Estas decisiones cierran los bloqueos de planificación. Si el equipo las cambia, debe actualizar `spec.md`, `plan.md`, `research.md` y `tasks.md` antes de tocar el esquema compartido.

| Tema | Decisión |
|---|---|
| Modelo canónico | `transactions` + `internal.ledger_entries`; `financial_movements` se migra hacia delante y no se mantiene doble escritura. |
| Migración y reset | EP-MOV prepara la migración con revisión de EP-CTA; `supabase db reset` debe reconstruir el esquema completo antes de integrar. Se conservan datos históricos del baseline V4.2. |
| Transferencias | Una transacción de negocio con dos asientos balanceados: origen negativo y destino positivo. |
| Aislamiento | `user_id` derivado de la sesión (`auth.uid()`), sin introducir `workspace_id` en S2. |
| Contrato | `register_transaction_v1` con `APPLIED`, `DUPLICATE`, `CONFLICT` y `REJECTED`. |
| Idempotencia | Unicidad por `user_id + idempotency_key`; hash igual repite resultado, hash distinto produce conflicto. |
| Errores | Timeout, desconexión y 5xx son transitorios; validación/RLS son permanentes; conflicto de clave/hash requiere resolución. |
| Room | La base sube de versión 3 a 4 con migración no destructiva. |
| Outbox | EP-MOV usa outbox propia con lease, orden por cuenta, backoff exponencial y recuperación de leases vencidos. |
| Proyección | El saldo se actualiza transaccionalmente y se reconstruye desde ledger para reconciliación. |
| Similitud | Cinco minutos; mismo usuario, cuenta origen, tipo, monto y moneda. Sólo advierte. |
| Formulario | Categoría obligatoria para gastos; opcional para ingresos y transferencias. |
| Referencias bloqueadas | Se muestran como no seleccionables; nunca se aceptan nuevas operaciones sobre ellas. |
| Free | Registro manual, historial y deduplicación no consumen cupos. |
| UI | Pantalla 11 funcional y Pantalla 10 mínima; edición, anulación, reembolso y filtros avanzados quedan diferidos. |
| Diseño | Al no existir `DESIGN.md`, se reutilizan el tema y componentes actuales hasta que aparezca una fuente visual oficial. |
| Rendimiento | Referencia: emulador Pixel 6 API 35, dataset de 10 000 movimientos y alta manual con datos preparados. |
| Dependencias | Se integra contra los contratos de EP-CTA y EP-CCO presentes en `main`; el código de Sprint 3 se conserva fuera de alcance. |
| Revisión | El cierre requiere evidencia y revisión cruzada de finanzas, privacidad, seguridad, arquitectura y pruebas. |

Las preguntas siguientes quedan como registro de conversación y validación con el equipo; las que contradigan esta tabla requieren una nueva decisión explícita.

## Bloqueantes de arquitectura y backend

1. ¿El equipo confirma la decisión de `transactions` + `internal.ledger_entries` como modelo canónico?
2. ¿Quién es dueño de la migración compartida y qué versiones/datasets históricos debe soportar?
3. ¿El equipo confirma una transferencia como una transacción con dos asientos balanceados?
4. ¿Room replicará ledger + proyección o sólo movimientos + saldo? ¿Cuál es el procedimiento oficial de reconciliación?
5. ¿Aprobamos `register_transaction_v1` y los estados `APPLIED`, `DUPLICATE`, `CONFLICT`, `REJECTED`?
6. ¿El `supabase db reset` actual debe reconstruir todo el esquema compartido antes de integrar esta épica?
7. ¿Confirmamos que el servidor debe comparar `request_hash` cuando ya existe una `idempotency_key`?
8. ¿Qué errores son transitorios, permanentes o conflictos para definir reintentos y UX?
9. ¿Qué validaciones exactas aplican a cuenta destino, categoría, comercio, moneda y pertenencia al espacio?

## Room, sincronización y proyección

10. ¿Qué versión final de `KipuDatabase` y qué estrategia de migración corresponde a este sprint?
11. ¿EP-MOV reutiliza una outbox compartida o crea una específica? ¿Qué lease, orden y backoff son estándar?
12. ¿El saldo se actualiza como proyección transaccional y además se ofrece reconstrucción desde ledger?

## UI y producto

13. ¿El equipo confirma categoría obligatoria sólo para gastos?
14. ¿El historial mínimo del Sprint 2 queda aprobado sin filtros avanzados ni acciones contextuales?
15. ¿Dónde está `DESIGN.md` o cuál es la fuente visual oficial que lo reemplaza?
16. ¿La acción futura debe llamarse “Eliminar” o “Anular”? Para auditoría se recomienda “Anular”.
17. ¿El formulario puede seleccionar cuentas/categorías bloqueadas sólo para consulta o debe ocultarlas?
18. Si una política de plan rechaza el alta, ¿qué código y mensaje accionable debe mostrar la UI?
19. ¿Registrar movimientos, ver historial y confirmar similares son siempre funciones Free sin consumir cupos?

## Duplicados y calidad

20. ¿El equipo confirma la ventana de similitud de cinco minutos?
21. ¿El equipo confirma cuenta origen, tipo, monto y moneda como dimensiones de similitud, dejando las demás como contexto?
22. ¿La advertencia ocurre antes del commit local? Se recomienda antes, para no crear y revertir datos.
23. ¿Qué dispositivo/emulador y dataset oficial se usarán para medir los 300 ms y 10 000 movimientos?
24. ¿Quién revisa y qué evidencia acepta para cerrar HU18, HU19 y HU23?

## Dependencias y coordinación de ramas

25. ¿Qué commits o ramas de EP-CTA y EP-CCO son prerrequisito y cuándo se integrarán a `main`?
26. Si ya existe código de Sprint 3 en `main`, ¿se conserva, se marca como experimental o se corrige dentro de esta entrega?
27. ¿Quién resuelve conflictos en migraciones compartidas y `KipuDatabase` cuando se integren las ramas?
