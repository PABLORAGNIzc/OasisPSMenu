package me.oasisland.oasispsmenu.util;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.ProtectionStones;
import dev.espi.protectionstones.PSRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PSLookup
 * --------
 * Utilidades centralizadas para buscar regiones de ProtectionStones y
 * resolver el material real del bloque protector.
 *
 * Toda la lógica de prioridad y de material vive acá para que todos los
 * menús (Selector, HomeMenu, HomeEditorMenu, etc.) usen siempre la misma
 * jerarquía sin código duplicado.
 */
public class PSLookup {

    // ---------------------------------------------------------------
    //  JERARQUÍA DE PRIORIDAD
    //  Número menor = mayor prioridad.
    //  Esta tabla debe coincidir exactamente con los tipos configurados
    //  en los archivos .toml de ProtectionStones.
    // ---------------------------------------------------------------
    private static final Map<String, Integer> PRIORITY = Map.of(
            "CRYING_OBSIDIAN", 1,   // 250x250 — máxima prioridad
            "DIAMOND_ORE",     2,   // 100x100
            "DEEPSLATE_DIAMOND_ORE", 2, // variante deepslate
            "EMERALD_ORE",     3,   // 64x64
            "DEEPSLATE_EMERALD_ORE", 3,
            "GOLD_ORE",        4,   // 32x32
            "DEEPSLATE_GOLD_ORE", 4,
            "REDSTONE_ORE",    5,   // 16x16 — mínima prioridad
            "DEEPSLATE_REDSTONE_ORE", 5
    );

    // ---------------------------------------------------------------
    //  OBTENER EL MATERIAL REAL DEL BLOQUE PROTECTOR
    // ---------------------------------------------------------------

    /**
     * Lee el campo `type` del archivo .toml que corresponde a la región
     * y lo convierte en un Material de Bukkit.
     *
     * El campo puede venir en varios formatos según la versión de PS:
     *   - "REDSTONE_ORE"              (mayúsculas, sin namespace)
     *   - "minecraft:redstone_ore"    (con namespace, minúsculas)
     *   - "redstone_ore"              (solo minúsculas)
     *
     * Si no se puede resolver, devuelve GRASS_BLOCK como fallback.
     */
    public static Material getRegionMaterial(PSRegion region) {
        try {
            dev.espi.protectionstones.PSProtectBlock opts = region.getTypeOptions();
            if (opts == null) return Material.GRASS_BLOCK;

            String raw = opts.type;
            if (raw == null || raw.isBlank()) return Material.GRASS_BLOCK;

            // Quitar el prefijo "minecraft:" si existe
            if (raw.contains(":")) {
                raw = raw.substring(raw.indexOf(':') + 1);
            }

            // Normalizar a mayúsculas (Bukkit espera UPPER_CASE)
            Material mat = Material.matchMaterial(raw.toUpperCase());
            return mat != null ? mat : Material.GRASS_BLOCK;

        } catch (Exception e) {
            return Material.GRASS_BLOCK;
        }
    }

    // ---------------------------------------------------------------
    //  TAMAÑO (EN BLOQUES) SEGÚN EL MATERIAL DEL BLOQUE PROTECTOR
    // ---------------------------------------------------------------

    /**
     * Devuelve el tamaño total (ancho, en bloques) que va a tener la
     * protección para un material de bloque protector dado.
     *
     * Esta tabla es la MISMA jerarquía que ya usan HomeMenu y
     * SelectorMenu para mostrarle el tamaño al jugador (16x16, 32x32,
     * etc), centralizada acá para que el guard de colocación
     * (PlacementGuardListener) pueda calcular el radio ANTES de que
     * la región exista, usando el material del bloque que se está
     * por colocar en vez de una región ya creada.
     *
     * Devuelve 0 si el material no está en la tabla (no podemos
     * calcular el área con seguridad, así que quien llame a esto debe
     * tratar 0 como "no lo sabemos, no bloquear").
     */
    public static int getProtectionSize(Material mat) {
        if (mat == null) return 0;
        return switch (mat) {
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> 16;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE         -> 32;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE   -> 64;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE   -> 100;
            case CRYING_OBSIDIAN                       -> 250;
            default                                    -> 0;
        };
    }

    // ---------------------------------------------------------------
    //  UBICACIÓN EXACTA DEL BLOQUE PROTECTOR
    // ---------------------------------------------------------------

    private static final java.util.regex.Pattern PROTECTION_ID_PATTERN =
            java.util.regex.Pattern.compile("^ps(-?\\d+)x(-?\\d+)y(-?\\d+)z$");

