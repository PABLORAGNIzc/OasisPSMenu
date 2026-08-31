package me.oasisland.oasispsmenu.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ColorUtil
 * ---------
 * Traduce tanto los códigos de color con & como los colores hex &#RRGGBB
 * al formato de texto legado que Paper/Spigot entiende en item lore y nombres.
 *
 * Ejemplo:
 *   "&#B247FF250x250" → §x§B§2§4§7§F§F250x250
 *   "&aTexto"         → §aTexto
 */
public class ColorUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    /**
     * Traduce códigos & Y colores hex &#RRGGBB en un mismo string.
     * Es el método que hay que usar en lugar de
     * ChatColor.translateAlternateColorCodes() cuando el texto puede
     * contener colores hex.
     */
    public static String colorize(String text) {
        if (text == null) return "";

        // 1. Convertir &#RRGGBB → §x§R§R§G§G§B§B (formato legado de Paper)
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            StringBuilder hexSeq = new StringBuilder("§x");
            for (char c : matcher.group(1).toCharArray()) {
                hexSeq.append('§').append(c);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(hexSeq.toString()));
        }
        matcher.appendTail(sb);
        text = sb.toString();

        // 2. Convertir los códigos &a, &b, &c, etc.
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
