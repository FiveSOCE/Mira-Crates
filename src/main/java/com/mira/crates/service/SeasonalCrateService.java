package com.mira.crates.service;

import com.mira.crates.MiraCratesPlugin;

import java.time.Instant;

public final class SeasonalCrateService {
    private final MiraCratesPlugin plugin;

    public SeasonalCrateService(MiraCratesPlugin plugin) { this.plugin = plugin; }

    public boolean active(String crateId) {
        String base = "seasonal-crates." + crateId + ".";
        if (!plugin.getConfig().getBoolean(base + "enabled", false)) return true;
        Instant now = Instant.now();
        Instant start = parse(plugin.getConfig().getString(base + "start", ""));
        Instant end = parse(plugin.getConfig().getString(base + "end", ""));
        if (start != null && now.isBefore(start)) return false;
        return end == null || now.isBefore(end);
    }

    public String window(String crateId) {
        String base = "seasonal-crates." + crateId + ".";
        if (!plugin.getConfig().getBoolean(base + "enabled", false)) return "always";
        return plugin.getConfig().getString(base + "start", "?") + " -> " + plugin.getConfig().getString(base + "end", "?");
    }

    private Instant parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return Instant.parse(raw.trim()); }
        catch (Exception ex) {
            plugin.getLogger().warning("Invalid seasonal crate timestamp: " + raw + " (use ISO-8601, e.g. 2026-12-01T00:00:00Z)");
            return null;
        }
    }
}
