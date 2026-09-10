package com.mira.crates.gui;

import com.mira.core.api.MiraCore;
import com.mira.crates.model.CrateDefinition;
import com.mira.crates.model.RewardDefinition;
import com.mira.crates.service.CrateModeService;
import com.mira.crates.service.DefinitionService;
import com.mira.crates.service.RewardEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class PreviewService {
    private static final int PAGE_SIZE = 45;
    private final MiraCore core;
    private final DefinitionService definitions;
    private final RewardEngine rewards;
    private final CrateModeService modes;

    public PreviewService(MiraCore core, DefinitionService definitions, RewardEngine rewards, CrateModeService modes) {
        this.core = core;
        this.definitions = definitions;
        this.rewards = rewards;
        this.modes = modes;
    }

    public boolean open(Player player, String crateId, int requestedPage) {
        CrateDefinition crate = definitions.crate(crateId).orElse(null);
        if (crate == null) return false;
        List<RewardDefinition> visible = crate.rewards().stream()
                .filter(reward -> reward.permission() == null || reward.permission().isBlank()
                        || player.hasPermission(reward.permission()))
                .toList();
        int maxPage = Math.max(0, (visible.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(maxPage, requestedPage));

        MiraInventoryHolder holder = new MiraInventoryHolder(MiraInventoryHolder.Type.PREVIEW, crate.id(), page);
        Inventory inventory = Bukkit.createInventory(holder, 54, core.messages().parse(crate.displayName() + " &8Rewards"));
        holder.bind(inventory);
        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < visible.size(); slot++) {
            ItemStack item = rewards.displayItem(player, crate, visible.get(start + slot));
            if (modes.isChoice(crate.id())) item = asChoicePreview(item);
            inventory.setItem(slot, item);
        }
        inventory.setItem(48, GuiItems.item(Material.ARROW, core.messages().parse("&fPrevious Page"), List.of()));
        inventory.setItem(49, GuiItems.item(Material.BARRIER, core.messages().parse("&cClose"), List.of()));
        inventory.setItem(50, GuiItems.item(Material.ARROW, core.messages().parse("&fNext Page"), List.of()));
        if (modes.isChoice(crate.id())) {
            inventory.setItem(53, GuiItems.item(Material.EMERALD,
                    core.messages().parse("&aChoice Crate"), List.of(
                            core.messages().parse("&7You choose your reward when opening this crate."),
                            core.messages().parse("&7Chance percentages do not apply."))));
        }
        player.openInventory(inventory);
        return true;
    }

    public void handleClick(Player player, MiraInventoryHolder holder, int rawSlot) {
        if (rawSlot == 48) open(player, holder.context(), holder.page() - 1);
        else if (rawSlot == 49) player.closeInventory();
        else if (rawSlot == 50) open(player, holder.context(), holder.page() + 1);
    }

    private ItemStack asChoicePreview(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.removeIf(line -> PlainTextComponentSerializer.plainText().serialize(line).startsWith("Chance: "));
        if (!lore.isEmpty()) lore.add(Component.empty());
        lore.add(core.messages().parse("&aSelectable Choice Reward").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
