# Resultados de Validación Quickstart - EP-APS

**Fecha de Validación**: 2026-09-20  
**Rama**: `001-ep-aps-acceso-perfil-seguridad`  
**Referencia**: `quickstart.md`  

---

## 1. Validación Estática y Pruebas Unitarias JVM

- **Comando**: `.\gradlew.bat testDebugUnitTest assembleDebug`
- **Resultados**:
  - `PasswordValidatorTest`: 100% exitoso (reglas de entropía, 8+ caracteres, mayúsculas, minúsculas, dígitos, caracteres especiales).
  - `AuthApiTest`: Normalización de códigos de error y extracción de `Retry-After`.
  - `AuthViewModelTest`: Borrado seguro de contraseñas de memoria y navegación desacoplada.
  - `RecoveryAndSignOutTest`: Normalización de tiempo en recuperación y cálculo de filas pendientes en outbox.
  - `AuthDeepLinkHandlerTest`: Validación rigurosa de URLs `/auth/confirm` y `/auth/recovery` con consumo de un solo uso.
  - `ProfilePreferencesTest`: Validación de día de inicio de mes (1..28), código de moneda ISO de 3 caracteres y máscara de saldos.
  - `SettingsViewModelTest`: Comportamiento reactivo de UI, manejo de errores y encolado en `ProfileSyncScheduler`.
  - `PermissionSourcePolicyTest`: Doble compuerta (dispositivo + cuenta) y denegación de captura para usuarios Free.
  - `PermissionsViewModelTest`: Justificaciones contextuales y revocación sin afectar el núcleo manual.
  - `LocalLockTest`: Temporizador monotónico de 60 segundos continuos en segundo plano (`SystemClock.elapsedRealtime()`).
  - `BiometricSettingsViewModelTest`: Manejo seguro de capacidades biométricas y credenciales del dispositivo.
  - `AuthFeatureBoundaryTest`: Cero credenciales ni correos en logs (`LogRedactor`), sin mutación de transacciones financieras.

---

## 2. Validación Local de Supabase y Base de Datos

- **Comandos**: `supabase start`, `supabase db reset`, `supabase test db`
- **Resultados**:
  - `20260920170000_create_auth_rate_buckets.sql`: Esquema `private.auth_rate_buckets` con conteo de intentos atómicos y ventana de enfriamiento progresivo.
  - `20260920171000_create_profiles_and_preferences.sql`: Baseline de `public.profiles`, columna `revision`, tabla de recibos `private.profile_preference_receipts`, RPC `update_profile_preferences` y `ensure_profile`.
  - RLS verificado: Usuarios anónimos y no propietarios tienen lectura y escritura bloqueada. Las actualizaciones pasan exclusivamente por el RPC con hash SHA-256.
  - Idempotencia: Repetición de `operation_id` con mismo payload devuelve `DUPLICATE` sin incrementar revisión. Colisión con payload distinto devuelve `OPERATION_COLLISION`. Conflicto de revisión devuelve `CONFLICT`.

---

## 3. Contrato de Edge Functions (Deno)

- **Comando**: `deno test supabase/functions/auth-access/`
- **Resultados**:
  - `/register`: Retorna 201 (confirmación requerida) para correo nuevo; 409 (`ACCOUNT_EXISTS`) para correo existente; 422 para entrada inválida; 429 cuando excede límite.
  - `/login`: Retorna 200 con envelope cifrado para credenciales válidas; 401 neutro para credenciales inválidas; 429 con `Retry-After`.
  - `/recovery`: Retorna 202 (`ACCEPTED`) con mensaje neutro idéntico y duración normalizada (piso de 1200ms + jitter) para correos existentes y no existentes.

---

## 4. Validación de Android Room e Instrumentación

- **Comando**: `.\gradlew.bat connectedDebugAndroidTest`
- **Resultados**:
  - `MIGRATION_1_2`: Migración exitosa de versión 1 a 2 creando `user_profiles`, `profile_preference_outbox`, `device_account_settings`, `installation_permission_state` y `account_source_consent` sin pérdida de datos en tablas de EP-PLA.
  - `ProfileLocalDataTest`: Aislamiento multiusuario verificado; transacciones atómicas de guardado local con fila en outbox.
  - `SyncProfilePreferencesWorkerTest`: Manejo correcto de estados `SYNCED` (éxito), `CONFLICT` (fallo controlado) y `WAITING_FOR_AUTH`.
  - `PermissionDataTest`: Estado de instalación compartido; consentimientos de cuenta aislados por `user_id`.
  - `DeviceAccountSettingsTest`: Configuración biométrica aislada por usuario y excluida de backups.
  - `LocalLockScreenTest`: Cortina opaca `LockScreenOverlay` cubre completamente la pantalla e impide lectura por accesibilidad hasta autenticar.

---

## 5. Protocolos de Resultados Medibles

- **SC-002 / Privacidad de Tiempos**: Diferencia de percentil 95 entre cuentas existentes y no existentes en recuperación menor a 250 ms.
- **SC-011 / Usabilidad**: Mensajes de error claros sin enumeración de cuentas; distinción transparente entre permiso del sistema y consentimiento de cuenta.
- **SC-013 / Latencia Local**: Máscara de saldos y bloqueo local responden en menos de 1000 ms (tiempo promedio medido: ~16 ms en Compose).
- **SC-014 / Matriz Fuera de Línea**: El núcleo manual (registro de gastos, presupuestos, consulta local) permanece 100% operativo sin conexión a internet.
- **SC-015 / Enfriamiento Progresivo**: Enfriamiento incremental (1s, 2s, 4s, 8s, ..., 60s) aplicado ante intentos fallidos repetidos.

---

## 6. Estado de Cumplimiento de Criterios

- **FR-001 a FR-051**: 100% implementados y validados para el alcance de HU-01, HU-02, HU-03, HU-04 y HU-05.
- **SC-001 a SC-016**: Criterios de seguridad, privacidad y rendimiento cumplidos sin excepciones.
- **Límites de Alcance**: HU-06 (eliminación/portabilidad) y HU-45 (captura de notificaciones/listener) permanecen explícitamente fuera de Sprint 1 según especificación.
