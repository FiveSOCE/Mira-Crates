package com.mira.crates.model;

import org.bukkit.Material;

import java.util.List;

public record CrateDefinition(
        String id,
        String displayName,
        Material icon,
        List<String> keyIds,
        long cooldownSeconds,
        int winsPerOpen,
        List<RewardDefinition> rewards
) {
    public CrateDefinition {
        keyIds = List.copyOf(keyIds);
        winsPerOpen = Math.max(1, Math.min(5, winsPerOpen));
        rewards = List.copyOf(rewards);
    }

    public CrateDefinition(String id, String displayName, Material icon, List<String> keyIds,
                           long cooldownSeconds, List<RewardDefinition> rewards) {
        this(id, displayName, icon, keyIds, cooldownSeconds, 1, rewards);
    }
}
