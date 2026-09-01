package com.mira.crates.model;

import org.bukkit.Material;

import java.util.List;

public record CrateDefinition(
        String id,
        String displayName,
        Material icon,
        List<String> keyIds,
        long cooldownSeconds,
        List<RewardDefinition> rewards
) {
    public CrateDefinition {
        keyIds = List.copyOf(keyIds);
        rewards = List.copyOf(rewards);
    }
}
