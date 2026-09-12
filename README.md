# Kipu

## Integraciones de Spec Kit

Este proyecto tiene instaladas las integraciones `opencode` y `agy` de Spec Kit.
La integración predeterminada debe seleccionarse desde la raíz del proyecto según
el agente que se vaya a utilizar.

### Usar OpenCode

```bash
specify integration use opencode
```

### Usar AGY

```bash
specify integration use agy
```

El comando `specify integration use <integracion>` cambia la integración
predeterminada sin desinstalar las demás integraciones disponibles.

Para comprobar cuál está activa y consultar el estado de las integraciones:

```bash
specify integration status
```

También se pueden listar las integraciones disponibles e instaladas:

```bash
specify integration list
```

No se recomienda usar la opción `--force` para un cambio normal, ya que puede
sobrescribir archivos compartidos que hayan sido personalizados.
