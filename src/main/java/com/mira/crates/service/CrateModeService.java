package com.mira.crates.service;

import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.model.CrateOpeningMode;
import com.mira.crates.util.Ids;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Persists the per-crate RANDOM/CHOICE opening mode without disturbing existing crate definitions. */
public final class CrateModeService {
    private final File file;
    private final Map<String, CrateOpeningMode> modes = new LinkedHashMap<>();

    public CrateModeService(MiraCratesPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "opening-modes.yml");
        reload();
    }

    public void reload() {
        modes.clear();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var root = yaml.getConfigurationSection("modes");
        if (root == null) return;
        for (String rawId : root.getKeys(false)) {
            String id = Ids.normalize(rawId);
            String rawMode = root.getString(rawId, "RANDOM");
            try {
                modes.put(id, CrateOpeningMode.valueOf(rawMode.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                modes.put(id, CrateOpeningMode.RANDOM);
            }
        }
    }

    public CrateOpeningMode mode(String crateId) {
        return modes.getOrDefault(Ids.normalize(crateId), CrateOpeningMode.RANDOM);
    }

    public boolean isChoice(String crateId) {
        return mode(crateId) == CrateOpeningMode.CHOICE;
    }

    public CrateOpeningMode toggle(String crateId) {
        CrateOpeningMode next = isChoice(crateId) ? CrateOpeningMode.RANDOM : CrateOpeningMode.CHOICE;
        set(crateId, next);
        return next;
    }

    public void set(String crateId, CrateOpeningMode mode) {
        String id = Ids.normalize(crateId);
        if (mode == null || mode == CrateOpeningMode.RANDOM) modes.remove(id);
        else modes.put(id, mode);
        save();
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, CrateOpeningMode> entry : modes.entrySet()) {
            if (entry.getValue() != CrateOpeningMode.RANDOM) {
                yaml.set("modes." + entry.getKey(), entry.getValue().name());
            }
        }
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            yaml.save(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save " + file.getName(), ex);
        }
    }
}