    /**
     * Devuelve la ubicación exacta (coordenadas de bloque) del cubo
     * protector físico de una región, parseando el ID que le pone
     * ProtectionStones — formato "ps<X>x<Y>y<Z>z" (mismo que ya
     * usaba RegionEventListener para construirlo al revés).
     *
     * Se usa para: resaltar el cubo protector con el contorno blanco
     * de "Ver límites", y para chequear si el lugar está libre antes
     * de volver a mostrar una protección oculta.
     *
     * Devuelve null si el ID no tiene ese formato (no debería pasar
     * en una región de PS real, pero por las dudas).
     */
    public static Location getProtectionBlockLocation(PSRegion region) {
        var matcher = PROTECTION_ID_PATTERN.matcher(region.getWGRegion().getId());
        if (!matcher.matches()) return null;

        int x = Integer.parseInt(matcher.group(1));
        int y = Integer.parseInt(matcher.group(2));
        int z = Integer.parseInt(matcher.group(3));

        return new Location(region.getWorld(), x, y, z);
    }

    // ---------------------------------------------------------------
    //  OBTENER LA PRIORIDAD NUMÉRICA DE UNA REGIÓN
    // ---------------------------------------------------------------

    /**
     * Devuelve el número de prioridad de la región (menor = más importante).
     * Las regiones cuyo tipo no aparece en la tabla reciben prioridad 99
     * (tratadas como las de menor importancia).
     */
    public static int getRegionPriority(PSRegion region) {
        try {
            dev.espi.protectionstones.PSProtectBlock opts = region.getTypeOptions();
            if (opts == null || opts.type == null) return 99;

            String raw = opts.type;
            if (raw.contains(":")) raw = raw.substring(raw.indexOf(':') + 1);
            raw = raw.toUpperCase();

            return PRIORITY.getOrDefault(raw, 99);
        } catch (Exception e) {
            return 99;
        }
    }

    // ---------------------------------------------------------------
    //  BUSCAR LA REGIÓN DE MAYOR PRIORIDAD EN LA UBICACIÓN DEL JUGADOR
    // ---------------------------------------------------------------

    /**
     * Devuelve la región de ProtectionStones con MAYOR prioridad en la
     * ubicación actual del jugador.
     *
     * Si el jugador está dentro de regiones superpuestas (ej. 16x16 dentro
     * de una 100x100), se devuelve la de mayor tamaño según la jerarquía
     * definida en PRIORITY. Devuelve null si no hay ninguna región de PS.
     */
    public static PSRegion getRegionAt(Player player) {
        List<PSRegion> candidatas = getAllRegionsAt(player.getLocation());

        if (candidatas.isEmpty()) return null;
        if (candidatas.size() == 1) return candidatas.get(0);

        // Ordenar por prioridad ascendente (1 = más importante) y devolver la primera
        candidatas.sort((a, b) -> getRegionPriority(a) - getRegionPriority(b));
        return candidatas.get(0);
    }

    /**
     * Devuelve TODAS las regiones de ProtectionStones en una ubicación,
     * independientemente de la prioridad. Útil para sistemas de ban.
     */
    public static List<PSRegion> getAllRegionsAt(Location loc) {
        List<PSRegion> result = new ArrayList<>();
        if (loc == null || loc.getWorld() == null) return result;

        World world = loc.getWorld();
        RegionManager rm = WorldGuard.getInstance().getPlatform()
                .getRegionContainer()
                .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
        if (rm == null) return result;

        var vector = com.sk89q.worldedit.bukkit.BukkitAdapter.asBlockVector(loc);

        for (ProtectedRegion region : rm.getApplicableRegions(vector).getRegions()) {
            if (ProtectionStones.isPSRegion(region)) {
                PSRegion psRegion = PSRegion.fromWGRegion(world, region);
                if (psRegion != null) result.add(psRegion);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------
    //  BUSCAR UNA REGIÓN POR ID
    // ---------------------------------------------------------------

    /**
     * Busca una región por su ID exacto de WorldGuard + nombre de mundo.
     * Se usa cuando volvemos de un sub-menú y el MenuHolder solo guarda el ID.
     */
    public static PSRegion getRegionById(String worldName, String regionId) {
        if (worldName == null || regionId == null) return null;
        World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) return null;

        RegionManager rm = WorldGuard.getInstance().getPlatform()
                .getRegionContainer()
                .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
        if (rm == null) return null;

        ProtectedRegion region = rm.getRegion(regionId);
        if (region == null || !ProtectionStones.isPSRegion(region)) return null;

        return PSRegion.fromWGRegion(world, region);
    }
}
