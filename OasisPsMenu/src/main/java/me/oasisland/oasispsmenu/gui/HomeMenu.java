package me.oasisland.oasispsmenu.gui;

import dev.espi.protectionstones.PSPlayer;
import dev.espi.protectionstones.PSRegion;
import me.oasisland.oasispsmenu.OasisPSMenu;
import me.oasisland.oasispsmenu.util.ColorUtil;
import me.oasisland.oasispsmenu.util.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * HomeMenu — "Ver todas tus protecciones"
 * ========================================
 * Tamaño: 54 slots.
 *
 * Layout fijo:
 *   GRAY_STAINED_GLASS_PANE : 0-9, 17, 18, 26, 27, 35, 36, 44-47, 51-53
 *   ARROW  (slot 48)        : Volver al Selector
 *   BARRIER(slot 49)        : Cerrar menú
 *   HOPPER (slot 50)        : Ordenar (ALL / MEMBERS / OWNERS / WORLD)
 *   ARROW  (slot 45)        : Página anterior — solo si page > 0
 *   ARROW  (slot 51)        : Página siguiente — solo si hay más items
 *                             (reemplaza el cristal gris)
 *
 * Slots disponibles para items de protección (28 por página):
 *   10-16, 19-25, 28-34, 37-43
 *
 * Modos de orden (hopper, cicla al hacer click):
 *   ALL     → todas las protecciones
 *   MEMBERS → solo donde el jugador es miembro
 *   OWNERS  → solo donde el jugador es dueño
 *   WORLD   → ordenadas por nombre de mundo
 */
public class HomeMenu {

