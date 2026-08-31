package me.oasisland.oasispsmenu.listeners;

import dev.espi.protectionstones.PSRegion;
import me.oasisland.oasispsmenu.OasisPSMenu;
import me.oasisland.oasispsmenu.gui.FlagsMenu;
import me.oasisland.oasispsmenu.gui.HomeEditorMenu;
import me.oasisland.oasispsmenu.gui.HomeMenu;
import me.oasisland.oasispsmenu.gui.SelectorMenu;
import me.oasisland.oasispsmenu.util.PSLookup;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.UUID;

/**
 * CommandInterceptListener
 * ------------------------
 * - Registra /psmenu como el comando principal (abre el Selector).
 * - Intercepta subcomandos de /ps que queremos reemplazar por GUI:
 *   /ps home  -> HomeMenu
 *   /ps flag  -> FlagsMenu (si está dentro de una protección)
 *   /ps kick, /ps ban, /ps unban, /ps leave -> lógica propia
 * - /ps SIN subcomando abre el Selector también (configurable).
 */
public class CommandInterceptListener implements Listener, CommandExecutor {

    private final OasisPSMenu plugin;

    public CommandInterceptListener(OasisPSMenu plugin) {
        this.plugin = plugin;
    }

    // ---- /psmenu -------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("psmenu")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando solo puede usarse en el juego.");
            return true;
        }
        if (!player.hasPermission("oasispsmenu.use")) {
            player.sendMessage(ChatColor.RED + "No tenes permiso para usar este menú.");
            return true;
        }
        SelectorMenu.open(plugin, player);
        return true;
    }

    // ---- Interceptacion de /ps -----------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String msg = event.getMessage().toLowerCase().trim();
        if (!msg.startsWith("/ps ") && !msg.equals("/ps")) return;

        Player player = event.getPlayer();
        String[] parts = msg.split("\\s+");

        // /ps SOLO -> Selector (si está habilitado en config)
        if (parts.length == 1) {
            boolean enabled = plugin.getConfig().getBoolean("inventories.main-menu.enabled", true);
            if (enabled && player.hasPermission("oasispsmenu.use")) {
                event.setCancelled(true);
                SelectorMenu.open(plugin, player);
            }
            return;
        }

        String sub = parts[1];

        switch (sub) {
            case "home" -> {
                event.setCancelled(true);
                if (!player.hasPermission("oasispsmenu.use")) {
                    player.sendMessage(ChatColor.RED + "No tenes permiso.");
                    return;
                }
                HomeMenu.open(plugin, player, 0);
            }
            case "flag", "flags" -> {
                event.setCancelled(true);
                if (!player.hasPermission("oasispsmenu.use")) {
                    player.sendMessage(ChatColor.RED + "No tenes permiso.");
                    return;
                }
                PSRegion region = PSLookup.getRegionAt(player);
                if (region == null) {
                    player.sendMessage(ChatColor.RED + "Tenes que estar parado dentro de tu proteccion.");
                    return;
                }
                if (!region.getWGRegion().getOwners().contains(player.getUniqueId())
                        && !player.hasPermission("oasispsmenu.admin")) {
                    player.sendMessage(ChatColor.RED + "Solo el dueno de la proteccion puede editar sus flags.");
                    return;
                }
                FlagsMenu.open(plugin, player, region, 0);
            }
            case "kick" -> {
                event.setCancelled(true);
                handleKick(player, parts);
            }
            case "ban" -> {
                event.setCancelled(true);
                handleBan(player, parts);
            }
            case "unban" -> {
                event.setCancelled(true);
                handleUnban(player, parts);
            }
            case "leave" -> {
                event.setCancelled(true);
                handleLeave(player);
            }
            // Cualquier otro subcomando lo maneja ProtectionStones normalmente
        }
    }

    private void handleKick(Player player, String[] parts) {
        if (!player.hasPermission("oasispsmenu.kick")) {
            player.sendMessage(ChatColor.RED + "No tenes permiso para expulsar jugadores.");
            return;
        }
        if (parts.length < 3) { player.sendMessage(ChatColor.YELLOW + "Uso: /ps kick <jugador>"); return; }
        PSRegion region = PSLookup.getRegionAt(player);
        if (region == null || !region.getWGRegion().getOwners().contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Tenes que estar parado en una proteccion de la que seas dueno.");
            return;
        }
        Player target = plugin.getServer().getPlayer(parts[2]);
        if (target == null) { player.sendMessage(ChatColor.RED + "Ese jugador no esta conectado."); return; }
        target.teleport(target.getWorld().getSpawnLocation());
        target.sendMessage(ChatColor.RED + "Fuiste expulsado de la proteccion de " + player.getName() + ".");
        player.sendMessage(ChatColor.GREEN + "Expulsaste a " + target.getName() + " de tu proteccion.");
    }

    private void handleBan(Player player, String[] parts) {
        if (!player.hasPermission("oasispsmenu.ban")) {
            player.sendMessage(ChatColor.RED + "No tenes permiso para banear jugadores.");
            return;
        }
        if (parts.length < 3) { player.sendMessage(ChatColor.YELLOW + "Uso: /ps ban <jugador>"); return; }
        PSRegion region = PSLookup.getRegionAt(player);
        if (region == null || !region.getWGRegion().getOwners().contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Tenes que estar parado en una proteccion de la que seas dueno.");
            return;
        }
        UUID targetUuid;
        Player targetOnline = plugin.getServer().getPlayer(parts[2]);
        if (targetOnline != null) {
            targetUuid = targetOnline.getUniqueId();
        } else {
            targetUuid = plugin.getServer().getOfflinePlayer(parts[2]).getUniqueId();
        }
        plugin.getDataStore().ban(region.getWGRegion().getId(), targetUuid);
        player.sendMessage(ChatColor.GREEN + "Banneaste a " + parts[2] + " de tu proteccion.");
        if (targetOnline != null) {
            targetOnline.teleport(targetOnline.getWorld().getSpawnLocation());
            targetOnline.sendMessage(ChatColor.RED + "Fuiste banneado de la proteccion de " + player.getName() + ".");
        }
    }

    private void handleUnban(Player player, String[] parts) {
        if (parts.length < 3) { player.sendMessage(ChatColor.YELLOW + "Uso: /ps unban <jugador>"); return; }
        PSRegion region = PSLookup.getRegionAt(player);
        if (region == null || !region.getWGRegion().getOwners().contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Tenes que estar parado en una proteccion de la que seas dueno.");
            return;
        }
        plugin.getDataStore().unban(region.getWGRegion().getId(), plugin.getServer().getOfflinePlayer(parts[2]).getUniqueId());
        player.sendMessage(ChatColor.GREEN + "Le quitaste el ban a " + parts[2] + " en esta proteccion.");
    }

    private void handleLeave(Player player) {
        PSRegion region = PSLookup.getRegionAt(player);
        if (region == null) { player.sendMessage(ChatColor.RED + "No estas dentro de ninguna proteccion."); return; }
        boolean wasOwner = region.getWGRegion().getOwners().contains(player.getUniqueId());
        boolean wasMember = region.getWGRegion().getMembers().contains(player.getUniqueId());
        region.getWGRegion().getOwners().removePlayer(player.getUniqueId());
        region.getWGRegion().getMembers().removePlayer(player.getUniqueId());
        if (wasOwner || wasMember) player.sendMessage(ChatColor.GREEN + "Saliste de la proteccion.");
        else player.sendMessage(ChatColor.RED + "No formas parte de esta proteccion.");
    }
}
