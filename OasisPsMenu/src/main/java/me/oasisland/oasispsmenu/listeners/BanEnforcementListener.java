package me.oasisland.oasispsmenu.listeners;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.ProtectionStones;
import me.oasisland.oasispsmenu.OasisPSMenu;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * BanEnforcementListener
 * -----------------------
 * Cada vez que un jugador se mueve (cambia de bloque) o se teletransporta,
 * revisamos si esta entrando a una region de ProtectionStones donde esta
 * banneado. Si es asi, lo regresamos a su ubicacion anterior.
 *
 * Para no recalcular esto en cada micro-movimiento (lag), solo revisamos
 * cuando cambia de BLOQUE, no de sub-posicion (mirar para los costados no
 * dispara el chequeo).
 */
public class BanEnforcementListener implements Listener {

    private final OasisPSMenu plugin;

    public BanEnforcementListener(OasisPSMenu plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return; // no cambio de bloque, no hace falta chequear
        }
        checkAndBlock(event.getPlayer(), event.getTo(), event);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        checkAndBlock(event.getPlayer(), event.getTo(), event);
    }

    private void checkAndBlock(Player player, Location to, org.bukkit.event.Cancellable event) {
        if (to == null || to.getWorld() == null) return;

        RegionManager rm = WorldGuard.getInstance().getPlatform()
                .getRegionContainer()
                .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(to.getWorld()));
        if (rm == null) return;

        var vector = com.sk89q.worldedit.bukkit.BukkitAdapter.asBlockVector(to);

        for (ProtectedRegion region : rm.getApplicableRegions(vector).getRegions()) {
            if (!ProtectionStones.isPSRegion(region)) continue;

            if (plugin.getDataStore().isBanned(region.getId(), player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Estas banneado de esta proteccion, no podes entrar.");
                return;
            }
        }
    }
}
