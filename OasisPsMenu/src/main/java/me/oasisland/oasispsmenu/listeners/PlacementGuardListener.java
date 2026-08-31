package me.oasisland.oasispsmenu.listeners;

import dev.espi.protectionstones.ProtectionStones;
import me.oasisland.oasispsmenu.OasisPSMenu;
import me.oasisland.oasispsmenu.util.PSLookup;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * PlacementGuardListener
 * -----------------------
 * Evita que un jugador coloque un cubo protector si hay OTRO jugador
 * parado dentro del área que esa protección va a cubrir. Esto evita
 * que alguien "robe" terreno protegiendo un área donde ya hay otro
 * jugador construyendo o parado.
 *
 * CÓMO FUNCIONA (y por qué a esta prioridad):
 * ProtectionStones no expone en esta versión un evento cancelable
 * "antes de crear la región" (lo mismo que ya notamos en
 * RegionEventListener), así que calculamos NOSOTROS el área que la
 * protección va a cubrir usando la misma tabla de tamaños que ya se
 * muestra en los menús (PSLookup.getProtectionSize) y revisamos si
 * hay jugadores ahí ANTES de que el bloque llegue a colocarse.
 *
 * Usamos prioridad HIGH: si cancelamos acá, el BlockPlaceEvent queda
 * cancelado y el bloque nunca se coloca — por lo tanto ProtectionStones
 * nunca ve una colocación "exitosa" y no crea la región. Es el mismo
 * principio que ya usa RegionEventListener (prioridad MONITOR +
 * ignoreCancelled=true) para registrar solamente las colocaciones que
 * sí se concretaron: como nuestro chequeo corre ANTES de MONITOR, si
 * cancelamos acá, RegionEventListener directamente ni se entera.
 *
 * SUPUESTO IMPORTANTE (avisame si no coincide con tu configuración):
 * Asumimos que las protecciones de ProtectionStones cubren TODA la
 * altura del mundo (de piso a cielo), que es el comportamiento por
 * defecto del plugin. Por eso el chequeo de abajo solo mira X/Z (el
 * "cuadrado" de la protección) y no filtra por altura del jugador.
 * Si en tu .toml configuraste una altura limitada para algún tipo de
 * bloque protector, decime y agrego el filtro de Y también.
 */
public class PlacementGuardListener implements Listener {

    private final OasisPSMenu plugin;

    public PlacementGuardListener(OasisPSMenu plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // Solo nos interesa si el bloque colocado es un cubo protector de PS.
        if (!ProtectionStones.isProtectBlock(event.getBlockPlaced())) return;

        Player placer = event.getPlayer();

        // Los admins (o quien tenga este permiso puntual) se saltan el chequeo.
        if (placer.hasPermission("oasispsmenu.placebypass")) return;

        int size = PSLookup.getProtectionSize(event.getBlockPlaced().getType());
        if (size <= 0) {
            // Tipo de bloque que no está en nuestra tabla: no podemos calcular
            // el área con seguridad, así que dejamos pasar la colocación en
            // vez de arriesgarnos a bloquear algo que no deberíamos.
            return;
        }

        int radius = size / 2;
        int centerX = event.getBlockPlaced().getX();
        int centerZ = event.getBlockPlaced().getZ();

        int minX = centerX - radius;
        int maxX = centerX + radius;
        int minZ = centerZ - radius;
        int maxZ = centerZ + radius;

        for (Player other : event.getBlockPlaced().getWorld().getPlayers()) {
            if (other.getUniqueId().equals(placer.getUniqueId())) continue;
            // A un jugador en modo espectador no lo contamos: no está "parado" ahí de verdad.
            if (other.getGameMode() == GameMode.SPECTATOR) continue;

            int px = other.getLocation().getBlockX();
            int pz = other.getLocation().getBlockZ();

            if (px >= minX && px <= maxX && pz >= minZ && pz <= maxZ) {
                event.setCancelled(true);
                placer.sendMessage(ChatColor.RED + "No podés colocar una protección acá: "
                        + ChatColor.YELLOW + other.getName() + ChatColor.RED
                        + " está dentro del área que esta protección cubriría (" + size + "x" + size + ").");
                return;
            }
        }
    }
}
