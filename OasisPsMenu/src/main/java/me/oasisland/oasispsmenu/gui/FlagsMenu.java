package me.oasisland.oasispsmenu.gui;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.RegionGroupFlag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import dev.espi.protectionstones.PSRegion;
import me.oasisland.oasispsmenu.OasisPSMenu;
import me.oasisland.oasispsmenu.data.FlagsConfig;
import me.oasisland.oasispsmenu.util.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FlagsMenu ("Editor de Flags")
 * ------------------------------
 * Réplica COMPLETA del menú real visto en el video (27/08): 46 flags
 * en 2 páginas, con el layout exacto confirmado frame a frame —
 * materiales, nombres, descripciones, orden y relleno de cristales
 * todo verificado contra el video, más los ajustes que pidió Pablo:
 *
 *   - Relleno BLACK_STAINED_GLASS_PANE en los slots de flags.yml
 *     (filler-slots), lista exacta que pasó Pablo.
 *   - Slot 48: ARROW → "Regresar" (vuelve al Editor de la protección).
 *   - Slot 49: BOOK → indicador de página (informativo, "Página X/Y").
 *   - Slot 50: ARROW → navega entre página 1 y 2. Como solo hay 2
 *     páginas, este único botón cambia de rol: en la página 1 dice
 *     "Página siguiente →", en la página 2 dice "← Página anterior".
 *
 * Dos tipos de flag:
 *   - StateFlag (la mayoría): click izquierdo cicla el GRUPO al que
 *     aplica, click derecho cicla el ESTADO (ALLOW → DENY → sin definir).
 *   - StringFlag (greeting, greeting-title, greeting-action, farewell,
 *     farewell-title, farewell-action): cualquier click abre un
 *     prompt de chat para escribir el texto — ver
 *     MenuClickListener.askForFlagText().
 */
public class FlagsMenu {

    public static void open(OasisPSMenu plugin, Player player, PSRegion region, int page) {
        FlagsConfig cfg = plugin.getFlagsConfig();

        List<String> allFlags = cfg.getOrderedFlagKeys();
        List<Integer> slots = cfg.getIconSlots();
        int perPage = Math.max(1, slots.size());
        int totalPages = Math.max(1, (int) Math.ceil(allFlags.size() / (double) perPage));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = ChatColor.translateAlternateColorCodes('&',
                "&8• Editor de Flags &7[" + (page + 1) + "/" + totalPages + "] &8•");

        MenuHolder holder = new MenuHolder(MenuHolder.MenuType.FLAGS, region.getWGRegion().getId(), region.getWorld().getName(), page);
        Inventory inv = plugin.getServer().createInventory(holder, cfg.getMenuSize(), title);
        holder.setInventory(inv);

        // ── Relleno decorativo (lista exacta pedida por Pablo) ─────────
        ItemStack fillerPane = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int s : cfg.getFillerSlots()) {
            if (s >= 0 && s < cfg.getMenuSize()) inv.setItem(s, fillerPane);
        }

        Map<Integer, String> slotFlagMap = new HashMap<>();
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();

        int start = page * perPage;
        int end = Math.min(start + perPage, allFlags.size());

        for (int i = start; i < end; i++) {
            String flagKey = allFlags.get(i);
            int slot = slots.get(i - start);

            Flag<?> wgFlag = registry.get(flagKey);
            if (wgFlag == null) {
                plugin.getLogger().warning("flags.yml define '" + flagKey + "' pero ese flag no existe en WorldGuard/ProtectionStones. Se omite del menú.");
                continue;
            }

            FlagsConfig.FlagDefinition def = cfg.getFlagDefinition(flagKey);
            ItemStack item = buildIcon(def, region, wgFlag);
            inv.setItem(slot, item);
            slotFlagMap.put(slot, flagKey);
        }

        holder.setSlotFlagMap(slotFlagMap);

        // ── Slot 48: Regresar ───────────────────────────────────────────
        inv.setItem(cfg.getBackButtonSlot(), new ItemBuilder(Material.ARROW)
                .name("&cRegresar")
                .lore("&7Volver al Editor de la protección.").build());

        // ── Slot 49: indicador de página (informativo) ─────────────────
        inv.setItem(cfg.getPageInfoSlot(), new ItemBuilder(Material.BOOK)
                .name("&fPágina " + (page + 1) + " de " + totalPages)
                .lore("&7Mostrando " + (end - start) + " de " + allFlags.size() + " flags.").build());

        // ── Slot 50: navegación de página (solo si hay más de 1 página) ─
        if (totalPages > 1) {
            boolean hasNext = page < totalPages - 1;
            inv.setItem(cfg.getPageNavSlot(), new ItemBuilder(Material.ARROW)
                    .name(hasNext ? "&aPágina siguiente →" : "&a← Página anterior")
                    .lore(hasNext ? "&7Ir a la página " + (page + 2) : "&7Volver a la página " + page).build());
        }