    // Slots que el cristal gris ocupa SIEMPRE (decoración)
    private static final int[] GRAY_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 17, 18, 26, 27, 35, 36,
            44, 45, 46, 47, 51, 52, 53
    };

    // Slots donde se colocan los ítems de protección (en orden)
    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int PER_PAGE = ITEM_SLOTS.length; // 28

    // Slots fijos de botones
    private static final int SLOT_BACK      = 48;
    private static final int SLOT_CLOSE     = 49;
    private static final int SLOT_SORT      = 50;
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_NEXT_PAGE = 51;

    public static void open(OasisPSMenu plugin, Player player, int page, String sortMode) {
        // 1. Obtener y filtrar la lista de regiones
        PSPlayer psPlayer = PSPlayer.fromUUID(player.getUniqueId());
        List<PSRegion> all = psPlayer.getPSRegionsCrossWorld(player.getWorld(), true);
        List<PSRegion> filtered = filter(all, player, sortMode);

        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        // 2. Crear inventario
        String title = ChatColor.translateAlternateColorCodes('&', "&8Mis Protecciones");
        MenuHolder holder = new MenuHolder(MenuHolder.MenuType.HOME, null, null, page);
        holder.setSortMode(sortMode);
        Inventory inv = plugin.getServer().createInventory(holder, 54, title);
        holder.setInventory(inv);

        // 3. Cristales grises de relleno
        ItemStack grayPane = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int s : GRAY_SLOTS) inv.setItem(s, grayPane);

        // 4. Ítems de protección de la página actual
        int start = page * PER_PAGE;
        int end   = Math.min(start + PER_PAGE, filtered.size());

        if (filtered.isEmpty()) {
            // Sin protecciones → ítem informativo en el slot central (22)
            inv.setItem(22, new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                    .name("&cNo tienes cubos protectores colocados")
                    .lore(
                            "&7Adquiere cubos protectores aquí",
                            "&8• &e/protes"
                    ).build());
        } else {
            for (int i = start; i < end; i++) {
                PSRegion region = filtered.get(i);
                inv.setItem(ITEM_SLOTS[i - start], buildRegionItem(plugin, player, region));
            }
        }

        // 5. Botones de paginación (reemplazan el cristal si aplica)
        if (page > 0) {
            inv.setItem(SLOT_PREV_PAGE, new ItemBuilder(Material.ARROW)
                    .name("&e« Página anterior")
                    .lore("&7Ir a la página &f" + page).build());
        }
        if (end < filtered.size()) {
            inv.setItem(SLOT_NEXT_PAGE, new ItemBuilder(Material.ARROW)
                    .name("&ePágina siguiente »")
                    .lore("&7Ir a la página &f" + (page + 2)).build());
        }

        // 6. Botón Volver (slot 48)
        inv.setItem(SLOT_BACK, new ItemBuilder(Material.ARROW)
                .name("&7« Volver al menú").build());

        // 7. Botón Cerrar (slot 49)
        inv.setItem(SLOT_CLOSE, new ItemBuilder(Material.BARRIER)
                .name("&cCerrar").build());

        // 8. Hopper de ordenación (slot 50)
        inv.setItem(SLOT_SORT, buildSortItem(sortMode));

        player.openInventory(inv);
    }

    /** Sobrecarga para abrir con sortMode por defecto */
    public static void open(OasisPSMenu plugin, Player player, int page) {
        open(plugin, player, page, "ALL");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Filtra y ordena la lista de regiones según el modo activo.
     */
    private static List<PSRegion> filter(List<PSRegion> all, Player player, String mode) {
        List<PSRegion> result = new ArrayList<>();
        for (PSRegion r : all) {
            switch (mode) {
                case "MEMBERS" -> {
                    if (r.getWGRegion().getMembers().contains(player.getUniqueId())
                            && !r.getWGRegion().getOwners().contains(player.getUniqueId()))
                        result.add(r);
                }
                case "OWNERS" -> {
                    if (r.getWGRegion().getOwners().contains(player.getUniqueId()))
                        result.add(r);
                }
                default -> result.add(r);
            }
        }

        // Orden base: de más antigua a más reciente.
        // Las que no tienen fecha registrada van al final, ordenadas por nombre.
        result.sort((a, b) -> {
            long tsA = OasisPSMenu.getInstance().getDataStore()
                    .getCreationDate(a.getWGRegion().getId());
            long tsB = OasisPSMenu.getInstance().getDataStore()
                    .getCreationDate(b.getWGRegion().getId());
            boolean aFecha = tsA > 0;
            boolean bFecha = tsB > 0;
            if (aFecha && bFecha)  return Long.compare(tsA, tsB);  // ambas: más antigua primero
            if (aFecha)            return -1;                        // A tiene fecha → va antes
            if (bFecha)            return 1;                         // B tiene fecha → va antes
            // Ninguna tiene fecha → alfabético por nombre
            String nA = a.getName() != null ? a.getName() : a.getWGRegion().getId();
            String nB = b.getName() != null ? b.getName() : b.getWGRegion().getId();
            return nA.compareToIgnoreCase(nB);
        });

        // Modo WORLD: segundo nivel por nombre de mundo (respetando el orden por fecha dentro de cada mundo)
        if ("WORLD".equals(mode)) {
            result.sort((a, b) -> {
                int wc = a.getWorld().getName().compareToIgnoreCase(b.getWorld().getName());
                if (wc != 0) return wc;
                long tsA = OasisPSMenu.getInstance().getDataStore()
                        .getCreationDate(a.getWGRegion().getId());
                long tsB = OasisPSMenu.getInstance().getDataStore()
                        .getCreationDate(b.getWGRegion().getId());
                if (tsA > 0 && tsB > 0) return Long.compare(tsA, tsB);
                if (tsA > 0) return -1;
                if (tsB > 0) return 1;
                String nA = a.getName() != null ? a.getName() : a.getWGRegion().getId();
                String nB = b.getName() != null ? b.getName() : b.getWGRegion().getId();
                return nA.compareToIgnoreCase(nB);
            });
        }

        return result;
    }

    /**
     * Construye el ítem visual de una protección con el lore completo.
     */
    private static ItemStack buildRegionItem(OasisPSMenu plugin, Player player, PSRegion region) {
        boolean isOwner = region.getWGRegion().getOwners().contains(player.getUniqueId());
        String nombre = region.getName() != null && !region.getName().isBlank()
                ? region.getName()
                : region.getWGRegion().getId();

        Material mat = me.oasisland.oasispsmenu.util.PSLookup.getRegionMaterial(region);
        String tamanoColoreado = getSizeColored(mat);

        // --- Propietario original ---
        String propietario = "Desconocido";
        java.util.UUID creatorUuid = plugin.getDataStore().getCreator(region.getWGRegion().getId());
        if (creatorUuid != null) {
            String name = org.bukkit.Bukkit.getOfflinePlayer(creatorUuid).getName();
            propietario = name != null ? name : creatorUuid.toString().substring(0, 8);
        } else {
            // Sin registro → usamos el primer owner de la lista
            var ownerIds = region.getWGRegion().getOwners().getUniqueIds();
            if (!ownerIds.isEmpty()) {
                java.util.UUID first = ownerIds.iterator().next();
                String name = org.bukkit.Bukkit.getOfflinePlayer(first).getName();
                propietario = name != null ? name : first.toString().substring(0, 8);
            }
        }

        // --- Dueños (todos los owners) ---
        StringBuilder duenos = new StringBuilder();
        for (java.util.UUID uuid : region.getWGRegion().getOwners().getUniqueIds()) {
            String n = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
            if (duenos.length() > 0) duenos.append("&f, &f");
            duenos.append(n != null ? n : uuid.toString().substring(0, 8));
        }
        if (duenos.length() == 0) duenos.append("Ninguno");

        // --- Miembros ---
        StringBuilder miembros = new StringBuilder();
        for (java.util.UUID uuid : region.getWGRegion().getMembers().getUniqueIds()) {
            String n = org.bukkit.Bukkit.getOfflinePlayer(uuid).getName();
            if (miembros.length() > 0) miembros.append("&f, &f");
            miembros.append(n != null ? n : uuid.toString().substring(0, 8));
        }
        if (miembros.length() == 0) miembros.append("Ninguno");

        // --- Ubicación (centro de la región) ---
        var min = region.getWGRegion().getMinimumPoint();
        var max = region.getWGRegion().getMaximumPoint();
        int cx = (min.x() + max.x()) / 2;
        int cy = (min.y() + max.y()) / 2;
        int cz = (min.z() + max.z()) / 2;

        // --- Fecha de creación ---
        long ts = plugin.getDataStore().getCreationDate(region.getWGRegion().getId());
        String fecha;
        if (ts > 0) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm");
            fecha = sdf.format(new java.util.Date(ts));
        } else {
            fecha = "No disponible";
        }

        // ¿Glow para diamond y crying_obsidian?
        boolean glow = mat == Material.DIAMOND_ORE
                || mat == Material.DEEPSLATE_DIAMOND_ORE
                || mat == Material.CRYING_OBSIDIAN;

        ItemBuilder builder = new ItemBuilder(mat)
                .name((isOwner ? "&a" : "&e") + nombre)
                .lore(
                        "&8• &fCubo Protector &7[" + tamanoColoreado + "&7]",
                        "",
                        "&8• &7Nombre: &f" + nombre,
                        "&8• &7Mundo: &f" + region.getWorld().getName(),
                        "&8• &7Propietario: &f" + propietario,
                        "&8• &7Dueños: &f" + duenos,
                        "&8• &7Miembros: &f" + miembros,
                        "",
                        "&8• &7Ubicación: &fX: &a" + cx + "&f, Y: &a" + cy + "&f, Z: &a" + cz,
                        "&e⌚ &7Colocado el: &f" + fecha,
                        "",
                        "&8» &eClic izquierdo para teletransportar",
                        "&8» &eClic derecho para gestionar"
                );

        if (glow) builder.glowing();
        return builder.build();
    }

    /** Devuelve el tamaño con su color correspondiente según el bloque protector. */
    private static String getSizeColored(Material mat) {
        // Reutiliza la tabla centralizada en PSLookup — el color por
        // tamaño se mantiene acá porque es puramente visual de este menú.
        int size = me.oasisland.oasispsmenu.util.PSLookup.getProtectionSize(mat);
        return switch (size) {
            case 16  -> "&c16x16";
            case 32  -> "&e32x32";
            case 64  -> "&a64x64";
            case 100 -> "&b100x100";
            case 250 -> "&#B247FF250x250";
            default  -> "&7Desconocido";
        };
    }

    /**
     * Construye el ítem del hopper con el modo activo resaltado.
     * Replica el tooltip de la imagen (♦ = activo, ◇ = inactivo).
     */
    private static ItemStack buildSortItem(String current) {
        return new ItemBuilder(Material.HOPPER)
                .name("&fClasificar por categoría")
                .lore(
                        "&7Filtrar el listado",
                        "",
                        (current.equals("ALL")     ? "&e♦" : "&8◇") + " Ordenar por: Todos"
                                + (current.equals("ALL")     ? " &e(Actual)" : ""),
                        (current.equals("MEMBERS")  ? "&e♦" : "&8◇") + " Ordenar por: Miembros"
                                + (current.equals("MEMBERS")  ? " &e(Actual)" : ""),
                        (current.equals("OWNERS")   ? "&e♦" : "&8◇") + " Ordenar por: Dueños"
                                + (current.equals("OWNERS")   ? " &e(Actual)" : ""),
                        (current.equals("WORLD")    ? "&e♦" : "&8◇") + " Ordenar por: Mundo"
                                + (current.equals("WORLD")    ? " &e(Actual)" : ""),
                        "",
                        "&5• Clickea para ordenar por "
                                + nextModeName(current)
                ).build();
    }

    /** Devuelve el nombre legible del PRÓXIMO modo (para el hint del hopper) */
    private static String nextModeName(String current) {
        return switch (current) {
            case "ALL"     -> "Miembros";
            case "MEMBERS" -> "Dueños";
            case "OWNERS"  -> "Mundo";
            default        -> "Todos";
        };
    }

    /** Cicla al siguiente modo de orden */
    public static String nextMode(String current) {
        return switch (current) {
            case "ALL"     -> "MEMBERS";
            case "MEMBERS" -> "OWNERS";
            case "OWNERS"  -> "WORLD";
            default        -> "ALL";
        };
    }

    // Constantes de slots accesibles desde el listener
    public static int getSlotBack()     { return SLOT_BACK; }
    public static int getSlotClose()    { return SLOT_CLOSE; }
    public static int getSlotSort()     { return SLOT_SORT; }
    public static int getSlotPrevPage() { return SLOT_PREV_PAGE; }
    public static int getSlotNextPage() { return SLOT_NEXT_PAGE; }
    public static int[] getItemSlots()  { return ITEM_SLOTS; }
}
