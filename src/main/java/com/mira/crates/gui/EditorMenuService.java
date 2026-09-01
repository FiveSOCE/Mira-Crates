package com.mira.crates.gui;

import com.mira.core.api.MiraCore;
import com.mira.crates.model.CrateDefinition;
import com.mira.crates.model.KeyDefinition;
import com.mira.crates.model.RarityDefinition;
import com.mira.crates.service.CrateItemService;
import com.mira.crates.service.CrateLocationService;
import com.mira.crates.service.DefinitionService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Comparator;
import java.util.List;

public final class EditorMenuService {
    private static final int PAGE_SIZE = 45;
    private final MiraCore core;
    private final DefinitionService definitions;
    private final CrateLocationService locations;
    private final PreviewService previews;
    private final CrateEditorService crateEditor;
    private final CrateItemService crateItems;

    public EditorMenuService(MiraCore core, DefinitionService definitions, CrateLocationService locations,
                             PreviewService previews, CrateEditorService crateEditor, CrateItemService crateItems) {
        this.core = core;
        this.definitions = definitions;
        this.locations = locations;
        this.previews = previews;
        this.crateEditor = crateEditor;
        this.crateItems = crateItems;
    }

    public void openMain(Player player) {
        MiraInventoryHolder holder = new MiraInventoryHolder(MiraInventoryHolder.Type.MAIN, "", 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, core.messages().parse("&5MiraCrates Editor"));
        holder.bind(inventory);
        inventory.setItem(10, GuiItems.item(Material.PURPLE_SHULKER_BOX, core.messages().parse("&dManage Crates"), List.of(
                core.messages().parse("&7Crates: &f" + definitions.crates().size()),
                core.messages().parse("&7Click to edit, preview or receive crates"))));
        inventory.setItem(12, GuiItems.item(Material.LIME_SHULKER_BOX, core.messages().parse("&aCreate New Crate"), List.of(
                core.messages().parse("&7Name, colour, reward items and chances"),
                core.messages().parse("&7are configured through GUIs."))));
        inventory.setItem(14, GuiItems.item(Material.TRIPWIRE_HOOK, core.messages().parse("&dKeys"), List.of(
                core.messages().parse("&7Definitions: &f" + definitions.keys().size()))));
        inventory.setItem(16, GuiItems.item(Material.NETHER_STAR, core.messages().parse("&dRarities"), List.of(
                core.messages().parse("&7Definitions: &f" + definitions.rarities().size()))));
        inventory.setItem(22, GuiItems.item(Material.BOOK, core.messages().parse("&fGUI-First Workflow"), List.of(
                core.messages().parse("&7Use &f/mcrates create &7or the green shulker."),
                core.messages().parse("&7Commands are kept mainly for diagnostics/admin recovery."))));
        player.openInventory(inventory);
    }

    public void openCreate(Player player) {
        crateEditor.startCreate(player);
    }

    public void handleClick(Player player, MiraInventoryHolder holder, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        switch (holder.type()) {
            case MAIN -> {
                if (rawSlot == 10) openCrates(player, 0);
                else if (rawSlot == 12) crateEditor.startCreate(player);
                else if (rawSlot == 14) openKeys(player, 0);
                else if (rawSlot == 16) openRarities(player, 0);
            }
            case CRATES -> handleCrateList(player, holder, event);
            case KEYS -> handlePagedNavigation(player, holder, rawSlot,
                    () -> openKeys(player, holder.page() - 1), () -> openKeys(player, holder.page() + 1));
            case RARITIES -> handlePagedNavigation(player, holder, rawSlot,
                    () -> openRarities(player, holder.page() - 1), () -> openRarities(player, holder.page() + 1));
            default -> { }
        }
    }

