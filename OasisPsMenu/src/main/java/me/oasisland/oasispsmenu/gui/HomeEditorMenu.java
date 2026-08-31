package me.oasisland.oasispsmenu.gui;

import com.sk89q.worldedit.math.BlockVector3;
import dev.espi.protectionstones.PSRegion;
import me.oasisland.oasispsmenu.OasisPSMenu;
import me.oasisland.oasispsmenu.util.ItemBuilder;
import me.oasisland.oasispsmenu.util.PSLookup;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HomeEditorMenu ("Editor")
 * ==========================
 * Menú de gestión de UNA protección puntual. Se abre al hacer click
 * izquierdo en "Protección actual" (Selector) o click derecho sobre una
 * protección en "Mis Protecciones" (HomeMenu).
 *
 * Layout confirmado frame a frame contra el video real, más el ajuste
 * del 27/08 (tamaño 45, título con nombre de la protección, botón de
 * "Colocar Home" y relleno de cristales):
 *
 *   Fila 2 (slots 9-17):
 *     10 → material real del bloque protector : "Protección Actual" (info, no clickeable)
 *     12 → BARRIER                             : "Lista de Baneados"
 *     13 → RED_BANNER                          : "Editar Flags"
 *     14 → PITCHER_POD                         : "Prioridad Protección"
 *     15 → LIME_DYE                            : "Ocultar/Mostrar Protección"
 *     16 → NAME_TAG                            : "Renombrar Protección"
 *
 *   Fila 3 (slots 18-26):
 *     19 → ENDER_PEARL                         : "Teletransporte"
 *     21 → TNT       (agregado, no en el video): "Remover protección"
 *     22 → DARK_OAK_DOOR                       : "Colocar Home" (equivale a /ps sethome)
 *     23 → TEST_BLOCK                           : "Ver límites"
 *     24 → BRUSH                               : "Lista de Propietarios"
 *     25 → SPYGLASS                            : "Lista de Miembros"
 *
 *   Fila 5 (slots 36-44):
 *     39 → ARROW                               : "Regresar" (vuelve al Selector)
 *     41 → WRITABLE_BOOK                        : "Ver todas tus protecciones" (abre "Mis Protecciones")
 *
 * Resto de los slots (ver FILLER_SLOTS): BLACK_STAINED_GLASS_PANE de relleno.
 *
 * Removidos el 27/08 a pedido de Pablo:
 *   - "Salir de la protección" (RED_BED, slot 20) → el slot ahora es
 *     relleno. El comando /ps leave sigue existiendo igual, solo se
 *     sacó el botón del menú.
 *
 * Cambiado el 27/08 (2): "Eliminar protección" (que corría /ps remove,
 * borrado permanente) pasó a llamarse "Remover protección" y ahora usa
 * /ps unclaim (le devuelve el bloque al inventario). Si no hay espacio
 * en el inventario, no se ejecuta nada hasta que el jugador libere un
 * slot — ver MenuClickListener.
 *
 * Cambiado el 27/08 (3): "Remover protección" ahora abre primero un
 * submenú de confirmación (RemoveConfirmMenu) en vez de ejecutar el
 * comando directo. Además, el comando pasó de teletransportar al
 * jugador + "/ps unclaim" a usar la variante remota
 * "/ps unclaim <id>" (sin mover al jugador de donde está) — ver
 * MenuClickListener.handleRemoveConfirmClick.
 *
 * Cambiado el 29/08 (4): dos ajustes a pedido de Pablo, tras ver el bug
 * de la imagen (el bloque real y el BlockDisplay se superponían y
 * competían visualmente — "z-fighting"):
 *
 *   - FIX DEL BUG (versión 1): en Minecraft vanilla, el brillo con
 *     color ("glowing") solo existe para ENTIDADES — un bloque de
 *     verdad no puede brillar así, no importa el plugin. Por eso
 *     seguimos necesitando un BlockDisplay para lograrlo. En esta
 *     primera vuelta, se ocultaba el bloque real con un paquete a AIRE
 *     solo para ese jugador y se ponía el BlockDisplay en su lugar —
 *     pero esto generaba colisión fantasma (el jugador podía atravesar
 *     el bloque caminando, porque el BlockDisplay no tiene colisión
 *     ninguna). Reemplazado en el siguiente ajuste (29/08 (5)) — ver
 *     ahí la versión definitiva.
 *   - Patrón de partículas nuevo: en vez del cuadrado por el perímetro,
 *     ahora salen 4 líneas rectas desde el cubo protector hacia
 *     adelante/atrás/izquierda/derecha, hasta el límite de la
 *     protección en cada dirección (ver drawCross()).
 *   - Color: sigue siendo por tier de protección (getTierColor()) —
 *     rojo para 16x16, mismo mecanismo que ya se armó para el
 *     degradado de las partículas.
 *
 * Cambiado el 29/08 (5), DEFINITIVO sobre la colisión: se sacó por
 * completo la idea de "apagar" el bloque real. El bloque real ahora
 * NUNCA se toca — sigue sólido y con colisión normal todo el tiempo,
 * cero riesgo de desync. Para evitar el z-fighting SIN esconder el
 * bloque, el BlockDisplay se dibuja apenas más grande (2%) que el
 * bloque real y centrado sobre él — así sus caras no compiten en el
 * mismo plano exacto (truco estándar para resaltar un bloque sin
 * "clonarlo" exactamente encima). Además: las partículas de la cruz
 * ahora salen del CENTRO del bloque (antes salían de arriba), se
 * sumaron partículas en el centro de la cara de arriba y de abajo, y
 * volvió el perímetro completo (las 4 paredes de la protección),
 * ahora en simultáneo con la cruz — ver drawCross(), drawTopBottom() y
 * drawPerimeter().
 */
public class HomeEditorMenu {

    private static final int MENU_SIZE = 45;

    // "Ver límites": máximo 60 segundos, redibuja cada 0.5s.
    private static final long VIEW_MAX_TICKS = 1200L;
    private static final long VIEW_REDRAW_INTERVAL = 10L;