        player.openInventory(inv);
    }

    private static ItemStack buildIcon(FlagsConfig.FlagDefinition def, PSRegion region, Flag<?> wgFlag) {
        ItemStack item = new ItemStack(def.material, def.amount);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&a" + def.displayName));

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.translateAlternateColorCodes('&', "&7CONFIGURABLE FLAG"));
        lore.add("");
        for (String line : def.description.split("\n")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', "&f" + line));
        }
        lore.add("");

        if (def.textFlag && wgFlag instanceof StringFlag stringFlag) {
            // Flag de TEXTO (greeting, farewell, etc): no tiene ALLOW/DENY,
            // muestra el texto actual (o "Indefinido") y una pista de click.
            String current = region.getWGRegion().getFlag(stringFlag);
            lore.add(ChatColor.translateAlternateColorCodes('&', "&6✎ Información"));
            lore.add(ChatColor.translateAlternateColorCodes('&',
                    " &7• Estado: " + (current != null && !current.isBlank() ? "&f" + current : "&c?Indefinido")));
            lore.add(ChatColor.translateAlternateColorCodes('&', " &7• Grupos: &fN/A"));
            lore.add("");
            lore.add(ChatColor.translateAlternateColorCodes('&', "&e▸ ¡Click para añadir un texto!"));
        } else {
            String stateLabel = getStateLabel(region, wgFlag);
            String stateColor = getStateColor(region, wgFlag);
            String groupName = getGroupName(region, wgFlag);
            String groupColor = groupName.equals("N/A") ? "&f" : "&e";

            lore.add(ChatColor.translateAlternateColorCodes('&', "&6✎ Información"));
            lore.add(ChatColor.translateAlternateColorCodes('&', " &7• Estado: " + stateColor + stateLabel));
            lore.add(ChatColor.translateAlternateColorCodes('&', " &7• Grupos: " + groupColor + groupName));
            lore.add("");
            lore.add(ChatColor.translateAlternateColorCodes('&', "&e▸ Click izquierdo — ¡Activar o desactivar la bandera!"));
            lore.add(ChatColor.translateAlternateColorCodes('&', "&e▸ Click derecho — ¡Activa o desactiva los grupos!"));
        }

        meta.setLore(lore);

        if (def.modelData > 0) {
            meta.setCustomModelData(def.modelData);
        }
        meta.addItemFlags(def.itemFlags.toArray(new org.bukkit.inventory.ItemFlag[0]));
        if (def.glowing) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static String getStateLabel(PSRegion region, Flag<?> wgFlag) {
        if (!(wgFlag instanceof StateFlag stateFlag)) return "N/A";
        StateFlag.State current = (StateFlag.State) region.getWGRegion().getFlag(stateFlag);
        if (current == null) return "?Indefinido";
        return current == StateFlag.State.ALLOW ? "✓ Permitido" : "✗ Prohibido";
    }

    private static String getStateColor(PSRegion region, Flag<?> wgFlag) {
        if (!(wgFlag instanceof StateFlag stateFlag)) return "&7";
        StateFlag.State current = (StateFlag.State) region.getWGRegion().getFlag(stateFlag);
        if (current == null) return "&7";
        return current == StateFlag.State.ALLOW ? "&a" : "&c";
    }

    private static String getGroupName(PSRegion region, Flag<?> wgFlag) {
        RegionGroupFlag groupFlag = wgFlag.getRegionGroupFlag();
        if (groupFlag == null) return "N/A";
        RegionGroup group = region.getWGRegion().getFlag(groupFlag);
        if (group == null) return "N/A";
        return switch (group) {
            case MEMBERS -> "Members";
            case OWNERS -> "Owners";
            case NON_MEMBERS -> "Non-Members";
            case NON_OWNERS -> "Non-Owners";
            default -> "N/A";
        };
    }

    /**
     * Click derecho: cicla el estado del flag (ALLOW -> DENY -> sin definir).
     * No hace nada si el flag es de texto (StringFlag) — esos se manejan
     * por chat, ver MenuClickListener.askForFlagText().
     */
    public static void cycleState(PSRegion region, String flagKey) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        Flag<?> flag = registry.get(flagKey);
        if (!(flag instanceof StateFlag stateFlag)) return;

        StateFlag.State current = (StateFlag.State) region.getWGRegion().getFlag(stateFlag);
        StateFlag.State next;
        if (current == null) {
            next = StateFlag.State.ALLOW;
        } else if (current == StateFlag.State.ALLOW) {
            next = StateFlag.State.DENY;
        } else {
            next = null;
        }
        region.getWGRegion().setFlag(stateFlag, next);
    }

    /**
     * Click izquierdo: cicla el grupo al que aplica el flag.
     * No hace nada si el flag es de texto (StringFlag).
     */
    public static void cycleGroup(OasisPSMenu plugin, PSRegion region, String flagKey) {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        Flag<?> flag = registry.get(flagKey);
        if (flag == null || !(flag instanceof StateFlag)) return;

        RegionGroupFlag groupFlag = flag.getRegionGroupFlag();
        if (groupFlag == null) return;

        List<String> order = plugin.getConfig().getStringList("default-flag-groups");
        if (order.isEmpty()) {
            order = List.of("ALL", "MEMBERS", "OWNERS", "NON_MEMBERS", "NON_OWNERS");
        }

        RegionGroup current = region.getWGRegion().getFlag(groupFlag);
        String currentName = current != null ? current.name() : "ALL";

        int idx = order.indexOf(currentName);
        String nextName = order.get((idx + 1) % order.size());

        RegionGroup next = parseGroup(nextName);
        region.getWGRegion().setFlag(groupFlag, next);
    }

    /** true si el flag es de texto (greeting, farewell, etc) — se edita por chat, no con click. */
    public static boolean isTextFlag(OasisPSMenu plugin, String flagKey) {
        return plugin.getFlagsConfig().getFlagDefinition(flagKey).textFlag;
    }

    private static RegionGroup parseGroup(String name) {
        return switch (name) {
            case "MEMBERS" -> RegionGroup.MEMBERS;
            case "OWNERS" -> RegionGroup.OWNERS;
            case "NON_MEMBERS" -> RegionGroup.NON_MEMBERS;
            case "NON_OWNERS" -> RegionGroup.NON_OWNERS;
            default -> RegionGroup.ALL;
        };
    }
}
