package me.oasisland.oasispsmenu.gui;

import dev.espi.protectionstones.PSPlayer;
import dev.espi.protectionstones.PSRegion;
import me.oasisland.oasispsmenu.OasisPSMenu;
import me.oasisland.oasispsmenu.util.ItemBuilder;
import me.oasisland.oasispsmenu.util.PSLookup;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * SelectorMenu — Selector de Protección
 * ----------------------------------------
 * Menú principal que se abre al ejecutar /ps o /psmenu.
 * 27 slots (3 filas).
 *
 * Layout:
 *   BLACK_STAINED_GLASS_PANE : 0,1,7,8,9,17,18,19,25,26
 *   GRAY_STAINED_GLASS_PANE  : 2,6,10,16,20,24
 *   COMPASS     (slot 11)    → Ver todas tus protecciones
 *   COAL_ORE    (slot 13)    → Info de la protección actual (BARRIER si no hay)
 *   LECTERN     (slot 15)    → Tu información personal
 */
public class SelectorMenu {

    private static final int[] BLACK_SLOTS = {0, 1, 7, 8, 9, 17, 18, 19, 25, 26};
    private static final int[] GRAY_SLOTS  = {2, 6, 10, 16, 20, 24};

    public static void open(OasisPSMenu plugin, Player player) {
        String title = ChatColor.translateAlternateColorCodes('&',
                "&8• Selector de &8&lProtección &8•");

        MenuHolder holder = new MenuHolder(MenuHolder.MenuType.SELECTOR, null, null, 0);
        Inventory inv = plugin.getServer().createInventory(holder, 27, title);
        holder.setInventory(inv);

        // ── Cristales de relleno ──────────────────────────────────────
        ItemBuilder blackPane = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ");
        ItemBuilder grayPane  = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ");

        for (int s : BLACK_SLOTS) inv.setItem(s, blackPane.build());
        for (int s : GRAY_SLOTS)  inv.setItem(s, grayPane.build());

        // ── Slot 11: COMPASS — Ver todas las protecciones ─────────────
        inv.setItem(11, new ItemBuilder(Material.COMPASS)
                .name("&aVer todas tus protecciones")
                .lore(
                        "&8⌜Lista de protecciones⌟",
                        "",
                        " &7Muestra la lista de todas las protecciones",
                        " &7que tienes colocadas en los mundos",
                        "",
                        "&eClic izquierdo para abrir lista"
                ).build());

        // ── Slot 13: Info de la protección actual ─────────────────────
        PSRegion regionActual = PSLookup.getRegionAt(player);
        if (regionActual != null) {
            Material matReal = PSLookup.getRegionMaterial(regionActual);

            // Tamaño según el tipo de bloque configurado en el .toml
            String tamano = getSizeDescription(matReal);

            // Nombre de la protección (default = ID de WorldGuard si no tiene nombre)
            String nombre = regionActual.getName() != null && !regionActual.getName().isBlank()
                    ? regionActual.getName()
                    : regionActual.getWGRegion().getId();

            // Lista de propietarios (nombres separados por coma)
            StringBuilder propietarios = new StringBuilder();
            for (java.util.UUID uuid : regionActual.getWGRegion().getOwners().getUniqueIds()) {
                String ownerName = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
                if (propietarios.length() > 0) propietarios.append(", ");
                propietarios.append(ownerName != null ? ownerName : uuid.toString().substring(0, 8));
            }
            if (propietarios.length() == 0) propietarios.append("Desconocido");

            // ¿El bloque es de alto rango? → efecto glow
            boolean glow = matReal == Material.DIAMOND_ORE
                    || matReal == Material.CRYING_OBSIDIAN
                    || matReal == Material.DEEPSLATE_DIAMOND_ORE;

            ItemBuilder builder = new ItemBuilder(matReal)
                    .name("&aProtección actual")
                    .lore(buildInfoLore(tamano, nombre, propietarios.toString(), tienePermiso(regionActual, player)));

            if (glow) builder.glowing();
            inv.setItem(13, builder.build());

        } else {
            // Sin protección → barrier con instrucción de /protes (solo informativo,
            // ya no se ejecuta nada automático al hacer click — ver handleClick).
            inv.setItem(13, new ItemBuilder(Material.BARRIER)
                    .name("&cNo hay protección en tu ubicación")
                    .lore(
                            "",
                            "&7Adquiere cubos protectores aquí",
                            "&8• &e/protes"
                    ).build());
        }

        // ── Slot 15: LECTERN — Info del jugador ───────────────────────
        PSPlayer psPlayer = PSPlayer.fromUUID(player.getUniqueId());
        List<PSRegion> propias = psPlayer.getPSRegionsCrossWorld(player.getWorld(), false);

        // Límite via permiso protectionstones.maxregions.<N>
        int limite = -1;
        for (int i = 1; i <= 200; i++) {
            if (player.hasPermission("protectionstones.maxregions." + i)) {
                limite = i;
            }
        }
        String limiteStr = (limite == -1) ? "&fSin límite" : "&f" + limite;

        inv.setItem(15, new ItemBuilder(Material.LECTERN)
                .name("&fTu información")
                .lore(
                        "&7Jugador: &f" + player.getName(),
                        "&7Protecciones colocadas: &f" + propias.size(),
                        "&7Límite de protecciones: " + limiteStr
                ).build());

        player.openInventory(inv);
    }