    // Una sesión de "Ver límites" activa por jugador — permite cancelarla
    // si vuelve a tocar la opción (toggle) y limpiar todo si se desconecta.
    private static final Map<UUID, ViewSession> ACTIVE_VIEWS = new ConcurrentHashMap<>();

    private static class ViewSession {
        final BukkitTask task;
        // Arrays de 1 elemento en vez de campos sueltos: la entidad de
        // brillo se puede crear/destruir a mitad de sesión (si la
        // protección se oculta o se muestra mientras "Ver límites" ya
        // está corriendo) — con un array, tanto el ciclo de redibujado
        // como el toggle/desconexión ven siempre la referencia actual,
        // no una copia vieja que ya no existe.
        //
        // Entity (genérico) en vez de BlockDisplay: ahora la entidad de
        // brillo puede ser un BlockDisplay (protección visible, con la
        // textura real) O un Shulker invisible (protección oculta, solo
        // el contorno) — ver spawnGlowingDisplay() y spawnGlowingShulker().
        final Entity[] entityHolder;
        final Team[] teamHolder;
        // Nuevo (este ajuste): si está oculta ahora mismo, en un array
        // para poder actualizarlo tanto desde el ciclo de redibujado como
        // desde syncVisibility() (el cambio instantáneo al tocar
        // "Ocultar/Mostrar Protección") — ambos tienen que ver y escribir
        // el MISMO valor, si no, uno podría pisar al otro.
        final boolean[] hiddenHolder;
        final Location blockLoc;
        final Material expectedMaterial;
        final World world;
        final ChatColor tierColor;

        ViewSession(BukkitTask task, Entity[] entityHolder, Team[] teamHolder, boolean[] hiddenHolder,
                    Location blockLoc, Material expectedMaterial, World world, ChatColor tierColor) {
            this.task = task;
            this.entityHolder = entityHolder;
            this.teamHolder = teamHolder;
            this.hiddenHolder = hiddenHolder;
            this.blockLoc = blockLoc;
            this.expectedMaterial = expectedMaterial;
            this.world = world;
            this.tierColor = tierColor;
        }
    }

