# Implementation Plan: Movimientos y ledger

**Branch**: `4-ep-mov` | **Date**: 2026-09-22 | **Spec**: [spec.md](./spec.md)

**Input**: especificación de `specs/004-ep-mov-movimientos-ledger/spec.md` y documentación funcional V4.2 de `Kipu_Documentacion`.

## Summary

El Sprint 2 implementa HU18, HU19 y HU23: registrar gasto, ingreso o transferencia manual; actualizar saldos de forma atómica; y evitar reenvíos exactos mientras se advierten posibles duplicados. La solución usa Room como autoridad visible local y una bandeja de salida para sincronizar con Supabase/PostgreSQL. El modelo remoto objetivo es `transactions` + `internal.ledger_entries`; `financial_movements` se considera legado y requiere una migración hacia delante acordada con EP-CTA antes de modificar el esquema compartido.

## Delivery Boundary

Incluido esta semana:

- Pantalla 11 funcional para Gasto, Ingreso y Transferencia.
- Flujo de registro local, actualización de saldo, outbox y sincronización remota.
- Idempotencia exacta por `idempotency_key` + `request_hash`.
- Advertencia de similitud dentro de cinco minutos.
- Pantalla 10 mínima para verificar que el movimiento aparece en el historial.
- Pruebas unitarias, de integración Room, de migración/RPC y de UI críticas.

Diferido:

- HU20 edición, HU21 anulación, HU22 reembolso, HU24 filtros avanzados y HU25 permisos granulares.
- Acciones contextuales Editar/Anular/Reembolsar y filtros avanzados de la Pantalla 10.

## Technical Context

**Language/Version**: Kotlin 2.4.20, Java 17, SQL/PLpgSQL para PostgreSQL 17  
**Primary Dependencies**: Jetpack Compose (BOM 2026.08), Room 2.8.5, WorkManager 2.11.2, Hilt, Supabase Kotlin 3.8  
**Storage**: Room/SQLite local; Supabase/PostgreSQL remoto  
**Testing**: JUnit, kotlinx-coroutines-test, Room instrumentation, Compose UI tests, Supabase CLI/pgTAP  
**Target Platform**: Android minSdk 24, targetSdk 36, compileSdk 37  
**Project Type**: aplicación Android con backend Supabase gestionado por migraciones  
**Performance Goals**: alta local visible en menos de 300 ms p95; historial de 10 000 movimientos consultable sin bloquear UI; sincronización reintentable  
**Constraints**: offline-first, operaciones monetarias enteras en minor units, RLS estricta, sin doble contabilización, sin pérdida ante cierre/reinicio  
**Scale/Scope**: tres tipos básicos, una pantalla de alta y una lista mínima; un usuario/espacio sólo puede operar referencias de su ámbito

## Constitution Check

*GATE: debe mantenerse antes y después del diseño.*

| Principio | Resultado | Evidencia |
|---|---|---|
| Especificación antes de código | PASS | `spec.md`, investigación y contratos preceden la implementación. |
| Offline-first | PASS | Room confirma localmente y outbox sincroniza/reintenta. |
| Corrección financiera | PASS | Montos en minor units, asientos balanceados y modelo canónico documentado; la migración tendrá revisión de EP-CTA. |
| Seguridad y aislamiento | PASS | RPC valida membresía/propiedad y las tablas mantienen RLS. |
| Calidad verificable | PASS | Cada historia incluye pruebas independientes y criterios medibles. |
| Cambios evolutivos | PASS | Se exige migración hacia delante desde `financial_movements`, sin doble escritura permanente. |

**Gate de implementación**: las decisiones del incremento quedan fijadas en [research.md](./research.md); cualquier cambio posterior debe registrarse como decisión aprobada antes de alterar tablas compartidas.

## Architecture

### Flujo principal

