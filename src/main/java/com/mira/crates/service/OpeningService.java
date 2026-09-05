package com.mira.crates.service;

import com.mira.core.api.MiraCore;
import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.gui.GuiItems;
import com.mira.crates.gui.MiraInventoryHolder;
import com.mira.crates.model.CrateDefinition;
import com.mira.crates.model.RewardDefinition;
import com.mira.crates.model.RewardRoll;
import com.mira.crates.util.CosmeticsBridge;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

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

    public boolean attemptOpen(Player player, String crateId, boolean bypassRequirements) {
        return attemptOpen(player, crateId, bypassRequirements, false, false);
    }

    public boolean attemptPhysicalOpen(Player player, String crateId, boolean quickOpen) {
        return attemptOpen(player, crateId, false, quickOpen, true);
    }

    private boolean attemptOpen(Player player, String crateId, boolean bypassRequirements,
                                boolean quickOpen, boolean requireHeldKey) {
        if (sessions.containsKey(player.getUniqueId())) {
            core.messages().send(player, "&cYou already have a crate opening in progress.");
            return false;
        }

        CrateDefinition crate = definitions.crate(crateId).orElse(null);
        if (crate == null) {
            core.messages().send(player, "&cUnknown crate: " + crateId);
            return false;
        }
        if (!bypassRequirements && !seasons.active(crate.id())) {
            core.messages().send(player, "&cThis seasonal crate is not currently active. &7(" + seasons.window(crate.id()) + ")");
            return false;
        }
        if (!bypassRequirements && !player.hasPermission("miracrates.use")) {
            core.messages().send(player, "&cYou do not have permission to open crates.");
            return false;
        }
        if (!bypassRequirements && crate.keyIds().isEmpty()) {
            core.messages().send(player, "&cThis crate has no valid key configured and cannot be opened.");
            return false;
        }
        if (!bypassRequirements) {
            long remaining = playerData.cooldownRemainingSeconds(player.getUniqueId(), crate.id(), crate.cooldownSeconds());
            if (remaining > 0L) {
                core.messages().send(player, "&eYou can open this crate again in &f" + remaining + "s&e.");
                return false;
            }
        }

        List<RewardRoll> rolls = new ArrayList<>();
        for (int i = 0; i < crate.winsPerOpen(); i++) {
            RewardRoll roll = rewards.roll(player, crate).orElse(null);
            if (roll == null) {
                core.messages().send(player, "&cThis crate has no eligible rewards configured.");
                return false;
            }
            rolls.add(roll);
        }

        String keyUsed = null;
        if (!bypassRequirements) {
            Optional<String> consumed = requireHeldKey
                    ? keys.consumeHeld(player, crate.keyIds())
                    : keys.consumeAny(player, crate.keyIds());
            if (consumed.isEmpty()) {
                if (requireHeldKey) {
                    core.messages().send(player, "&cHold " + keys.primaryKeyDisplayName(crate.keyIds())
                            + " &cin your main hand and right-click this crate.");
                } else {
                    core.messages().send(player, "&cYou do not have a key accepted by this crate.");
                }
                return false;
            }
            keyUsed = consumed.get();
        }

        if (quickOpen) {
            return completeQuick(player, crate, rolls, keyUsed);
        }

        CosmeticsBridge.playVisualOnly(player, "crate_open", player.getLocation());
        MiraInventoryHolder holder = new MiraInventoryHolder(MiraInventoryHolder.Type.OPENING, crate.id(), 0);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                core.messages().parse(crate.displayName() + " &8Opening"));
        holder.bind(inventory);
        fillFrame(inventory);
        player.openInventory(inventory);

        Session session = new Session(player, crate, rolls, keyUsed, inventory);
        sessions.put(player.getUniqueId(), session);
        startAnimation(session);
        return true;
    }

    public boolean isOpening(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public void finishNow(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (session.task != null) session.task.cancel();
        while (session.currentIndex < session.rolls.size()) {
            finishCurrentReward(session);
            if (!sessions.containsKey(player.getUniqueId())) break;
        }
    }

    public void shutdown() {
        for (Session session : new ArrayList<>(sessions.values())) {
            if (session.task != null) session.task.cancel();
            if (session.closeTask != null) session.closeTask.cancel();
            while (session.currentIndex < session.rolls.size()) {
                grantAndAnnounce(session.player, session.crate, session.currentRoll(), session.keyUsed);
                session.currentIndex++;
            }
            finaliseOpen(session);
        }
    }

    private void startAnimation(Session session) {
        if (!sessions.containsKey(session.player.getUniqueId())) return;

        int interval = Math.max(1, plugin.getConfig().getInt("opening.slider-interval-ticks", 2));
        int steps = Math.max(12, plugin.getConfig().getInt("opening.slider-steps", 40));

        List<RewardDefinition> visualPool = session.crate.rewards().stream()
                .filter(reward -> reward.weight() > 0.0D)
                .filter(reward -> reward.permission() == null || reward.permission().isBlank()
                        || session.player.hasPermission(reward.permission()))
                .toList();

        if (visualPool.isEmpty()) {
            finishCurrentReward(session);
            return;
        }

        updateRollIndicator(session);

        List<RewardDefinition> reel = new ArrayList<>(steps + 9);
        for (int i = 0; i < steps + 9; i++) {
            reel.add(visualPool.get(ThreadLocalRandom.current().nextInt(visualPool.size())));
        }
        reel.set(steps + 4, session.currentRoll().reward());

        BukkitRunnable task = new BukkitRunnable() {
            private int position;

            @Override
            public void run() {
                if (!sessions.containsKey(session.player.getUniqueId())) {
                    cancel();
                    return;
                }

                renderReel(session, reel, position);
                CosmeticsBridge.playAudio(session.player, "crate_spin_tick", session.player.getLocation());

                if (position >= steps) {
                    cancel();
                    session.task = null;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (sessions.containsKey(session.player.getUniqueId())) {
                            finishCurrentReward(session);
                        }
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
            session.inventory.setItem(9 + offset,
                    rewards.displayItem(session.player, session.crate, reel.get(position + offset)));
        }
    }

    private void finishCurrentReward(Session session) {
        if (!sessions.containsKey(session.player.getUniqueId())) return;
        if (session.currentIndex >= session.rolls.size()) {
            scheduleClose(session);
            return;
        }

        RewardRoll roll = session.currentRoll();
        session.inventory.setItem(13, rewards.displayItem(session.player, session.crate, roll.reward()));
        boolean granted = grantAndAnnounce(session.player, session.crate, roll, session.keyUsed);
        if (granted) session.successfulRewards++;

        session.currentIndex++;

        if (session.currentIndex < session.rolls.size()) {
            startAnimation(session);
        } else {
            finaliseOpen(session);
            scheduleClose(session);
        }
    }

    private boolean completeQuick(Player player, CrateDefinition crate, List<RewardRoll> rolls, String keyUsed) {
        int successes = 0;
        for (RewardRoll roll : rolls) {
            if (grantAndAnnounce(player, crate, roll, keyUsed)) successes++;
        }
        if (successes > 0) {
            playerData.markOpened(player.getUniqueId(), crate.id());
            return true;
        }
        if (keyUsed != null) keys.give(player, keyUsed, 1);
        core.messages().send(player, "&cNo crate rewards could be delivered. Your key was refunded.");
        return false;
    }

    private boolean grantAndAnnounce(Player player, CrateDefinition crate, RewardRoll roll, String keyUsed) {
        boolean granted = rewards.grant(player, roll);
        if (!granted) {
            core.messages().send(player, "&cOne of your crate rewards could not be delivered.");
            plugin.getLogger().warning("Failed to deliver crate reward " + roll.reward().id()
                    + " to " + player.getName());
            return false;
        }

        history.record(player, crate.id(), roll, keyUsed);
        player.sendMessage(core.messages().prefix()
                .append(core.messages().parse("&aYou won "))
                .append(rewards.rewardNameComponent(roll.reward()))
                .append(core.messages().parse("&a!")));

        String cosmeticEvent = cosmeticRewardEvent(roll.reward());
        if ("crate_reward_legendary".equals(cosmeticEvent)) {
            CosmeticsBridge.playVisualOnly(player, cosmeticEvent, player.getLocation());
            CosmeticsBridge.playAudioGlobal(cosmeticEvent, player.getLocation());
        } else {
            CosmeticsBridge.play(player, cosmeticEvent, player.getLocation());
        }

        if (isRare(roll.reward())) {
            jackpots.record(player, crate, roll.reward());
            String message = plugin.getConfig().getString("rare-win.message",
                            "&6[Jackpot] &f%player% &7won %reward% &7from %crate%&7!")
                    .replace("%player%", player.getName())
                    .replace("%crate%", crate.displayName());
            Bukkit.broadcast(core.messages().prefix().append(formatRewardMessage(message, roll.reward())));
            core.milestones().award(player.getUniqueId(), "miracrates.jackpot", "MiraCrates",
                    Map.of("crate", crate.id(), "reward", roll.reward().id(),
                            "rarity", roll.reward().rarityId()));
        }
        return true;
    }

    private void finaliseOpen(Session session) {
        if (session.finalised) return;
        session.finalised = true;

        if (session.successfulRewards > 0) {
            playerData.markOpened(session.player.getUniqueId(), session.crate.id());
        } else if (session.keyUsed != null) {
            keys.give(session.player, session.keyUsed, 1);
            core.messages().send(session.player, "&cNo crate rewards could be delivered. Your key was refunded.");
        }
    }

    private void scheduleClose(Session session) {
        if (session.closeTask != null) return;
        long delay = Math.max(1L, plugin.getConfig().getLong("opening.auto-close-delay-ticks", 60L));

        session.closeTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sessions.remove(session.player.getUniqueId());
            if (session.player.isOnline()
                    && session.player.getOpenInventory().getTopInventory() == session.inventory) {
                session.player.closeInventory();
            }
        }, delay);
    }

    private void updateRollIndicator(Session session) {
        if (session.rolls.size() <= 1) {
            session.inventory.setItem(0, new ItemStack(Material.PURPLE_STAINED_GLASS_PANE));
            return;
        }

        session.inventory.setItem(0, GuiItems.item(Material.PAPER,
                core.messages().parse("&fReward Roll &d" + (session.currentIndex + 1)
                        + " &8/ &d" + session.rolls.size()),
                List.of(core.messages().parse("&7Each reward rolls one after another."))));
    }

    private Component formatRewardMessage(String template, RewardDefinition reward) {
        String[] parts = template.split("%reward%", -1);
        Component out = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].isEmpty()) out = out.append(core.messages().parse(parts[i]));
            if (i < parts.length - 1) out = out.append(rewards.rewardNameComponent(reward));
        }
        return out;
    }

    private String cosmeticRewardEvent(RewardDefinition reward) {
        String rarity = reward.rarityId() == null ? "" : reward.rarityId().toLowerCase(Locale.ROOT);
        if (rarity.contains("legend") || rarity.contains("mythic")) return "crate_reward_legendary";
        if (rarity.contains("rare") || rarity.contains("epic")) return "crate_reward_rare";
        return "crate_reward_common";
    }

    private boolean isRare(RewardDefinition reward) {
        if (reward.broadcast()) return true;
        return plugin.getConfig().getStringList("rare-win.rarities").stream()
                .anyMatch(rarity -> rarity.equalsIgnoreCase(reward.rarityId()));
    }

    private static void fillFrame(Inventory inventory) {
        ItemStack glass = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot < 9 || slot > 17) inventory.setItem(slot, glass);
        }
        inventory.setItem(4, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
        inventory.setItem(22, new ItemStack(Material.YELLOW_STAINED_GLASS_PANE));
    }

    private static final class Session {
        private final Player player;
        private final CrateDefinition crate;
        private final List<RewardRoll> rolls;
        private final String keyUsed;
        private final Inventory inventory;
        private int currentIndex;
        private int successfulRewards;
        private boolean finalised;
        private BukkitRunnable task;
        private BukkitTask closeTask;

        private Session(Player player, CrateDefinition crate, List<RewardRoll> rolls,
                        String keyUsed, Inventory inventory) {
            this.player = player;
            this.crate = crate;
            this.rolls = List.copyOf(rolls);
            this.keyUsed = keyUsed;
            this.inventory = inventory;
        }

        private RewardRoll currentRoll() {
            return rolls.get(currentIndex);
        }
    }
}
