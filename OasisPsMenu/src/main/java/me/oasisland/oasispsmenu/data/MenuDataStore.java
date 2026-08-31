package me.oasisland.oasispsmenu.data;

import me.oasisland.oasispsmenu.OasisPSMenu;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * MenuDataStore
 * -------------
 * ProtectionStones no tiene un concepto nativo de "banear a un jugador de mi
 * protección", así que lo implementamos nosotros mismos guardando, por cada
 * región (identificada por su ID de WorldGuard), el set de UUIDs banneados
 * y si la región está marcada como "oculta" (hide-protections en el menú).
 *
 * Todo se guarda en plugins/OasisPSMenu/data.yml
 */
public class MenuDataStore {

    private final OasisPSMenu plugin;
    private final File file;
    private YamlConfiguration yaml;

    // regionId -> set de UUIDs banneados de esa región
    private final Map<String, Set<UUID>> bans = new HashMap<>();
    // regionId -> si el dueño decidió ocultar la protección
    private final Set<String> hiddenRegions = new HashSet<>();
    // regionId -> timestamp de creación (ms desde epoch)
    private final Map<String, Long> creationDates = new HashMap<>();
    // regionId -> UUID del jugador que colocó el bloque originalmente
    private final Map<String, UUID> creators = new HashMap<>();

    public MenuDataStore(OasisPSMenu plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear data.yml: " + e.getMessage());
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);

        bans.clear();
        hiddenRegions.clear();
        creationDates.clear();
        creators.clear();

        if (yaml.isConfigurationSection("bans")) {
            for (String regionId : yaml.getConfigurationSection("bans").getKeys(false)) {
                Set<UUID> set = new HashSet<>();
                for (String uuidStr : yaml.getStringList("bans." + regionId)) {
                    try { set.add(UUID.fromString(uuidStr)); } catch (IllegalArgumentException ignored) {}
                }
                bans.put(regionId, set);
            }
        }
        hiddenRegions.addAll(yaml.getStringList("hidden-regions"));

        if (yaml.isConfigurationSection("creation-dates")) {
            for (String regionId : yaml.getConfigurationSection("creation-dates").getKeys(false)) {
                creationDates.put(regionId, yaml.getLong("creation-dates." + regionId));
            }
        }
        if (yaml.isConfigurationSection("creators")) {
            for (String regionId : yaml.getConfigurationSection("creators").getKeys(false)) {
                try {
                    creators.put(regionId, UUID.fromString(yaml.getString("creators." + regionId)));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void save() {
        for (Map.Entry<String, Set<UUID>> entry : bans.entrySet()) {
            yaml.set("bans." + entry.getKey(),
                    entry.getValue().stream().map(UUID::toString).toList());
        }
        yaml.set("hidden-regions", new java.util.ArrayList<>(hiddenRegions));
        for (Map.Entry<String, Long> entry : creationDates.entrySet()) {
            yaml.set("creation-dates." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, UUID> entry : creators.entrySet()) {
            yaml.set("creators." + entry.getKey(), entry.getValue().toString());
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar data.yml: " + e.getMessage());
        }
    }

    // ---------- BANS ----------

    public boolean isBanned(String regionId, UUID uuid) {
        return bans.containsKey(regionId) && bans.get(regionId).contains(uuid);
    }

    public void ban(String regionId, UUID uuid) {
        bans.computeIfAbsent(regionId, k -> new HashSet<>()).add(uuid);
        save();
    }

    public void unban(String regionId, UUID uuid) {
        if (bans.containsKey(regionId)) {
            bans.get(regionId).remove(uuid);
            save();
        }
    }

    public Set<UUID> getBannedPlayers(String regionId) {
        return bans.getOrDefault(regionId, new HashSet<>());
    }

    // ---------- OCULTAR PROTECCIÓN ----------

    public boolean isHidden(String regionId) {
        return hiddenRegions.contains(regionId);
    }

    public void setHidden(String regionId, boolean hidden) {
        if (hidden) {
            hiddenRegions.add(regionId);
        } else {
            hiddenRegions.remove(regionId);
        }
        save();
    }

    // ---------- FECHAS DE CREACIÓN ----------

    public void setCreationDate(String regionId, long timestamp) {
        creationDates.put(regionId, timestamp);
        save();
    }

    /** Devuelve el timestamp de creación en ms, o -1 si no está registrado. */
    public long getCreationDate(String regionId) {
        return creationDates.getOrDefault(regionId, -1L);
    }

    // ---------- CREADOR ORIGINAL ----------

    public void setCreator(String regionId, UUID uuid) {
        creators.put(regionId, uuid);
        save();
    }

    /** Devuelve el UUID del jugador que colocó el bloque, o null si no hay registro. */
    public UUID getCreator(String regionId) {
        return creators.get(regionId);
    }
}
