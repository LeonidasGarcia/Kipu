# Contract: UI Sprint 2

## Pantalla 11 — Acción rápida

- Modal/bottom sheet con tabs `Gasto`, `Ingreso`, `Transferencia`.
- Monto en minor units mediante entrada decimal localizada.
- Selector de cuenta origen; selector destino sólo para transferencia.
- Categoría, comercio, fecha/hora y nota según decisiones del equipo.
- Botón principal deshabilitado ante datos inválidos o envío en curso.
- Error de validación junto al campo y error de sincronización con acción de reintento/corrección.
- Diálogo de posible duplicado con `Cancelar` y `Registrar de todos modos`.

## Pantalla 10 — Historial mínimo

- Grupos cronológicos.
- Fila con icono/tipo, descripción, cuenta, monto con signo y estado de sincronización.
- El movimiento confirmado localmente aparece sin esperar red.
- Búsqueda, chips avanzados y acciones Editar/Anular/Reembolsar quedan diferidos.

## Accesibilidad y privacidad

- Etiquetas semánticas y objetivos táctiles de al menos 48 dp.
- No depender sólo del color para tipo o estado.
- Lectura correcta de monto, moneda y signo por TalkBack.
- Datos sensibles ocultables al entrar en segundo plano según el mecanismo global de privacidad.

## Estilo

Reutilizar tema, tipografía, espaciado y componentes existentes. `DESIGN.md` no fue localizado; la fidelidad final queda condicionada a que el equipo indique la fuente visual oficial.
