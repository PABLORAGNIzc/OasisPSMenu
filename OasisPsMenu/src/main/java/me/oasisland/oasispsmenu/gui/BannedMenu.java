package me.oasisland.oasispsmenu.gui;

import dev.espi.protectionstones.PSRegion;
import me.oasisland.oasispsmenu.OasisPSMenu;
import me.oasisland.oasispsmenu.util.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * BannedMenu ("Lista de baneados")
 * ----------------------------------
 * Rediseño 28/08 a partir de las capturas que mandó Pablo. Layout
 * confirmado:
 *
 *   - Título: "Lista de baneados (N)" con la cantidad de baneados.
 *   - Cabezas de los baneados en los mismos 28 slots "de contenido"
 *     que usa el Editor de Flags (10-16, 19-25, 28-34, 37-43).
 *   - Relleno BLACK_STAINED_GLASS_PANE en los slots que pasó Pablo
 *     (los mismos que en FlagsMenu — mantenemos el mismo patrón
 *     visual en todos los submenús).
 *   - Slot 48: ARROW -> Regresar (vuelve al Editor).
 *   - Slot 49: BOOK -> Cerrar el menú.
 *   - Slot 50: BARRIER -> Añadir un baneado (abre un prompt de chat).
 *
 * Nota sobre las skins: cuando se banea a alguien que nunca jugó en
 * el server (o está offline), MenuClickListener.askBanName ya NO usa
 * Bukkit.getOfflinePlayer(nombre) (ese método es engañoso: para
 * jugadores que nunca se conectaron puede devolver un UUID que no es
 * el real, y entonces la cabeza sale con la skin de Steve en vez de
 * la del jugador real). En su lugar resuelve el perfil real contra
 * Mojang con Server#createProfile(nombre).update(), así la cabeza acá
 * siempre muestra la skin correcta.
 */
public class BannedMenu {

    // Mismos 28 slots "de contenido" que el Editor de Flags, para que
    // todos los submenús tengan la misma cara.
    private static final int[] HEAD_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int[] FILLER_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44,
            45, 46, 47, 51, 52, 53
    };

    public static void open(OasisPSMenu plugin, Player player, PSRegion region) {
        Set<UUID> banned = plugin.getDataStore().getBannedPlayers(region.getWGRegion().getId());

        // Punto 2 (28/08): título con emoji, cantidad de baneados y página.
        // Por ahora el menú no está paginado (si hay más de 28 baneados, se
        // muestran los primeros 28 nada más — ver nota en HEAD_SLOTS), así
        // que la página siempre es "[1]". El día que se agregue paginación
        // acá (como ya tiene el Editor de Flags), este número va a reflejar
        // la página real automáticamente.
        int page = 1;
        String title = ChatColor.translateAlternateColorCodes('&',
                "&8🚫 Lista de baneados (" + banned.size() + ") [" + page + "]");

        MenuHolder holder = new MenuHolder(MenuHolder.MenuType.BANS, region.getWGRegion().getId(), region.getWorld().getName(), 0);
        Inventory inv = plugin.getServer().createInventory(holder, 54, title);
        holder.setInventory(inv);

        // ── Relleno decorativo ───────────────────────────────────────
        ItemStack fillerPane = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int s : FILLER_SLOTS) inv.setItem(s, fillerPane);

        // ── Cabezas de los baneados ──────────────────────────────────
        int i = 0;
        for (UUID uuid : banned) {
            if (i >= HEAD_SLOTS.length) break; // más de 28 baneados: los primeros 28 por ahora
            inv.setItem(HEAD_SLOTS[i], buildHead(uuid));
            i++;
        }

        // ── Slot 48: Regresar ────────────────────────────────────────
        inv.setItem(48, new ItemBuilder(Material.ARROW)
                .name("&cRegresar")
                .lore("&7Volver al Editor de la protección.").build());

        // ── Slot 49: Cerrar ──────────────────────────────────────────
        inv.setItem(49, new ItemBuilder(Material.BOOK)
                .name("&fCerrar")
                .lore("&7Cierra este menú.").build());

        // ── Slot 50: Añadir baneado ──────────────────────────────────
        inv.setItem(50, new ItemBuilder(Material.BARRIER)
                .name("&cAñadir baneado")
                .lore("&7Click y escribí el nombre en el chat.").build());

        player.openInventory(inv);
    }

    private static ItemStack buildHead(UUID uuid) {
        OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName() != null ? offline.getName() : uuid.toString();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(offline);
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&aProhibido: " + name));
        meta.setLore(List.of(
                ChatColor.translateAlternateColorCodes('&', "&7Información"),
                "",
                ChatColor.translateAlternateColorCodes('&', "&f Este es un usuario baneado y"),
                ChatColor.translateAlternateColorCodes('&', "&f puedes desbanearlo aquí mismo"),
                "",
                ChatColor.translateAlternateColorCodes('&', "&e▸ Haz clic para desbanear")
        ));
        head.setItemMeta(meta);
        return head;
    }
}
