# Revisión de HungerGames para Ubuntu

Fecha: 2026-10-07. Servidor informado: **Paper 1.21.4**.

Se revisaron el arranque y apagado, descubrimiento de mundos, archivos YAML e idiomas, comandos, listeners, ciclo de partida, equipos, cofres, suministros, restauración, estadísticas y dependencias de compilación. Se corrigieron problemas reproducibles en el código. No se dispone del servidor migrado, su configuración de producción ni sus logs: estos hallazgos son posibles causas de las fallas comunicadas, no una confirmación de cuál ocurrió allí.

## Correcciones

| Área | Problema encontrado | Comportamiento corregido |
| --- | --- | --- |
| Mundos | La búsqueda dependía del directorio desde donde se ejecutaba Java (`.`). Un servicio puede iniciar Java desde otro directorio. | Usa el contenedor de mundos de Bukkit y agrega los mundos ya cargados. Conserva sus nombres originales. |
| Recarga | Acumulaba mundos duplicados y volvía a incluir el lobby como arena. | Reconstruye las listas, aplica whitelist/exclusiones y siempre excluye el lobby. |
| Idiomas | Al crear la carpeta faltante no volvía a listar sus archivos. Los idiomas antiguos se normalizaban después de extraer/cargar los nuevos. | Extrae y vuelve a leer; normaliza nombres antes de extraer. |
| Idiomas migrados | En Linux podían coexistir `ES_ES.yml` y `es_es.yml`; `renameTo` podía reemplazar la traducción existente. | Conserva ambos archivos cuando hay conflicto, avisa en consola y usa el nombre canónico. |
| Traducciones | Un idioma predeterminado inexistente provocaba valores nulos. Las claves faltantes se rellenaban con el idioma predeterminado para todos los idiomas. | Normaliza códigos de idioma, usa inglés como respaldo y completa cada idioma con su recurso correspondiente. La recarga actualiza la caché. |
| Codificación y locale | Lecturas de recursos y conversiones de identificadores dependían de la configuración regional del sistema. | Los recursos se leen como UTF-8; comandos, claves y enums usan `Locale.ROOT`. |
| Configuración | Crear archivos de una arena sobrescribía las copias raíz de configuración y botín. Validar claves no actualizaba la caché. | Copia directamente desde el JAR al archivo nuevo y actualiza las claves en memoria. |
| YAML inválido | La validación podía cargar un YAML inválido como vacío y guardarlo con valores predeterminados. | La validación de `config.yml` y `settings.yml` informa el error y conserva el archivo original. |
| Carteles | Entradas antiguas/inválidas deshabilitaban el plugin o provocaban excepciones; mundos no cargados quedaban registrados como nulos. | Omite las entradas inválidas con un aviso, conserva el YAML y recarga las listas sin registros obsoletos. |
| PacketEvents | Se buscaba `PacketEvents`; el descriptor del artefacto 2.7.0 declara `packetevents`. | Usa el nombre declarado y comprueba que esté habilitado. |
| Restauración | Renombraba el mundo hacia `plugins/.../templates`, algo que puede fallar entre montajes. Mover un mundo enlazado podía reemplazar su symlink. | Prepara y renombra junto al mundo real, conserva el enlace y mantiene la copia anterior. |
| Guardado | Un error al preparar la plantilla podía dejar la arena bloqueada; al finalizar siempre activaba autosave. | Libera el bloqueo si falla la preparación y restablece el valor anterior de autosave. |
| Comandos | Varios comandos desde consola suponían que el mundo estaba cargado. `border` pedía un argumento pero leía tres. | Rechazan mundos descargados con un mensaje; consola acepta `hg border <mundo> <tamaño> <x> <z>`. |
| Acceso a arenas | Carteles y teletransporte podían cargar un mundo mientras se restauraba. | Comprueban el bloqueo antes de intentar cargarlo. Los errores de carga se informan explícitamente. |
| MySQL | El plugin seguía registrando estadísticas aunque la inicialización hubiera fallado; usaba conexiones independientes y no cerraba todos los recursos. | Estadísticas activas solo después de inicialización exitosa; conexión compartida con operaciones serializadas, comprobación/reconexión y cierre de consultas. |
| JDBC | Dependía del descubrimiento automático del driver por class loaders del servidor. | Carga explícitamente el driver incluido en el JAR y establece límites de conexión y lectura. |
| Estadísticas mensuales | La columna del mes dependía de consultas posteriores o de PlaceholderAPI; podía faltar para el primer jugador o al cambiar de mes. | Comprueba/crea la columna antes de usarla; las consultas de esquema se restringen a la base actual. |
| Eventos y estadísticas | Los eventos podían llegar antes de que terminara la carga asíncrona y acceder a estadísticas nulas. Al desconectar se consultaban mundos/configuraciones desde el hilo SQL. | Comprueba los datos disponibles y captura identidad y tiempo de juego en el hilo principal. |
| Estadísticas de combate | Bajas y asistencias se añadían a la víctima. | La muerte corresponde a la víctima, la baja al atacante y la asistencia al jugador que ayudó. |
| Red | La comprobación de actualizaciones y las imágenes de estadísticas bloqueaban el hilo principal. | Se ejecutan en segundo plano; las peticiones de imágenes tienen timeout y cierran sus recursos. Una comprobación fallida no se interpreta como una versión nueva. |
| Apagado | Un arranque incompleto o un lobby faltante podían impedir la limpieza y el cierre de MySQL. | Tolera inicialización incompleta y registra errores por arena sin interrumpir el resto del apagado. |
| Brújula y consejos | La brújula descartaba todos los enemigos en solo; un índice antiguo podía salir del rango. Los consejos no avanzaban. | Corrige la búsqueda sin equipo, normaliza el índice y rota los consejos; tolera una lista vacía. |
| Otros mundos | El listener de rotura aplicaba las restricciones de HG también fuera de sus mundos. | Limita esas restricciones a los mundos habilitados para HG. |

