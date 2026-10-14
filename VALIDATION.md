## 2026-10-13 — Preparación de 0.4.0 para Google Play

- `versionCode` 5, `versionName` 0.4.0 y compile/target SDK 36. AGP 8.10.1, Gradle 8.11.1 con checksum del distribuidor y NDK 28.2.13676358.
- AAB release reducido con R8 y firmado con una clave de subida propia. `scripts/build-release.ps1 -SkipNative`: correcto. El instalador APK de desarrollo conserva su firma anterior.
- `lintRelease`: 0 errores y 17 advertencias; se corrigió el escape del separador de unidad en `local.properties` y en el ejemplo del README.
- Bundletool 1.18.3 validó el AAB y confirmó targetSdk 36 y `PAGE_ALIGNMENT_16K`. Los segmentos ELF LOAD de las cuatro bibliotecas ARM64/x86_64 tienen alineación de 16 KB. El APK universal generado desde el AAB pasó `zipalign -c -P 16`.
- `jarsigner -verify`: firma válida. El certificado autofirmado de subida no requiere una autoridad de certificación pública.
- Suite instrumentada debug: seis pruebas aprobadas en el emulador Android 16 con páginas de 16 KB, usando el nuevo targetSdk 36.
- El código release extraído del AAB se firmó con la clave de desarrollo exclusivamente para comprobar su arranque en el emulador sin borrar datos: inicio en frío correcto, pantalla de desbloqueo visible y sin errores nativos ni excepciones fatales en los registros del proceso. Después se restauró la variante debug. No se ejecutó la suite instrumentada contra el código reducido por R8.
- Acceso a Privacidad disponible antes de desbloquear la bóveda; intent externo comprobado con la política publicada. Captura revisada con Android 16 y la paleta Synthwave, sin recortes de cabecera por las barras del sistema.
- Los archivos locales de firma y los AAB de distribución están excluidos de Git. No contienen datos de la bóveda del usuario.
- Pendientes: pruebas en teléfono físico ARM64, instalación desde un canal de Google Play, formularios y revisión de publicación.

## 2026-10-13 — Enlaces del desarrollador y Android 16

- Añadidos accesos mediante iconos a PayPal, GitHub de OscarTired y el portafolio de Octa_Dev. Usan el navegador externo; no se añade permiso de Internet.
- Los controles tienen áreas táctiles de 48 dp y descripciones accesibles. Permanecen deshabilitados durante operaciones y mientras se muestra la frase de recuperación sin confirmar.
- `assembleDebug`, `lintDebug` y `assembleDebugAndroidTest`: aprobados. Lint: 0 errores y 4 advertencias.
- Suite instrumentada existente: 6 pruebas aprobadas en `Pixel_8_Pro_API_36`, Android 16/API 36, x86_64 y páginas de memoria de 16 KB. Cubren bóveda, recuperación y respaldos, sesión, carpetas y recursos/apariencia. Esta ejecución no valida la variante ARM64 en un teléfono físico.
- Los tres iconos se pulsaron en el emulador y se verificaron los intents del navegador con las URLs esperadas. Al regresar, la interfaz volvió a solicitar el desbloqueo de la bóveda. No se realizaron pagos ni se cambiaron datos de la bóveda existente.
- `scripts/dev.ps1 -Device emulator-5554`: compilación incremental, instalación y arranque correctos. El comando utiliza la firma debug existente, conserva los datos y no exporta el instalador de distribución. La opción `-Watch` automatiza este ciclo para cambios Kotlin/recursos; no se validó una sesión prolongada de vigilancia.
- Android Studio Live Edit requiere habilitarlo y ejecutar la variante debug desde el IDE. La activación de Live Edit no se ha verificado en esta sesión.

## 2026-09-19 — Optimización, copia de recuperación y GIF

- Variante `lightweight`: R8 y reducción de recursos, sin depuración, con la misma firma de desarrollo. No se cambian el cifrado ni el formato de la bóveda.
- APK anterior: 12.370.039 bytes. APK optimizado: aproximadamente 4,33 MB (65 % menos); incluye ARM64 y x86_64.
- Firma SHA-256 comprobada: coincide con el instalador anterior.
- `assembleDebug`, `lintDebug`, `assembleLightweight` y `lintLightweight`: correctos; lint final de lightweight con 0 errores y 10 avisos (6 sobre versiones de dependencias y 4 sobre recursos/estilo).
- `connectedDebugAndroidTest`: 6 pruebas aprobadas en Pixel 8 Pro / API 35. Incluyen puente nativo, bóveda, backups, bloqueo, carpetas, fuentes, límite de imágenes y persistencia de GIF.
- La prueba nueva importa un GIF de dos fotogramas, verifica que conserva sus bytes y animación al recargar, libera el fondo inactivo, detiene la animación al ocultar/desconectar la vista y conserva el fondo anterior ante un archivo inválido.
- APK optimizado instalado sobre la app existente y arranque en frío confirmado (`am start -W`: `Status: ok`).
- La instrumentación directa de la variante reducida no quedó validada: AndroidX Test falla al enlazar un método Kotlin transformado por R8. La suite soportada sigue usando `debug`; el arranque se comprobó por separado con el APK optimizado.
- Pendiente: medición comparativa de RAM/batería en teléfono físico y ejecución del fallback de GIF en Android 8.

