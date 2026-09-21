# Matriz de Validación en Dispositivos - EP-APS

**Fecha de Ejecución**: 2026-09-20  
**Rama**: `001-ep-aps-acceso-perfil-seguridad`  
**Criterio de Aceptación**: SC-009, SC-010  

---

## 1. Matriz de Versiones de Android

| Dispositivo / Emulador | Nivel de API | Tipo de Autenticador | Permiso Notificaciones | Resultado Global |
| :--- | :--- | :--- | :--- | :--- |
| **Pixel 8 Pro** | Android 14 (API 34) | Huella / Facial (Class 3) | `POST_NOTIFICATIONS` runtime | ✓ PASS |
| **Pixel 6** | Android 13 (API 33) | Huella en pantalla (Class 3) | `POST_NOTIFICATIONS` runtime | ✓ PASS |
| **Galaxy A52** | Android 12 (API 31) | Huella / PIN del dispositivo | Notificaciones automáticas | ✓ PASS |
| **Pixel 3a** | Android 10 (API 29) | PIN / Patrón de dispositivo | Notificaciones heredadas | ✓ PASS |
| **Dispositivo sin Biometría** | Android 13 (API 33) | Solo PIN / Patrón de pantalla | `POST_NOTIFICATIONS` runtime | ✓ PASS |

---

## 2. Escenarios Clave Validados

### Escenario A: Bloqueo Local y Temporizador Monotónico (60s)
1. **Paso**: Iniciar sesión, habilitar Desbloqueo Local en configuración.
2. **Paso**: Minimizar la aplicación y esperar 59 segundos.
3. **Resultado**: Al regresar al segundo 59, la aplicación permanece desbloqueada sin interrupciones.
4. **Paso**: Minimizar la aplicación y esperar 60 segundos continuos.
5. **Resultado**: Al regresar al segundo 60, se presenta inmediatamente la cortina opaca de bloqueo (`LockScreenOverlay`) ocultando todos los saldos.
6. **Paso**: Autenticar exitosamente con huella o PIN.
7. **Resultado**: La cortina se retira de inmediato; la sesión remota permanece intacta sin consumir red.

### Escenario B: Muerte de Proceso (Process Kill & Restart)
1. **Paso**: Con el Desbloqueo Local habilitado, forzar detención de la aplicación (`am kill` o reinicio del sistema).
2. **Paso**: Abrir la aplicación nuevamente.
3. **Resultado**: La aplicación inicia en estado `LOCKED`, mostrando la cortina opaca antes de cargar o mostrar cualquier cifra confidencial.

### Escenario C: Cancelación / Falla de Prompt Biométrico
1. **Paso**: En estado bloqueado, presionar "Desbloquear" y cancelar el diálogo biométrico del sistema.
2. **Resultado**: La pantalla permanece completamente bloqueada tras la cortina opaca, permitiendo reintentar o cerrar sesión de forma segura.

### Escenario D: Núcleo Manual Fuera de Línea
1. **Paso**: Activar modo avión en el dispositivo.
2. **Paso**: Denegar o revocar todos los permisos de notificaciones y automatización.
3. **Resultado**: El registro manual de gastos, presupuestos e instrumentos financieros permanece 100% funcional y disponible localmente en Room.
