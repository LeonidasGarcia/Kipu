# Auditoría de Seguridad y Release - EP-APS

**Fecha de Auditoría**: 2026-09-20  
**Rama**: `001-ep-aps-acceso-perfil-seguridad`  
**Criterios de Aceptación**: SC-006, SC-007, SC-008, SC-013  

---

## 1. Auditoría de Secretos y Reglas de Backup

| Componente | Verificación | Estado | Evidencia |
| :--- | :--- | :--- | :--- |
| **Exclusión de Backups** | `backup_rules.xml` y `data_extraction_rules.xml` | ✓ PASS | `kipu.db`, `kipu_session.enc`, `shared_prefs`, `datastore/` excluidos explícitamente de backups locales y en la nube de Android. |
| **Almacenamiento de Sesión** | `KeystoreEncryptedSessionStorage` | ✓ PASS | Cifrado AES-256-GCM con claves generadas en Android Keystore con autenticación local. |
| **Sanitización de Logs** | `LogRedactor` / `SecureLog` | ✓ PASS | Filtra y reemplaza automáticamente correos (`[REDACTED_EMAIL]`), contraseñas (`[REDACTED_PASSWORD]`) y tokens Bearer/JWT. |
| **Credenciales en APK** | Escaneo estático de código | ✓ PASS | Cero tokens de servicio (`service_role`), cero secretos embebidos en código fuente compilado. |

---

## 2. Auditoría de Base de Datos y Supabase RLS

### `public.profiles`
- **RLS**: Habilitado y forzado (`FORCE ROW LEVEL SECURITY`).
- **Política `profiles_own_select`**: `auth.uid() = user_id` para usuarios autenticados.
- **Modificación**: Denegada para usuarios autenticados directos; las actualizaciones se ejecutan exclusivamente a través del RPC `update_profile_preferences` mediante el rol dedicado `profile_preferences_executor`.
- **Mitigación Search Path**: La función `update_profile_preferences` tiene fijado `SET search_path = ''` para evitar ataques de hijacking de esquemas.

### `private.profile_preference_receipts`
- **Aislamiento de Esquema**: Ubicado en el esquema `private`, inaccesible para roles `anon` y `authenticated`.
- **Garantía Idempotente**: Registro de hashes SHA-256 para evitar reprocesamiento de transacciones ya aplicadas.

### Rate Limiting y Protección contra Fuerza Bruta
- Esquema `private.auth_rate_buckets` con funciones PL/pgSQL atómicas para conteo de intentos por IP y por identificador normalizado.
- Enfriamiento exponencial progresivo (1s, 2s, 4s, 8s, ..., 60s) en Edge Functions y visualizado en la aplicación móvil.
