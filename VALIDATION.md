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
