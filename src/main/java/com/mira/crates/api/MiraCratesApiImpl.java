package com.mira.crates.api;

import com.mira.crates.model.CrateDefinition;
import com.mira.crates.service.DefinitionService;
import com.mira.crates.service.KeyService;
import com.mira.crates.service.OpeningService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class MiraCratesApiImpl implements MiraCratesApi {
    private final DefinitionService definitions;
    private final KeyService keys;
    private final OpeningService openings;

    public MiraCratesApiImpl(DefinitionService definitions, KeyService keys, OpeningService openings) {
        this.definitions = definitions;
        this.keys = keys;
        this.openings = openings;
    }

    @Override
    public Set<String> crateIds() {
        return definitions.crates().stream().map(CrateDefinition::id).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<String> keyIds() {
        return definitions.keys().stream().map(key -> key.id()).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Optional<CrateSnapshot> crate(String crateId) {
        return definitions.crate(crateId).map(crate -> new CrateSnapshot(
                crate.id(), crate.displayName(), crate.keyIds(), crate.rewards().size()));
    }

    @Override
    public Optional<ItemStack> createKey(String keyId, int amount) {
        return keys.create(keyId, amount);
    }

    @Override
    public boolean giveKey(Player player, String keyId, int amount) {
        return keys.give(player, keyId, amount);
    }

    @Override
    public boolean openCrate(Player player, String crateId, boolean bypassRequirements) {
        return openings.attemptOpen(player, crateId, bypassRequirements);
    }
}
