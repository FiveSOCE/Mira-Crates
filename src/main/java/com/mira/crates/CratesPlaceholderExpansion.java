package com.mira.crates;

import com.mira.crates.service.JackpotService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public final class CratesPlaceholderExpansion extends PlaceholderExpansion {
    private final MiraCratesPlugin plugin;
    private final JackpotService jackpots;

    public CratesPlaceholderExpansion(MiraCratesPlugin plugin, JackpotService jackpots) {
        this.plugin = plugin;
        this.jackpots = jackpots;
    }

    @Override public @NotNull String getIdentifier() { return "miracrates"; }
    @Override public @NotNull String getAuthor() { return "FiveS"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        return switch (params.toLowerCase()) {
            case "jackpot_player", "latest_jackpot_player" -> jackpots.player();
            case "jackpot_reward", "latest_jackpot_reward" -> jackpots.reward();
            case "jackpot_crate" -> jackpots.crate();
            case "jackpot_rarity" -> jackpots.rarity();
            default -> null;
        };
    }
}
