# Implementation Plan: EP-APS - Acceso, Perfil y Seguridad

**Branch**: `001-ep-aps-acceso-perfil-seguridad` | **Date**: 2026-09-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-ep-aps-acceso-perfil-seguridad/spec.md`

## Summary

Implementar HU-01 a HU-05 como una vertical Android nativa que separa tres controles: identidad remota de Supabase Auth, propietario local previamente verificado y bloqueo local del dispositivo. El cliente conservará los datos por propietario en Room, ocultará todo estado privado al cerrar o cambiar de cuenta y sincronizará perfil/preferencias mediante RLS y revisión optimista. Biometría y permisos permanecerán locales. El backend añade un límite endurecido para el registro que, por decisión explícita, informa correos existentes, y corrige la seguridad y reproducibilidad de `public.profiles` sin introducir efectos financieros ni captura automática.

## Technical Context

**Language/Version**: Kotlin 2.4.20 sobre Java 17; SQL PostgreSQL/Supabase; TypeScript/Deno para la función de registro

**Primary Dependencies**: Jetpack Compose Material 3, Navigation Compose, Hilt 2.60.1, Coroutines/StateFlow 1.11.0, Room 2.8.5, DataStore 1.2.1, WorkManager 2.11.2, AndroidX Biometric 1.1.0, supabase-kt 3.8.0 (`auth-kt` y `postgrest-kt`), Ktor 3.5.1

**Storage**: Room `kipu.db` para caché, estado por dispositivo y outbox; DataStore/Android Keystore para metadatos mínimos de sesión y material cifrado; Supabase Auth; PostgreSQL `public.profiles` y estado privado de limitación de registro

**Testing**: JUnit 4, kotlinx-coroutines-test, Ktor MockEngine, Room migration tests, Compose/Espresso instrumented tests, Hilt tests, pgTAP, Deno tests y matriz manual en dispositivos Android reales

**Target Platform**: Android 7.0+ (`minSdk 24`), `targetSdk 36`, `compileSdk 37`

**Project Type**: Aplicación móvil Android nativa de un solo módulo con backend Supabase

**Performance Goals**: Respuesta visual menor a 1 segundo en al menos 95% de las operaciones locales de máscara, preferencia y bloqueo; navegación privada sin fotogramas que expongan datos; sincronización fuera del hilo principal

**Constraints**: Local-first; primer acceso, recuperación y renovación remota requieren red; cero mezcla entre propietarios; secretos fuera de logs y backups; bloqueo al arranque y tras 60 segundos continuos en segundo plano; HU-06, Premium efectivo y captura de contenido fuera de alcance

**Scale/Scope**: 5 historias, 22 escenarios oficiales, 51 requisitos funcionales, 1 aplicación Android, 1 Edge Function pública endurecida, 1 tabla remota existente, 5 entidades Room nuevas y una migración Room

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Pre-Phase 0 | Post-Phase 1 evidence |
|------|-------------|-----------------------|
| Financial integrity | PASS: las preferencias solo cambian presentación y HU-05 no crea hechos financieros. | PASS: contratos y modelo prohíben conversiones, movimientos, candidatos y concesión de Premium. |
| Local-first and retry-safe | PASS: la especificación exige núcleo manual offline y pendientes ligados a propietario. | PASS: `LocalOwner`, caché Room, outbox por usuario, identificador de operación y revisión optimista cubren reintentos y conflictos. |
| Security and privacy | PASS condicionado a RLS, mínimos privilegios, secretos protegidos y consentimiento contextual. | PASS: se diseñan RLS forzado, pruebas de dos usuarios, grants mínimos, endpoint de registro limitado, sesión cifrada, redacción y permisos revocables. |
| Native Android boundaries | PASS: Kotlin/Compose y adaptadores de proveedor; no se mueve lógica financiera a UI. | PASS: dominio de sesión/preferencias independiente de Supabase, Android y Compose mediante puertos. |
| Specification and traceability | PASS para planificación: HU-01 a HU-05 y FR-001 a FR-051 permanecen estables. IMPLEMENTATION BLOCKED mientras `spec.md` siga `Draft`, especialmente por la enumeración deliberada de FR-051. | PASS de diseño: modelo, contratos y quickstart enlazan requisitos y no declaran HU-06/HU-45 implementadas. La aprobación formal del spec y su riesgo de enumeración sigue siendo gate previo a tareas de implementación. |
| Quality and migrations | PASS condicionado a migraciones versionadas, pruebas RLS, plataforma real y accesibilidad. | PASS: se exige reconstrucción local de `profiles`, migración Room 1→2, pgTAP, pruebas de migración, dispositivo real y evidencia a 200%. |
| Product boundary | PASS: no pagos, Open Banking, OCR, captura ni proveedores sociales. | PASS: las interfaces de permisos no consumen contenido y el alcance permanece en identidad, privacidad y consentimiento. |

No existen violaciones constitucionales de diseño. La implementación no puede comenzar hasta aprobar formalmente el spec y aceptar de forma explícita el riesgo residual de FR-051. El hallazgo remoto de migraciones faltantes y privilegios excesivos también es un prerequisito: debe corregirse antes de declarar completa la vertical.

## Project Structure

### Documentation (this feature)

```text
specs/001-ep-aps-acceso-perfil-seguridad/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── auth-session-contract.md
│   ├── local-protection-contract.md
│   ├── permission-source-contract.md
│   └── profile-api.openapi.yaml
└── tasks.md                         # creado posteriormente por /speckit.tasks
```

### Source Code (repository root)

```text
app/src/main/java/com/kipu/app/
├── core/
│   ├── database/                    # KipuDatabase v2, migración y DAO compartidos
│   ├── logging/                     # redacción de errores y secretos
│   ├── network/                     # cliente Supabase y conectividad
│   ├── security/                    # Keystore, bloqueo local y gateway biométrico
│   └── session/                     # SessionCoordinator, LocalOwner y gates de propietario
├── feature/
│   ├── auth/
│   │   ├── data/                    # adaptadores Supabase/Edge y almacenamiento de sesión
│   │   ├── domain/                  # AuthRepository, estados y validación
│   │   └── presentation/            # acceso, registro, recuperación y nueva contraseña
│   ├── settings/
│   │   ├── data/                    # perfil local/remoto, outbox y permisos locales
│   │   ├── domain/                  # preferencias, privacidad y consentimiento
│   │   └── presentation/            # perfil, privacidad, biometría y permisos
│   └── plans/                       # vertical EP-PLA existente, no se reestructura
├── navigation/                      # gates de sesión/bloqueo y deep links
└── ui/                              # tema por cuenta y superficies privadas

