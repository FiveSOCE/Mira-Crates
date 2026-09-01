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

        int topSize = event.getView().getTopInventory().getSize();
        int rawSlot = event.getRawSlot();

        // The crate editor intentionally lets admins use normal left/right clicks in
        // their own inventory so they can put a reward item on the cursor. All
        // transfer-style clicks remain blocked so editor items cannot be moved.
        if (rawSlot >= topSize) {
            if (holder.type() == MiraInventoryHolder.Type.CRATE_EDITOR
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
        if (!crateEditor.isAwaitingName(player.getUniqueId())) return;

        // This message is editor input, not chat. Cancel it before normal chat
        // processing and remove all recipients so it is not broadcast to anyone.
        event.setCancelled(true);
        event.getRecipients().clear();
        String crateName = event.getMessage();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            crateEditor.submitChatName(player, crateName);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        crateEditor.cancelNameInput(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MiraInventoryHolder holder)) return;

        if (holder.type() == MiraInventoryHolder.Type.CRATE_EDITOR) {
            int topSize = event.getView().getTopInventory().getSize();
            boolean touchesEditor = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
            if (!touchesEditor) return;
        }

        event.setCancelled(true);
    }
}
