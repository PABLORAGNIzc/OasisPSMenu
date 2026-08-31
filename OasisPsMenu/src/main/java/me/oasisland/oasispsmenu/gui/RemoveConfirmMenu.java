package me.oasisland.oasispsmenu.gui;

import dev.espi.protectionstones.PSRegion;
import me.oasisland.oasispsmenu.OasisPSMenu;
import me.oasisland.oasispsmenu.util.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * RemoveConfirmMenu ("¿Deseas remover la protección?")
 * -------------------------------------------------------
 * Submenú de confirmación que se abre al hacer click en "Remover
 * protección" desde el Editor, para evitar que alguien la remueva
 * sin querer. Layout confirmado por Pablo (imagen de referencia):
 *
 *   Slot 12 → LIME_CONCRETE : Confirmar
 *   Slot 14 → RED_CONCRETE  : Cancelar
 *   Slot 22 → ARROW         : Volver (al Editor)
 *   BLACK_STAINED_GLASS_PANE en 0,1,7,8,9,17,18,19,25,26
 *   GRAY_STAINED_GLASS_PANE  en 2,6,10,16,20,24
 */
public class RemoveConfirmMenu {

    private static final int[] BLACK_SLOTS = {0, 1, 7, 8, 9, 17, 18, 19, 25, 26};
    private static final int[] GRAY_SLOTS  = {2, 6, 10, 16, 20, 24};

    public static void open(OasisPSMenu plugin, Player player, PSRegion region) {
        String title = ChatColor.translateAlternateColorCodes('&', "&8¿Deseas remover la protección?");

        MenuHolder holder = new MenuHolder(MenuHolder.MenuType.REMOVE_CONFIRM, region.getWGRegion().getId(), region.getWorld().getName(), 0);
        Inventory inv = plugin.getServer().createInventory(holder, 27, title);
        holder.setInventory(inv);

        ItemBuilder blackPane = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ");
        ItemBuilder grayPane  = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ");
        for (int s : BLACK_SLOTS) inv.setItem(s, blackPane.build());
        for (int s : GRAY_SLOTS)  inv.setItem(s, grayPane.build());

        inv.setItem(12, new ItemBuilder(Material.LIME_CONCRETE)
                .name("&a✔ Confirmar")
                .lore("&7Remueve la protección y te",
                      "&7devuelve el bloque al inventario",
                      "&7(si tenés espacio libre).").build());

        inv.setItem(14, new ItemBuilder(Material.RED_CONCRETE)
                .name("&c❌ Cancelar")
                .lore("&7Cierra este menú sin hacer nada.").build());

        inv.setItem(22, new ItemBuilder(Material.ARROW)
                .name("&e← Volver")
                .lore("&7Volver al Editor de la protección.").build());

        player.openInventory(inv);
    }
}
