package com.mira.crates.service;

import com.mira.core.api.MiraCore;
import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.gui.MiraInventoryHolder;
import com.mira.crates.model.CrateDefinition;
import com.mira.crates.model.RewardDefinition;
import com.mira.crates.model.RewardRoll;
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
    private final Map<UUID, Session> sessions = new HashMap<>();

    public OpeningService(MiraCratesPlugin plugin, MiraCore core, DefinitionService definitions, KeyService keys,
                          RewardEngine rewards, PlayerDataService playerData, HistoryService history) {
        this.plugin = plugin;
        this.core = core;
        this.definitions = definitions;
        this.keys = keys;
        this.rewards = rewards;
        this.playerData = playerData;
        this.history = history;
    }

    public boolean attemptOpen(Player player, String crateId, boolean bypassRequirements) {
        if (sessions.containsKey(player.getUniqueId())) {
            core.messages().send(player, "&cYou already have a crate opening in progress.");
            return false;
        }
        CrateDefinition crate = definitions.crate(crateId).orElse(null);
        if (crate == null) {
            core.messages().send(player, "&cUnknown crate: " + crateId);
            return false;
        }
        if (!bypassRequirements && !player.hasPermission("miracrates.use")) {
            core.messages().send(player, "&cYou do not have permission to open crates.");
            return false;
        }
        if (!bypassRequirements) {
            long remaining = playerData.cooldownRemainingSeconds(player.getUniqueId(), crate.id(), crate.cooldownSeconds());
            if (remaining > 0L) {
                core.messages().send(player, "&eYou can open this crate again in &f" + remaining + "s&e.");
                return false;
            }
        }

        RewardRoll roll = rewards.roll(player, crate).orElse(null);
        if (roll == null) {
            core.messages().send(player, "&cThis crate has no eligible rewards configured.");
            return false;
        }

        String keyUsed = null;
        if (!bypassRequirements && !crate.keyIds().isEmpty()) {
            Optional<String> consumed = keys.consumeAny(player, crate.keyIds());
            if (consumed.isEmpty()) {
                core.messages().send(player, "&cYou do not have a key accepted by this crate.");
                return false;
            }
            keyUsed = consumed.get();
        }

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

    public boolean isOpening(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void finishNow(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null) finish(session);
    }

    public void shutdown() {
        for (Session session : new ArrayList<>(sessions.values())) finish(session);
    }

    private void startAnimation(Session session) {
        int interval = Math.max(1, plugin.getConfig().getInt("opening.roulette-interval-ticks", 4));
        int duration = Math.max(interval, plugin.getConfig().getInt("opening.animation-ticks", 60));
        List<RewardDefinition> visualPool = session.crate.rewards().stream()
                .filter(reward -> reward.weight() > 0.0D)
                .filter(reward -> reward.permission() == null || reward.permission().isBlank() || session.player.hasPermission(reward.permission()))
                .toList();

        BukkitRunnable task = new BukkitRunnable() {
            private int elapsed;

            @Override
            public void run() {
                if (!sessions.containsKey(session.player.getUniqueId())) {
                    cancel();
                    return;
                }
                elapsed += interval;
                if (!visualPool.isEmpty()) {
                    RewardDefinition visual = visualPool.get(ThreadLocalRandom.current().nextInt(visualPool.size()));
                    session.inventory.setItem(13, rewards.displayItem(session.player, session.crate, visual));
                }
                if (elapsed >= duration) {
                    cancel();
                    session.task = null;
                    finish(session);
                }
            }
        };
        session.task = task;
        task.runTaskTimer(plugin, 0L, interval);
    }

    private void finish(Session session) {
        if (sessions.remove(session.player.getUniqueId()) == null) return;
        if (session.task != null) session.task.cancel();
        session.inventory.setItem(13, rewards.displayItem(session.player, session.crate, session.roll.reward()));
        boolean granted = rewards.grant(session.player, session.roll);
        if (!granted) {
            if (session.keyUsed != null) keys.give(session.player, session.keyUsed, 1);
            core.messages().send(session.player, "&cThat reward could not be delivered. Your key was refunded.");
            plugin.getLogger().warning("Failed to deliver crate reward " + session.roll.reward().id() + " to " + session.player.getName());
            return;
        }
        playerData.markOpened(session.player.getUniqueId(), session.crate.id());
        history.record(session.player, session.crate.id(), session.roll, session.keyUsed);
        core.messages().send(session.player, "&aYou won " + session.roll.reward().displayName() + "&a!");
    }

    private static void fillFrame(Inventory inventory) {
        ItemStack glass = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot != 13) inventory.setItem(slot, glass);
        }
    }

    private static final class Session {
        private final Player player;
        private final CrateDefinition crate;
        private final RewardRoll roll;
        private final String keyUsed;
        private final Inventory inventory;
        private BukkitRunnable task;

        private Session(Player player, CrateDefinition crate, RewardRoll roll, String keyUsed, Inventory inventory) {
            this.player = player;
            this.crate = crate;
            this.roll = roll;
            this.keyUsed = keyUsed;
            this.inventory = inventory;
        }
    }
}
