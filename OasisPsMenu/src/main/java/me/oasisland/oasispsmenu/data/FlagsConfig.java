package me.oasisland.oasispsmenu.data;

import me.oasisland.oasispsmenu.OasisPSMenu;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FlagsConfig
 * -----------
 * Lee flags.yml y expone cada flag como un FlagDefinition.
 * La estructura del YAML es:
 *   icon-slots: "10,11,12,..."   (slots separados por coma)
 *   back-button-slot: 48
 *   menu-size: 54
 *   flags:
 *     <flagKey>:
 *       material: MATERIAL_NAME
 *       display_name: "texto"
 *       description: "multi\nlinea"
 */
public class FlagsConfig {

    public static class FlagDefinition {
        public Material material = Material.PAPER;
        public int amount = 1;
        public boolean glowing = false;
        public List<ItemFlag> itemFlags = new ArrayList<>();
        public int modelData = 0;
        public String displayName = "";
        public String description = "";
        public boolean textFlag = false; // true = flag de texto (greeting, farewell, etc), no ALLOW/DENY
        public Map<String, String> displayStates = new LinkedHashMap<>();
    }

    private final OasisPSMenu plugin;
    private final File file;
    private YamlConfiguration yaml;

    public FlagsConfig(OasisPSMenu plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "flags.yml");
    }

    public void load() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                try (InputStream in = plugin.getResource("flags.yml")) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, file.toPath());
                    } else {
                        file.createNewFile();
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear flags.yml: " + e.getMessage());
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);

        InputStream defStream = plugin.getResource("flags.yml");
        if (defStream != null) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream, StandardCharsets.UTF_8)));
        }
    }

    public int getMenuSize() {
        int size = yaml.getInt("menu-size", 54);
        size = Math.max(9, Math.min(54, size));
        return (size / 9) * 9;
    }

    public List<Integer> getIconSlots() {
        String raw = yaml.getString("icon-slots", "");
        List<Integer> slots = new ArrayList<>();
        for (String part : raw.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            try {
                slots.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {}
        }
        return slots;
    }

    public int getBackButtonSlot() {
        return yaml.getInt("back-button-slot", 48);
    }

    public int getPageInfoSlot() {
        return yaml.getInt("page-info-slot", 49);
    }

    public int getPageNavSlot() {
        return yaml.getInt("page-nav-slot", 50);
    }

    /** Slots de relleno (BLACK_STAINED_GLASS_PANE), lista exacta pedida por Pablo. */
    public List<Integer> getFillerSlots() {
        String raw = yaml.getString("filler-slots", "");
        List<Integer> slots = new ArrayList<>();
        for (String part : raw.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            try {
                slots.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {}
        }
        return slots;
    }

    public List<String> getOrderedFlagKeys() {
        List<String> keys = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("flags");
        if (section != null) {
            keys.addAll(section.getKeys(false));
        }
        return keys;
    }

    public FlagDefinition getFlagDefinition(String flagKey) {
        FlagDefinition def = new FlagDefinition();
        String base = "flags." + flagKey + ".";

        try {
            def.material = Material.valueOf(yaml.getString(base + "material", "PAPER").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Material invalido para flag '" + flagKey + "', usando PAPER.");
            def.material = Material.PAPER;
        }

        def.amount = Math.max(1, yaml.getInt(base + "amount", 1));
        def.glowing = yaml.getBoolean(base + "glowing", false);
        def.modelData = yaml.getInt(base + "model_data", 0);
        def.displayName = yaml.getString(base + "display_name", flagKey);
        def.description = yaml.getString(base + "description", "");
        def.textFlag = yaml.getBoolean(base + "text_flag", false);

        for (String flagName : yaml.getStringList(base + "item_flags")) {
            try {
                def.itemFlags.add(ItemFlag.valueOf(flagName));
            } catch (IllegalArgumentException ignored) {}
        }

        ConfigurationSection statesSection = yaml.getConfigurationSection(base + "display_states");
        if (statesSection != null) {
            for (String stateKey : statesSection.getKeys(false)) {
                def.displayStates.put(stateKey.toUpperCase(), statesSection.getString(stateKey));
            }
        }

        return def;
    }
}
