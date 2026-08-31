package me.oasisland.oasispsmenu.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * MenuHolder
 * ----------
 * Bukkit identifica los inventarios por su InventoryHolder. Usamos esta
 * clase para "etiquetar" cada inventario que abrimos con:
 *   - qué tipo de menú es (HOME, MANAGE, FLAGS, MEMBERS)
 *   - a qué región de ProtectionStones pertenece (puede ser null en HOME)
 *   - número de página (para listas largas)
 *
 * Así, cuando el jugador hace click, el listener sabe exactamente
 * qué lógica aplicar sin tener que adivinar por el título del inventario.
 */
public class MenuHolder implements InventoryHolder {

    public enum MenuType {
        HOME, MANAGE, FLAGS, MEMBERS, SELECTOR, HOME_EDITOR,
        OWNERS, // Lista de Propietarios (separada de MEMBERS desde el rediseño del Editor)
        BANS,   // Lista de Baneados
        REMOVE_CONFIRM // "¿Deseas remover la protección?"
    }

    private Inventory inventory;
    private final MenuType type;
    private final String regionId;
    private final String worldName;
    private final int page;
    private java.util.Map<Integer, String> slotFlagMap;
    private String sortMode = "ALL"; // para HomeMenu: ALL, MEMBERS, OWNERS, WORLD

    public MenuHolder(MenuType type, String regionId, String worldName, int page) {
        this.type = type;
        this.regionId = regionId;
        this.worldName = worldName;
        this.page = page;
    }

    public String getSortMode() { return sortMode; }
    public void setSortMode(String sortMode) { this.sortMode = sortMode; }

    public java.util.Map<Integer, String> getSlotFlagMap() {
        return slotFlagMap;
    }

    public void setSlotFlagMap(java.util.Map<Integer, String> slotFlagMap) {
        this.slotFlagMap = slotFlagMap;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public MenuType getType() {
        return type;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getPage() {
        return page;
    }
}
