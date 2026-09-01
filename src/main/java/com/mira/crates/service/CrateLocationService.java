package com.mira.crates.service;

import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.model.CrateLocation;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class CrateLocationService {
    private final MiraCratesPlugin plugin;
    private final File file;
    private final Map<String, CrateLocation> byBlock = new LinkedHashMap<>();

    public CrateLocationService(MiraCratesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "locations.yml");
        if (!file.exists()) plugin.saveResource("locations.yml", false);
        reload();
    }

    public void reload() {
        byBlock.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> row : yaml.getMapList("locations")) {
            try {
                String world = String.valueOf(row.get("world"));
                int x = Integer.parseInt(String.valueOf(row.get("x")));
                int y = Integer.parseInt(String.valueOf(row.get("y")));
                int z = Integer.parseInt(String.valueOf(row.get("z")));
                String crate = String.valueOf(row.get("crate"));
                CrateLocation location = new CrateLocation(world, x, y, z, crate);
                byBlock.put(location.blockKey(), location);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Skipping malformed crate location: " + row);
            }
        }
    }

    public Optional<CrateLocation> at(Block block) {
        return Optional.ofNullable(byBlock.get(key(block)));
    }

    public void set(Block block, String crateId) {
        CrateLocation location = CrateLocation.from(block, crateId);
        byBlock.put(location.blockKey(), location);
        save();
    }

    public boolean remove(Block block) {
        boolean removed = byBlock.remove(key(block)) != null;
        if (removed) save();
        return removed;
    }

    public int countForCrate(String crateId) {
        return (int) byBlock.values().stream().filter(location -> location.crateId().equalsIgnoreCase(crateId)).count();
    }

    public Collection<CrateLocation> all() {
        return Collections.unmodifiableCollection(byBlock.values());
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (CrateLocation location : byBlock.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("world", location.world());
            row.put("x", location.x());
            row.put("y", location.y());
            row.put("z", location.z());
            row.put("crate", location.crateId());
            rows.add(row);
        }
        yaml.set("locations", rows);
        try {
            yaml.save(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save locations.yml", ex);
        }
    }

    private static String key(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
