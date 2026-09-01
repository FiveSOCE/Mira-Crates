package com.mira.crates.service;

import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.model.RewardRoll;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

public final class HistoryService {
    private final MiraCratesPlugin plugin;

    public HistoryService(MiraCratesPlugin plugin) {
        this.plugin = plugin;
    }

    public void record(Player player, String crateId, RewardRoll roll, String keyUsed) {
        if (!plugin.getConfig().getBoolean("history.enabled", true)) return;
        String line = Instant.now() + " player=" + player.getUniqueId() + " name=" + player.getName()
                + " crate=" + crateId + " rarity=" + roll.rarity().id() + " reward=" + roll.reward().id()
                + " key=" + (keyUsed == null ? "bypass" : keyUsed) + System.lineSeparator();
        try {
            Files.writeString(plugin.getDataFolder().toPath().resolve("opening-history.log"), line,
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not append opening history: " + ex.getMessage());
        }
    }
}
