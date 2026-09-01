package com.mira.crates.service;

import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.util.Ids;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

public final class PlayerDataService {
    private final File file;
    private YamlConfiguration yaml;

    public PlayerDataService(MiraCratesPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!file.exists()) plugin.saveResource("playerdata.yml", false);
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public int virtualKeys(UUID playerId, String keyId) {
        return Math.max(0, yaml.getInt(path(playerId) + ".keys." + Ids.normalize(keyId), 0));
    }

    public void addVirtualKeys(UUID playerId, String keyId, int amount) {
        if (amount <= 0) return;
        String path = path(playerId) + ".keys." + Ids.normalize(keyId);
        yaml.set(path, virtualKeys(playerId, keyId) + amount);
        save();
    }

    public boolean consumeVirtualKey(UUID playerId, String keyId) {
        int current = virtualKeys(playerId, keyId);
        if (current <= 0) return false;
        yaml.set(path(playerId) + ".keys." + Ids.normalize(keyId), current - 1);
        save();
        return true;
    }

    public int openCount(UUID playerId, String crateId) {
        return Math.max(0, yaml.getInt(path(playerId) + ".opens." + Ids.normalize(crateId), 0));
    }

    public long lastOpenMillis(UUID playerId, String crateId) {
        return Math.max(0L, yaml.getLong(path(playerId) + ".last-open." + Ids.normalize(crateId), 0L));
    }

    public long cooldownRemainingSeconds(UUID playerId, String crateId, long cooldownSeconds) {
        if (cooldownSeconds <= 0L) return 0L;
        long elapsed = Instant.now().toEpochMilli() - lastOpenMillis(playerId, crateId);
        long remainingMs = cooldownSeconds * 1000L - elapsed;
        return remainingMs <= 0L ? 0L : (remainingMs + 999L) / 1000L;
    }

    public void markOpened(UUID playerId, String crateId) {
        String normalized = Ids.normalize(crateId);
        yaml.set(path(playerId) + ".opens." + normalized, openCount(playerId, normalized) + 1);
        yaml.set(path(playerId) + ".last-open." + normalized, Instant.now().toEpochMilli());
        save();
    }

    public void reload() {
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save playerdata.yml", ex);
        }
    }

    private static String path(UUID uuid) {
        return "players." + uuid;
    }
}
