package com.mira.crates.listener;

import com.mira.crates.gui.EditorMenuService;
import com.mira.crates.gui.MiraInventoryHolder;
import com.mira.crates.gui.PreviewService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {
    private final EditorMenuService editor;
    private final PreviewService previews;

    public MenuListener(EditorMenuService editor, PreviewService previews) {
        this.editor = editor;
        this.previews = previews;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MiraInventoryHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (holder.type() == MiraInventoryHolder.Type.OPENING) return;
        if (holder.type() == MiraInventoryHolder.Type.PREVIEW) previews.handleClick(player, holder, event.getRawSlot());
        else editor.handleClick(player, holder, event.getRawSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MiraInventoryHolder) event.setCancelled(true);
    }
}
