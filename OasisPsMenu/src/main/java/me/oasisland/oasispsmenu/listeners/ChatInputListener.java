package me.oasisland.oasispsmenu.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.oasisland.oasispsmenu.OasisPSMenu;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * ChatInputListener
 * -------------------
 * Reemplaza a la vieja API de "Conversaciones" de Bukkit
 * (ConversationFactory, StringPrompt, ConversationContext, Prompt) que
 * Paper marcó como deprecada y "para eliminación futura" — de ahí
 * salían todos los warnings al compilar.
 *
 * Cómo funciona: en vez de armar una Conversation, cualquier menú que
 * necesite que el jugador escriba algo por chat (un nombre, un número,
 * un texto) llama a prompt(jugador, función). Guardamos esa función en
 * un mapa. Cuando llega el PRÓXIMO mensaje de chat de ese jugador, lo
 * interceptamos acá (se cancela — nadie más lo ve en el chat público,
 * ni queda guardado en el historial del server), lo sacamos del mapa,
 * y ejecutamos la función con el texto que escribió.
 *
 * El evento de chat de Paper corre en un hilo aparte (no el principal
 * del servidor), así que la función se ejecuta con el scheduler en el
 * hilo principal — ahí sí es seguro tocar inventarios, regiones, etc.
 */
public class ChatInputListener implements Listener {

    private final OasisPSMenu plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatInputListener(OasisPSMenu plugin) {
        this.plugin = plugin;
    }

    /**
     * Registra que el PRÓXIMO mensaje de chat de este jugador se
     * procese con onInput, en vez de mandarse al chat como siempre.
     * Si ya había un prompt pendiente para ese jugador, lo reemplaza
     * (nunca se acumulan ni se ejecutan dos a la vez).
     */
    public void prompt(Player player, Consumer<String> onInput) {
        pending.put(player.getUniqueId(), onInput);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Consumer<String> callback = pending.remove(event.getPlayer().getUniqueId());
        if (callback == null) return;

        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(message));
    }

    // Si el jugador se desconecta con un prompt pendiente (por ejemplo,
    // le pedimos un nombre y se fue sin contestar), lo limpiamos del
    // mapa para no dejar basura acumulada ni ejecutar nada sobre un
    // jugador que ya no está.
    //
    // De paso (29/08), también cortamos cualquier sesión de "Ver
    // límites" activa (HomeEditorMenu) — así no queda un BlockDisplay
    // fantasma brillando en el mundo si el jugador se desconecta
    // mientras la tenía abierta.
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
        me.oasisland.oasispsmenu.gui.HomeEditorMenu.forceStopViewSession(plugin, event.getPlayer().getUniqueId());
    }

    // Si alguien se conecta MIENTRAS otro jugador tiene "Ver límites"
    // activo, por defecto vería el brillo del bloque (el hideEntity solo
    // se aplicó a los jugadores que ya estaban conectados en ese momento).
    // Esto lo oculta también para el recién llegado.
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        me.oasisland.oasispsmenu.gui.HomeEditorMenu.hideActiveViewsFrom(plugin, event.getPlayer());
    }
}
