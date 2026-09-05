package com.mira.crates.listener;

import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.gui.CrateEditorService;
import com.mira.crates.gui.EditorMenuService;
import com.mira.crates.gui.MiraInventoryHolder;
import com.mira.crates.gui.PreviewService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class MenuListener implements Listener {
    private final MiraCratesPlugin plugin;
    private final EditorMenuService editor;
    private final CrateEditorService crateEditor;
    private final PreviewService previews;

    public MenuListener(MiraCratesPlugin plugin, EditorMenuService editor, CrateEditorService crateEditor, PreviewService previews) {
        this.plugin = plugin;
        this.editor = editor;
        this.crateEditor = crateEditor;
        this.previews = previews;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MiraInventoryHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        boolean playerFacing = holder.type() == MiraInventoryHolder.Type.PREVIEW || holder.type() == MiraInventoryHolder.Type.OPENING;
        if (!playerFacing && !player.hasPermission("miracrates.admin")) {
            event.setCancelled(true);
            player.closeInventory();
            plugin.core().messages().send(player, "&cYou do not have permission to administer MiraCrates.");
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        int rawSlot = event.getRawSlot();

        if (rawSlot >= topSize) {
            if (holder.type() == MiraInventoryHolder.Type.CRATE_EDITOR
                    && player.hasPermission("miracrates.admin")
                    && (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT)) {
                return;
            }
            event.setCancelled(true);
            return;
        }

        if (rawSlot < 0) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        if (holder.type() == MiraInventoryHolder.Type.OPENING) return;
        if (holder.type() == MiraInventoryHolder.Type.PREVIEW) {
            previews.handleClick(player, holder, rawSlot);
            return;
        }
        if (holder.type() == MiraInventoryHolder.Type.CRATE_EDITOR
                || holder.type() == MiraInventoryHolder.Type.CRATE_CHANCE) {
            crateEditor.handleClick(player, holder, event);
            return;
        }
        editor.handleClick(player, holder, event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        boolean nameInput = crateEditor.isAwaitingName(player.getUniqueId());
        boolean commandInput = crateEditor.isAwaitingCommand(player.getUniqueId());
        if (!nameInput && !commandInput) return;

        event.setCancelled(true);
        event.getRecipients().clear();
        String input = event.getMessage();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (commandInput) crateEditor.submitChatCommand(player, input);
            else crateEditor.submitChatName(player, input);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        crateEditor.cancelNameInput(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MiraInventoryHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        boolean playerFacing = holder.type() == MiraInventoryHolder.Type.PREVIEW || holder.type() == MiraInventoryHolder.Type.OPENING;
        if (!playerFacing && !player.hasPermission("miracrates.admin")) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        if (holder.type() == MiraInventoryHolder.Type.CRATE_EDITOR) {
            int topSize = event.getView().getTopInventory().getSize();
            boolean touchesEditor = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
            if (!touchesEditor) return;
        }

        event.setCancelled(true);
    }
}
