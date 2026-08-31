package me.oasisland.oasispsmenu.gui;

import dev.espi.protectionstones.PSRegion;
import me.oasisland.oasispsmenu.OasisPSMenu;
import me.oasisland.oasispsmenu.util.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.sk89q.worldedit.math.BlockVector3;

/**
 * RegionManageMenu
 * ----------------
 * Hub central de una protección puntual. Desde acá el dueño/miembro
 * accede a: Teletransporte, Flags, Miembros, Ver bordes, Ocultar/Mostrar
 * y Salir de la protección.
 *
 * Cambiado el 29/08: "Teletransportarme" usaba
 * `world.getHighestBlockYAt(x, z)`, que busca el bloque más alto de
 * TODO el mundo en esa columna — si Pablo tenía un techo o cualquier
 * construcción arriba del cubo protector, el jugador terminaba
 * teletransportado ahí arriba, no encima del cubo. Ahora se usa la
 * ubicación real del cubo protector (PSLookup.getProtectionBlockLocation,
 * la misma que usa "Ver límites") y se aterriza justo un bloque arriba
 * de ÉL específicamente — sea cual sea lo que haya construido más
 * arriba en esa columna.
 */
public class RegionManageMenu {

    public static void open(OasisPSMenu plugin, Player player, PSRegion region) {
        String title = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("menu-titles.manage", "&2&lGestionar Protección"));

        MenuHolder holder = new MenuHolder(MenuHolder.MenuType.MANAGE, region.getWGRegion().getId(), region.getWorld().getName(), 0);
        Inventory inv = plugin.getServer().createInventory(holder, 27, title);
        holder.setInventory(inv);

        boolean hidden = plugin.getDataStore().isHidden(region.getWGRegion().getId());

        inv.setItem(11, new ItemBuilder(Material.ENDER_PEARL)
                .name("&bTeletransportarme")
                .lore("&7Te lleva al centro de tu protección").build());

        inv.setItem(13, new ItemBuilder(Material.WRITABLE_BOOK)
                .name("&eEditar Flags")
                .lore("&7Permitir/denegar acciones dentro", "&7de esta protección").build());

        inv.setItem(15, new ItemBuilder(Material.PLAYER_HEAD)
                .name("&6Miembros y Dueños")
                .lore("&7Ver, agregar o quitar jugadores").build());

        inv.setItem(20, new ItemBuilder(Material.GLOWSTONE)
                .name("&dVer bordes")
                .lore("&7Muestra el límite de la protección", "&7con partículas durante 8 segundos").build());

        inv.setItem(22, hidden
                ? new ItemBuilder(Material.ENDER_EYE).name("&aMostrar protección").lore("&7Actualmente: &cOculta").build()
                : new ItemBuilder(Material.ENDER_EYE).name("&cOcultar protección").lore("&7Actualmente: &aVisible").build());

        inv.setItem(24, new ItemBuilder(Material.RED_BED)
                .name("&cSalir de la protección")
                .lore("&7Te quita como miembro/dueño", "&7de esta protección").build());

        inv.setItem(26, new ItemBuilder(Material.BARRIER).name("&7« Volver").build());

        player.openInventory(inv);
    }

    /**
     * Teletransporta al jugador justo ARRIBA del cubo protector real
     * (no al punto más alto que haya en esa columna del mundo — ver
     * comentario de clase). Si por algún motivo no se puede ubicar el
     * bloque real (no debería pasar en una región de PS normal), cae al
     * cálculo anterior como respaldo, para no dejar al jugador sin
     * teletransporte.
     */
    public static void teleportToCenter(Player player, PSRegion region) {
        Location blockLoc = me.oasisland.oasispsmenu.util.PSLookup.getProtectionBlockLocation(region);

        if (blockLoc != null) {
            Location loc = new Location(blockLoc.getWorld(),
                    blockLoc.getBlockX() + 0.5,
                    blockLoc.getBlockY() + 1,
                    blockLoc.getBlockZ() + 0.5);
            player.teleport(loc);
            return;
        }

        // Respaldo (no debería pasar): centro geométrico de la región +
        // punto más alto del terreno.
        BlockVector3 min = region.getWGRegion().getMinimumPoint();
        BlockVector3 max = region.getWGRegion().getMaximumPoint();

        World world = region.getWorld();
        int x = (min.x() + max.x()) / 2;
        int z = (min.z() + max.z()) / 2;
        int y = world.getHighestBlockYAt(x, z) + 1;

        Location loc = new Location(world, x + 0.5, y, z + 0.5);
        player.teleport(loc);
    }
}