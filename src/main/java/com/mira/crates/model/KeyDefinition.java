package com.mira.crates.model;

import org.bukkit.Material;

import java.util.List;

public record KeyDefinition(String id, String displayName, Material material, boolean virtual, List<String> lore) {
    public KeyDefinition {
        lore = List.copyOf(lore);
    }
}
