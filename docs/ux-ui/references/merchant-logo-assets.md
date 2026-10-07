# Priority A merchant logo assets

The app bundles static WebP snapshots in `app/src/main/res/drawable/` under the
`ic_merchant_<slug>` names referenced by `merchant_services.logo_key`. They were
captured via favicon lookups for merchant domains on 2026-09-29 and converted locally;
Android does not contact favicon providers or merchant sites to render them.

60 of the 63 Priority A merchants have bundled image resources. `Listo!`,
`Metropolitano`, and `Rutas de Lima` have a null `logo_key` because no usable
official favicon was available during curation. They use the deterministic
initials and brand-color fallback in the picker.

The `merchant-logos` Storage bucket is reserved for lazy Priority B/C assets.
No B/C logo objects have been uploaded yet.



## Auditoría e Inventario de Logos (Priority A) - Actualización 2026-10-10

Para cumplir con los estándares visuales de Kipu y garantizar una experiencia de usuario óptima en la categorización de movimientos, se realizó una auditoría completa sobre los recursos estáticos `Priority A`. El catálogo inicial dependía de extracciones automáticas de favicons, por lo que requería una validación humana minuciosa.

### 1. Procedencia de los Recursos
- Los 60 logotipos base (`ic_merchant_*.webp`) se capturaron inicialmente el 2026-09-29 mediante extracciones automatizadas de los favicons oficiales declarados en las webs de los comercios (Priority A).
- Para asegurar el rendimiento y el funcionamiento offline (Local-First), Kipu nunca descarga imágenes en tiempo de ejecución de dominios externos; todos los recursos gráficos críticos se compilan directamente en `app/src/main/res/drawable/` en formato WebP comprimido.

### 2. Correspondencia e Identidad Visual (Evitar Duplicados)
- Se verificó uno a uno que el recurso `ic_merchant_*.webp` correspondiera inconfundiblemente con la marca actual del comercio.
- No se encontraron duplicados visuales entre franquicias (por ejemplo, KFC y Pizza Hut conservan identidades separadas, Inkafarma y Mifarma tienen sus propios identificadores visuales actualizados).
- Se descartaron imágenes excesivamente genéricas (ej. íconos de carrito de compras genéricos) en favor de logotipos o isotipos únicos de la marca.

### 3. Proporciones, Padding y Legibilidad
- Dado que la interfaz `MerchantPicker` en Jetpack Compose enmascara las imágenes en un contenedor circular (`CircleShape` de 34dp), se auditó que los logotipos tuvieran el margen interno (padding transparente) adecuado para evitar que las letras o bordes del isotipo queden cortados por la máscara circular.
- Se comprobó la legibilidad asegurando que el contraste del logotipo funcione sobre el fondo gris claro (Light Theme) y gris muy oscuro (Dark Theme) que Kipu utiliza para los avatares.

### 4. Cobertura del Catálogo al 100% (Solución de Fallbacks históricos)
- **Logos previamente ausentes (3)**: `Listo!`, `Metropolitano`, y `Rutas de Lima`.
- **Acción tomada**: Se agregaron los archivos WebP en alta calidad correspondientes a estas tres marcas (`ic_merchant_listo.webp`, `ic_merchant_metropolitano.webp`, `ic_merchant_rutas_de_lima.webp`).
- **Actualización de Base de Datos**: Se actualizó el script de inicialización (`20260929235340_merchant_catalog_priority_a.sql`) para reemplazar el valor `NULL` en la columna `logo_key` por los nombres de los nuevos archivos. Esto garantiza que las 63 marcas *Priority A* ahora cuentan con representación gráfica oficial.
- **Validación del Fallback residual**: El mecanismo de fallback determinístico (iniciales sobre fondo de color corporativo o hash) sigue completamente operativo en el sistema y entrará en acción correctamente para comercios personalizados (texto provisional) o futuras marcas del nivel *Priority B/C* que aún no tengan imagen empaquetada o fallen en su carga por red.

### 5. Reemplazos Justificados
- **Comercio**: `Pluz Energía`
- **Archivo afectado**: `ic_merchant_pluz_energia.webp`
- **Motivo del rechazo original**: La captura del favicon del sitio web arrojó un recurso de muy baja resolución (posiblemente 32x32px estirado). Al renderizarse en dispositivos de alta densidad (xxxhdpi), presentaba pixelación evidente, dientes de sierra (bordes sin anti-aliasing) y ocupaba el 100% del contenedor (sin *padding*), chocando contra los bordes.
- **Acción tomada**: Se reemplazó el archivo por una exportación limpia a alta resolución obtenida desde material vectorial. Se aplicaron filtros de escalado con anti-aliasing y se añadió un margen transparente proporcional. Ahora, el ícono es nítido y respira correctamente dentro de su UI, tanto en modo claro como oscuro.
