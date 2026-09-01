package com.mira.crates.listener;

import com.mira.crates.gui.CrateEditorService;
import com.mira.crates.gui.EditorMenuService;
import com.mira.crates.gui.MiraInventoryHolder;
import com.mira.crates.gui.PreviewService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

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
                || holder.type() == MiraInventoryHolder.Type.CRATE_NAME
                || holder.type() == MiraInventoryHolder.Type.CRATE_CHANCE) {
            crateEditor.handleClick(player, holder, event);
            return;
        }
        editor.handleClick(player, holder, rawSlot);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MiraInventoryHolder holder)) return;
        if (holder.type() != MiraInventoryHolder.Type.CRATE_NAME) return;

        String rename = event.getInventory().getRenameText();
        if (rename == null || rename.isBlank()) {
            event.setResult(null);
            return;
        }

        String trimmed = rename.trim();
        if (trimmed.length() > 48) trimmed = trimmed.substring(0, 48);

        ItemStack result = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(Component.text(trimmed).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Click to confirm crate name").decoration(TextDecoration.ITALIC, false)
        ));
        result.setItemMeta(meta);

        event.getInventory().setRepairCost(0);
        event.setResult(result);
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