```text
Compose UI
  -> RegisterTransactionUseCase
  -> MovementRepository
  -> Room transaction (movement + ledger/projection + outbox + receipt)
  -> WorkManager
  -> register_transaction_v1 RPC
  -> command receipt remoto
  -> reconciliación local
```

### Fuente de verdad y saldos

- Room es la autoridad de lectura visible mientras el dispositivo está offline.
- PostgreSQL es la autoridad reconciliada entre dispositivos y usa `user_id` como frontera de aislamiento.
- Una transferencia se modela inicialmente como una transacción de negocio con dos asientos: `SOURCE = -amount` y `DESTINATION = +amount`.
- El saldo proyectado se actualiza en la misma transacción que el movimiento y se puede reconstruir desde el ledger.
- `transaction_links` relaciona operaciones distintas; no sustituye los dos asientos de una transferencia salvo decisión explícita del equipo.

### Idempotencia y similitud

- Repetición exacta: misma clave y mismo hash devuelve el resultado anterior sin nuevos efectos.
- Conflicto: misma clave y hash distinto se rechaza.
- Similitud: misma cuenta, tipo, monto y moneda dentro de cinco minutos presenta advertencia; confirmar genera una clave nueva y permite continuar.
- Una advertencia de similitud no es identidad ni un bloqueo definitivo.

### UI del incremento

Regla de formulario: la categoría es obligatoria para Gasto y opcional para Ingreso/Transferencia.

- Pantalla 11: tabs Gasto/Ingreso/Transferencia; monto, cuenta origen, destino para transferencia, categoría/comercio opcionales según reglas acordadas, fecha/hora, nota y confirmación de posible duplicado.
- Pantalla 10 mínima: grupos cronológicos y filas con icono, descripción, cuenta, monto con signo y estado de sincronización.
- Se implementa con fidelidad visual estricta a `docs/stitch-design-system.md` y `Stich Prompts.md` (Pantalla 10 para Historial y Pantalla 11 para Modal de Registro en Bottom Sheet). Reutiliza tema, componentes, tokens de color (Primary #0F766E, Income #16A34A, Expense #E85D5D) y números tabulares `tnum`.

### Sincronización

- La confirmación local escribe movimiento, ledger/proyección, recibo local y outbox atómicamente.
- El worker reclama elementos con lease, procesa en orden estable y clasifica errores transitorios, permanentes y conflictos.
- Un cierre entre confirmación y llamada remota no pierde el comando; un cierre después de la llamada se resuelve con idempotencia.

## Project Structure

### Documentation

```text
specs/004-ep-mov-movimientos-ledger/
|-- spec.md
|-- plan.md
|-- research.md
|-- data-model.md
|-- quickstart.md
|-- team-questions.md
|-- review-record.md
|-- contracts/
|   |-- register-transaction-v1.md
|   |-- sync-and-deduplication.md
|   `-- ui-contract.md
`-- tasks.md
```

### Source Code

```text
app/src/main/java/com/kipu/app/
|-- core/database/
|-- feature/movements/
|   |-- data/local/
|   |-- data/remote/
|   |-- data/sync/
|   |-- domain/model/
|   |-- domain/
|   |-- presentation/
|   `-- di/
|-- navigation/
`-- ui/component/

app/src/test/
app/src/androidTest/
supabase/migrations/
supabase/tests/
```

**Structure Decision**: mantener la organización existente por feature y crear `feature/movements` con capas `data`, `domain`, `presentation` y `di`; los cambios remotos viven únicamente en migraciones versionadas y pruebas de Supabase.

## Post-design Constitution Check

El diseño continúa en PASS. El modelo canónico, la migración desde `financial_movements`, la representación de transferencias, el aislamiento por `user_id`, la regla de categoría y la versión del contrato remoto quedaron documentados como decisiones de Sprint 2. Cualquier modificación futura requiere una nueva decisión trazable.

## Complexity Tracking

No se solicitan excepciones constitucionales. La bandeja de salida y los recibos son necesarios para offline-first e idempotencia; no constituyen capas opcionales.