    private void openCrates(Player player, int requestedPage) {
        List<CrateDefinition> values = definitions.crates().stream().sorted(Comparator.comparing(CrateDefinition::id)).toList();
        int page = clampPage(requestedPage, values.size());
        MiraInventoryHolder holder = new MiraInventoryHolder(MiraInventoryHolder.Type.CRATES, "", page);
        Inventory inventory = Bukkit.createInventory(holder, 54, core.messages().parse("&5MiraCrates &8- Crates"));
        holder.bind(inventory);
        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < values.size(); slot++) {
            CrateDefinition crate = values.get(start + slot);
            inventory.setItem(slot, GuiItems.item(crate.icon(), core.messages().parse(crate.displayName()), List.of(
                    core.messages().parse("&7ID: &f" + crate.id()),
                    core.messages().parse("&7Rewards: &f" + crate.rewards().size()),
                    core.messages().parse("&7Placed: &f" + locations.countForCrate(crate.id())),
                    core.messages().parse("&aLeft-click: edit"),
                    core.messages().parse("&dShift-left: preview rewards"),
                    core.messages().parse("&eRight-click: give crate shulker"))));
        }
        nav(inventory);
        player.openInventory(inventory);
    }

    private void openKeys(Player player, int requestedPage) {
        List<KeyDefinition> values = definitions.keys().stream().sorted(Comparator.comparing(KeyDefinition::id)).toList();
        int page = clampPage(requestedPage, values.size());
        MiraInventoryHolder holder = new MiraInventoryHolder(MiraInventoryHolder.Type.KEYS, "", page);
        Inventory inventory = Bukkit.createInventory(holder, 54, core.messages().parse("&5MiraCrates &8- Keys"));
        holder.bind(inventory);
        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < values.size(); slot++) {
            KeyDefinition key = values.get(start + slot);
            inventory.setItem(slot, GuiItems.item(key.material(), core.messages().parse(key.displayName()), List.of(
                    core.messages().parse("&7ID: &f" + key.id()),
                    core.messages().parse("&7Type: &f" + (key.virtual() ? "Virtual" : "Physical")))));
        }
        nav(inventory);
        player.openInventory(inventory);
    }

    private void openRarities(Player player, int requestedPage) {
        List<RarityDefinition> values = definitions.rarities().stream().sorted(Comparator.comparing(RarityDefinition::id)).toList();
        int page = clampPage(requestedPage, values.size());
        MiraInventoryHolder holder = new MiraInventoryHolder(MiraInventoryHolder.Type.RARITIES, "", page);
        Inventory inventory = Bukkit.createInventory(holder, 54, core.messages().parse("&5MiraCrates &8- Rarities"));
        holder.bind(inventory);
        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < values.size(); slot++) {
            RarityDefinition rarity = values.get(start + slot);
            inventory.setItem(slot, GuiItems.item(rarity.icon(), core.messages().parse(rarity.displayName()), List.of(
                    core.messages().parse("&7ID: &f" + rarity.id()),
                    core.messages().parse("&7Weight: &f" + rarity.weight()))));
        }
        nav(inventory);
        player.openInventory(inventory);
    }

    private void handleCrateList(Player player, MiraInventoryHolder holder, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        if (rawSlot == 48) { openCrates(player, holder.page() - 1); return; }
        if (rawSlot == 49) { openMain(player); return; }
        if (rawSlot == 50) { openCrates(player, holder.page() + 1); return; }
        if (rawSlot < 0 || rawSlot >= PAGE_SIZE) return;
        List<CrateDefinition> values = definitions.crates().stream().sorted(Comparator.comparing(CrateDefinition::id)).toList();
        int index = holder.page() * PAGE_SIZE + rawSlot;
        if (index >= values.size()) return;
        CrateDefinition crate = values.get(index);
        if (event.getClick().isRightClick()) {
            crateItems.give(player, crate.id());
            core.messages().send(player, "&aGave you &f" + crate.id() + "&a crate.");
        } else if (event.getClick().isShiftClick()) {
            previews.open(player, crate.id(), 0);
        } else {
            crateEditor.openEdit(player, crate.id());
        }
    }

    private void handlePagedNavigation(Player player, MiraInventoryHolder holder, int rawSlot, Runnable previous, Runnable next) {
        if (rawSlot == 48) previous.run();
        else if (rawSlot == 49) openMain(player);
        else if (rawSlot == 50) next.run();
    }

    private void nav(Inventory inventory) {
        inventory.setItem(48, GuiItems.item(Material.ARROW, core.messages().parse("&fPrevious Page"), List.of()));
        inventory.setItem(49, GuiItems.item(Material.BARRIER, core.messages().parse("&cBack"), List.of()));
        inventory.setItem(50, GuiItems.item(Material.ARROW, core.messages().parse("&fNext Page"), List.of()));
    }

    private static int clampPage(int requested, int size) {
        int max = Math.max(0, (size - 1) / PAGE_SIZE);
        return Math.max(0, Math.min(max, requested));
    }
}
