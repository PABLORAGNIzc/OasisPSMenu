package me.oasisland.oasispsmenu.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * ItemBuilder
 * -----------
 * Helper para construir ItemStacks con nombre, lore y efectos de forma fluida.
 * Soporta colores & y colores hex &#RRGGBB en nombre y lore.
 */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        meta.setDisplayName(ColorUtil.colorize(name));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        List<String> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(ColorUtil.colorize(line));
        }
        meta.setLore(lore);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        List<String> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(ColorUtil.colorize(line));
        }
        meta.setLore(lore);
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    /** Agrega brillo visual (encantamiento oculto) al ítem. */
    public ItemBuilder glowing() {
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
