# Acceso e introducción de Kipu

Fecha: 2026-10-05. El usuario proporcionó `Omboarding 3 - Corregido.png` y autorizó completar la tercera página. Sustituye al PNG duplicado original. Se implementan ilustración Compose de privacidad, texto aprobado, indicador final, «Empezar» y «Ya tengo una cuenta»; ambas acciones guardan la finalización antes de abrir login.

## Fuentes e integración

Se inspeccionaron las ocho referencias originales y la tercera página corregida de `../mockups/Sesion/`, la constitución, spec/plan/tasks de EP-APS, HU-01/02/03 y HU-52, y la memoria central de Kipu. Se aplicaron las skills Android Kotlin, UI UX Pro Max y Compose Animations, además del flujo Spec Kit y la revisión de código.

El proyecto conserva un módulo Android, Hilt, repositorios/use cases, StateFlow, Navigation Compose, Room, Supabase Auth y el Design System Calm Emerald con Inter. DataStore Preferences ya era una dependencia del proyecto; se utiliza para el checkpoint de introducción, sin credenciales ni datos por cuenta. Las preferencias de perfil permanecen en Room.

Login, registro, recuperación, confirmación y nueva contraseña son componentes Compose reales. Las ilustraciones son cards, textos e iconos Compose con datos de ejemplo exclusivos de la introducción. Se reutilizan KipuCard, colores, tipografía y motion tokens.

## Archivos creados

En `app/src/main/java/com/kipu/app/feature/auth/`:

- `presentation/AuthComponents.kt`: surface adaptable, card, correo, contraseña, CTA con loading y mensaje de error.
- `presentation/AuthFormScreen.kt`: composición compartida de login y registro.
- `presentation/OnboardingScreen.kt`: pager de tres posiciones, swipe, avance, retroceso e indicador.
- `presentation/OnboardingIllustrations.kt`: ilustraciones Compose de las tres páginas, incluida la composición de privacidad con saldo oculto, candado, credenciales y ausencia de APIs bancarias.
- `presentation/OnboardingViewModel.kt`: checkpoint y finalización persistente antes de navegar.
- `presentation/AuthCallbackViewModel.kt`: procesamiento retenido de callbacks, deduplicación y cola de enlaces distintos.
- `data/OnboardingPreferences.kt`: persistencia de página y finalización.
- `data/AuthSessionGate.kt`: exclusión entre restauración e instalación de sesión de recuperación.

Pruebas creadas: AuthSessionGateTest, RecoveryRequestTest, AuthCallbackViewModelTest, AuthNavigationTest, AuthVisualFlowTest y OnboardingPreferencesTest.

## Archivos modificados

- `MainActivity.kt`: arranque según sesión/checkpoint, callbacks retenidos y limpieza del intent consumido.
- `navigation/AuthNavigation.kt`, `navigation/KipuNavHost.kt`: introducción y rutas de acceso; selección de plan seguida por biometría; salida segura del reset al login.
- `feature/auth/presentation/LoginScreen.kt`, `RegisterScreen.kt`, `RecoveryScreen.kt`, `ResetPasswordScreen.kt`: interfaces reales compartidas y adaptables.
- `AuthViewModel.kt`, `RecoveryViewModel.kt`, `RecoveryUiState.kt`: prevención de solicitudes duplicadas, estados/cooldown y restauración del formulario de recuperación sin guardar contraseñas.
- `feature/auth/domain/RecoveryUseCases.kt`: validación local, fallos de servidor y rate limit tipado.
- `feature/auth/data/SupabaseAuthRepository.kt`, `RecoverySessionInstaller.kt`: serialización de cambios de sesión.
- Tests existentes `AuthViewModelTest.kt`, `RecoveryViewModelTest.kt`, `AuthScreensTest.kt`.
- `specs/001-ep-aps-acceso-perfil-seguridad/{spec,plan,tasks}.md`: refinamiento y propagación VIS-APS-001 a VIS-APS-006.

Los cambios anteriores del workspace en otras funcionalidades se preservan.

## Navegación y Auth

Arranque → restauración de sesión → destino privado existente, o introducción pendiente/login. Omitir o finalizar guarda el checkpoint antes de abrir login. Login conecta registro y recuperación. El registro conserva la verificación de correo; después de autenticarse se mantiene la selección de plan/biometría según las preferencias existentes. Reset elimina su back stack al regresar al login.

Las operaciones usan los repositorios y la Edge Function `auth-access` existentes. No se añadió login social ni campos personales. Se conserva la política de 8–72 caracteres con letra y número. Recuperación responde de forma neutra para impedir enumeración de cuentas; errores locales/red/servidor muestran feedback seguro. No se agregaron logs con credenciales o URLs de recuperación.

Callbacks sobreviven a recreación de Activity. La restauración se cancela antes del intercambio PKCE y ambos imports comparten una exclusión. Enlaces distintos recibidos durante una instalación se procesan en orden.

