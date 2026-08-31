package me.oasisland.oasispsmenu;

import me.oasisland.oasispsmenu.data.FlagsConfig;
import me.oasisland.oasispsmenu.data.MenuDataStore;
import me.oasisland.oasispsmenu.listeners.BanEnforcementListener;
import me.oasisland.oasispsmenu.listeners.ChatInputListener;
import me.oasisland.oasispsmenu.listeners.CommandInterceptListener;
import me.oasisland.oasispsmenu.listeners.MenuClickListener;
import me.oasisland.oasispsmenu.listeners.PlacementGuardListener;
import me.oasisland.oasispsmenu.listeners.RegionEventListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * OasisPSMenu
 * -----------
 * Plugin que agrega menús GUI (cofres) para administrar las protecciones
 * creadas con ProtectionStones: lista de "homes", editor de flags, lista
 * de miembros/dueños, y comandos de kick/ban/unban/leave.
 *
 * Es un ADDON: no reemplaza a ProtectionStones, depende de él. Todo lo que
 * hacemos acá es leer y escribir sobre las regiones que PS ya creó usando
 * su API pública (dev.espi.protectionstones.*) y la API de WorldGuard
 * para los flags.
 */
public class OasisPSMenu extends JavaPlugin {

    private static OasisPSMenu instance;
    private MenuDataStore dataStore;
    private FlagsConfig flagsConfig;
    private ChatInputListener chatInputListener;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Verificamos que las dependencias obligatorias estén presentes y activas.
        //    Si falta alguna, desactivamos el plugin para no romper el servidor.
        if (getServer().getPluginManager().getPlugin("ProtectionStones") == null) {
            getLogger().severe("No se encontró ProtectionStones. Desactivando OasisPSMenu...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            getLogger().severe("No se encontró WorldGuard. Desactivando OasisPSMenu...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Generamos config.yml por defecto si no existe, y cargamos el almacén
        //    de datos propio (bans y protecciones ocultas).
        saveDefaultConfig();
        this.dataStore = new MenuDataStore(this);
        this.dataStore.load();
        this.flagsConfig = new FlagsConfig(this);
        this.flagsConfig.load();

        // 3. Registramos los listeners y el executor del comando /psmenu.
        CommandInterceptListener interceptListener = new CommandInterceptListener(this);
        getServer().getPluginManager().registerEvents(interceptListener, this);
        getCommand("psmenu").setExecutor(interceptListener);
        getServer().getPluginManager().registerEvents(new MenuClickListener(this), this);
        getServer().getPluginManager().registerEvents(new BanEnforcementListener(this), this);
        getServer().getPluginManager().registerEvents(new RegionEventListener(this), this);
        getServer().getPluginManager().registerEvents(new PlacementGuardListener(this), this);
        this.chatInputListener = new ChatInputListener(this);
        getServer().getPluginManager().registerEvents(chatInputListener, this);

        getLogger().info("OasisPSMenu habilitado correctamente. Marca: OasisLand.");
    }

    @Override
    public void onDisable() {
        // Guardamos los datos (bans, protecciones ocultas) al apagar el servidor.
        if (dataStore != null) {
            dataStore.save();
        }
        getLogger().info("OasisPSMenu desactivado.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("oasispsmenu")) return false;

        if (!sender.hasPermission("oasispsmenu.admin")) {
            sender.sendMessage("§cNo tenés permiso para usar este comando.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            dataStore.load();
            flagsConfig.load();
            sender.sendMessage("§a[OasisPSMenu] Configuración recargada.");
            return true;
        }

        sender.sendMessage("§e/oasispsmenu reload §7- Recarga config.yml y data.yml");
        return true;
    }

    public static OasisPSMenu getInstance() {
        return instance;
    }

    public MenuDataStore getDataStore() {
        return dataStore;
    }

    public FlagsConfig getFlagsConfig() {
        return flagsConfig;
    }

    public ChatInputListener getChatInputListener() {
        return chatInputListener;
    }
}
