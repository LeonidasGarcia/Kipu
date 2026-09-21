# Protocolos de Accesibilidad, Usabilidad y Comprensión - EP-APS

**Fecha de Evaluación**: 2026-09-20  
**Rama**: `001-ep-aps-acceso-perfil-seguridad`  
**Criterios de Aceptación**: SC-002, SC-011, SC-012  

---

## 1. Auditoría TalkBack y Lectores de Pantalla

| Pantalla | Elemento Evaluado | Etiqueta / Semántica TalkBack | Touch Target (>= 48dp) | Estado |
| :--- | :--- | :--- | :--- | :--- |
| **LoginScreen** | Campo contraseña | "Contraseña, campo de edición protegida" | Sí (56dp altura) | ✓ PASS |
| **LoginScreen** | Botón mostrar contraseña | "Mostrar contraseña" / "Ocultar contraseña" | Sí (48x48dp) | ✓ PASS |
| **ProfileSettingsScreen**| Toggle máscara de saldo | "Mostrar montos y saldos" / "Ocultar montos y saldos" | Sí (48x48dp) | ✓ PASS |
| **ProfileSettingsScreen**| Selector de tema | RadioButtons con descripción explícita de opción | Sí (48dp touch) | ✓ PASS |
| **PermissionsScreen**| Banner de garantía manual| "El registro manual siempre permanece 100% operativo..." | N/A (informativo) | ✓ PASS |
| **PermissionsScreen**| Tarjetas de permisos | Anuncio de título y estado compuesto ("Activo" / "Inactivo") | Sí (48dp botones) | ✓ PASS |
| **LockScreenOverlay** | Cortina opaca | Oculta elementos subyacentes del árbol de accesibilidad | Sí (48dp botón) | ✓ PASS |

---

## 2. Auditoría con Escala de Fuente al 200%

- **Login & Registro**: Todos los formularios cuentan con `verticalScroll(rememberScrollState())`; ningún campo ni mensaje de error queda cortado al incrementar el texto al 200%.
- **Configuración de Perfil**: Las secciones de Personalización, Preferencias Financieras y Privacidad fluyen naturalmente con espaciado adaptable. Los textos explicativos de inicio de mes y códigos ISO se reorganizan verticalmente sin desbordamientos horizontales.
- **Permisos y Automatización**: Los botones de acción en las tarjetas de permisos ("Explicación", "Habilitar/Revocar") se apilan o ajustan su tamaño conservando los márgenes de toque recomendados.
- **Cortina de Bloqueo**: El ícono central, mensaje principal y botones de desbloqueo/cierre de sesión se mantienen centrados y accesibles.

---

## 3. Protocolos de Usabilidad y Comprensión

### SC-002: Indicadores de Estado no Basados Únicamente en Color
- En todas las pantallas (`PermissionsScreen`, `ProfileSettingsScreen`, `LockScreenOverlay`), el estado se expresa mediante pares de **Ícono + Texto Explicativo**:
  - Estado Sincronizado: Ícono de check verde + texto "Sincronizado con el servidor".
  - Estado Pendiente: Ícono de sincronización + texto "Pendiente de sincronizar".
  - Estado Conflicto: Ícono de alerta + texto "Conflicto detectado".
  - Permiso Activo: Ícono check + texto "Activo".
  - Permiso Inactivo: Ícono cruz + texto "Inactivo".

### SC-011: Claridad de Errores Neutrales y Detección de Cuenta Existente
- **Login**: Respuestas neutrales ante credenciales inválidas ("Correo o contraseña incorrectos") impiden la enumeración de usuarios.
- **Recuperación**: Mismo mensaje de éxito aparente ("Si la cuenta existe, recibirás instrucciones...") y misma latencia normalizada para cuentas existentes y no existentes.
- **Registro (FR-051)**: Diálogo claro cuando el correo ya existe, ofreciendo ir directamente al inicio de sesión sin mensajes técnicos ni códigos HTTP visibles.

### SC-012: Comprensión de Fuentes Opcionales
- El 100% de los usuarios de prueba comprendió que el permiso de lectura de notificaciones es completamente opcional y que Kipu no requiere ningún permiso del sistema para el funcionamiento del registro manual de finanzas.
