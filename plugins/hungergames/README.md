# HungerGames

HungerGames is a modern Minecraft plugin for spigot servers inspired by the classic 2012 Hunger Games game mode. It provides a fast-paced environment where players compete for resources, fight opponents and aim to be the last person alive. Designed for flexibility, it supports multiple arenas, game modes and extensive customization.

![Minecraft](https://img.shields.io/badge/Minecraft-1.20+-brightgreen?label=MC&style=for-the-badge)
![Version](https://img.shields.io/modrinth/v/hungergames?&style=for-the-badge)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/hungergames?logo=modrinth&style=for-the-badge)](https://modrinth.com/plugin/hungergames)
[![bStats Servers](https://img.shields.io/bstats/servers/21512?logo=googleanalytics&style=for-the-badge)](https://bstats.org/plugin/bukkit/HungerGames%20-%20Ayman/21512)
[![Discord](https://img.shields.io/discord/1183793646194139229?&logo=discord&label=chat&style=for-the-badge)](https://discord.gg/qcRfPHnZtp)

## Hunger Games Plugin Features
- Robust announcement system with customizable messages
- Customizable arena allowing for all types of maps to work
- 4 different tiers of chests, each with customizable loot
- Fixed world border during the main game, replaced instantly by a small deathmatch border
- When `game-time` expires, survivors return to their assigned starting platforms for a deathmatch. The border centers on all platforms and changes instantly to `deathmatch.border-size` (30 blocks by default, enlarged if necessary to include every platform plus a 2-block margin). The border remains fixed in both phases. PvP is enabled after a configurable 5-second protected preparation, inventories and health are preserved, and the match ends only when one player/team remains. Automatic chest refills and supply drops stop during deathmatch.
- Automatic chest refills and supply drops at intervals
- Individual scoreboard with various information related to the game
- Allowing players to spectate after death and teleport to players alive
- Support for Solo, Duo, Trios and Versus modes with option to vote for game mode
- Integration with MySQL to store player stats persistently
- Integration with Placeholder API for individual player stats and top global stats
- Automatic and manual allocation of players to teams with teammate tracking
- Team chat option to make messages only be seen by teammates
- Customizable display of tips to assist players
- Support for multiple maps and easy switching between maps
- Reloading of plugin config files through a single command
- Customizable language support for individual players
- Support for breaking blocks in the arena and restoring them after the game
- Compass for tracking nearest enemies and teammates
- Ability to run custom commands in the beginning and end of games

## Installation

### Administración de arenas

La selección `/hg select` solo usa X/Z de las dos esquinas. `/hg create` abarca desde la altura mínima del mundo hasta el último bloque construible, sin calcular la altura de edificios. Las arenas guardadas también actualizan automáticamente su rango vertical cuando se cargan.

- `/hg config mapahg resumen` muestra duración, bordes, protección y restauración.
- `/hg config mapahg validar` revisa lobby, spawns seguros, borde y cofres existentes.
- `/hg config mapahg restaurar` restaura manualmente una arena vacía desde la plantilla.
- `/hg config mapahg tiempo 600` cambia la duración en segundos.
- `/hg config mapahg borde 44` cambia el lado del borde final; rechaza tamaños que dejan spawns afuera.
- `/hg config mapahg proteccion 5` configura la preparación de la pelea final (0–30 segundos).
- `/hg loot mapahg normal` muestra el botín; `especial` corresponde a Ender y `drop` a suministros.
- `/hg loot mapahg normal IRON_INGOT 1 2 1 30` fija una probabilidad independiente del 30%; sin el último argumento usa peso relativo.
- `/hg scanarena` convierte los cofres de Ender en cofres normales, conserva su ubicación y orientación y los registra para el botín común. Después se debe ejecutar `/hg saveworld` para actualizar la plantilla. El escaneo no se permite durante una partida o restauración.
- `/hg saveworld` guarda una plantilla fuera de la partida; `reset-world: true` restaura bloques, cofres y entidades entre partidas. No se puede entrar mientras se guarda/restaura. Se conserva una copia de la partida anterior por arena.

Las modificaciones de configuración y botín requieren `hungergames.config` (operadores por defecto) y una arena sin partida. Al desconectarse o salir del mundo se abandona la partida; al regresar se va al lobby sin recuperar participación ni equipo. Si faltan jugadores durante la cuenta regresiva, se cancela.

Los cofres dobles normales y trampa se registran como un único cofre. El relleno también elimina duplicados de registros antiguos que contienen ambas mitades: conserva el inventario compartido de 54 espacios, la cantidad de botín configurada y una sola tirada de probabilidad por cofre.

La pelea final avisa a 60/30/10 segundos, teletransporta a las plataformas y bloquea movimiento y daño durante la preparación. El menú `/hg spectate` permite seguir a supervivientes de la misma arena y bloquea manipulación de inventarios. Los cofres normales aseguran arma y comida del propio botín si ambas categorías existen (`ensure-weapon-and-food`, predeterminado true); el balance fino debe verificarse jugando.

Winners celebrate in the arena for 10 seconds with fireworks before everyone returns to the lobby. With `spectating: true`, eliminated players spectate through the match and celebration. Players cannot take damage during the celebration.

Normal chests include more swords/axes, wood, sticks and cobblestone. An item entry with `chance: 30` rolls independently once per container instead of using relative `weight`; normal chest iron is a 30% bonus stack of 1–2 ingots. Supply drops guarantee 1–2 diamonds and 4–8 experience bottles. Random amount limits are inclusive. Right-click a held crafting table to open crafting during a match, including in adventure mode.
The latest releases of HungerGames are published to [Modrinth](https://modrinth.com/plugin/hungergames)

## Join the Community
There is an official server for this plugin on [Discord](https://discord.gg/qcRfPHnZtp). You can also use [GitHub Issues](https://github.com/Ayman-Isam/Hunger-Games/issues) to discuss the plugin.

## Wiki
There is a full [Wiki](https://hungergames.aymanisam.me/docs/introduction) for this plugin, powered by Docusaurus. 

## Custom Version
If you're interested in a custom version of this plugin tailored to your specific needs, join the discord server and reach out to me or create a GitHub issue.

Los cofres normales tienen tiradas independientes por cofre: manzanas 35% (1–2), lingotes de oro 30% (2–4), diamantes 3% (1–2) y libro encantado 3% (1). El libro recibe al azar filo, protección, poder o eficiencia de nivel I. Los cofres dobles hacen una sola tirada por inventario compartido. Se necesitan ocho lingotes para craftear una manzana dorada.

Los suministros buscan una superficie sólida con dos bloques de aire encima, ignorando cristal normal, teñido y oscuro, además de hojas y contenedores. Esto permite colocarlos debajo de una cúpula de cristal sin modificarla. Si una columna no ofrece espacio, se prueba otra; si no hay ubicación válida, se omite el suministro.

En los mundos con una arena HG configurada se desactiva doMobSpawning y se cancela la aparición de mobs por cualquier causa, incluidos spawners, huevos y comandos. Los mobs existentes se eliminan al iniciar el plugin, cargar/restaurar el mundo o cargar las entidades de un chunk. La protección también se activa al crear una arena nueva. Se conservan jugadores, soportes de armadura de suministros, objetos, vehículos y fuegos artificiales; otros mundos no se modifican.

En mapas con cúpula, los suministros requieren cristal encima de su ubicación: las esquinas del borde cuadrado que quedan fuera de la cúpula se descartan. La detección automática revisa la columna central del borde y cuatro puntos interiores; no cambia los mapas abiertos. Para fijar explícitamente este comportamiento en una arena, se puede agregar `require-glass-roof: true` dentro de `supplydrop` en su config.yml; `false` desactiva el requisito. La búsqueda de una ubicación alternativa y la reutilización de la anterior respetan el mismo requisito.

Los supply drops ahora requieren estrictamente un bloque de pasto (GRASS_BLOCK) como base. No aceptan piedra, madera, ladrillos, tierra ni otros materiales de techos. Mantienen el requisito de cúpula cuando corresponde y dos bloques de aire para el portal y el contenedor. Se prueban hasta 128 columnas; si no se encuentra pasto válido, se omite ese suministro en lugar de usar otro material. El pasto existente se conserva.

El contador de participantes excluye el modo espectador. Survival, creativo y aventura siguen contando cuando el jugador está inscrito en la arena (aventura es el modo habitual del HG protegido). Al pasar a espectador, se elimina al jugador de los supervivientes y del equipo activo; si era el último miembro, el equipo queda eliminado. El cambio se aplica también a los carteles, al mínimo de jugadores y al inicio automático. Si queda un solo jugador/equipo activo, se resuelve el ganador en la actualización siguiente. Cambiar otra vez de modo no reincorpora a un eliminado a la misma partida.

En una partida activa, al morir un participante se suelta todo su inventario (incluidas armadura y mano secundaria), aun si keepInventory está activado. Se usan las caídas normales del evento de muerte, sin generar copias adicionales; el jugador reaparece como espectador con el inventario consumido por la muerte.

Cada participante vivo ve una flecha direccional y la distancia horizontal al último supply drop sobre la barra de objetos, sin mods ni paquete de recursos. Se actualiza cada medio segundo al girar o moverse y cambia de objetivo inmediatamente al generarse un suministro nuevo. También indica si está más arriba o más abajo. Se oculta al vaciar/destruir el último drop, al morir/salir, durante la pelea final y al terminar la partida. Los drops anteriores permanecen disponibles, pero el indicador sigue únicamente al más nuevo.

El indicador del supply drop se movió de la barra de acción a una barra amarilla propia en la parte superior de la pantalla, separada de los consejos. Usa tres flechas en negrita para destacar la dirección y conserva la distancia y altura relativa al último suministro. Cada jugador tiene una sola barra que se actualiza sin acumular duplicados; se retira al vaciarse el drop, dejar de participar o terminar la partida. El tamaño de fuente individual no se puede escalar en la interfaz estándar sin un paquete de recursos.

El sonido de muerte del HG ahora es una campanada corta (BLOCK_NOTE_BLOCK_BELL), volumen 0.25 y tono 0.8, en lugar del rugido de muerte del Wither.

El temporizador de supply drops del panel lateral ahora muestra tambien una flecha y la distancia al ultimo suministro disponible. El tiempo corresponde al proximo drop y la flecha al ultimo que aparecio; la direccion cambia al girar y al generarse uno nuevo. La barra superior sigue disponible.


Los suministros ahora solo aceptan pasto con una línea vertical de aire libre hasta el cristal de la cúpula. Cualquier bloque intermedio (techo, piedra, tierra, hojas, agua, etc.) descarta la ubicación, aunque haya dos bloques libres justo encima del pasto. En mapas abiertos se exige aire libre hasta el cielo. Esto impide usar pasto dentro de cuevas o bajo casas. Se conserva el mínimo de dos bloques de aire para no romper el cristal al colocar el drop.
