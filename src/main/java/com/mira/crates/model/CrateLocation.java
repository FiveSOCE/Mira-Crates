package com.mira.crates.model;

import org.bukkit.block.Block;

public record CrateLocation(String world, int x, int y, int z, String crateId) {
    public static CrateLocation from(Block block, String crateId) {
        return new CrateLocation(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(), crateId);
    }

    public String blockKey() {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
