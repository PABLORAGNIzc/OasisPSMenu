package me.oasisland.oasispsmenu.listeners;

import dev.espi.protectionstones.ProtectionStones;
import me.oasisland.oasispsmenu.OasisPSMenu;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * RegionEventListener
 * -------------------
 * Captura la fecha de creación y el creador de cada protección nueva.
 *
 * PSCreateRegionEvent no existe en ProtectionStones 2.10.6, así que usamos
 * BlockPlaceEvent (evento estándar de Bukkit, siempre disponible).
 * Cuando el bloque colocado es un cubo protector de PS, registramos:
 *   - Timestamp de creación
 *   - UUID del jugador que lo colocó
 *
 * La región todavía no existe en WorldGuard en el momento del BlockPlaceEvent
 * (PS la crea en el siguiente tick), así que guardamos los datos del bloque
 * y los asociamos a la región usando su ID generado de forma predecible
 * por PS (que usa las coordenadas del bloque: "ps<x>x<y>x<z>").
 */
public class RegionEventListener implements Listener {

    private final OasisPSMenu plugin;

    public RegionEventListener(OasisPSMenu plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // Verificar si el bloque colocado es un cubo protector de PS
        if (!ProtectionStones.isProtectBlock(event.getBlockPlaced())) return;

        // Formato REAL del ID en ProtectionStones 2.x: ps<X>x<Y>y<Z>z
        
        int x = event.getBlockPlaced().getX();
        int y = event.getBlockPlaced().getY();
        int z = event.getBlockPlaced().getZ();
        String regionId = "ps" + x + "x" + y + "y" + z + "z"; // confirmado: ps-4x13y9z

        long now = System.currentTimeMillis();
        plugin.getDataStore().setCreationDate(regionId, now);
        plugin.getDataStore().setCreator(regionId, event.getPlayer().getUniqueId());
    }
}
