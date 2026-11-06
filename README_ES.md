[EN](README.md) | **ES**

# GTAV Riding Companion para Android

App nativa de Android que acompaña al mod de ciclismo [GTBike V](https://www.gtbikev.com/) para GTA V. Mientras pedaleas, el teléfono va en el manillar. Desde él puedes ver métricas en vivo, elegir recorridos, ajustar tu perfil y los ajustes de ciclismo, elegir qué sensores usa el PC, seleccionar y seguir entrenamientos y descargar tus actividades guardadas. Todo pasa por el puente del PC a través de la red Wi-Fi de casa.

La parte del PC (puente de Node.js y adaptador de GTA V) está en un repositorio aparte: **[gtav-riding-companion-bridge](https://github.com/jhonarias91/gtav-riding-companion-bridge)**. La app lo necesita para comunicarse con el juego; configúralo primero siguiendo [su README](https://github.com/jhonarias91/gtav-riding-companion-bridge/blob/HEAD/README_ES.md).

> **Próximamente:** la app se publicará para Android, para que no tengas que compilarla, y el puente tendrá un ejecutable de Windows (`.exe`), para que no necesites Node.js. Mientras tanto, compila la app como se explica abajo.

> Proyecto comunitario no oficial y sin afiliación con GTBike V, Rockstar Games, Zwift ni Garmin.

## Funciones

| Área | Qué hace la app |
| --- | --- |
| **Rodar** | Potencia con zonas, velocidad, cadencia, frecuencia cardíaca, distancia, tiempo y desnivel. Los datos ausentes o desactualizados se muestran como ausentes, nunca como un cero falso. Incluye el interruptor para activar o desactivar el mod y un mapa calibrado del juego. |
| **Recorridos** | Busca en el catálogo de recorridos del PC, abre una vista previa en el PC y aplícalo (en bucle o en sentido inverso si el recorrido lo permite) o cambia a sesión libre. |
| **Entrenamiento** | Biblioteca de entrenamientos, **Seleccionar e iniciar** un entrenamiento igual que el botón Seleccionar del mod (el mod pone el rodillo en ERG; si hay uno en curso, se sustituye después de un aviso) y progreso en vivo. Pausar, saltar y terminar se hacen en el PC. |
| **Sensores del PC** | Para cada función (potencia, control del rodillo, cadencia, frecuencia cardíaca, dirección, controles), consulta la fuente que usa el PC y elige **Auto**, **Lock** o **Skip**, como en *Device Selection* del mod. |
| **Buscar sensores con el teléfono** | Lista los sensores Bluetooth cercanos que GTBike V puede usar y envía tu elección al PC. El PC fija ese sensor y se conecta a él directamente. El teléfono solo escucha los anuncios y nunca se conecta, así que los datos de la sesión no pasan por el teléfono. |
| **Actividades** | Guarda la sesión actual como con la opción *Save* del mod; consulta los archivos FIT guardados en el PC con resumen, gráfica de potencia con los cortes reales, descarga y compartir. |
| **Ciclistas y clasificación** | Clasificación del recorrido actual, de solo lectura (cuando el puente se inicia con `--enable-riders`). |
| **Ajustes del PC** | Peso, FTP, frecuencia cardíaca máxima, dificultad del rodillo, sensación de la carretera, piloto automático, esquivar obstáculos, bicicleta y personaje. |
| **Ayuda en todas partes** | Cada opción importante tiene un botón **?** con una explicación que funciona sin conexión. |
| **Más** | Inglés y español, tema claro u oscuro, unidades métricas o imperiales, diagnóstico de conexión (el informe copiado no incluye tokens ni direcciones) y un modo de demostración que no necesita PC. |

La app no incluye a propósito el control manual del modo ERG, la potencia objetivo ni las acciones de pausar, saltar o terminar entrenamientos.

## Requisitos

- Teléfono: Android 8.0 (API 26) o posterior. El escáner del QR de conexión usa los servicios de Google Play; sin ellos, escribe la dirección, el puerto y la huella de la clave a mano. Bluetooth de bajo consumo es opcional y solo hace falta para buscar sensores con el teléfono.
- PC: el [puente GTAV Riding Companion](https://github.com/jhonarias91/gtav-riding-companion-bridge) v1 en ejecución (`node bridge/start-v1.mjs`), GTA V con GTBike V **0.8.0.4** y el adaptador instalado.
- Ambos dispositivos en la misma red local. El PC puede estar conectado por Ethernet.
- Para compilar: **JDK 17 o posterior** y el SDK de Android con la plataforma 37 (Android Studio incluye ambos). El wrapper de Gradle descarga Gradle 9.6.

## Inicio rápido

1. Configura e inicia el puente del PC siguiendo el [README del puente](https://github.com/jhonarias91/gtav-riding-companion-bridge/blob/HEAD/README_ES.md#inicio-rápido). Desde la carpeta del puente:

   ```powershell
   node bridge/start-v1.mjs
   ```

2. Compila e instala la app de depuración en un teléfono conectado por USB, con la depuración USB activada. Desde este repositorio:

   ```powershell
   .\gradlew.bat :app:installDebug
   ```

   En macOS o Linux, usa `./gradlew :app:installDebug`.

3. Abre la app y, en **Conecta con tu PC**, toca **Escanear código QR** y apunta la cámara a la ventana del puente. El QR lleva la dirección, el puerto, la huella de la clave, la identidad del puente y una clave de emparejamiento de un solo uso, así que el teléfono verifica el puente y se empareja solo. Cada QR empareja un teléfono; pulsa Enter en la ventana del puente para mostrar uno nuevo.

   Sin el escáner de QR, escribe la dirección y el puerto que muestra la consola, la **Huella de la clave del puente (HTTPS)** y el código de emparejamiento de seis dígitos (pulsa Enter en la consola para ver el código vigente).

4. Inicia GTA V con GTBike V. La pantalla **Rodar** muestra cada capa por separado (puente, adaptador del juego, mod, receptores ANT+ y Bluetooth), así que puedes ver exactamente qué falta.

El emparejamiento dura 30 días. Cuando vence, la app pide escanear el QR de nuevo.

Para explorar la app sin PC, toca **Explorar con datos de demostración** en la pantalla de conexión. Los datos de demostración siempre están identificados como tales.

> **Transporte.** El puente sirve HTTPS de forma predeterminada y la app fija su clave pública, así que rechaza cualquier otro certificado. HTTP sin cifrar (`http-dev`) es solo para desarrollo: el puente debe iniciarse con `--http-dev` y solo lo aceptan las compilaciones de depuración de la app (`app/src/debug/res/xml/network_security_config.xml`). Las compilaciones release rechazan el tráfico sin cifrar e informan `TLS_REQUIRED`.

## Permisos

| Permiso | Para qué |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Llegar al puente del PC en la red local y explicar las desconexiones |
| `BLUETOOTH_SCAN` (Android 12 o posterior, `neverForLocation`) | Solo en **Buscar sensores con el teléfono**, para listar los sensores cercanos |
| `ACCESS_FINE_LOCATION`, `BLUETOOTH`, `BLUETOOTH_ADMIN` (solo Android 11 y anteriores) | Android los exige para cualquier búsqueda Bluetooth en esas versiones. La app nunca lee tu ubicación. |

La app nunca pide `BLUETOOTH_CONNECT` ni se conecta a los sensores. El escáner de QR es el de los servicios de Google Play, así que la app no pide el permiso `CAMERA`.

## Compilación y pruebas

```powershell
.\gradlew.bat :app:assembleDebug                                 # APK de depuración en app\build\outputs\apk\debug
.\gradlew.bat :core:test :bridge-client:test :app:testDebugUnitTest   # pruebas de JVM
.\gradlew.bat :app:lintDebug                                     # lint de Android
.\gradlew.bat :app:connectedDebugAndroidTest                     # pruebas de interfaz en un dispositivo o emulador conectado
```

`testDebugUnitTest` por sí solo no ejecuta los módulos de Kotlin puro; incluye `:core:test` y `:bridge-client:test` como arriba.

Las pruebas de `bridge-client` codifican comandos y los comparan con los fixtures JSON del contrato del puente. Esperan que [gtav-riding-companion-bridge](https://github.com/jhonarias91/gtav-riding-companion-bridge) esté clonado junto a este repositorio (`../gtav-riding-companion-bridge`). Para usar otra ubicación, pasa `-PbridgeRepo=RUTA`.

### Firma de la release

Las compilaciones release solo se firman si existe `keystore.properties` en la raíz del repositorio. Nunca lo subas al repositorio; `.gitignore` ya lo excluye.

```properties
storeFile=..\\keys\\gtbike-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

```powershell
.\gradlew.bat :app:assembleRelease
```

### Textos de ayuda

Los textos de ayuda (inglés y español) se generan a partir de un catálogo de ayuda contextual que se guarda fuera de este repositorio (`../planning/android/AYUDA_CONTEXTUAL.md`). Los archivos generados están en el repositorio, así que solo necesitas el catálogo para cambiar la ayuda. Después de editarlo, ejecuta:

```powershell
node tools/generate-help-resources.mjs
```

El script reescribe `values*/strings_help.xml` y `ui/help/HelpTopic.kt`.

## Estructura del proyecto

| Módulo | Contenido |
| --- | --- |
| `core` | Dominio en Kotlin puro: modelos que reflejan el contrato v1 del puente, reglas (activación del mod, bloqueo de controles, zonas de potencia), el clasificador de sensores Bluetooth y los repositorios de demostración. Sin dependencias de Android. |
| `bridge-client` | Cliente OkHttp + kotlinx.serialization para el puente v1: emparejamiento por QR o código, consultas periódicas, comandos y operaciones, HTTPS con clave pública fijada. |
| `app` | Interfaz Jetpack Compose (Material 3), Hilt, Navigation, preferencias con DataStore y escáner Bluetooth. |

Tecnologías: Kotlin 2.2, AGP 9.5, Compose BOM 2026.09, Hilt 2.60, OkHttp 5.5, kotlinx.serialization 1.9. `minSdk` 26, `targetSdk` 36, `compileSdk` 37.

## Notas de diseño

- **El PC es la fuente de verdad.** La app envía comandos tipados y espera a que el puente informe que observó el cambio (`applied`). Una conexión perdida nunca reenvía un comando.
- **Las capacidades dirigen la interfaz.** Cada control se habilita según `GET /api/v1/capabilities`. Un control no disponible muestra el motivo (mod apagado, juego en pausa, versión del mod no compatible…) y su ayuda sigue abriéndose.
- **Sin datos falsos.** Los valores ausentes, desactualizados o no capturados se muestran como tales.

## Próximos pasos

- **App de Android publicada.** Podrás instalarla directamente en lugar de compilarla desde el código fuente.
- **Ejecutable de Windows (`.exe`) para el puente.** Podrás ejecutar la parte del PC sin instalar Node.js. Consulta el [repositorio del puente](https://github.com/jhonarias91/gtav-riding-companion-bridge).

## Licencia

Todavía no se ha elegido una licencia. Hasta que se añada un archivo `LICENSE`, todos los derechos están reservados.
