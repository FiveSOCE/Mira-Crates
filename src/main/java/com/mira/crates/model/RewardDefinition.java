package com.mira.crates.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record RewardDefinition(
        String id,
        RewardType type,
        String rarityId,
        double weight,
        String displayName,
        Material icon,
        int amount,
        String permission,
        boolean broadcast,
        ItemStack item,
        String data
) {
    public RewardDefinition {
        if (item != null) item = item.clone();
    }

    @Override
    public ItemStack item() {
        return item == null ? null : item.clone();
    }
}