    // Slots que se rellenan con BLACK_STAINED_GLASS_PANE (puramente decorativo).
    private static final int[] FILLER_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 17, 18, 20, 26, 27, 28, 29, 30,
            31, 32, 33, 34, 35, 36, 37, 38, 40, 42, 43, 44
    };

    public static void open(OasisPSMenu plugin, Player player, PSRegion region) {
        Material matReal = PSLookup.getRegionMaterial(region);
        int size = PSLookup.getProtectionSize(matReal);
        String tamano = size > 0 ? size + "x" + size : "Desconocido";
        String nombre = region.getName() != null && !region.getName().isBlank()
                ? region.getName()
                : region.getWGRegion().getId();

        // Punto 1: título con el nombre real de la protección.
        String title = ChatColor.translateAlternateColorCodes('&', "&8• Editor (" + nombre + ")");

        MenuHolder holder = new MenuHolder(MenuHolder.MenuType.HOME_EDITOR, region.getWGRegion().getId(), region.getWorld().getName(), 0);
        Inventory inv = plugin.getServer().createInventory(holder, MENU_SIZE, title); // Punto 2: tamaño 45
        holder.setInventory(inv);

        // Punto 3: relleno decorativo en los slots indicados.
        ItemBuilder fillerPane = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ");
        for (int s : FILLER_SLOTS) inv.setItem(s, fillerPane.build());

        boolean glow = matReal == Material.DIAMOND_ORE
                || matReal == Material.CRYING_OBSIDIAN
                || matReal == Material.DEEPSLATE_DIAMOND_ORE;

        // ── Slot 10: Protección Actual (info, no clickeable) — Punto 4: ya estaba bien ─
        ItemBuilder actual = new ItemBuilder(matReal)
                .name("&aProtección Actual")
                .lore(
                        "&8⌜Información⌟",
                        "",
                        " &8• &7Protección: &f" + tamano,
                        " &8• &7Nombre: &f" + nombre,
                        " &8• &7ID: &f" + region.getWGRegion().getId(),
                        " &8• &7Prioridad: &f" + region.getWGRegion().getPriority()
                );
        if (glow) actual.glowing();
        inv.setItem(10, actual.build());

        // ── Slot 12: Lista de Baneados ─────────────────────────────────
        inv.setItem(12, new ItemBuilder(Material.BARRIER)
                .name("&cLista de Baneados")
                .lore("&7Podrás abrir un menú con la",
                      "&7lista de jugadores baneados.",
                      "",
                      "&e¡Ver la lista de baneados!").build());

        // ── Slot 13: Editar Flags ───────────────────────────────────────
        inv.setItem(13, new ItemBuilder(Material.RED_BANNER)
                .name("&aEditar Flags")
                .lore("&7Aquí podrás editar las Flags",
                      "&7de tu protección.",
                      "",
                      "&e¡Edite sus flags de protección!").build());

        // ── Slot 14: Prioridad Protección ───────────────────────────────
        inv.setItem(14, new ItemBuilder(Material.PITCHER_POD)
                .name("&aPrioridad Protección")
                .lore("&7Aquí podrás cambiar la",
                      "&7prioridad de tu protección.",
                      "",
                      "&e¡Cambiar la prioridad de su protección!",
                      "&7Actual: &f" + region.getWGRegion().getPriority()).build());

        // ── Slot 15: Ocultar / Mostrar Protección ───────────────────────
        boolean hidden = region.isHidden();
        inv.setItem(15, new ItemBuilder(Material.LIME_DYE)
                .name(hidden ? "&aMostrar Protección" : "&aOcultar Protección")
                .lore("&7Aquí podrás " + (hidden ? "mostrar" : "ocultar") + " tu",
                      "&7bloque de protección.",
                      "",
                      "&e¡" + (hidden ? "Mostrar" : "Ocultar") + " bloque de protección!").build());

        // ── Slot 16: Renombrar Protección ───────────────────────────────
        inv.setItem(16, new ItemBuilder(Material.NAME_TAG)
                .name("&aRenombrar Protección")
                .lore("&7Aquí podrás cambiar el nombre",
                      "&7de tu protección. (No usar Colores)",
                      "",
                      "&e¡Cambiar el nombre de su protección!").build());

        // ── Slot 19: Teletransporte — Punto 5: ya estaba bien ubicado ───
        inv.setItem(19, new ItemBuilder(Material.ENDER_PEARL)
                .name("&aTeletransporte")
                .lore("&7Podrá transportarse a su",
                      "&7protección desde cualquier lugar.",
                      "",
                      "&e¡Teletranspórtate a tu protección!").build());

        // ── Slot 21: Remover protección (mantenido, no en el video) ─────
        inv.setItem(21, new ItemBuilder(Material.TNT)
                .name("&4Remover protección")
                .lore("&7Quita el bloque de protección y",
                      "&7te lo devuelve a tu inventario.",
                      "",
                      "&7Te va a pedir confirmación antes",
                      "&7de hacerlo.").build());

        // ── Slot 22: Colocar Home (Punto 6, nuevo — equivale a /ps sethome) ─
        inv.setItem(22, new ItemBuilder(Material.DARK_OAK_DOOR)
                .name("&aColocar Home")
                .lore("&7Establece el home de tu bloque de",
                      "&7protección en tu ubicación actual.",
                      "",
                      "&e¡Establece tu sethome de protección!").build());

        // ── Slot 23: Ver límites (Punto 3, 28/08) ────────────────────────
        // Confirmado: el servidor de Pablo corre 1.21.11, así que
        // TEST_BLOCK (agregado en el juego recién en la 1.21.5) ya existe
        // en la API contra la que compilamos (ver pom.xml).
        inv.setItem(23, new ItemBuilder(Material.TEST_BLOCK)
                .name("&aVer límites")
                .lore("&7Revisa los límites de tu protección",
                      "",
                      "&e¡Podrás ver los límites de tu protección!").build());

        // ── Slot 24: Lista de Propietarios ──────────────────────────────
        inv.setItem(24, new ItemBuilder(Material.BRUSH)
                .name("&aLista de Propietarios")
                .lore("&7Aquí puedes gestionar los propietarios",
                      "&7de tu protección.",
                      "",
                      "&e¡Ver la lista de propietarios!").build());

        // ── Slot 25: Lista de Miembros ───────────────────────────────────
        inv.setItem(25, new ItemBuilder(Material.SPYGLASS)
                .name("&aLista de Miembros")
                .lore("&7Aquí puedes gestionar los miembros",
                      "&7de tu protección.",
                      "",
                      "&e¡Ver la lista de miembros!").build());

        // ── Slot 39: Regresar ────────────────────────────────────────────
        inv.setItem(39, new ItemBuilder(Material.ARROW)
                .name("&cRegresar").build());

        // ── Slot 41: Ver todas tus protecciones (Punto 3, 27/08) ─────────
        inv.setItem(41, new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&aVer todas tus protecciones")
                .lore("&7Muestra la lista de todas las",
                      "&7protecciones que tenés colocadas.").build());

        player.openInventory(inv);
    }

    /**
     * "Ver límites" (29/08, versión final — sistema propio, privado, y
     * SIN el bug de superposición). Al tocar la opción:
     *
     *   1) Si YA había una sesión activa para este jugador, la corta ahí
     *      mismo (toggle: tocar de nuevo = apagar).
     *   2) Si no, arranca una nueva: dibuja 4 líneas de partículas desde
     *      el cubo protector hacia el límite en cada dirección
     *      (adelante/atrás/izquierda/derecha) Y le pone al cubo un
     *      brillo de color — ambas cosas SOLO visibles para el jugador
     *      que activó la opción.
     *
     * La sesión se corta sola por cualquiera de estas 3 razones:
     *   - Pasan 60 segundos.
     *   - El jugador sale del área de la protección.
     *   - El jugador vuelve a tocar "Ver límites" (toggle-off, punto 1).
     *
     * OPTIMIZACIÓN: el paso entre partículas de las líneas crece con el
     * tamaño de la protección (acotado a ~64 partículas por línea), para
     * que una protección de 250x250 no tire miles de partículas por
     * refresco.
     */
    public static void showBorders(OasisPSMenu plugin, Player player, PSRegion region) {
        UUID uuid = player.getUniqueId();

        // Toggle: si ya había una sesión activa, la cortamos y listo.
        ViewSession existing = ACTIVE_VIEWS.remove(uuid);
        if (existing != null) {
            stopSession(plugin, existing);
            player.sendMessage(ChatColor.YELLOW + "Dejaste de ver los límites de tu protección.");
            return;
        }

        BlockVector3 min = region.getWGRegion().getMinimumPoint();
        BlockVector3 max = region.getWGRegion().getMaximumPoint();
        World world = region.getWorld();
        int width = max.x() - min.x();
        int step = Math.max(1, width / 64);

        Material matReal = PSLookup.getRegionMaterial(region);
        Particle.DustTransition gradient = getBorderGradient(matReal);
        ChatColor tierColor = getTierColor(matReal);

        // ── Brillo del cubo protector ────────────────────────────────────
        Location blockLoc = PSLookup.getProtectionBlockLocation(region);
        Entity glowEntity = null;
        Team team = null;

        // Declarados acá afuera (y no adentro del "if" de abajo) para que
        // sigan existiendo más abajo, donde el BukkitRunnable los necesita
        // para poder re-chequear si la protección se ocultó/mostró DESPUÉS
        // de que arrancó la sesión de "Ver límites".
        Material expectedMaterial = matReal;
        boolean hidden = false;

        if (blockLoc != null) {
            expectedMaterial = PSLookup.getRegionMaterial(region);
            Material actualMaterial = blockLoc.getBlock().getType();
            // No confiamos solo en region.isHidden() (confirmado en video que
            // ese dato de ProtectionStones no siempre coincide con la
            // realidad) — también comparamos el bloque FÍSICO real.
            hidden = region.isHidden() || actualMaterial != expectedMaterial;

            if (hidden) {
                // Requerimiento técnico (29/08, este ajuste — reemplaza el
                // intento anterior con vidrio blanco): confirmado con el
                // video del server de referencia que la técnica real es un
                // Shulker invisible con Glowing — cuando un Shulker está
                // invisible pero tiene Glowing activo, Minecraft solo
                // dibuja las líneas blancas de su caja exterior (su
                // hitbox) y el interior queda 100% transparente, sin
                // ninguna cara sólida — exactamente el efecto "contorno
                // tipo hitbox" que se ve en el video. Ver
                // spawnGlowingShulker() para el detalle de cada ajuste.
                glowEntity = spawnGlowingShulker(plugin, player, uuid, world, blockLoc, tierColor);
                team = getOrCreateGlowTeam(plugin, tierColor);
            } else {
                glowEntity = spawnGlowingDisplay(plugin, player, uuid, world, blockLoc, blockLoc.getBlock().getBlockData(), tierColor);
                team = getOrCreateGlowTeam(plugin, tierColor);
            }
        }

        player.sendMessage(ChatColor.GREEN + "Mostrando los límites de tu protección — se corta solo a los 60s, si salís del área, o si volvés a tocar esta opción.");

        Entity[] entityHolder = { glowEntity }; // mutable, para poder reemplazarlo si cambia el estado oculto/visible a mitad de sesión
        Team[] teamHolder = { team };
        boolean[] hiddenHolder = { hidden }; // compartido con syncVisibility() — ver comentario en ViewSession
        Location finalBlockLoc = blockLoc;
        Material finalExpectedMaterial = expectedMaterial;
        World finalWorld = world;
        ChatColor finalTierColor = tierColor;

        BukkitTask task = new BukkitRunnable() {
            long elapsed = 0L;

            @Override
            public void run() {
                if (!player.isOnline() || elapsed >= VIEW_MAX_TICKS) {
                    endSession(plugin, uuid, entityHolder[0], teamHolder[0]);
                    cancel();
                    return;
                }

                Location loc = player.getLocation();
                BlockVector3 pos = BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                if (!region.getWGRegion().contains(pos)) {
                    endSession(plugin, uuid, entityHolder[0], teamHolder[0]);
                    player.sendMessage(ChatColor.YELLOW + "Saliste del área — dejaste de ver los límites.");
                    cancel();
                    return;
                }

                // Red de seguridad (este ajuste): el cambio INSTANTÁNEO al
                // tocar "Ocultar/Mostrar Protección" ahora pasa por
                // syncVisibility(), llamado directo desde el botón del menú
                // — ver MenuClickListener. Este chequeo de acá solo cubre
                // el caso de que el estado cambie por otra vía (otro
                // plugin, /ps hide directo por consola, etc.) sin pasar
                // por nuestro botón.
                if (finalBlockLoc != null) {
                    Material actual = finalBlockLoc.getBlock().getType();
                    boolean nowHidden = region.isHidden() || actual != finalExpectedMaterial;
                    if (nowHidden != hiddenHolder[0]) {
                        swapGlowEntity(plugin, player, uuid, entityHolder, teamHolder, hiddenHolder,
                                nowHidden, finalWorld, finalBlockLoc, finalTierColor);
                    }
                }

                if (finalBlockLoc != null) {
                    // Punto 1, 2 y 3 (29/08, ajuste con el video de
                    // referencia): cruz + esquinas + perímetro, TODO a
                    // la misma altura (la del bloque) para que se vean
                    // como una sola figura conectada — antes el
                    // perímetro iba a la altura del jugador (que cambia
                    // todo el tiempo caminando) y la cruz a la del
                    // bloque, por eso nunca calzaban entre sí.
                    double y = finalBlockLoc.getY() + 0.5;
                    drawCross(player, finalBlockLoc, min, max, step, y, gradient);
                    drawTopBottom(player, finalBlockLoc, gradient);
                    drawPerimeter(player, min, max, step, y, gradient);
                    drawCorners(player, min, max, y, gradient);
                    // Punto 3 (este ajuste): partícula extra en el centro
                    // exacto del cubo, solo cuando la protección está
                    // oculta — ayuda a ubicar el centro real del bloque
                    // mientras solo se ve el contorno transparente del
                    // Shulker, sin ninguna textura de referencia.
                    if (hiddenHolder[0]) {
                        drawHiddenCenterMarker(player, finalBlockLoc);
                    }
                    // Punto 1 (29/08, este ajuste): columnas verticales
                    // en las 4 esquinas, desde el límite de abajo hasta
                    // el de arriba de la protección (los límites reales
                    // que tiene guardados WorldGuard — para la mayoría
                    // de tus protecciones eso es de piso a cielo, ya que
                    // así vienen configuradas en tus block.toml con
                    // y_radius = -1).
                    drawCornerColumns(player, min, max, gradient);
                    // Nuevo (29/08, este ajuste): columna vertical en el
                    // centro del cubo protector, y una más en el punto
                    // medio de cada una de las 4 paredes (donde tocan
                    // las líneas de la cruz).
                    drawCenterColumn(player, finalBlockLoc, min, max, gradient);
                    drawWallColumns(player, finalBlockLoc, min, max, gradient);
                }
                elapsed += VIEW_REDRAW_INTERVAL;
            }
        }.runTaskTimer(plugin, 0L, VIEW_REDRAW_INTERVAL);

        ACTIVE_VIEWS.put(uuid, new ViewSession(task, entityHolder, teamHolder, hiddenHolder,
                finalBlockLoc, finalExpectedMaterial, finalWorld, finalTierColor));
    }

    /**
     * Color del brillo (equipo de scoreboard) por tier de protección.
     * Los equipos de Minecraft solo aceptan uno de los 16 ChatColor de
     * toda la vida (no un color RGB libre como el degradado de las
     * partículas) — por eso este método devuelve ChatColor, no Color.
     * Por ahora solo está definido el de 16x16 (rojo, el que pidió
     * Pablo) — el resto cae al mismo rojo hasta que se definan los demás.
     */
    private static ChatColor getTierColor(Material mat) {
        return ChatColor.RED;
    }

    /**
     * Degradado de color de las partículas por tamaño de protección. Por
     * ahora solo está definido el de 16x16 (rojo) — el resto cae al
     * mismo rojo por defecto hasta que se definan los demás.
     */
    private static Particle.DustTransition getBorderGradient(Material mat) {
        int size = PSLookup.getProtectionSize(mat);
        return switch (size) {
            case 16 -> new Particle.DustTransition(
                    Color.fromRGB(120, 0, 0),   // rojo oscuro
                    Color.fromRGB(255, 40, 40), // rojo brillante
                    1.1f);
            default -> new Particle.DustTransition(
                    Color.fromRGB(120, 0, 0),
                    Color.fromRGB(255, 40, 40),
                    1.1f);
        };
    }

    /**
     * Dibuja 4 líneas de partículas privadas desde el cubo protector
     * hacia el límite de la protección en cada dirección — norte, sur,
     * este y oeste — en vez del cuadrado por el perímetro que usábamos
     * antes.
     */
    /**
     * Crea el BlockDisplay que le da al cubo protector su brillo de color
     * (con la textura real del bloque) — usado tanto al abrir la sesión de
     * "Ver límites" (si la protección está visible) como cuando pasa de
     * oculta a visible A MITAD de una sesión ya activa.
     *
     * El bloque real NUNCA se toca — este BlockDisplay es una entidad
     * aparte, 2% más grande que el bloque real y centrada sobre él, para
     * que sus caras no compitan exactamente en el mismo plano (evita el
     * parpadeo/textura rota que se vio en un ajuste anterior). La
     * iluminación se fuerza al máximo (15/15) para que nunca salga negro
     * sea cual sea la luz real de esa zona. La entidad se oculta a todos
     * los jugadores conectados menos al que activó la opción.
     */
    /**
     * Busca (o crea) el equipo de scoreboard que le da a las entidades de
     * "Ver límites" su color de brillo — centralizado acá en vez de
     * repetir la misma lógica en cada lugar que crea/reemplaza una
     * entidad, para no tener 5 copias del mismo código.
     *
     * Punto 1 (este ajuste): además de `entity.setCollidable(false)` en
     * cada entidad individual, el EQUIPO también le dice explícitamente
     * al cliente "los miembros de este equipo nunca chocan con nada"
     * (`Team.Option.COLLISION_RULE` = `NEVER`). Con las dos cosas juntas
     * queda garantizado que el jugador atraviese la entidad sin sentir
     * ningún bloqueo físico — `setCollidable(false)` solo no alcanzaba.
     */
    private static Team getOrCreateGlowTeam(OasisPSMenu plugin, ChatColor tierColor) {
        Scoreboard board = plugin.getServer().getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam("psm_ver_limites");
        if (team == null) {
            team = board.registerNewTeam("psm_ver_limites");
        }
        team.setColor(tierColor);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        return team;
    }

    private static BlockDisplay spawnGlowingDisplay(OasisPSMenu plugin, Player player, UUID uuid, World world,
                                                      Location blockLoc, org.bukkit.block.data.BlockData blockData,
                                                      ChatColor tierColor) {
        BlockDisplay display = world.spawn(blockLoc, BlockDisplay.class, entity -> {
            entity.setBlock(blockData);
            entity.setGlowing(true);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
            entity.setGravity(false);
            entity.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            float scale = 1.02f;
            float offset = -(scale - 1f) / 2f; // recentra el cubo agrandado
            entity.setTransformation(new org.bukkit.util.Transformation(
                    new Vector3f(offset, offset, offset),
                    new AxisAngle4f(0f, 0f, 0f, 1f),
                    new Vector3f(scale, scale, scale),
                    new AxisAngle4f(0f, 0f, 0f, 1f)
            ));
        });

        Team team = getOrCreateGlowTeam(plugin, tierColor);
        team.addEntry(display.getUniqueId().toString());

        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.getUniqueId().equals(uuid)) {
                other.hideEntity(plugin, display);
            }
        }

        return display;
    }

    /**
     * Requerimiento técnico (29/08, este ajuste — reemplaza el intento con
     * vidrio blanco): entidad Shulker invisible con Glowing, para el caso
     * "protección oculta". Confirmado con el video del server de
     * referencia: cuando un Shulker está invisible (`setInvisible(true)`)
     * pero tiene Glowing activo, Minecraft solo dibuja las líneas blancas
     * de su caja exterior (su hitbox) — el interior queda 100%
     * transparente, sin ninguna cara sólida. Exactamente el efecto
     * "contorno tipo hitbox" pedido.
     *
     * Ajustes para que no interfiera para nada con el juego normal:
     *   - setAI(false): sin esto, el Shulker puede "abrirse" (asomar la
     *     cabeza) solo por su comportamiento normal al detectar un
     *     jugador cerca, lo que rompería el contorno prolijo de cubo
     *     cerrado. Sin IA, se queda siempre cerrado.
     *   - setCollidable(false) + team con COLLISION_RULE=NEVER (en
     *     getOrCreateGlowTeam): dos capas para la misma garantía — un
     *     Shulker de verdad tiene colisión sólida (te podés quedar
     *     trabado contra uno), y `setCollidable(false)` solo no alcanzaba
     *     para que el jugador lo atraviese sin sentir ningún bloqueo
     *     (confirmado en la prueba de Pablo). Con el equipo diciéndole
     *     explícitamente al cliente "nunca choques" queda 100% traspasable.
     *   - setInvulnerable(true) + setSilent(true): no se puede lastimar
     *     ni hace ningún sonido.
     *   - setPersistent(false): no sobrevive a un reinicio del server ni
     *     se guarda en el mundo — es 100% temporal, se limpia solo con
     *     remove() al terminar la sesión igual, pero esto es una
     *     protección extra.
     *
     * Punto 2 (este ajuste): antes se spawneaba directo en `blockLoc`
     * (las coordenadas de la ESQUINA del bloque, x.0/y.0/z.0) — eso
     * funciona bien para un BlockDisplay (que sí usa la esquina como
     * ancla, igual que un bloque real), pero un mob normal como el
     * Shulker interpreta esas coordenadas como su CENTRO. Resultado: el
     * Shulker quedaba corrido medio bloque en X y Z — no era un problema
     * de escala, era de posición. Ahora se calcula el centro real del
     * bloque (X+0.5, Z+0.5) antes de spawnearlo.
     */
    private static Shulker spawnGlowingShulker(OasisPSMenu plugin, Player player, UUID uuid, World world,
                                                 Location blockLoc, ChatColor tierColor) {
        Location centered = new Location(world,
                blockLoc.getBlockX() + 0.5,
                blockLoc.getBlockY(),
                blockLoc.getBlockZ() + 0.5);

        Shulker shulker = world.spawn(centered, Shulker.class, entity -> {
            entity.setInvisible(true);
            entity.setGlowing(true);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setAI(false);
            entity.setCollidable(false);
            entity.setPersistent(false);
            entity.setGravity(false);
        });

        Team team = getOrCreateGlowTeam(plugin, tierColor);
        team.addEntry(shulker.getUniqueId().toString());

        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.getUniqueId().equals(uuid)) {
                other.hideEntity(plugin, shulker);
            }
        }

        return shulker;
    }

    /**
     * Punto 1: la cruz sale del CENTRO del bloque (X+0.5, Y+0.5, Z+0.5).
     * Ahora recibe `y` como parámetro (en vez de calcularlo adentro) para
     * compartir exactamente la misma altura que el perímetro y las
     * esquinas — así se ven como una sola figura conectada.
     *
     * Además, cada línea SIEMPRE termina justo en el límite exacto
     * (min/max), sin importar si el "step" divide justo la distancia o
     * no — antes, si no daba justo, la línea podía quedarse corta y no
     * llegar al borde, dejando un huequito.
     */
    private static void drawCross(Player player, Location blockLoc, BlockVector3 min, BlockVector3 max,
                                   int step, double y, Particle.DustTransition gradient) {
        int bx = blockLoc.getBlockX();
        int bz = blockLoc.getBlockZ();

        int z = bz;
        while (z > min.z()) { // hacia el norte
            player.spawnParticle(Particle.DUST_COLOR_TRANSITION, bx + 0.5, y, z + 0.5, 1, 0, 0, 0, 0, gradient);
            z -= step;
        }
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, bx + 0.5, y, min.z() + 0.5, 1, 0, 0, 0, 0, gradient);

        z = bz;
        while (z < max.z()) { // hacia el sur
            player.spawnParticle(Particle.DUST_COLOR_TRANSITION, bx + 0.5, y, z + 0.5, 1, 0, 0, 0, 0, gradient);
            z += step;
        }
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, bx + 0.5, y, max.z() + 0.5, 1, 0, 0, 0, 0, gradient);

        int x = bx;
        while (x > min.x()) { // hacia el oeste
            player.spawnParticle(Particle.DUST_COLOR_TRANSITION, x + 0.5, y, bz + 0.5, 1, 0, 0, 0, 0, gradient);
            x -= step;
        }
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, min.x() + 0.5, y, bz + 0.5, 1, 0, 0, 0, 0, gradient);

        x = bx;
        while (x < max.x()) { // hacia el este
            player.spawnParticle(Particle.DUST_COLOR_TRANSITION, x + 0.5, y, bz + 0.5, 1, 0, 0, 0, 0, gradient);
            x += step;
        }
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, max.x() + 0.5, y, bz + 0.5, 1, 0, 0, 0, 0, gradient);
    }

    /**
     * Punto 2: un puñado de partículas justo en el centro de la cara de
     * arriba y de la cara de abajo del cubo protector, para completar
     * la cobertura vertical alrededor del bloque.
     */
    /**
     * Punto 3 (este ajuste): marca el centro exacto del cubo (X+0.5,
     * Y+0.5, Z+0.5) con partículas END_ROD — distintas del degradado rojo
     * que usa todo lo demás, para que se note claramente como un
     * "puntero" al centro, útil cuando el Shulker invisible no deja ver
     * ninguna textura de referencia. Privado, solo lo ve el jugador.
     */
    private static void drawHiddenCenterMarker(Player player, Location blockLoc) {
        player.spawnParticle(Particle.END_ROD,
                blockLoc.getBlockX() + 0.5, blockLoc.getBlockY() + 0.5, blockLoc.getBlockZ() + 0.5,
                3, 0.05, 0.05, 0.05, 0);
    }

    private static void drawTopBottom(Player player, Location blockLoc, Particle.DustTransition gradient) {
        double x = blockLoc.getBlockX() + 0.5;
        double z = blockLoc.getBlockZ() + 0.5;
        double top = blockLoc.getBlockY() + 1.0;
        double bottom = blockLoc.getBlockY();
        double center = blockLoc.getBlockY() + 0.5; // punto 2 (29/08, este ajuste): también el centro exacto del cubo

        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, top, z, 3, 0.05, 0.05, 0.05, 0, gradient);
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, bottom, z, 3, 0.05, 0.05, 0.05, 0, gradient);
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, center, z, 4, 0.08, 0.08, 0.08, 0, gradient);
    }

    /**
     * Punto 4 (ahora también punto 3): el perímetro completo de la
     * protección — las 4 paredes exteriores — a la MISMA altura `y` que
     * la cruz, para que se conecten como una sola figura. Igual que en
     * drawCross, cada pared SIEMPRE llega exacto al límite (min/max),
     * sin dejar huecos si el "step" no divide justo la distancia.
     */
    private static void drawPerimeter(Player player, BlockVector3 min, BlockVector3 max,
                                       int step, double y, Particle.DustTransition gradient) {
        int x = min.x();
        while (x < max.x()) {
            player.spawnParticle(Particle.DUST_COLOR_TRANSITION, x + 0.5, y, min.z() + 0.5, 1, 0, 0, 0, 0, gradient);
            player.spawnParticle(Particle.DUST_COLOR_TRANSITION, x + 0.5, y, max.z() + 0.5, 1, 0, 0, 0, 0, gradient);
            x += step;
        }
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, max.x() + 0.5, y, min.z() + 0.5, 1, 0, 0, 0, 0, gradient);
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, max.x() + 0.5, y, max.z() + 0.5, 1, 0, 0, 0, 0, gradient);

        int z = min.z();
        while (z < max.z()) {
            player.spawnParticle(Particle.DUST_COLOR_TRANSITION, min.x() + 0.5, y, z + 0.5, 1, 0, 0, 0, 0, gradient);
            player.spawnParticle(Particle.DUST_COLOR_TRANSITION, max.x() + 0.5, y, z + 0.5, 1, 0, 0, 0, 0, gradient);
            z += step;
        }
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, min.x() + 0.5, y, max.z() + 0.5, 1, 0, 0, 0, 0, gradient);
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, max.x() + 0.5, y, max.z() + 0.5, 1, 0, 0, 0, 0, gradient);
    }

    /**
     * Punto 1: un racimo de partículas bien visible en cada una de las
     * 4 esquinas de la protección, a la misma altura que el resto —
     * igual que se ve en el video de referencia que mandó Pablo.
     */
    private static void drawCorners(Player player, BlockVector3 min, BlockVector3 max, double y, Particle.DustTransition gradient) {
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, min.x() + 0.5, y, min.z() + 0.5, 4, 0.1, 0.1, 0.1, 0, gradient);
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, min.x() + 0.5, y, max.z() + 0.5, 4, 0.1, 0.1, 0.1, 0, gradient);
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, max.x() + 0.5, y, min.z() + 0.5, 4, 0.1, 0.1, 0.1, 0, gradient);
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, max.x() + 0.5, y, max.z() + 0.5, 4, 0.1, 0.1, 0.1, 0, gradient);
    }

    /**
     * Punto 1: en cada una de las 4 esquinas, una columna de partículas
     * de punta a punta — desde el límite de ABAJO hasta el límite de
     * ARRIBA que tiene guardados WorldGuard para esta protección
     * (min.y() / max.y()). Para la mayoría de tus protecciones eso es
     * de piso a cielo (y_radius = -1 en los block.toml), así que en la
     * práctica se ve como una columna que sigue de largo hacia arriba y
     * abajo — el efecto "infinito" que pediste.
     *
     * OPTIMIZACIÓN: igual que con las líneas horizontales, el paso
     * entre partículas de la columna crece con la altura total (acotado
     * a ~64 partículas por columna), para no tirar cientos de
     * partículas por refresco en mundos con mucha altura disponible.
     */
    /**
     * Punto 2 (29/08, este ajuste): las columnas de las esquinas casi no
     * se veían. La causa real: para una protección con "y_radius: -1"
     * (de piso a cielo, ~384 bloques en 1.21), la columna se repartía
     * en TODO ese rango con partículas bastante separadas — pero
     * Minecraft no dibuja partículas más allá de cierta distancia del
     * jugador (la "distancia de render de partículas" del cliente), así
     * que la enorme mayoría de esos puntos quedaban invisibles, muy
     * lejos arriba o abajo de donde estabas parado.
     *
     * Arreglado: en vez de repartir parejo en todo el alto de la
     * protección, ahora se concentra la densidad en una ventana de
     * ±32 bloques alrededor de tu altura ACTUAL (recortada a los
     * límites reales de la protección) — así siempre hay una columna
     * bien densa y visible cerca tuyo, sin importar qué tan alta sea
     * la protección en total.
     */
    /**
     * Punto compartido: dibuja UNA columna vertical de partículas en un
     * punto X,Z fijo — usado tanto para las esquinas como (nuevo, este
     * ajuste) para el centro del cubo y el punto medio de cada pared.
     * La densidad se concentra en una ventana de ±32 bloques alrededor
     * de tu altura actual (recortada a los límites reales de la
     * protección), para que siempre se vea bien cerca tuyo sin importar
     * qué tan alta sea la protección — ver comentario más detallado más
     * abajo en drawCornerColumns.
     */
    private static void drawVerticalColumn(Player player, double x, double z, int minY, int maxY, Particle.DustTransition gradient) {
        int playerY = player.getLocation().getBlockY();
        int windowMin = Math.max(minY, playerY - 32);
        int windowMax = Math.min(maxY, playerY + 32);
        int yStep = 2;

        int y = windowMin;
        while (y < windowMax) {
            player.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y, z, 1, 0, 0, 0, 0, gradient);
            y += yStep;
        }
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, windowMax, z, 1, 0, 0, 0, 0, gradient);
    }

    /**
     * Punto 2 (29/08, ajuste anterior): las columnas de las esquinas
     * casi no se veían. La causa real: para una protección con
     * "y_radius: -1" (de piso a cielo, ~384 bloques en 1.21), la
     * columna se repartía en TODO ese rango con partículas bastante
     * separadas — pero Minecraft no dibuja partículas más allá de
     * cierta distancia del jugador (la "distancia de render de
     * partículas" del cliente), así que la enorme mayoría de esos
     * puntos quedaban invisibles, muy lejos arriba o abajo de donde
     * estabas parado. Ahora usa drawVerticalColumn(), que concentra la
     * densidad cerca de tu altura actual.
     */
    private static void drawCornerColumns(Player player, BlockVector3 min, BlockVector3 max, Particle.DustTransition gradient) {
        drawVerticalColumn(player, min.x() + 0.5, min.z() + 0.5, min.y(), max.y(), gradient);
        drawVerticalColumn(player, min.x() + 0.5, max.z() + 0.5, min.y(), max.y(), gradient);
        drawVerticalColumn(player, max.x() + 0.5, min.z() + 0.5, min.y(), max.y(), gradient);
        drawVerticalColumn(player, max.x() + 0.5, max.z() + 0.5, min.y(), max.y(), gradient);
    }

    /**
     * Nuevo (29/08, este ajuste): columna vertical en el CENTRO exacto
     * del cubo protector (su propia X,Z) — de punta a punta de la
     * protección, igual que las de las esquinas.
     */
    private static void drawCenterColumn(Player player, Location blockLoc, BlockVector3 min, BlockVector3 max, Particle.DustTransition gradient) {
        drawVerticalColumn(player, blockLoc.getBlockX() + 0.5, blockLoc.getBlockZ() + 0.5, min.y(), max.y(), gradient);
    }

    /**
     * Nuevo (29/08, este ajuste): columna vertical en el punto medio de
     * cada una de las 4 paredes — exactamente donde las líneas de la
     * cruz (drawCross) tocan el perímetro, tal cual pediste.
     */
    private static void drawWallColumns(Player player, Location blockLoc, BlockVector3 min, BlockVector3 max, Particle.DustTransition gradient) {
        double bx = blockLoc.getBlockX() + 0.5;
        double bz = blockLoc.getBlockZ() + 0.5;

        drawVerticalColumn(player, bx, min.z() + 0.5, min.y(), max.y(), gradient); // pared norte
        drawVerticalColumn(player, bx, max.z() + 0.5, min.y(), max.y(), gradient); // pared sur
        drawVerticalColumn(player, min.x() + 0.5, bz, min.y(), max.y(), gradient); // pared oeste
        drawVerticalColumn(player, max.x() + 0.5, bz, min.y(), max.y(), gradient); // pared este
    }

    /**
     * Reemplaza la entidad de brillo actual por la que corresponda según
     * `nowHidden` — Shulker invisible si pasó a oculta, BlockDisplay con
     * la textura real si volvió a visible. Compartido entre el ciclo de
     * redibujado (como red de seguridad) y syncVisibility() (el cambio
     * instantáneo real, llamado directo desde el botón del menú).
     */
    private static void swapGlowEntity(OasisPSMenu plugin, Player player, UUID uuid,
                                        Entity[] entityHolder, Team[] teamHolder, boolean[] hiddenHolder,
                                        boolean nowHidden, World world, Location blockLoc, ChatColor tierColor) {
        hiddenHolder[0] = nowHidden;

        // Se saca la entidad anterior (si había) antes de poner la nueva.
        if (entityHolder[0] != null) {
            if (teamHolder[0] != null) teamHolder[0].removeEntry(entityHolder[0].getUniqueId().toString());
            entityHolder[0].remove();
            entityHolder[0] = null;
        }

        if (nowHidden) {
            entityHolder[0] = spawnGlowingShulker(plugin, player, uuid, world, blockLoc, tierColor);
        } else {
            entityHolder[0] = spawnGlowingDisplay(plugin, player, uuid, world, blockLoc, blockLoc.getBlock().getBlockData(), tierColor);
        }

        teamHolder[0] = getOrCreateGlowTeam(plugin, tierColor);
    }

    /**
     * Requerimiento técnico (este ajuste): antes, el cambio de entidad al
     * ocultar/mostrar la protección con "Ver límites" ya activo se
     * detectaba recién en el próximo ciclo del temporizador (cada 0.5s) —
     * de ahí la demora que se notaba. Ahora, MenuClickListener llama a
     * este método DIRECTO en el mismo instante en que se procesa el click
     * en "Ocultar/Mostrar Protección", así el cambio de entidad es
     * inmediato, en el mismo tick — sin esperar ningún ciclo del
     * scheduler. Si el jugador no tiene "Ver límites" activo en ese
     * momento, no hace nada (no hay ninguna entidad que actualizar).
     */
    public static void syncVisibility(OasisPSMenu plugin, Player player, PSRegion region) {
        ViewSession session = ACTIVE_VIEWS.get(player.getUniqueId());
        if (session == null || session.blockLoc == null) return;

        Material actual = session.blockLoc.getBlock().getType();
        boolean nowHidden = region.isHidden() || actual != session.expectedMaterial;

        if (nowHidden != session.hiddenHolder[0]) {
            swapGlowEntity(plugin, player, player.getUniqueId(), session.entityHolder, session.teamHolder,
                    session.hiddenHolder, nowHidden, session.world, session.blockLoc, session.tierColor);
        }
    }

    private static void endSession(OasisPSMenu plugin, UUID uuid, Entity entity, Team team) {
        ACTIVE_VIEWS.remove(uuid);
        if (entity != null) {
            if (team != null) team.removeEntry(entity.getUniqueId().toString());
            entity.remove();
        }
    }

    private static void stopSession(OasisPSMenu plugin, ViewSession session) {
        session.task.cancel();
        Entity entity = session.entityHolder[0];
        if (entity != null) {
            Team team = session.teamHolder[0];
            if (team != null) team.removeEntry(entity.getUniqueId().toString());
            entity.remove();
        }
    }

    /**
     * Corta cualquier sesión de "Ver límites" activa de este jugador sin
     * mandarle ningún mensaje — para usar cuando se desconecta, así no
     * queda un BlockDisplay fantasma dando vueltas en el mundo. (No hace
     * falta devolverle el bloque real: si se desconectó, ya no hay a
     * quién devolvérselo).
     */
    public static void forceStopViewSession(OasisPSMenu plugin, UUID uuid) {
        ViewSession session = ACTIVE_VIEWS.remove(uuid);
        if (session != null) {
            session.task.cancel();
            Entity entity = session.entityHolder[0];
            if (entity != null) {
                Team team = session.teamHolder[0];
                if (team != null) team.removeEntry(entity.getUniqueId().toString());
                entity.remove();
            }
        }
    }

    /**
     * Oculta todas las sesiones de "Ver límites" activas de OTROS
     * jugadores a este jugador recién conectado. Sin esto, alguien que
     * se conecta mientras otro jugador tiene "Ver límites" abierto vería
     * el brillo del bloque por defecto (los jugadores nuevos no heredan
     * el hideEntity que se aplicó al momento de activar la sesión).
     */
    public static void hideActiveViewsFrom(OasisPSMenu plugin, Player joining) {
        for (Map.Entry<UUID, ViewSession> entry : ACTIVE_VIEWS.entrySet()) {
            if (entry.getKey().equals(joining.getUniqueId())) continue;
            Entity entity = entry.getValue().entityHolder[0];
            if (entity != null) {
                joining.hideEntity(plugin, entity);
            }
        }
    }
}