La copia anterior restaurada ahora queda en `<contenedor de mundos>/.<arena>-hg-previous`. El directorio temporal es `.<arena>-hg-restoring`. Si el mundo usa un symlink, ambas carpetas se ubican junto a su destino real. Las copias ocultas se excluyen del descubrimiento de arenas. Las plantillas continúan en `plugins/HungerGames/templates/<arena>`.

Se conservaron los cambios locales previos de compilación: driver MySQL incluido, servicios JDBC combinados, `OUTPUT_DIR` opcional y permiso de ejecución de `gradlew`. El artefacto compilado usa la API 1.21.4 y Java 21 como objetivo. FastBoard se actualizó a 2.2.2. Para construirlo hace falta un JDK 21 completo; un JRE 21 no incluye el compilador. Paper 1.21.4 requiere Java 21, según [la documentación de Paper](https://docs.papermc.io/paper/getting-started/).

## Correcciones específicas para Paper 1.21.4

- **«Ya estás en una partida»**: las desconexiones durante la cuenta regresiva no liberaban el spawn ni la lista de espera. Ahora se limpian en cualquier fase; la comparación por UUID contempla objetos de jugador de una sesión anterior. La reconexión y salida del mundo eliminan participación residual. El registro ocurre después de un teletransporte exitoso y del reinicio del jugador; un teletransporte cancelado o spawn inválido no reserva lugar. El autoinicio captura el mundo de la arena, independientemente de dónde esté quien lo activó.
- **Panel de tiempo**: FastBoard 2.2.2, líneas cargadas inmediatamente y recuperación del panel ausente durante el temporizador. El cálculo tolera registros faltantes de una partida anterior e intervalos cero. Al salir o desconectarse se elimina el panel anterior.
- **Cofres vacíos**: la configuración incluía `SPEED`, `JUMP`, `INSTANT_HEAL`, `INSTANT_DAMAGE` y `REGEN`, nombres que `PotionType.valueOf` ya no acepta en 1.21.4. El parser traduce esos nombres y mantiene niveles y duración con `setBasePotionType`. Los recursos nuevos usan los nombres actuales; los YAML personalizados se conservan. Se prepara el botín antes de vaciar el inventario, se omiten entradas inválidas con diagnóstico y se conserva el contenido si no hay ninguna entrada válida. Una ubicación inválida no corta toda la lista. El primer relleno se ejecuta durante el inicio de la partida.
- **API actual**: reemplazo de constantes antiguas de atributos, encantamientos y partículas; reparación de yunques mediante `AnvilView`. El reinicio ajusta primero la salud máxima, elimina efectos y luego restaura la salud. Los buffs aceptan nombres antiguos de efectos como `DAMAGE_RESISTANCE`, además de las claves actuales.
- **Partidas incompletas**: un error durante el arranque se registra y revierte los indicadores de partida; la finalización libera spawns, jugadores en espera, equipos y tareas pendientes de autoinicio.

Estos errores se reprodujeron o verificaron sobre el código y la API indicada. Sin el log del servidor migrado no se puede afirmar que expliquen por sí solos todos los síntomas allí. La visualización de FastBoard debe comprobarse con un cliente conectado a Paper.

## Verificación

```bash
./gradlew test shadowJar --console=plain
git diff --check
```

Resultado final: **100 pruebas, 0 fallos, 0 errores, 0 omitidas**. La compilación del JAR y `git diff --check` finalizaron correctamente. Se comprobó además la carga del driver MySQL y su servicio JDBC desde el JAR mediante un class loader aislado, sin conectarse a una base.

Las pruebas cubren arranque con carpeta de idiomas ausente, traducciones migradas y nombres en conflicto, idioma predeterminado desconocido, locale turco, UTF-8, recarga, preservación de archivos, YAML inválido, carteles, mundos fuera del directorio de ejecución, comandos sobre mundos descargados, estadísticas pendientes, columnas mensuales y restauración con symlinks y entre sistemas de archivos. La suite existente cubre cofres, muerte, respawn, participación, pelea final, suministros y protección contra mobs. Se incorporó MockBukkit 4.33.2, que usa la API de Paper 1.21.4 y suministra sus registros de tipos. Los casos nuevos crean todas las entradas de `items.yml`, verifican pociones antiguas, entradas inválidas, contenido preservado, teletransporte cancelado, registros posteriores al ingreso exitoso, desconexión en cuenta regresiva, eliminación por UUID, registros de otra arena, datos del panel, limpieza al finalizar y rollback del arranque fallido. MockBukkit solo se usa en tests y no se incluye en el plugin. No se ejecutó una partida en un proceso real de Paper ni se verificó el panel visual con un cliente.

Los tests SQL usan conexiones simuladas: no equivalen a una prueba contra la base migrada. Las pruebas de symlinks y montajes se ejecutan en Linux; la de montajes usa una plantilla temporal en `/dev/shm` y omite el caso si no hay dos sistemas de archivos disponibles.

## Verificación pendiente en el servidor migrado

1. Obtener `logs/latest.log` desde el arranque hasta el primer fallo y confirmar que el proceso de Paper 1.21.4 use Java 21.
2. Comprobar que los nombres de mundos coincidan exactamente con `lobby-world`, `ignored-worlds`, `region.world`, spawnpoints y ubicaciones de cofres. Revisar también el nombre de cada carpeta dentro de `plugins/HungerGames`. El plugin no renombra mundos ni sus configuraciones automáticamente.
3. Confirmar que el usuario del servicio pueda leer/escribir mundos, plantillas y configuraciones. Los permisos y propietarios del servidor migrado no pueden verificarse desde este repositorio.
4. Si MySQL está habilitado, confirmar la importación de datos, conectividad y permisos de la cuenta. Los nombres utilizados son `player_stats` y `player_monthly_playtime`. La sensibilidad a mayúsculas de bases/tablas puede cambiar entre Windows y Unix; no se modificó la configuración de MySQL ni la base de producción. Véase [la documentación de MySQL](https://dev.mysql.com/doc/refman/8.4/en/identifier-case-sensitivity.html).
5. Probar una partida completa en una copia del servidor: entrada, cuenta regresiva, botín, suministros, muerte/respawn, pelea final, ganador, regreso al lobby y segunda partida después de restaurar. Incluir una desconexión y una recarga de configuración.

La elección del contenedor de mundos sigue la [API de Bukkit `getWorldContainer`](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Server.html#getWorldContainer()). No se desplegó este JAR ni se modificó ningún servicio o dato de producción.
