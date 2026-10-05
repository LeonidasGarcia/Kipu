# Evidencia de verificación final

Las capturas PNG y sus jerarquías XML corresponden a recorridos manuales en el Samsung SM-A165M. Los XML están sanitizados: se retiró el atributo `content-desc` y no se conservaron archivos crudos. `device-metadata.txt` registra versión instalada, dispositivo y preferencias restauradas.

## Recorridos representativos

| Recorrido | Evidencia principal |
| :--- | :--- |
| Dashboard, escala habitual y aumentada | `dashboard_final_dark_font_090`, `dashboard_dark_130_after` |
| Acciones del Dashboard y acceso mediante scroll a 130% | `dashboard_scrolled_dark_font_130`, `dashboard_bottom_dark_font_130` |
| Estado final visible, Dark y font scale 0.9 | `dashboard_post_verification_dark_font_090` |
| Privacidad y saldos enmascarados | `dashboard_privacy_masked_dark_font_090`, `dashboard_privacy_masked_dark_font_130`, `dashboard_privacy_masked_scrolled_dark_font_130` |
| Tema claro/oscuro y escalado en Dashboard | `dashboard_final_light_font_090`, `dashboard_final_dark_font_130` |
| Historial, chips y tipografía amplia | `history_final_dark_font_090`, `history_final_dark_font_130`, `history_final_light_font_090`, `history_final_light_font_130` |
| Filtros Free y opciones Premium | `filters_final_dark_font_130`, `filters_final_light_font_130` |
| Gasto y teclado; transferencia sin destino PEN | `quick_movement_final_dark_font_090`, `quick_movement_keyboard_final_dark_font_090`, `quick_transfer_final_dark_font_090`, `quick_transfer_final_dark_font_130` |
| Selectores de instrumentos sin guardar | `instrument_form_final_dark_font_090`, `credit_instrument_form_final_dark_font_090` |
| Movimiento ya anulado | `movement_detail_voided_final_dark_font_090` |
| Selector de tema con texto ampliado | `settings_final_dark_font_130`, `settings_final_light_font_130` |
| Reducción temporal de animación | `dashboard_motion_zero_dark_font_090`, `history_motion_zero_dark_font_090` |

Cada nombre de la tabla identifica un par `.png` / `.xml`. No se enumeran las extensiones para mantener legible el índice.

`dashboard_dark_130_after`, `dashboard_scrolled_dark_font_130`, `dashboard_bottom_dark_font_130` y `dashboard_post_verification_dark_font_090` se capturaron después de instalar el APK final (15:12 del 2026-10-03). El resto de recorridos finales se capturó durante la misma batería, antes del último ajuste acotado al layout del Dashboard; ese ajuste no modificó Historial, filtros, formularios, instrumentos ni Ajustes. `before_fix_*` son anteriores a sus correcciones y se incluyen solo como referencia histórica.

## Referencias históricas

Los archivos `before_fix_*` registran estados anteriores que motivaron ajustes y no representan el resultado final. Los demás nombres sin el sufijo `final` son capturas auxiliares de pasos intermedios, comprobaciones de preferencias o retorno de navegación; sirven de trazabilidad, no como evidencia primaria del resultado final.

La evidencia `dashboard_dark_130_after` fue recapturada desde la compilación final. Tras recorrer ese viewport, `dashboard_scrolled_dark_font_130` muestra que la acción queda completamente disponible por desplazamiento y `dashboard_bottom_dark_font_130` muestra el final de las tarjetas con espacio inferior. El texto ampliado reduce el contenido que cabe inicialmente en pantalla, pero los controles permanecen alcanzables.
