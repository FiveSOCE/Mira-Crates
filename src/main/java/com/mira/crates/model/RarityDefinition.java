package com.mira.crates.model;

import org.bukkit.Material;

public record RarityDefinition(String id, String displayName, double weight, Material icon) {
}
