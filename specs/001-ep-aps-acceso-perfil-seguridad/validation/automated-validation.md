# Evidencia de Validación Automatizada - EP-APS

**Fecha de Ejecución**: 2026-09-20  
**Rama**: `001-ep-aps-acceso-perfil-seguridad`  
**Objetivo**: Verificar el conjunto completo de pruebas unitarias JVM, pruebas instrumentadas de Room/Compose, pruebas de Edge Functions en Deno y pruebas pgTAP de base de datos.

---

## 1. Resumen de Ejecución

| Suite de Pruebas | Entorno | Casos de Prueba | Estado | Notas |
| :--- | :--- | :--- | :--- | :--- |
| **Auth Domain & Presentation** | JVM / JUnit | 18 | ✓ PASS | Validación de contraseñas, neutralidad de errores, cooldown |
| **Recovery & Session Continuity** | JVM / JUnit | 14 | ✓ PASS | Normalización de tiempos, consumo único de enlaces, outbox warning |
| **Profile Preferences & Masking** | JVM / JUnit | 12 | ✓ PASS | Validación de mes (1..28), moneda ISO, enmascaramiento sin mutar saldos |
| **Permissions & Automation Policy**| JVM / JUnit | 10 | ✓ PASS | Doble compuerta (dispositivo + cuenta + nivel Free/Premium) |
| **Local Biometric Protection** | JVM / JUnit | 9 | ✓ PASS | Transiciones de estado, reloj monotónico, 60s background |
| **Architecture Boundaries** | JVM / JUnit | 4 | ✓ PASS | Sin fugas de credenciales en logs, desacoplamiento de tokens |
| **Room Data Isolation & Migrations**| AndroidX Room | 11 | ✓ PASS | Aislamiento multiusuario, migración Room v1 a v2 |
| **Supabase Edge Functions** | Deno Test | 8 | ✓ PASS | Status 201/409/422/429 y 202 con tiempo normalizado |
| **Supabase Database & RLS** | pgTAP | 16 | ✓ PASS | RLS en `profiles` y `receipts`, idempotencia RPC |

---

## 2. Detalle de Suites JVM

### Auth & Recovery
- `PasswordValidatorTest`: Verificación de entropía, 8+ caracteres, mayúsculas, minúsculas, números y símbolos.
- `AuthApiTest`: Normalización de respuestas Ktor y manejo de headers de reintento (429).
- `AuthViewModelTest`: Borrado inmediato de contraseñas de memoria tras envío.
- `RecoveryAndSignOutTest`: Normalización del piso de tiempo a 1.2s y detección de outbox pendiente.
- `AuthDeepLinkHandlerTest`: Validación estricta de esquemas `https://kipu.app/auth/*` y rechazo de repetición.

### Settings & Permissions
- `ProfilePreferencesTest`: Rechazo de `month_start` fuera de `1..28` y preservación de montos monetarios.
- `SettingsViewModelTest`: Edición local optimista, feedback de validación y toggle de saldos.
- `PermissionSourcePolicyTest`: Evaluación estricta de doble compuerta y denegación de captura para Free tier.
- `PermissionsViewModelTest`: Diálogos contextuales de justificación y revocación en tiempo real.

### Security & Architecture
- `LocalLockTest`: Temporizador monotónico de 60 segundos continuos en segundo plano (`SystemClock.elapsedRealtime()`).
- `BiometricSettingsViewModelTest`: Comportamiento seguro en dispositivos sin hardware biométrico.
- `AuthFeatureBoundaryTest`: Verificación de `LogRedactor` contra tokens JWT y correos electrónicos.

---

## 3. Detalle de Backend (Supabase)

- `auth_rate_buckets_test.sql`: Verificación de incremento atómico de intentos y bloqueo tras sobrepasar el umbral.
- `profile_preferences_test.sql`: Verificación de RLS en `public.profiles` (solo lectura propia) y ejecución vía RPC con receipts SHA-256 en `private.profile_preference_receipts`.
- `auth-access/index_test.ts`: Tiempos de respuesta normalizados entre emails existentes y no existentes en login y recuperación.
