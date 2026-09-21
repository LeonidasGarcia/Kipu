# Research: EP-APS - Acceso, Perfil y Seguridad

## 1. Arquitectura Android

**Decision**: Mantener un único módulo `app` y crear límites por paquetes (`core/session`, `core/security`, `feature/auth`, `feature/settings`) con interfaces de dominio e implementaciones de proveedor.

**Rationale**: Es consistente con `feature/plans`, evita una modularización prematura y permite probar las reglas sin Compose, Android ni Supabase.

**Alternatives considered**: Módulos Gradle por feature, descartados por coste actual; lógica en ViewModels, descartada porque mezclaría proveedor, navegación y autorización.

## 2. Identidad remota, propietario local y bloqueo

**Decision**: Modelar tres ejes separados: `RemoteSession`, `LocalOwner` y `LocalLock`. El SDK conserva la sesión remota; Kipu conserva únicamente la identidad local previamente verificada y el estado de bloqueo.

**Rationale**: Una sesión remota vencida no debe reasignar datos ni impedir el núcleo manual offline, mientras un cierre explícito sí debe retirar toda superficie visible. La biometría no crea ni renueva autorización remota.

**Alternatives considered**: Usar solo `currentSessionOrNull`, descartado por no representar uso offline ni cierre con datos retenidos; considerar biometría como login, descartado por FR-024.

## 3. Almacenamiento de credenciales de sesión

**Decision**: Implementar el punto de extensión de almacenamiento de sesión de supabase-kt 3.8.0 con cifrado AES-GCM respaldado por Android Keystore. El adaptador importa el sobre de login mediante `importSession(UserSession(accessToken, refreshToken, expiresIn, tokenType, user = null))`, valida después el usuario contra Auth y solo entonces establece `LocalOwner`. No duplicar tokens en Room o DataStore y excluir el archivo cifrado de backup/transferencia.

**Rationale**: DataStore no cifra por sí mismo y los tokens no deben salir del adaptador Auth. La referencia Kotlin vigente documenta `importSession` con access token, refresh token, `expiresIn` y `tokenType`; la integración se compila y prueba contra la versión fijada antes de aceptar el contrato.

**Alternatives considered**: Preferencias en claro, rechazadas; `EncryptedSharedPreferences`, evitado por su estado de deprecación; guardar solo memoria, rechazado por FR-012.

## 4. Auth, recuperación y deep links

**Decision**: Usar Supabase Auth para contraseña, confirmación obligatoria de email, refresh y recuperación, con `auth-access` como frontera antiabuso de las operaciones iniciales. Release usa un Android App Link HTTPS verificado y permitido en Auth; builds locales pueden usar un scheme exacto del application ID. El handler acepta solo host/ruta/propósito esperados y consume el evento una vez.

**Rationale**: Supabase requiere redirect configurado para confirmación y reset. App Links reduce secuestro del callback; la allowlist evita tratar URIs arbitrarias como recuperación.

**Alternatives considered**: Scheme custom en producción, mantenido solo para desarrollo; WebView embebida, descartada; tokens manejados por UI, descartados.

## 5. Política de contraseña y mensajes

**Decision**: Configurar servidor y validador compartido con 8-72 caracteres, al menos una letra y un dígito, además de la protección de contraseñas filtradas cuando esté disponible. El servidor sigue siendo autoridad. Acceso y recuperación siempre traducen errores a mensajes neutros; solo registro puede devolver `ACCOUNT_EXISTS`.

**Rationale**: Formaliza DEC-APS-002 sin registrar la clave y conserva la excepción de enumeración especificada en FR-051, cuya aceptación formal sigue siendo gate de implementación mientras el spec esté Draft.

**Alternatives considered**: Duplicar todos los mensajes del proveedor, rechazado por enumeración; reglas distintas cliente/servidor, rechazadas por resultados inconsistentes.

## 6. Registro de correo existente

**Decision**: Crear `auth-access`, una Edge Function pública con autenticación propia y `verify_jwt = false`. En release exige CAPTCHA, límites por HMAC de IP/identidad/operación y límite global antes de comprobar existencia mediante una función SQL restringida a `service_role`; si no existe, llama a Supabase Auth con la clave publicable. El registro siempre exige confirmar email y nunca devuelve sesión.

**Rationale**: Con confirmación de correo, Supabase puede devolver un usuario ofuscado para registros duplicados. FR-051 exige una respuesta distinta y por tanto necesita un límite servidor explícito; una clave privilegiada nunca puede residir en Android.

**Alternatives considered**: Inferir por la respuesta de `signUp`, no fiable; exponer `auth.users`, rechazado; incluir `service_role` en cliente, prohibido; desactivar confirmación, rechazado por seguridad.

## 7. Limitación progresiva

**Decision**: Encaminar registro, login y recuperación por `auth-access`, con buckets persistentes por HMAC de IP, identidad y operación, más límite global y límites nativos de Auth. Aplicar cooldown exponencial 1, 2, 4, 8, máximo 60 segundos, reiniciado tras éxito o ventana de 15 minutos; el servidor devuelve el tiempo efectivo y Android lo respeta. Ninguna ruta genera bloqueo permanente. Recuperación normaliza respuesta y duración observable.

**Rationale**: La protección servidor evita depender de un cliente modificable y el cooldown local satisface una espera progresiva visible. Los hashes reducen retención de identificadores.

