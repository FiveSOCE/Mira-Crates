package com.mira.crates.service;

import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.model.CrateDefinition;
import com.mira.crates.model.RewardDefinition;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

public final class JackpotService {
    private final MiraCratesPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public JackpotService(MiraCratesPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "jackpots.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void record(Player player, CrateDefinition crate, RewardDefinition reward) {
        data.set("latest.player", player.getName());
        data.set("latest.uuid", player.getUniqueId().toString());
        data.set("latest.crate", crate.id());
        data.set("latest.reward", reward.displayName());
        data.set("latest.rarity", reward.rarityId());
        data.set("latest.time", System.currentTimeMillis());
        save();
    }

    public String player() { return data.getString("latest.player", "None"); }
    public String reward() { return data.getString("latest.reward", "None"); }
    public String crate() { return data.getString("latest.crate", "None"); }
    public String rarity() { return data.getString("latest.rarity", "None"); }

    private void save() {
        try { data.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Could not save jackpots.yml: " + ex.getMessage()); }
    }
}
