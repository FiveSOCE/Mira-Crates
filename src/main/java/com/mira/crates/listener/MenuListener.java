package com.mira.crates.listener;

import com.mira.crates.gui.CrateEditorService;
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
    private final CrateEditorService crateEditor;
    private final PreviewService previews;

    public MenuListener(EditorMenuService editor, CrateEditorService crateEditor, PreviewService previews) {
        this.editor = editor;
        this.crateEditor = crateEditor;
        this.previews = previews;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MiraInventoryHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        if (holder.type() == MiraInventoryHolder.Type.OPENING) return;
        if (holder.type() == MiraInventoryHolder.Type.PREVIEW) {
            previews.handleClick(player, holder, event.getRawSlot());
            return;
        }
        if (holder.type() == MiraInventoryHolder.Type.CRATE_EDITOR
                || holder.type() == MiraInventoryHolder.Type.CRATE_NAME
                || holder.type() == MiraInventoryHolder.Type.CRATE_CHANCE) {
            crateEditor.handleClick(player, holder, event);
            return;
        }
        editor.handleClick(player, holder, event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MiraInventoryHolder) event.setCancelled(true);
    }
}