    /**
     * Devuelve la descripción de tamaño de la protección según el material
     * del bloque protector configurado en el .toml de ProtectionStones.
     */
    private static String getSizeDescription(Material mat) {
        // Reutiliza la tabla centralizada en PSLookup (misma fuente que
        // usa PlacementGuardListener para calcular el radio al colocar).
        int size = PSLookup.getProtectionSize(mat);
        return size > 0 ? size + "x" + size : "Desconocido";
    }

    /**
     * FIX DE SEGURIDAD (27/08): "Protección actual" mostraba el botón de
     * gestionar sin importar quién sea el jugador — cualquiera podía
     * pararse sobre una protección ajena, hacer click y administrarla
     * (renombrarla, editar flags, teletransportarse, etc). Ahora solo
     * cuenta como "con permiso" el dueño, un miembro, o alguien con el
     * permiso de administrador oasispsmenu.admin.manage (pensado para
     * OP o staff sin necesidad de darles OP — ver plugin.yml).
     */
    private static boolean tienePermiso(PSRegion region, Player player) {
        return region.getWGRegion().getOwners().contains(player.getUniqueId())
                || region.getWGRegion().getMembers().contains(player.getUniqueId())
                || player.hasPermission("oasispsmenu.admin.manage");
    }

    /** Arma el lore de "Protección actual", con o sin el hint de gestionar según el permiso. */
    private static List<String> buildInfoLore(String tamano, String nombre, String propietarios, boolean puedeGestionar) {
        List<String> lore = new ArrayList<>(List.of(
                "&8⌜Información⌟",
                "",
                " &8• &7Protección: &f" + tamano,
                " &8• &7Nombre: &f" + nombre,
                " &8• &7Propietarios: &f" + propietarios
        ));
        lore.add("");
        if (puedeGestionar) {
            lore.add("&eClic izquierdo para gestionar");
        } else {
            lore.add("&cNo sos dueño ni miembro de esta protección");
        }
        return lore;
    }

    /** Manejo de clicks — solo slots con ítems funcionales. */
    public static void handleClick(OasisPSMenu plugin, Player player, int slot) {
        switch (slot) {
            case 11 -> HomeMenu.open(plugin, player, 0);
            case 13 -> {
                PSRegion region = PSLookup.getRegionAt(player);

                // Sin protección en la ubicación: el ítem es puramente
                // informativo (BARRIER con la pista de /protes). Ya NO
                // cerramos el menú ni ejecutamos ningún comando solos —
                // el jugador cierra el menú y escribe /protes por su cuenta.
                // Sonido "de aldeano sin permiso", el típico de menús.
                if (region == null) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                // FIX DE SEGURIDAD: solo el dueño, un miembro, o un admin
                // (oasispsmenu.admin.manage) pueden abrir el Editor.
                if (!tienePermiso(region, player)) {
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    player.sendMessage(ChatColor.RED + "No tenés permiso para gestionar esta protección — no sos su dueño ni miembro.");
                    return;
                }

                HomeEditorMenu.open(plugin, player, region);
            }
            // Slot 15 (lectern): solo informativo
        }
    }
}