## UI y motion

Insets del sistema y teclado, scroll, ancho máximo, targets accesibles, etiquetas semánticas, acciones Next/Done, focus management, visibilidad de contraseña y loading integrado. Contraseñas y confirmación no se guardan en SavedState. Fuente ampliada al 200% y tema oscuro conservan acciones accesibles.

Errores: fade de 150 ms. Login/registro y recuperación: transiciones de 200 ms. Avance del pager: 250 ms; indicador: 150 ms. Se usa la detección existente de Reduce Motion; transiciones programáticas pasan a inmediatas al activarla. Sin delays artificiales ni animación decorativa continua.

## Verificación

- Build debug y build lab con AndroidTest: realizados mediante Gradle.
- 53 pruebas unitarias de autenticación y deep links: sin fallos.
- 14 pruebas instrumentadas en viewport estándar: sin fallos.
- 8 pruebas de interfaz en viewport compacto 320 × 640 dp: sin fallos.
- 8 pruebas de interfaz en viewport equivalente al S24+ (1440 × 3120 px, densidad 560), con animator duration scale 0: sin fallos. No se utilizó un S24+ físico.
- Lint debug: 0 errores y 273 advertencias del workspace. Incluye advertencias de orden de parámetros Compose y APIs de test existentes; no se cambió el Design System global para eliminarlas.
- App debug ejecutada en emulador: primer arranque muestra introducción; omitir abre login; force-stop y nuevo arranque permanecen en login.
- Comparación manual de renders con las referencias de login, registro, recuperación/error/confirmación y las tres páginas de introducción, incluyendo `Omboarding 3 - Corregido.png`. Se generaron capturas de revisión para los distintos tamaños y la ejecución real; los archivos locales se retiraron en la limpieza posterior.

En compactos o con fuente ampliada se desplaza el contenido verticalmente; indicadores y acción del pager permanecen accesibles. Se corrigieron espacios de recuperación y el gesto de la prueba de swipe, que antes actuaba sobre un título parcialmente fuera del viewport.

## Diferencias justificadas y pendientes

- **Tercera página:** implementada según la referencia corregida, manteniendo exactamente el texto y las dos acciones. La ilustración usa elementos Compose, con tamaños relativos y colores del Design System.
- El texto del PNG que revela correo inexistente se reemplaza por el comportamiento neutro de la spec.
- El carácter especial no se exige: la política vigente requiere letra y número.
- Se evita afirmar expiración de 15 minutos sin configuración comprobada. El enlace se describe como temporal y de un solo uso.
- Se utiliza una descripción de cifrado Kipu sin afirmar una certificación bancaria.
- Radios, iconos y proporciones se adaptan a tokens y targets accesibles. Las ilustraciones conservan el contenido y jerarquía con iconos oficiales; no son reproducciones raster exactas.
- Términos y privacidad: faltan documentos/URLs aprobados en el proyecto. Los enlaces muestran un aviso de indisponibilidad; no abren destinos inventados.
- **Proveedor real:** no se enviaron correos ni se crearon usuarios remotos. La arquitectura existente aún incluye tokens CAPTCHA de desarrollo (`dummy-dev-captcha`, `valid_captcha`); el despliegue con CAPTCHA obligatorio requiere conectar el challenge real y validar configuración de Supabase/redirects. Las pruebas locales y MockEngine no acreditan ese recorrido remoto.

El contenido de las tres páginas ya está implementado. La validación/configuración remota y los documentos legales siguen pendientes; las pruebas locales no acreditan preparación del despliegue de producción.

## Cierre de la referencia corregida

La página 3 se comparó con el PNG corregido y la captura adicional compartida por el usuario. Se ajustaron la posición vertical del grupo, el peso semibold del título, el ancho del párrafo y el encabezado centrado. Las labels de la ilustración decorativa conservan escala gráfica; la descripción accesible comunica su contenido completo. El título, la explicación y las acciones respetan la escala de texto del usuario. Las dos acciones finales quedan fuera del scroll y se deshabilitan durante guardado. Nuevas pruebas verifican «Empezar», «Ya tengo una cuenta», tamaño de texto al 200% y bloqueo durante guardado. Las capturas locales nuevas se retiraron en la limpieza posterior.

## Ajuste de registro y motion para A16

El usuario reportó desbordamiento con teclado cerrado y ausencia de la transición login/registro. La causa de layout era la acumulación de logo de 80dp, padding y gaps uniformes; la causa de motion era navegar a otro destino al pulsar una pestaña, desmontando el formulario que debía animarse.

Se utiliza un perfil compacto cuando la ventana tiene menos de 940dp de altura: logo de 56dp, padding de card de 20dp y gaps menores. Los campos, botones y pestañas conservan targets accesibles y la tipografía del sistema. El registro con confirmación y mensaje de coincidencia se valida sin scroll a 384×832dp y texto normal. El contenido conserva scroll con IME, errores o texto ampliado.