# Validación de SpiritByte Android

Fecha: 12 de septiembre de 2026.

## Actualización 0.3.0: logo, carpetas y exportación selectiva en PC

- Logo copiado del PNG original de escritorio, utilizado en cabecera e icono adaptativo.
- Gestión de carpetas con 22 iconos Lucide compartidos con escritorio, colores,
  subcarpetas, asignación y filtro de credenciales.
- Cinco pruebas instrumentadas aprobadas en Android 15. La nueva prueba verifica
  persistencia de iconos y colores, rechazo de ciclos y eliminación sin pérdida de credenciales.
- Motor compartido: 12 pruebas aprobadas; cuatro nuevas verifican selección de
  backups, exclusión de credenciales, conservación de ancestros y roundtrip cifrado.
- Escritorio: seis pruebas aprobadas y compilación TypeScript/Vite completada.
- APK 0.3.0-dev (versionCode 3), motor ARM64/x86_64 e instaladores Windows 0.1.2
  NSIS y MSI generados. `lintDebug`: 0 errores, 10 avisos no bloqueantes.
- Las pruebas usan datos ficticios o bóvedas temporales. Validación en teléfono físico pendiente.

## Actualización 0.2.0: apariencia de escritorio

- APK `com.spiritbyte.android`, versionCode 2, compilado e instalado en emulador Android 15.
- Inicio de la app y revisión visual de Geist Pixel, Apariencia e intro del zorro completados.
- Cuatro pruebas instrumentadas aprobadas: carga de las cinco fuentes y del zorro;
  imagen limitada a 2048 píxeles, persistencia y rechazo de imágenes inválidas;
  operaciones reales de bóveda y backups; bloqueo durante un desbloqueo en curso.
- `assembleDebug` y `lintDebug`: aprobados; 0 errores y 6 avisos de versiones de dependencias.
- El SVG se rasteriza una vez fuera del hilo UI; las advertencias genéricas de
  complejidad vectorial se suprimen únicamente en ese recurso por esta razón.
- Capturas reales: [Apariencia](docs/screenshots/apariencia.png) e
  [Intro del zorro](docs/screenshots/intro-fox.png).
- Sin cambios en el formato de bóveda, el identificador de aplicación o la firma
  debug; se mantiene la actualización sobre la instalación anterior.
- Falta medir memoria/rendimiento y verificar la apariencia en teléfonos físicos.

## Historial: primera entrega 0.1.0

| Comprobación | Resultado |
| --- | --- |
| `cargo test --manifest-path core/Cargo.toml` | 8 pruebas aprobadas |
| `cargo test --manifest-path native/Cargo.toml --lib` | 1 prueba aprobada |
| Motor nativo Android `arm64-v8a` y `x86_64` | Compilado en release |
| Bindings UniFFI Kotlin | Generados y compilados |
| `:app:assembleDebug` | APK generado |
| `:app:lintDebug` | 0 errores; 5 avisos de versiones más recientes |
| `:app:assembleDebugAndroidTest` | APK de pruebas generado |
| `cargo check --lib` en escritorio | Aprobado con el motor compartido |
| Ejecución de pruebas en Android | Pendiente: emulador con ADB `unauthorized` |
| Validación visual y pruebas en teléfono físico | Pendientes |

La prueba Rust móvil comprueba interoperabilidad con las operaciones de backups
del motor de escritorio, persistencia, bloqueo, contraseña incorrecta y recuperación.
La suite del núcleo cubre además alteración de backups y validación de estructura.

Se añadieron dos pruebas instrumentadas: una carga la biblioteca nativa real y
prueba la bóveda; otra comprueba que un bloqueo durante el desbloqueo descarta
los resultados tardíos. Ambas compilan; no se consideran ejecutadas ni aprobadas
hasta correrlas en un dispositivo/emulador autorizado.

En la entrega 0.1.0 no se había verificado su arranque ni su apariencia en Android.
La ejecución y la revisión visual en emulador se completaron en 0.2.0, como se indica
arriba. El APK sigue siendo debug. Biometría y autocompletado aún no están implementados.
