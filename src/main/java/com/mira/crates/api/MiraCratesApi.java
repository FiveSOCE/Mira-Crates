package com.mira.crates.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Set;

public interface MiraCratesApi {
    Set<String> crateIds();
    Set<String> keyIds();
    Optional<CrateSnapshot> crate(String crateId);
    Optional<ItemStack> createKey(String keyId, int amount);
    boolean giveKey(Player player, String keyId, int amount);
    boolean openCrate(Player player, String crateId, boolean bypassRequirements);
}