app/src/test/java/com/kipu/app/
├── core/session/
├── core/security/
├── feature/auth/
└── feature/settings/

app/src/androidTest/java/com/kipu/app/
├── core/database/                   # Room 1→2 y aislamiento
├── feature/auth/                    # Compose/deep link/sesión
└── feature/settings/                # accesibilidad, biometría y permisos

supabase/
├── functions/auth-access/           # registro/login/recuperación con antiabuso propio
├── migrations/                      # baseline profiles + hardening versionado
└── tests/database/                  # pgTAP de RLS, grants, trigger y RPC
```

**Structure Decision**: Mantener el módulo Android único y la separación por vertical ya usada por `feature/plans`. Los límites nuevos viven en `core/session`, `core/security`, `feature/auth` y `feature/settings`; no se crea un segundo módulo Gradle porque todavía no aporta aislamiento adicional al tamaño actual.

## Delivery Design

### Flujo de sesión y propietario

1. `AuthRepository` traduce estados de supabase-kt a estados de dominio y nunca expone tokens a UI.
2. `SessionCoordinator` combina `RemoteSession`, `LocalOwner` y `LocalLock`. Solo publica un espacio privado si el propietario remoto/local coincide y el bloqueo está abierto.
3. Una sesión remota vencida sin red puede conservar el núcleo local del propietario previamente verificado; las llamadas remotas quedan suspendidas y los cambios se encolan.
4. Un cierre explícito limpia credenciales y propietario activo, marca sus outboxes como `WAITING_FOR_AUTH` y conserva sus filas Room ocultas. Otra cuenta nunca puede leerlas ni enviarlas.
5. Un cambio de identidad invalida navegación y estado de presentación antes de activar el nuevo propietario.

### Persistencia y sincronización

- Room sube de versión 1 a 2 con `user_profiles`, `profile_preference_outbox`, `device_account_settings`, `installation_permission_state` y `account_source_consent`; todas las consultas privadas requieren `user_id` explícito, salvo el estado Android deliberadamente compartido por instalación.
- Las preferencias se escriben primero localmente dentro de una transacción y luego WorkManager envía una operación estable. El RPC remoto registra un recibo por `operation_id` y hash, reproduce el resultado de reintentos idénticos y compara `expected_revision`; ante conflicto se conserva la edición local pendiente y se solicita resolución, sin sobrescribir silenciosamente otro dispositivo.
- El contador de pendientes de cierre agrega las outboxes de EP-PLA y EP-APS por el propietario activo.
- `public.profiles` recibe `revision`, validaciones e interfaz de escritura mínima. `update_profile_preferences` es `SECURITY DEFINER SET search_path = ''`, propiedad de `profile_preferences_executor` (`NOLOGIN`, no superuser, sin `BYPASSRLS`), valida `auth.uid()` y no acepta `user_id`; políticas específicas permiten a ese rol SELECT/UPDATE solo la fila del sujeto JWT y SELECT/INSERT de su recibo. `PUBLIC`/`anon` no ejecutan el RPC; `authenticated` solo ejecuta el RPC y selecciona su fila.
- `handle_new_user()` es propiedad de `profile_bootstrap_executor` (`NOLOGIN`, no superuser, sin `BYPASSRLS`), con INSERT y SELECT limitado por RLS al sujeto Auth para soportar `ensure_profile()`; la función trigger no acepta parámetros, usa exclusivamente `NEW.id`, es idempotente con `ON CONFLICT DO NOTHING`, fija `search_path=''` y no puede ejecutarse directamente por roles cliente. Una migración backfill y `ensure_profile()` reparan usuarios Auth sin perfil bajo los mismos límites.
- `biometric_enabled` queda deprecado y fuera del DTO/RPC. El remoto inspeccionado contiene 0 perfiles y 0 valores activos al 2026-09-20; la migración exige esa precondición y aborta si aparece un `true`, en vez de desactivar silenciosamente una protección posiblemente usada.

### Backend y abuso

- Registro, login y recuperación pasan por `auth-access`, además de los límites nativos de Supabase Auth. En release exige CAPTCHA, consume buckets persistentes por HMAC de IP, identidad y operación, aplica límites globales y devuelve `retry_after_seconds`; el cooldown Android refleja la misma política pero nunca es la única barrera.
- El registro usa esa frontera porque FR-051 exige una diferencia observable que Supabase oculta con confirmación de correo. La configuración de este incremento exige confirmación de email: registrar nunca crea una sesión Android; después de confirmar, la persona inicia sesión. La enumeración distribuida no puede eliminarse por completo mientras FR-051 exista, por lo que requiere aceptación explícita del riesgo antes de implementar.
- Login recibe una contraseña transitoria y devuelve un sobre de sesión únicamente al adaptador Auth, que lo instala inmediatamente en el almacén cifrado del SDK; nunca llega a ViewModel/Compose. Recuperación siempre devuelve la misma respuesta pública y normaliza su ventana temporal.
- La Edge Function no registra correo, contraseña, CAPTCHA, token, IP sin hash ni respuesta secreta. No confía en headers enviados por el cliente como prueba de origen. Solo sus funciones auxiliares necesarias reciben `EXECUTE`, nunca `PUBLIC` o `anon`.
- `handle_new_user()` conserva `SECURITY DEFINER` únicamente porque el trigger de `auth.users` debe crear el perfil; usa `search_path = ''`, nombres calificados y `EXECUTE` revocado a roles cliente.

### Protección local y permisos

- `BiometricPrompt` usa biometría fuerte o credencial del dispositivo. Activar exige éxito; Kipu almacena solo el booleano por usuario/dispositivo y marcas de tiempo, nunca material biométrico.
- El reloj de bloqueo usa tiempo monotónico. Arranque y 60 segundos continuos en segundo plano bloquean; cancelación/fallo mantiene una cubierta opaca y permite reintento.
- Superficies privadas aplican protección de capturas/vista de recientes y muestran la cubierta antes de restaurar contenido.
- HU-05 guarda autorización Android a nivel instalación y consentimiento/capacidad a nivel cuenta. Implementa explicación, estado y navegación para avisos propios de Kipu. La interfaz de acceso al contenido de otras aplicaciones queda cerrada sin autorización de capacidad y sin listener/captura; su integración real corresponde a HU-45.

### Verification Strategy

- Unitarias: validación de formularios, neutralidad de errores, backoff, transición de sesión, propietario, bloqueo monotónico, máscara, preferencias y redacción.
- Persistencia: migración Room 1→2 de las cinco tablas, consultas cruzadas con dos usuarios, grant Android compartido por instalación, consentimientos aislados por cuenta, outbox por propietario y reapertura offline.
- Contrato/integración: MockEngine para Auth/Edge/PostgREST y pruebas de deep links válidos, repetidos, vencidos y alterados.
- Base de datos: pgTAP para RLS forzado, índices, propietarios/grants/policies de cada rol, trigger/backfill/repair, RPC, concurrencia, revisión optimista, idempotencia/colisión de recibos y acceso cruzado.
- Android real: BiometricPrompt/credencial, proceso muerto, 60 segundos en background, revocación externa, vista de recientes y accesibilidad.
- Seguridad: advisors antes/después de migrar, inspección de logs y artefactos release, y prueba de que ninguna clave `service_role` está en el APK.

## Complexity Tracking

No aplica: el diseño no requiere excepciones a la constitución.
