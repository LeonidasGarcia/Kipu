# Quickstart de validación: Sprint 2 EP-MOV

## Prerrequisitos

- JDK 17 y Android SDK configurados.
- Emulador/dispositivo API 24 o superior.
- Supabase CLI y contenedor local disponibles para pruebas de migración.
- Decisiones bloqueantes de [team-questions.md](./team-questions.md) resueltas antes de modificar tablas compartidas.

## Comandos base

```powershell
.\gradlew.bat test
.\gradlew.bat connectedAndroidTest
supabase db reset
supabase test db
```

Use las tareas Gradle reales del proyecto si los módulos agregan variantes específicas. No se considera validado un cambio remoto sólo por compilar Android.

## Recorrido mínimo

1. Registrar un gasto offline y comprobar fila, saldo y estado pendiente.
2. Registrar un ingreso offline y comprobar incremento de saldo.
3. Registrar una transferencia y verificar débito/crédito atómicos.
4. Forzar cierre tras el commit local; reiniciar y comprobar que la outbox continúa.
5. Sincronizar y comprobar que el servidor tiene un solo resultado lógico.
6. Reenviar la misma clave/hash y comprobar `DUPLICATE` sin nuevos asientos.
7. Reenviar la misma clave con otro hash y comprobar `CONFLICT`.
8. Crear un movimiento similar dentro de la ventana y comprobar advertencia.
9. Cancelar la advertencia y comprobar ausencia de efectos.
10. Confirmar la advertencia y comprobar un nuevo movimiento con clave nueva.
11. Intentar referencias de otro espacio y comprobar rechazo por RLS/RPC.
12. Abrir historial con 10 000 movimientos y comprobar UI fluida y paginada.

## Evidencia esperada

- Resultados de pruebas unitarias, instrumentadas y pgTAP.
- Captura o grabación de los tres tipos de alta y del diálogo de similitud.
- Consulta que demuestre balance de transferencias y ausencia de duplicados.
- Resultado de reconstrucción de saldo coincidente con la proyección.
- Registro de las decisiones del equipo vinculadas desde la spec o el PR.
