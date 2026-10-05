# Implementación de mockups aprobados

Fuente: brief de implementación aprobado y `../mockups/README.md`. La actualización
Calm Emerald prevalece sobre la paleta histórica de Stitch; este conserva tipografía,
espaciado y shapes. Ejecución sobre los cambios locales existentes, sin reemplazarlos.

## Estado recuperado y continuación — 2026-10-04

Las fases 1–5 tienen implementación y capturas del recorrido anterior en el Samsung.
La interrupción ocurrió durante la fase 6, después de un build, pruebas unitarias y
lint satisfactorios. Los checks de abajo se conservan abiertos como aceptación
integral, no como indicación de que falta implementar todas esas pantallas.

La continuación corrigió el dock y el aviso de cupo con fuente al 200 %, las siglas
recortadas de cuentas, la referencia enmascarada de tarjetas en disposición ampliada,
el contraste de «Todos» en Dark y el selector PEN/USD del registro rápido, que se
comprimía y estiraba el bloque del monto. Se conservan las decisiones aprobadas:
Planificación deshabilitada, FAB para registrar y cuatro dígitos requeridos en crédito.

La evidencia y sus límites se detallan en
[implementation-evidence/README.md](../implementation-evidence/README.md).
No se declara cerrada la aceptación con TalkBack ni una revisión independiente de
todo el diff acumulado. No se modificaron reglas financieras ni contratos de dominio
en esta continuación.

## Plan y verificación

- Refinamiento aprobado por el usuario: mostrar debajo del saldo la tarjeta de cupo
  «Plan Básico» cuando `activeComputableCount >= maxFreeQuota` y el cupo sea positivo.
  Incluye uso real de cuentas y tarjetas, porcentaje, estado «Límite alcanzado»,
  barra de progreso y «Mejorar a PRO» mediante la navegación existente a planes.
  Conserva el aviso compacto cuando hay cupo disponible. Esta tarjeta describe el
  cupo base; no determina el entitlement ni autoriza/bloquea operaciones.

- [ ] Fase 1: unificar `ui/theme/Color.kt`, `Theme.kt`, `CalmEmeraldTokens.kt`;
  corregir `navigation/KipuNavigationBar.kt` y transiciones en `MainActivity.kt`.
  Planificación deshabilitada con Próximamente (decisión explícita del usuario).
  Compilar e instalar; comprobar dock, FAB, atrás, Light/Dark y fuente grande.
- [ ] Fase 2: `DashboardScreen.kt`, entrada en `UnifiedInstrumentFormScreen.kt`.
  Conservar balance multidivisa y acceso a altas al alcanzar cupo; compilar y recorrer.
- [ ] Fase 3: formularios contextuales sobre el mismo AccountsViewModel y callbacks.
  Conservar catálogo/preset seleccionado, quitar valores financieros ficticios y
  reducir campos redundantes. Compilar; recorrer banco, producto, configuración,
  billetera y efectivo con teclado y atrás.
- [ ] Fase 4: `MovementHistoryScreen.kt`, detalle y filtros existentes.
  Mantener estado/query/repositorios. Compilar; comprobar filtros, signos y estados.
- [ ] Fase 5: categorías y subcategorías existentes, sin dock global.
  Compilar; comprobar validación y Sheet con teclado.
- [ ] Fase 6: QA físico Samsung SM-A165M en ambos temas y fuentes ampliadas;
  capturas en `../implementation-evidence/`, pruebas de regresión aplicables.

## Restricciones

No cambiar dominio, repositorios, base de datos, límites ni datos reales. No crear
movimientos financieros de prueba en la cuenta real. No generar mockups alternativos.
El FAB global solo registra movimientos. Objetivo táctil mínimo 48dp.

## Conflictos a verificar

- MOCKUP CONFLICT: configuración de tarjeta. El brief llama opcionales a los cuatro
  dígitos; la documentación activa los declara obligatorios para crédito. Conservar
  validación contractual hasta verificar/autorizar un cambio funcional.
- MOCKUP CONFLICT: dock. No hay destino Planificación implementado. Solución aprobada:
  mostrarlo deshabilitado; eliminar el acceso engañoso Sincronizar que registra movimientos.

## Riesgos de revisión

Fuente 200%, teclado con CTA inferior, regreso sin pérdida de selección, privacidad
de todos los montos y cambio de tema sin superficies de contraste incorrecto.