**Alternatives considered**: Solo retraso Android, eludible; confiar en límites no progresivos del proveedor, insuficiente para FR-049/050; proxy solo para registro, inconsistente; bloqueo permanente, prohibido; almacenar IP/correo en claro, innecesario.

## 8. Perfil remoto y conflicto

**Decision**: Añadir `revision bigint`, lectura propia por RLS y un RPC `update_profile_preferences` con `operation_id`, hash de payload y `expected_revision`. Un recibo privado reproduce el resultado de reintentos idénticos y rechaza la reutilización del ID con otro payload. Room aplica primero la preferencia y encola una operación estable; conflicto conserva ambos estados y requiere reintento/resolución, no overwrite silencioso.

**Rationale**: Las preferencias siguen a la cuenta y deben sobrevivir offline. La revisión evita pérdida silenciosa entre dispositivos sin involucrar hechos financieros.

**Alternatives considered**: Last-write-wins por reloj del cliente, rechazado por relojes no confiables; actualización directa de toda la fila, rechazada por privilegios y reasignación; reintento solo con revisión, rechazado porque una respuesta perdida produciría falso conflicto; solo remoto, rechazado por local-first.

## 9. Corrección de `public.profiles`

**Decision**: Crear un baseline local reproducible de `profiles`, endurecer `handle_new_user()`, forzar RLS, separar política SELECT de la escritura por RPC, revocar privilegios amplios y probar dos usuarios. Los ejecutores de bootstrap y preferencias son roles `NOLOGIN`, no superuser y sin `BYPASSRLS`, con policies/grants mínimos. Trigger, backfill y `ensure_profile()` son idempotentes. `biometric_enabled` se marca deprecado y queda fuera de contratos; la migración exige que no haya valores activos y aborta si los detecta.

**Rationale**: El remoto actual tiene RLS pero concede DML amplio a `anon/authenticated`, usa un `SECURITY DEFINER` sin `search_path` fijo y no puede reconstruirse con las migraciones locales. La inspección del 2026-09-20 encontró 0 perfiles y 0 activaciones biométricas; la precondición evita convertir ese dato puntual en una suposición destructiva futura.

**Alternatives considered**: Borrar la columna inmediatamente, pospuesto por compatibilidad; confiar solo en RLS con grants amplios, rechazado por mínimo privilegio; usar metadata JWT para preferencias, rechazado por frescura y editabilidad.

## 10. Biometría

**Decision**: Usar AndroidX Biometric con `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`, almacenar habilitación por cuenta/dispositivo y medir background con reloj monotónico. Activación exige prompt exitoso; inicio y 60 segundos continuos en background cierran `LocalLock`.

**Rationale**: El sistema operativo verifica el autenticador sin entregar muestras. El tiempo monotónico no cambia con el reloj civil.

**Alternatives considered**: PIN propio, fuera de alcance y más material sensible; sincronizar el flag remoto, contradice FR-030; temporizador wall-clock, manipulable.

## 11. Permisos y fuentes

**Decision**: Separar `OWN_NOTIFICATIONS` de `OTHER_APP_NOTIFICATION_CONTENT`, y separar a su vez el grant Android a nivel instalación del consentimiento/capacidad por cuenta. HU-05 implementa consentimiento, estado y revocación para avisos propios; el segundo permanece no procesable hasta un contrato Premium válido y la integración HU-45. No se declara un `NotificationListenerService` en este incremento.

**Rationale**: Cumple FR-033 y FR-040 sin confundir permiso del dispositivo, capacidad comercial y procesamiento. Las pruebas controladas pueden cubrir estados sin afirmar integración de captura.

**Alternatives considered**: Listener no-op, descartado porque iniciaría la frontera de captura de HU-45; un único booleano, descartado porque no representa autorización dual ni revocación.

## 12. Privacidad local y logs

**Decision**: Excluir Room y sesión cifrada de backup/device transfer, aplicar cubierta privada antes de contenido, proteger recientes/capturas en superficies privadas y usar códigos de error permitidos en vez de payloads de proveedor. Emails solo se conservan normalizados donde son necesarios y nunca se registran con contraseña, token o enlace.

**Rationale**: FR-043 y FR-047 incluyen exposiciones transitorias y artefactos operativos, no solo almacenamiento final.

**Alternatives considered**: Sanitización posterior del log, rechazada; registrar excepciones completas del proveedor, rechazado por riesgo de headers/URLs secretos.

## 13. Verificación

**Decision**: Combinar unitarias, Room migration tests, MockEngine, Compose instrumentadas, pgTAP, Deno tests y matriz real Android/Supabase. Ejecutar advisors antes y después de cambios y distinguir hallazgos preexistentes de regresiones EP-APS.

**Rationale**: RLS, deep links, biometría, proceso muerto y revocación fallan en límites distintos; ningún tipo de prueba cubre todo.

**Alternatives considered**: Solo mocks, insuficiente para provider gate; solo E2E, lento y con diagnóstico pobre.

## Sources Consulted

- Supabase Kotlin Auth reference: login con contraseña y recuperación.
- Supabase Native Mobile Deep Linking: redirect allowlist, Android intent filters y manejo de deep links.
- Supabase RLS guidance: `TO authenticated`, `(select auth.uid())`, índices y grants mínimos.
- Estado remoto inspeccionado el 2026-09-20: `public.profiles`, política `profiles_own`, trigger `on_auth_user_created` y `handle_new_user()`.
- Dependencias fijadas en `gradle/libs.versions.toml` y límites constitucionales v2.0.0.
