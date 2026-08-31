package me.oasisland.oasispsmenu.gui;

import dev.espi.protectionstones.PSRegion;
import me.oasisland.oasispsmenu.OasisPSMenu;
import me.oasisland.oasispsmenu.util.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Set;
import java.util.UUID;

/**
 * PeopleMenu — reemplaza a la vieja MembersMenu.
 * ------------------------------------------------
 * Antes de este rediseño, "Miembros y Dueños" era un solo menú que
 * mezclaba ambas listas. En el video real de PSMenu son DOS botones
 * separados en el Editor ("Lista de Propietarios" y "Lista de
 * Miembros"), cada uno con su propia lista y su propio agregar/quitar.
 *
 * Para no duplicar toda la lógica en dos archivos casi idénticos, este
 * único menú se abre en dos "modos" según el parámetro `owners`:
 *   - owners = true  -> gestiona region.getWGRegion().getOwners()
 *   - owners = false -> gestiona region.getWGRegion().getMembers()
 *
 * Cada modo SOLO toca su propio dominio de WorldGuard — agregar o
 * quitar un propietario nunca modifica la lista de miembros, y
 * viceversa (a diferencia de la MembersMenu vieja, que quitaba de
 * ambas listas a la vez).
 */
public class PeopleMenu {

    public static void open(OasisPSMenu plugin, Player player, PSRegion region, boolean owners) {
        String title = ChatColor.translateAlternateColorCodes('&',
                owners ? "&6&lLista de Propietarios" : "&e&lLista de Miembros");

        MenuHolder.MenuType type = owners ? MenuHolder.MenuType.OWNERS : MenuHolder.MenuType.MEMBERS;
        MenuHolder holder = new MenuHolder(type, region.getWGRegion().getId(), region.getWorld().getName(), 0);
        Inventory inv = plugin.getServer().createInventory(holder, 54, title);
        holder.setInventory(inv);

        Set<UUID> ids = owners
                ? region.getWGRegion().getOwners().getUniqueIds()
                : region.getWGRegion().getMembers().getUniqueIds();

        int slot = 0;
        for (UUID uuid : ids) {
            if (slot >= 45) break; // dejamos la última fila libre para los botones
            inv.setItem(slot++, buildHead(uuid, owners));
        }

        inv.setItem(49, new ItemBuilder(Material.LIME_DYE)
                .name(owners ? "&aAgregar propietario" : "&aAgregar miembro")
                .lore("&7Click y escribí el nombre en el chat").build());
        inv.setItem(45, new ItemBuilder(Material.ARROW).name("&7« Volver").build());

        player.openInventory(inv);
    }

    private static ItemStack buildHead(UUID uuid, boolean owner) {
        OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(uuid);
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(offline);
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                (offline.getName() != null ? offline.getName() : uuid.toString())));
        meta.setLore(java.util.List.of(
                ChatColor.translateAlternateColorCodes('&', "&7Rol: " + (owner ? "&6Dueño" : "&eMiembro")),
                ChatColor.translateAlternateColorCodes('&', "&7Click derecho para quitar")
        ));
        head.setItemMeta(meta);
        return head;
    }
}
