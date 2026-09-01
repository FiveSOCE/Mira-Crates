package com.mira.crates.gui;

import com.mira.core.api.MiraCore;
import com.mira.crates.model.CrateDefinition;
import com.mira.crates.model.RewardDefinition;
import com.mira.crates.service.DefinitionService;
import com.mira.crates.service.RewardEngine;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Comparator;
import java.util.List;

public final class PreviewService {
    private static final int PAGE_SIZE = 45;
    private final MiraCore core;
    private final DefinitionService definitions;
    private final RewardEngine rewards;

    public PreviewService(MiraCore core, DefinitionService definitions, RewardEngine rewards) {
        this.core = core;
        this.definitions = definitions;
        this.rewards = rewards;
    }

    public boolean open(Player player, String crateId, int requestedPage) {
        CrateDefinition crate = definitions.crate(crateId).orElse(null);
        if (crate == null) return false;
        List<RewardDefinition> visible = crate.rewards().stream()
                .filter(reward -> reward.permission() == null || reward.permission().isBlank() || player.hasPermission(reward.permission()))
                .sorted(Comparator.comparing(RewardDefinition::rarityId).thenComparing(RewardDefinition::id))
                .toList();
        int maxPage = Math.max(0, (visible.size() - 1) / PAGE_SIZE);
        int page = Math.max(0, Math.min(maxPage, requestedPage));

        MiraInventoryHolder holder = new MiraInventoryHolder(MiraInventoryHolder.Type.PREVIEW, crate.id(), page);
        Inventory inventory = Bukkit.createInventory(holder, 54, core.messages().parse(crate.displayName() + " &8Rewards"));
        holder.bind(inventory);
        int start = page * PAGE_SIZE;
        for (int slot = 0; slot < PAGE_SIZE && start + slot < visible.size(); slot++) {
            inventory.setItem(slot, rewards.displayItem(player, crate, visible.get(start + slot)));
        }
        inventory.setItem(48, GuiItems.item(Material.ARROW, core.messages().parse("&fPrevious Page"), List.of()));
        inventory.setItem(49, GuiItems.item(Material.BARRIER, core.messages().parse("&cClose"), List.of()));
        inventory.setItem(50, GuiItems.item(Material.ARROW, core.messages().parse("&fNext Page"), List.of()));
        player.openInventory(inventory);
        return true;
    }

    public void handleClick(Player player, MiraInventoryHolder holder, int rawSlot) {
        if (rawSlot == 48) open(player, holder.context(), holder.page() - 1);
        else if (rawSlot == 49) player.closeInventory();
        else if (rawSlot == 50) open(player, holder.context(), holder.page() + 1);
    }
}