Las pestañas ahora alternan el modo del mismo formulario/ViewModel. Las rutas login y register continúan como entradas válidas. El correo se conserva; contraseña y confirmación se limpian al alternar. Los campos adicionales expanden/contraen con fade de 250ms y la tarjeta asciende/desciende por su altura animada. La recuperación desaparece/aparece suavemente. Se respeta Reduce Motion. Desde la entrada login, Back en modo registro devuelve a login. La entrada directa a register tiene callbacks reales de login y recuperación.

Se añadieron tres pruebas: registro completo con teclado cerrado, posiciones intermedias de la animación y uso del callback de login desde la entrada register. Las tres pasan junto al resto de AuthVisualFlowTest (11 pruebas) en la configuración de referencia A16 (1080×2340px, densidad 450, fuente 1.0). La configuración actual del A16 conectado se leyó sin modificarlo: SM-A165M, densidad efectiva 420 y fuente 0.9 (aproximadamente 411×891dp). Ese tramo de validación se realizó en emulador. La comprobación física posterior solicitada por el usuario se documenta a continuación.

Resultados del ajuste A16: build debug/lab y lint debug correctos (0 errores, 273 advertencias); 53 unitarias sin fallos. Instrumentadas: 11 en A16 de referencia, 17 en la configuración actual del A16, 11 en tamaño equivalente al S24+ y 4 específicas de registro con movimiento reducido, todas sin fallos. Se ejecutó también MainActivity debug en emulador con densidad 420/fuente 0.9, comprobando el formulario entero y el cambio real de pestañas. Las capturas locales de esta validación se retiraron en la limpieza posterior.

Archivos de código del ajuste: AuthComponents.kt (padding adaptable), AuthFormScreen.kt (densidad del formulario y transición), RegisterScreen.kt (callback de login en modo compartido) y AuthNavigation.kt (pestañas sin desmontar el formulario). AuthVisualFlowTest.kt contiene las tres pruebas nuevas. Spec/plan/tasks propagados; T092 completada. No se modificaron las reglas de autenticación ni el Design System global.

## Validación física solicitada — SM-A165M

Por solicitud expresa del usuario se comprobó el A16 conectado. El hash del APK instalado difería del build corregido: aún tenía la versión anterior al ajuste. Se actualizó con `adb install -r` exitosamente, conservando los datos de la app. No se cambiaron densidad, fuente ni escalas de animación del teléfono.

Se abrió MainActivity real y se verificaron las pestañas login/registro. El registro completo, incluidos confirmación, CTA y aviso legal, cabe con el teclado cerrado. Se enfocó el campo de confirmación vacío y se comprobó que, con teclado Samsung abierto, el scroll permite alcanzar «Crear cuenta». No se enviaron solicitudes ni se ingresaron credenciales. Se dejó la app en registro con el teclado cerrado.

Se generaron capturas físicas del registro con teclado abierto/cerrado y un vídeo de transición en el teléfono real; esos archivos locales se retiraron en la limpieza posterior. No se midió FPS ni jank del dispositivo. No fue necesario cambiar c?digo adicional para esta comprobación.

## Legibilidad de requisitos de contraseña

Las capturas y grabaciones de esta implementación se conservan como evidencia local, excluida de Git. Las rutas de evidencia en este informe corresponden al workspace donde se realizó la validación.

Tras la referencia adicional del usuario, los requisitos pendientes se muestran neutros mientras se escribe. Solo al pulsar «Crear cuenta» pasan a rojo con icono de aviso si siguen sin cumplirse. Los cumplidos muestran un check relleno verde más claro y texto verde con contraste suficiente. Se elimina la etiqueta adicional «Listo» para seguir la referencia. Las descripciones accesibles distinguen «Pendiente», «No cumplido» y «Cumplido»; el estado se reconoce también por la forma del icono. El intento de registro se reinicia al cambiar de pestaña. No se cambia la política ni la altura habitual de las filas.

AuthFormScreen.kt contiene el ajuste y AuthVisualFlowTest.kt añade una prueba de actualización de los tres requisitos. Las 12 pruebas de interfaz pasan, incluida la comprobación de registro sin scroll en ventana A16, transición con posiciones intermedias y texto ampliado. Las capturas locales de revisión se retiraron en la limpieza posterior.

Build debug/lab correcto; 53 pruebas unitarias sin fallos; lint con 0 errores y 273 advertencias existentes. Se actualizó nuevamente el SM-A165M conservando datos y se abrió registro con campos vacíos y teclado cerrado. La captura local confirmó que el formulario completo sigue visible; se retiró en la limpieza posterior. No se enviaron solicitudes de autenticación.
