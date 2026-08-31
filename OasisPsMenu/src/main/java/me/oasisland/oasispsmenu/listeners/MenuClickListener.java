package me.oasisland.oasispsmenu.listeners;

import dev.espi.protectionstones.PSRegion;
import me.oasisland.oasispsmenu.OasisPSMenu;
import me.oasisland.oasispsmenu.gui.*;
import me.oasisland.oasispsmenu.util.ColorUtil;
import me.oasisland.oasispsmenu.util.PSLookup;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/**
 * MenuClickListener
 * ------------------
 * Un solo listener que cubre todos los menus del plugin.
 * Siempre cancela el evento para evitar que se saquen items del inventario.
 */
public class MenuClickListener implements Listener {

    private final OasisPSMenu plugin;

    public MenuClickListener(OasisPSMenu plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getItemMeta() == null) return;

        int slot = event.getSlot();
        ClickType click = event.getClick();

        switch (holder.getType()) {
            case SELECTOR    -> SelectorMenu.handleClick(plugin, player, slot);
            case HOME        -> handleHomeClick(player, holder, clicked, click, slot);
            case HOME_EDITOR -> handleHomeEditorClick(player, holder, clicked, slot);
            case FLAGS       -> handleFlagsClick(player, holder, clicked, click, slot);
            case MEMBERS     -> handlePeopleClick(player, holder, clicked, slot, false);
            case OWNERS      -> handlePeopleClick(player, holder, clicked, slot, true);
            case BANS        -> handleBansClick(player, holder, clicked, slot);
            case REMOVE_CONFIRM -> handleRemoveConfirmClick(player, holder, clicked);
            case MANAGE      -> {} // mantenemos compatibilidad, no se usa actualmente
        }
    }

    // ------------------------------------------------------------------ SELECTOR
    // Delegado a SelectorMenu.handleClick

    // ------------------------------------------------------------------ HOME

    private void handleHomeClick(Player player, MenuHolder holder, ItemStack clicked, ClickType click, int slot) {
        String sortMode = holder.getSortMode() != null ? holder.getSortMode() : "ALL";
        int page = holder.getPage();

        // Botón: Volver al Selector
        if (slot == HomeMenu.getSlotBack()) {
            SelectorMenu.open(plugin, player);
            return;
        }
        // Botón: Cerrar
        if (slot == HomeMenu.getSlotClose()) {
            player.closeInventory();
            return;
        }
        // Botón: Hopper — ciclar modo de orden
        if (slot == HomeMenu.getSlotSort()) {
            HomeMenu.open(plugin, player, 0, HomeMenu.nextMode(sortMode));
            return;
        }
        // Botón: Página anterior
        if (slot == HomeMenu.getSlotPrevPage() && page > 0) {
            HomeMenu.open(plugin, player, page - 1, sortMode);
            return;
        }
        // Botón: Página siguiente
        if (slot == HomeMenu.getSlotNextPage()) {
            HomeMenu.open(plugin, player, page + 1, sortMode);
            return;
        }

        // Slot 22: RED_STAINED_GLASS_PANE (lista vacía) — puramente informativo.
        // Ya NO cerramos el menú ni ejecutamos ningún comando automáticamente:
        // el jugador lee la pista del lore ("/protes"), cierra el menú manualmente
        // y lo escribe por su cuenta. Sonido "de aldeano sin permiso", el típico de menús.
        if (slot == 22 && clicked.getType() == org.bukkit.Material.RED_STAINED_GLASS_PANE) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // ¿Es un slot de región?
        boolean isItemSlot = false;
        for (int s : HomeMenu.getItemSlots()) {
            if (s == slot) { isItemSlot = true; break; }
        }
        if (!isItemSlot) return;

        // Buscar la región que corresponde a este slot
        var psPlayer = dev.espi.protectionstones.PSPlayer.fromUUID(player.getUniqueId());
        java.util.List<PSRegion> all = psPlayer.getPSRegionsCrossWorld(player.getWorld(), true);
        // Replicamos el mismo filtrado que HomeMenu para encontrar el índice correcto
        java.util.List<PSRegion> filtered = new java.util.ArrayList<>();
        for (PSRegion r : all) {
            switch (sortMode) {
                case "MEMBERS" -> {
                    if (r.getWGRegion().getMembers().contains(player.getUniqueId())
                            && !r.getWGRegion().getOwners().contains(player.getUniqueId()))
                        filtered.add(r);
                }
                case "OWNERS" -> {
                    if (r.getWGRegion().getOwners().contains(player.getUniqueId()))
                        filtered.add(r);
                }
                default -> filtered.add(r);
            }
        }
        if ("WORLD".equals(sortMode)) {
            filtered.sort(java.util.Comparator.comparing(r -> r.getWorld().getName()));
        }

        // Calcular índice global del ítem clickeado
        int[] itemSlots = HomeMenu.getItemSlots();
        int posInPage = -1;
        for (int i = 0; i < itemSlots.length; i++) {
            if (itemSlots[i] == slot) { posInPage = i; break; }
        }
        if (posInPage < 0) return;

        int globalIndex = page * itemSlots.length + posInPage;
        if (globalIndex >= filtered.size()) return;

        PSRegion region = filtered.get(globalIndex);

        if (click.isRightClick()) {
            HomeEditorMenu.open(plugin, player, region);
        } else {
            RegionManageMenu.teleportToCenter(player, region);
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Te teletransportaste a " +
                    (region.getName() != null ? region.getName() : region.getWGRegion().getId()) + ".");
        }
    }

    // ------------------------------------------------------------------ HOME EDITOR

    private void handleHomeEditorClick(Player player, MenuHolder holder, ItemStack clicked, int slot) {
        PSRegion region = PSLookup.getRegionById(holder.getWorldName(), holder.getRegionId());
        if (region == null) {
            player.sendMessage(ChatColor.RED + "Esa proteccion ya no existe.");
            player.closeInventory();
            return;
        }

        String name = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (name.contains("Lista de Baneados")) {
            if (!player.hasPermission("oasispsmenu.ban")
                    || !region.getWGRegion().getOwners().contains(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "Solo el dueño de la proteccion puede ver la lista de baneados.");
                return;
            }
            BannedMenu.open(plugin, player, region);
            return;
        }
        if (name.contains("Editar Flags")) {
            FlagsMenu.open(plugin, player, region, 0);
            return;
        }
        if (name.contains("Prioridad Protección")) {
            player.closeInventory();
            askForPriority(player, region);
            return;
        }
        if (name.contains("Ocultar Protección") || name.contains("Mostrar Protección")) {
            // Usamos la API real de ProtectionStones para leer y cambiar
            // el estado oculto/visible. Ya no usamos nuestro DataStore para
            // esto — PS maneja su propio estado internamente.
            boolean hidden = region.isHidden();
            if (hidden) {
                // Punto 4 (28/08): antes, si alguien ocultaba la protección
                // y después ocupaba ese mismo lugar con otro bloque (o
                // cualquier otra cosa), al intentar volver a mostrarla
                // rompía todo — PS no tiene dónde poner el cubo protector
                // de vuelta. Ahora chequeamos el lugar ANTES de mostrarla:
                // si hay algo colocado ahí, avisamos con el mismo mensaje
                // que usa el propio ProtectionStones y no hacemos nada más
                // hasta que el jugador libere ese espacio.
                org.bukkit.Location blockLoc = PSLookup.getProtectionBlockLocation(region);
                if (blockLoc != null && blockLoc.getBlock().getType() != Material.AIR) {
                    player.sendMessage(ColorUtil.colorize(
                        "&bPROTECCIONES &f» &c¡No puedes mostrar el bloque de protección si hay un bloque colocado donde el bloque de protección debería estar ubicado!"));
                    return;
                }
                region.unhide();
                player.sendMessage(ChatColor.GREEN + "Tu proteccion ahora es visible.");
            } else {
                region.hide();
                player.sendMessage(ChatColor.GREEN + "Tu proteccion ahora esta oculta.");
            }
            // Requerimiento técnico (este ajuste): si el jugador tiene
            // "Ver límites" activo en este momento, la entidad de brillo
            // (BlockDisplay/Shulker) se actualiza EN EL ACTO, en el mismo
            // tick — antes había que esperar hasta el próximo ciclo del
            // temporizador (cada 0.5s), lo que se notaba como una demora.
            HomeEditorMenu.syncVisibility(plugin, player, region);
            HomeEditorMenu.open(plugin, player, region);
            return;
        }
        if (name.contains("Renombrar Protección")) {
            player.closeInventory();
            askForNewName(player, region);
            return;
        }
        if (name.contains("Teletransporte")) {
            RegionManageMenu.teleportToCenter(player, region);
            player.closeInventory();
            return;
        }
        if (name.contains("Colocar Home")) {
            // Equivale a pararse en el lugar deseado y escribir /ps sethome.
            // No lo tocamos con teletransportes propios porque el punto es
            // justamente la ubicación ACTUAL del jugador dentro de la
            // protección — ProtectionStones valida el resto (permiso
            // protectionstones.sethome, que esté parado en la región, etc).
            player.closeInventory();
            plugin.getServer().dispatchCommand(player, "ps sethome");
            return;
        }
        if (name.contains("Remover protección")) {
            // Permiso separado del de gestionar: alguien puede tener
            // oasispsmenu.admin.manage (para editar) sin necesariamente
            // tener permiso para remover — eso requiere de forma explícita
            // ser dueño, miembro, o tener oasispsmenu.admin.remove.
            boolean puedeRemover = region.getWGRegion().getOwners().contains(player.getUniqueId())
                    || region.getWGRegion().getMembers().contains(player.getUniqueId())
                    || player.hasPermission("oasispsmenu.admin.remove");
            if (!puedeRemover) {
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                player.sendMessage(ChatColor.RED + "No tenés permiso para remover esta protección.");
                return;
            }
            RemoveConfirmMenu.open(plugin, player, region);
            return;
        }
        if (name.equals("Ver límites")) {
            HomeEditorMenu.showBorders(plugin, player, region);
            return;
        }
        if (name.contains("Lista de Propietarios")) {
            PeopleMenu.open(plugin, player, region, true);
            return;
        }
        if (name.contains("Lista de Miembros")) {
            PeopleMenu.open(plugin, player, region, false);
            return;
        }
        if (name.contains("Regresar")) {
            SelectorMenu.open(plugin, player);
            return;
        }
        if (name.contains("Ver todas tus protecciones")) {
            HomeMenu.open(plugin, player, 0);
            return;
        }
    }

    // ------------------------------------------------------------------ CONFIRMACIÓN DE REMOVER

    private void handleRemoveConfirmClick(Player player, MenuHolder holder, ItemStack clicked) {
        PSRegion region = PSLookup.getRegionById(holder.getWorldName(), holder.getRegionId());
        if (region == null) {
            player.sendMessage(ChatColor.RED + "Esa proteccion ya no existe.");
            player.closeInventory();
            return;
        }

        if (clicked.getType() == Material.ARROW) {
            HomeEditorMenu.open(plugin, player, region);
            return;
        }

        if (clicked.getType() == Material.RED_CONCRETE) {
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Cancelado. Tu protección sigue intacta.");
            return;
        }

        if (clicked.getType() == Material.LIME_CONCRETE) {
            // Segundo chequeo de permiso (defensa en profundidad, por si en el
            // futuro se agrega otra forma de llegar a este menú) — mismo
            // criterio que al abrir la confirmación: dueño, miembro, o
            // oasispsmenu.admin.remove.
            boolean puedeRemover = region.getWGRegion().getOwners().contains(player.getUniqueId())
                    || region.getWGRegion().getMembers().contains(player.getUniqueId())
                    || player.hasPermission("oasispsmenu.admin.remove");
            if (!puedeRemover) {
                player.closeInventory();
                player.sendMessage(ChatColor.RED + "No tenés permiso para remover esta protección.");
                return;
            }

            // Punto 1 (27/08, ajuste 2): antes esto teletransportaba al
            // jugador al centro de la protección para poder ejecutar
            // /ps unclaim (que por defecto exige estar parado adentro).
            // Desde ProtectionStones 2.10+ existe la variante remota
            // "/ps unclaim <id>" (permiso protectionstones.unclaim.remote),
            // que hace exactamente lo mismo SIN moverte de donde estás.
            //
            // Si el inventario está lleno, no ejecutamos nada — el jugador
            // tiene que liberar un espacio y volver a intentar. Así el
            // bloque nunca se pierde ni queda tirado en el piso.
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(ChatColor.RED + "Tu inventario está lleno. Liberá un espacio e intentá de nuevo para remover la protección.");
                return;
            }
            player.closeInventory();
            plugin.getServer().dispatchCommand(player, "ps unclaim " + region.getWGRegion().getId());
        }
    }

    // ------------------------------------------------------------------ FLAGS

    private void handleFlagsClick(Player player, MenuHolder holder, ItemStack clicked, ClickType click, int slot) {
        PSRegion region = PSLookup.getRegionById(holder.getWorldName(), holder.getRegionId());
        if (region == null) return;

        me.oasisland.oasispsmenu.data.FlagsConfig cfg = plugin.getFlagsConfig();

        // Slot 48: Regresar -> vuelve al Editor de la protección
        if (slot == cfg.getBackButtonSlot()) {
            HomeEditorMenu.open(plugin, player, region);
            return;
        }

        // Slot 50: navegación de página (el mismo botón cambia de sentido
        // según en qué página estemos — ver comentario en FlagsMenu.open)
        if (slot == cfg.getPageNavSlot() && clicked.getType() == Material.ARROW) {
            int totalPages = Math.max(1, (int) Math.ceil(
                    cfg.getOrderedFlagKeys().size() / (double) Math.max(1, cfg.getIconSlots().size())));
            int nextPage = holder.getPage() < totalPages - 1 ? holder.getPage() + 1 : holder.getPage() - 1;
            FlagsMenu.open(plugin, player, region, nextPage);
            return;
        }

        // Slot 49: indicador de página, puramente informativo
        if (slot == cfg.getPageInfoSlot()) return;

        // Es un flag: buscar por slot en el mapa
        String flagKey = holder.getSlotFlagMap() != null ? holder.getSlotFlagMap().get(slot) : null;
        if (flagKey == null) return;

        // Flags de TEXTO (greeting, farewell, etc): cualquier click abre
        // un prompt de chat en vez de ciclar grupo/estado.
        if (FlagsMenu.isTextFlag(plugin, flagKey)) {
            player.closeInventory();
            askForFlagText(player, region, flagKey, holder.getPage());
            return;
        }

        if (click.isLeftClick()) {
            FlagsMenu.cycleGroup(plugin, region, flagKey);
        } else if (click.isRightClick()) {
            FlagsMenu.cycleState(region, flagKey);
        }

        FlagsMenu.open(plugin, player, region, holder.getPage());
    }

    /** Prompt de chat para escribir (o borrar) el texto de un flag tipo StringFlag. */
    private void askForFlagText(Player player, PSRegion region, String flagKey, int page) {
        player.sendMessage(ColorUtil.colorize("&eEscribí el nuevo texto &7(podés usar & para colores)&e, &c'borrar'&e para quitarlo, o &c'cancelar'&e:"));

        plugin.getChatInputListener().prompt(player, input -> {
            if (input.equalsIgnoreCase("cancelar")) {
                player.sendMessage(ColorUtil.colorize("&cCancelado."));
                PSRegion r = PSLookup.getRegionById(region.getWorld().getName(), region.getWGRegion().getId());
                if (r != null) FlagsMenu.open(plugin, player, r, page);
                return;
            }

            com.sk89q.worldguard.protection.flags.registry.FlagRegistry registry =
                    com.sk89q.worldguard.WorldGuard.getInstance().getFlagRegistry();
            com.sk89q.worldguard.protection.flags.Flag<?> flag = registry.get(flagKey);

            if (flag instanceof com.sk89q.worldguard.protection.flags.StringFlag stringFlag) {
                PSRegion r = PSLookup.getRegionById(region.getWorld().getName(), region.getWGRegion().getId());
                if (r != null) {
                    if (input.equalsIgnoreCase("borrar")) {
                        r.getWGRegion().setFlag(stringFlag, null);
                        player.sendMessage(ColorUtil.colorize("&aTexto eliminado."));
                    } else {
                        r.getWGRegion().setFlag(stringFlag, ChatColor.translateAlternateColorCodes('&', input));
                        player.sendMessage(ColorUtil.colorize("&aTexto actualizado."));
                    }
                }
            }

            PSRegion r = PSLookup.getRegionById(region.getWorld().getName(), region.getWGRegion().getId());
            if (r != null) FlagsMenu.open(plugin, player, r, page);
        });
    }

    // ------------------------------------------------------------------ MIEMBROS / PROPIETARIOS
    // (owners = true -> gestiona getOwners(); owners = false -> gestiona getMembers())

    private void handlePeopleClick(Player player, MenuHolder holder, ItemStack clicked, int slot, boolean owners) {
        PSRegion region = PSLookup.getRegionById(holder.getWorldName(), holder.getRegionId());
        if (region == null) return;

        if (clicked.getType() == Material.ARROW) {
            HomeEditorMenu.open(plugin, player, region);
            return;
        }

        if (clicked.getType() == Material.LIME_DYE) {
            player.closeInventory();
            askPersonName(player, region, owners);
            return;
        }

        if (clicked.getItemMeta() instanceof SkullMeta skullMeta && skullMeta.getOwningPlayer() != null) {
            OfflinePlayer target = skullMeta.getOwningPlayer();
            if (owners) {
                region.getWGRegion().getOwners().removePlayer(target.getUniqueId());
            } else {
                region.getWGRegion().getMembers().removePlayer(target.getUniqueId());
            }
            player.sendMessage(ChatColor.GREEN + "Quitaste a " + target.getName() + " de la proteccion.");
            PeopleMenu.open(plugin, player, region, owners);
        }
    }

    // ------------------------------------------------------------------ BANEADOS

    private void handleBansClick(Player player, MenuHolder holder, ItemStack clicked, int slot) {
        PSRegion region = PSLookup.getRegionById(holder.getWorldName(), holder.getRegionId());
        if (region == null) return;

        // Slot 48: Regresar
        if (slot == 48 && clicked.getType() == Material.ARROW) {
            HomeEditorMenu.open(plugin, player, region);
            return;
        }

        // Slot 49: Cerrar
        if (slot == 49 && clicked.getType() == Material.BOOK) {
            player.closeInventory();
            return;
        }

        // Slot 50: Añadir baneado
        if (slot == 50 && clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            askBanName(player, region);
            return;
        }

        if (clicked.getItemMeta() instanceof SkullMeta skullMeta && skullMeta.getOwningPlayer() != null) {
            OfflinePlayer target = skullMeta.getOwningPlayer();
            plugin.getDataStore().unban(region.getWGRegion().getId(), target.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "Le quitaste el ban a " + target.getName() + " en esta proteccion.");
            BannedMenu.open(plugin, player, region);
        }
    }

    // ------------------------------------------------------------------ PROMPTS DE CHAT

    private void askForNewName(Player player, PSRegion region) {
        player.sendMessage(ColorUtil.colorize("&eEscribí el nuevo nombre de tu protección &7(máx. 40 caracteres)&e, o escribe &c'cancelar'&e:"));

        plugin.getChatInputListener().prompt(player, input -> {
            if (input.equalsIgnoreCase("cancelar")) {
                player.sendMessage(ColorUtil.colorize("&cCancelado."));
                return;
            }
            // Validación: máximo 40 caracteres
            if (input.trim().length() > 40) {
                player.sendMessage(ColorUtil.colorize(
                    "&cEl nombre es demasiado largo. Máximo &f40 caracteres &c(usaste &f"
                    + input.trim().length() + "&c). Intentá de nuevo."));
                askForNewName(player, region); // vuelve a pedir
                return;
            }
            region.setName(input.trim());
            player.sendMessage(ColorUtil.colorize("&aProtección renombrada a: &f" + input.trim()));
            HomeEditorMenu.open(plugin, player, region);
        });
    }

    private void askForPriority(Player player, PSRegion region) {
        player.sendMessage(ColorUtil.colorize("&eEscribí la nueva prioridad de tu protección &7(número entero, mayor = más importante)&e, o escribe &c'cancelar'&e. Prioridad actual: &f"
                + region.getWGRegion().getPriority()));

        plugin.getChatInputListener().prompt(player, input -> {
            if (input.equalsIgnoreCase("cancelar")) {
                player.sendMessage(ColorUtil.colorize("&cCancelado."));
                return;
            }
            int nueva;
            try {
                nueva = Integer.parseInt(input.trim());
            } catch (NumberFormatException e) {
                player.sendMessage(ColorUtil.colorize("&cEso no es un número válido. Intentá de nuevo."));
                askForPriority(player, region); // vuelve a pedir
                return;
            }
            region.getWGRegion().setPriority(nueva);
            player.sendMessage(ColorUtil.colorize("&aPrioridad actualizada a: &f" + nueva));
            HomeEditorMenu.open(plugin, player, region);
        });
    }

    /**
     * Punto 1 (28/08): resuelve un nombre de jugador a un UUID real,
     * verificando que haya jugado en ESTE servidor antes de aceptarlo
     * (ni para banear ni para agregar como miembro/propietario tiene
     * sentido aceptar a alguien que el servidor nunca vio).
     *
     * - Si está conectado ahora mismo: se acepta directo, ya está claro
     *   que jugó acá.
     * - Si no está conectado: se resuelve el perfil REAL contra Mojang
     *   (Server#createProfile().update(), asíncrono — mismo mecanismo
     *   que ya usábamos para la skin del baneo) y ahí sí se chequea
     *   OfflinePlayer#hasPlayedBefore() contra los datos de ESTE server.
     *
     * onResolved se ejecuta en el hilo principal con (uuid, nombre) si
     * todo salió bien. onFailed se ejecuta en el hilo principal con el
     * mensaje de error si no.
     */
    private void resolvePlayedBefore(String rawName,
                                      java.util.function.BiConsumer<UUID, String> onResolved,
                                      java.util.function.Consumer<String> onFailed) {
        String targetName = rawName.trim();
        Player online = plugin.getServer().getPlayer(targetName);
        if (online != null) {
            onResolved.accept(online.getUniqueId(), online.getName());
            return;
        }

        com.destroystokyo.paper.profile.PlayerProfile profile = plugin.getServer().createProfile(targetName);
        profile.update().thenAccept(updated ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (updated.getId() == null) {
                        onFailed.accept("No se encontró ningún jugador con ese nombre.");
                        return;
                    }
                    OfflinePlayer offline = plugin.getServer().getOfflinePlayer(updated.getId());
                    if (!offline.hasPlayedBefore()) {
                        onFailed.accept("Ese jugador no ha jugado en el servidor antes.");
                        return;
                    }
                    onResolved.accept(updated.getId(), updated.getName() != null ? updated.getName() : targetName);
                })
        );
    }

    private void askPersonName(Player player, PSRegion region, boolean owners) {
        player.sendMessage(ChatColor.YELLOW + "Escribi el nombre del jugador a agregar como "
                + (owners ? "propietario" : "miembro") + " (o 'cancel'):");

        plugin.getChatInputListener().prompt(player, input -> {
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.RED + "Cancelado.");
                PSRegion r = PSLookup.getRegionById(region.getWorld().getName(), region.getWGRegion().getId());
                if (r != null) PeopleMenu.open(plugin, player, r, owners);
                return;
            }

            resolvePlayedBefore(input,
                    (uuid, name) -> {
                        if (owners) {
                            region.getWGRegion().getOwners().addPlayer(uuid);
                        } else {
                            region.getWGRegion().getMembers().addPlayer(uuid);
                        }
                        player.sendMessage(ChatColor.GREEN + "Agregaste a " + name
                                + " como " + (owners ? "propietario" : "miembro") + ".");
                        PSRegion r = PSLookup.getRegionById(region.getWorld().getName(), region.getWGRegion().getId());
                        if (r != null) PeopleMenu.open(plugin, player, r, owners);
                    },
                    errorMsg -> {
                        player.sendMessage(ChatColor.RED + errorMsg);
                        PSRegion r = PSLookup.getRegionById(region.getWorld().getName(), region.getWGRegion().getId());
                        if (r != null) PeopleMenu.open(plugin, player, r, owners);
                    });
        });
    }

    /**
     * Prompt de chat para banear a un jugador nuevo.
     *
     * Punto 5 (28/08): antes esto usaba la palabra "cancelar" para
     * cancelar el proceso — pero un jugador podría llamarse justo así,
     * y quedaría sin forma de banearlo. Ahora usa "cancel" (en inglés),
     * como en la captura que mandó Pablo — mucho menos probable que
     * choque con un nombre real, aunque no es 100% imposible tampoco.
     *
     * Punto 6-7 (28/08): la resolución de UUID/skin real (para no
     * mostrar la skin de Steve) vive ahora en resolvePlayedBefore(),
     * compartida con el flujo de agregar miembro/propietario.
     *
     * Punto 1 (28/08, ajuste siguiente): resolvePlayedBefore() también
     * rechaza a cualquiera que nunca haya jugado en este servidor —
     * ni para banear ni para agregar tiene sentido aceptar un nombre
     * que el servidor nunca vio.
     */
    private void askBanName(Player player, PSRegion region) {
        player.sendMessage(ColorUtil.colorize("&aEscribe el nombre del jugador o &ccancel&a para cancelar el proceso."));

        plugin.getChatInputListener().prompt(player, input -> {
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.RED + "Cancelado.");
                PSRegion r = PSLookup.getRegionById(region.getWorld().getName(), region.getWGRegion().getId());
                if (r != null) BannedMenu.open(plugin, player, r);
                return;
            }

            Player targetOnline = plugin.getServer().getPlayer(input.trim());

            resolvePlayedBefore(input,
                    (uuid, name) -> {
                        banAndNotify(player, region, uuid, name);

                        // Punto 1 (28/08, ajuste anterior): solo lo movemos si
                        // está parado adentro de la protección ahora mismo, y lo
                        // sacamos por el borde más cercano — no al spawn del mundo.
                        if (targetOnline != null) {
                            var loc = targetOnline.getLocation();
                            var blockPos = com.sk89q.worldedit.math.BlockVector3.at(
                                    loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                            if (region.getWGRegion().contains(blockPos)) {
                                targetOnline.teleport(computeEdgeExit(region, loc));
                            }
                            targetOnline.sendMessage(ChatColor.RED + "Fuiste banneado de la proteccion de " + player.getName() + ".");
                        }
                    },
                    errorMsg -> {
                        player.sendMessage(ChatColor.RED + errorMsg);
                        PSRegion r = PSLookup.getRegionById(region.getWorld().getName(), region.getWGRegion().getId());
                        if (r != null) BannedMenu.open(plugin, player, r);
                    });
        });
    }

    /**
     * Punto 1 (28/08): calcula un punto seguro JUSTO AFUERA del borde
     * más cercano de la protección, para sacar de adentro a alguien
     * recién baneado (en vez de mandarlo al spawn del mundo).
     *
     * Cómo funciona: comparamos la distancia del jugador a los 4
     * bordes de la protección (oeste, este, norte, sur) y lo movemos
     * 2 bloques más allá del que tenga más cerca — así siempre sale
     * por el lado más corto, cualquiera sea.
     *
     * La altura (Y) del punto de salida se calcula con el bloque más
     * alto de esa columna en el mundo real (no la Y actual del
     * jugador), para no dejarlo flotando en el aire ni metido dentro
     * de un bloque si esa zona tiene otra construcción con distinta
     * altura.
     *
     * SUPUESTO: como el resto del plugin, asume que la protección
     * cubre toda la altura del mundo (comportamiento por defecto de
     * ProtectionStones) — por eso solo miramos X/Z, no Y, para decidir
     * el borde más cercano.
     */
    private org.bukkit.Location computeEdgeExit(PSRegion region, org.bukkit.Location current) {
        var min = region.getWGRegion().getMinimumPoint();
        var max = region.getWGRegion().getMaximumPoint();
        org.bukkit.World world = current.getWorld();

        double px = current.getX();
        double pz = current.getZ();

        double distWest  = px - min.x();
        double distEast  = (max.x() + 1) - px;
        double distNorth = pz - min.z();
        double distSouth = (max.z() + 1) - pz;

        double smallest = Math.min(Math.min(distWest, distEast), Math.min(distNorth, distSouth));

        int targetBlockX;
        int targetBlockZ;

        if (smallest == distWest) {
            targetBlockX = min.x() - 2;
            targetBlockZ = current.getBlockZ();
        } else if (smallest == distEast) {
            targetBlockX = max.x() + 2;
            targetBlockZ = current.getBlockZ();
        } else if (smallest == distNorth) {
            targetBlockX = current.getBlockX();
            targetBlockZ = min.z() - 2;
        } else {
            targetBlockX = current.getBlockX();
            targetBlockZ = max.z() + 2;
        }

        int safeY = world.getHighestBlockYAt(targetBlockX, targetBlockZ) + 1;

        return new org.bukkit.Location(world, targetBlockX + 0.5, safeY, targetBlockZ + 0.5,
                current.getYaw(), current.getPitch());
    }

    /** Banea, avisa por chat, y reabre BannedMenu con la lista actualizada. */
    private void banAndNotify(Player player, PSRegion region, UUID targetUuid, String targetName) {
        plugin.getDataStore().ban(region.getWGRegion().getId(), targetUuid);
        player.sendMessage(ChatColor.GREEN + "Banneaste a " + targetName + " de esta proteccion.");
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            PSRegion r = PSLookup.getRegionById(region.getWorld().getName(), region.getWGRegion().getId());
            if (r != null) BannedMenu.open(plugin, player, r);
        });
    }
}
