package com.mira.crates.service;

import com.mira.core.api.MiraCore;
import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.gui.MiraInventoryHolder;
import com.mira.crates.model.CrateDefinition;
import com.mira.crates.model.RewardDefinition;
import com.mira.crates.model.RewardRoll;
import com.mira.crates.util.CosmeticsBridge;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class OpeningService {
    private final MiraCratesPlugin plugin;
    private final MiraCore core;
    private final DefinitionService definitions;
    private final KeyService keys;
    private final RewardEngine rewards;
    private final PlayerDataService playerData;
    private final HistoryService history;
    private final JackpotService jackpots;
    private final SeasonalCrateService seasons;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public OpeningService(MiraCratesPlugin plugin, MiraCore core, DefinitionService definitions, KeyService keys,
                          RewardEngine rewards, PlayerDataService playerData, HistoryService history,
                          JackpotService jackpots, SeasonalCrateService seasons) {
        this.plugin = plugin;
        this.core = core;
        this.definitions = definitions;
        this.keys = keys;
        this.rewards = rewards;
        this.playerData = playerData;
        this.history = history;
        this.jackpots = jackpots;
        this.seasons = seasons;
    }

    public boolean attemptOpen(Player player, String crateId, boolean bypassRequirements) { return attemptOpen(player, crateId, bypassRequirements, false, false); }
    public boolean attemptPhysicalOpen(Player player, String crateId, boolean quickOpen) { return attemptOpen(player, crateId, false, quickOpen, true); }

    private boolean attemptOpen(Player player, String crateId, boolean bypassRequirements,
                                boolean quickOpen, boolean requireHeldKey) {
        if (sessions.containsKey(player.getUniqueId())) { core.messages().send(player, "&cYou already have a crate opening in progress."); return false; }
        CrateDefinition crate = definitions.crate(crateId).orElse(null);
        if (crate == null) { core.messages().send(player, "&cUnknown crate: " + crateId); return false; }
        if (!bypassRequirements && !seasons.active(crate.id())) { core.messages().send(player, "&cThis seasonal crate is not currently active. &7(" + seasons.window(crate.id()) + ")"); return false; }
        if (!bypassRequirements && !player.hasPermission("miracrates.use")) { core.messages().send(player, "&cYou do not have permission to open crates."); return false; }
        if (!bypassRequirements && crate.keyIds().isEmpty()) { core.messages().send(player, "&cThis crate has no valid key configured and cannot be opened."); return false; }
        if (!bypassRequirements) {
            long remaining = playerData.cooldownRemainingSeconds(player.getUniqueId(), crate.id(), crate.cooldownSeconds());
            if (remaining > 0L) { core.messages().send(player, "&eYou can open this crate again in &f" + remaining + "s&e."); return false; }
        }

        RewardRoll roll = rewards.roll(player, crate).orElse(null);
        if (roll == null) { core.messages().send(player, "&cThis crate has no eligible rewards configured."); return false; }

        String keyUsed = null;
        if (!bypassRequirements) {
            Optional<String> consumed = requireHeldKey ? keys.consumeHeld(player, crate.keyIds()) : keys.consumeAny(player, crate.keyIds());
            if (consumed.isEmpty()) {
                if (requireHeldKey) core.messages().send(player, "&cHold " + keys.primaryKeyDisplayName(crate.keyIds()) + " &cin your main hand and right-click this crate.");
                else core.messages().send(player, "&cYou do not have a key accepted by this crate.");
                return false;
            }
            keyUsed = consumed.get();
        }

        if (quickOpen) return complete(player, crate, roll, keyUsed);

        CosmeticsBridge.play(player, "crate_open", player.getLocation());
        MiraInventoryHolder holder = new MiraInventoryHolder(MiraInventoryHolder.Type.OPENING, crate.id(), 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, core.messages().parse(crate.displayName() + " &8Opening"));
        holder.bind(inventory);
        fillFrame(inventory);
        player.openInventory(inventory);

        Session session = new Session(player, crate, roll, keyUsed, inventory);
        sessions.put(player.getUniqueId(), session);
        startAnimation(session);
        return true;
    }

    public boolean isOpening(UUID playerId) { return sessions.containsKey(playerId); }
    public void finishNow(Player player) { Session session = sessions.get(player.getUniqueId()); if (session != null) finish(session); }
    public void shutdown() { for (Session session : new ArrayList<>(sessions.values())) finish(session); }

    private void startAnimation(Session session) {
        int interval = Math.max(1, plugin.getConfig().getInt("opening.slider-interval-ticks", 2));
        int steps = Math.max(12, plugin.getConfig().getInt("opening.slider-steps", 24));
        List<RewardDefinition> visualPool = session.crate.rewards().stream()
                .filter(reward -> reward.weight() > 0.0D)
                .filter(reward -> reward.permission() == null || reward.permission().isBlank()
                        || session.player.hasPermission(reward.permission()))
                .toList();

        if (visualPool.isEmpty()) {
            finish(session);
            return;
        }

        // Build the reel up-front. The real reward was already rolled before the GUI opened,
        // so the animation is presentation-only and cannot reroll or alter the outcome.
        List<RewardDefinition> reel = new ArrayList<>(steps + 9);
        for (int i = 0; i < steps + 9; i++) {
            reel.add(visualPool.get(ThreadLocalRandom.current().nextInt(visualPool.size())));
        }
        // The selector is the middle slot (13), i.e. offset 4 in the 9-slot reel.
        reel.set(steps + 4, session.roll.reward());

        BukkitRunnable task = new BukkitRunnable() {
            private int position;

            @Override
            public void run() {
                if (!sessions.containsKey(session.player.getUniqueId())) {
                    cancel();
                    return;
                }

                renderReel(session, reel, position);
                if (position >= steps) {
                    cancel();
                    session.task = null;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (sessions.containsKey(session.player.getUniqueId())) finish(session);
                    }, Math.max(1L, plugin.getConfig().getLong("opening.winner-hold-ticks", 8L)));
                    return;
                }
                position++;
            }
        };
        session.task = task;
        task.runTaskTimer(plugin, 0L, interval);
    }

    private void renderReel(Session session, List<RewardDefinition> reel, int position) {
        for (int offset = 0; offset < 9; offset++) {
            RewardDefinition visual = reel.get(position + offset);
            session.inventory.setItem(9 + offset,
                    rewards.displayItem(session.player, session.crate, visual));
        }
    }

    private void finish(Session session) {
        if (sessions.remove(session.player.getUniqueId()) == null) return;
        if (session.task != null) session.task.cancel();
        session.inventory.setItem(13, rewards.displayItem(session.player, session.crate, session.roll.reward()));
        complete(session.player, session.crate, session.roll, session.keyUsed);
    }

    private boolean complete(Player player, CrateDefinition crate, RewardRoll roll, String keyUsed) {
        boolean granted = rewards.grant(player, roll);
        if (!granted) {
            if (keyUsed != null) keys.give(player, keyUsed, 1);
            core.messages().send(player, "&cThat reward could not be delivered. Your key was refunded.");
            plugin.getLogger().warning("Failed to deliver crate reward " + roll.reward().id() + " to " + player.getName());
            return false;
        }
        playerData.markOpened(player.getUniqueId(), crate.id());
        history.record(player, crate.id(), roll, keyUsed);
        core.messages().send(player, "&aYou won " + roll.reward().displayName() + "&a!");
        String cosmeticEvent = cosmeticRewardEvent(roll.reward());
        if ("crate_reward_legendary".equals(cosmeticEvent)) {
            CosmeticsBridge.playVisualOnly(player, cosmeticEvent, player.getLocation());
            CosmeticsBridge.playAudioGlobal(cosmeticEvent, player.getLocation());
        } else {
            CosmeticsBridge.play(player, cosmeticEvent, player.getLocation());
        }

        if (isRare(roll.reward())) {
            jackpots.record(player, crate, roll.reward());
            String message = plugin.getConfig().getString("rare-win.message", "&6[Jackpot] &f%player% &7won %reward% &7from %crate%&7!")
                    .replace("%player%", player.getName())
                    .replace("%reward%", roll.reward().displayName())
                    .replace("%crate%", crate.displayName());
            Bukkit.broadcast(core.messages().prefix().append(core.messages().parse(message)));
            core.milestones().award(player.getUniqueId(), "miracrates.jackpot", "MiraCrates",
                    Map.of("crate", crate.id(), "reward", roll.reward().id(), "rarity", roll.reward().rarityId()));
        }
        return true;
    }

    private String cosmeticRewardEvent(RewardDefinition reward) {
        String rarity = reward.rarityId() == null ? "" : reward.rarityId().toLowerCase(Locale.ROOT);
        if (rarity.contains("legend") || rarity.contains("mythic")) return "crate_reward_legendary";
        if (rarity.contains("rare") || rarity.contains("epic")) return "crate_reward_rare";
        return "crate_reward_common";
    }

    private boolean isRare(RewardDefinition reward) {
        if (reward.broadcast()) return true;
        return plugin.getConfig().getStringList("rare-win.rarities").stream().anyMatch(rarity -> rarity.equalsIgnoreCase(reward.rarityId()));
    }

    private static void fillFrame(Inventory inventory) {
        ItemStack glass = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot < 9 || slot > 17) inventory.setItem(slot, glass);
        }

        // Selector markers above and below the winning center slot.
        inventory.setItem(4, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
        inventory.setItem(22, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
    }

    private static final class Session {
        private final Player player;
        private final CrateDefinition crate;
        private final RewardRoll roll;
        private final String keyUsed;
        private final Inventory inventory;
        private BukkitRunnable task;
        private Session(Player player, CrateDefinition crate, RewardRoll roll, String keyUsed, Inventory inventory) {
            this.player = player; this.crate = crate; this.roll = roll; this.keyUsed = keyUsed; this.inventory = inventory;
        }
    }
}
