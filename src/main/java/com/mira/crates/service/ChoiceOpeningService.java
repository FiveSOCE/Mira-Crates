package com.mira.crates.service;

import com.mira.core.api.MiraCore;
import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.gui.GuiItems;
import com.mira.crates.model.CrateDefinition;
import com.mira.crates.model.KeyDefinition;
import com.mira.crates.model.RewardDefinition;
import com.mira.crates.model.RewardRoll;
import com.mira.crates.util.CosmeticsBridge;
import com.mira.crates.util.Ids;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Player-facing choose-your-reward opening flow for crates configured in CHOICE mode. */
public final class ChoiceOpeningService implements Listener {
    private static final int REWARD_LIMIT = 45;
    private final MiraCratesPlugin plugin;
    private final MiraCore core;
    private final DefinitionService definitions;
    private final KeyService keys;
    private final RewardEngine rewards;
    private final PlayerDataService playerData;
    private final HistoryService history;
    private final JackpotService jackpots;
    private final SeasonalCrateService seasons;
    private final Map<UUID, ChoiceSession> sessions = new HashMap<>();

    public ChoiceOpeningService(MiraCratesPlugin plugin, MiraCore core, DefinitionService definitions,
                                KeyService keys, RewardEngine rewards, PlayerDataService playerData,
                                HistoryService history, JackpotService jackpots, SeasonalCrateService seasons) {
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

    public boolean attemptPhysicalOpen(Player player, String crateId) {
        if (sessions.containsKey(player.getUniqueId())) {
            core.messages().send(player, "&cYou already have a crate choice in progress.");
            return false;
        }

        CrateDefinition crate = definitions.crate(crateId).orElse(null);
        if (crate == null) {
            core.messages().send(player, "&cUnknown crate: " + crateId);
            return false;
        }
        if (!seasons.active(crate.id())) {
            core.messages().send(player, "&cThis seasonal crate is not currently active. &7(" + seasons.window(crate.id()) + ")");
            return false;
        }
        if (!player.hasPermission("miracrates.use")) {
            core.messages().send(player, "&cYou do not have permission to open crates.");
            return false;
        }
        if (crate.keyIds().isEmpty()) {
            core.messages().send(player, "&cThis crate has no valid key configured and cannot be opened.");
            return false;
        }
        long remaining = playerData.cooldownRemainingSeconds(player.getUniqueId(), crate.id(), crate.cooldownSeconds());
        if (remaining > 0L) {
            core.messages().send(player, "&eYou can open this crate again in &f" + remaining + "s&e.");
            return false;
        }
        if (!hasAcceptedHeldKey(player, crate)) {
            core.messages().send(player, "&cHold " + keys.primaryKeyDisplayName(crate.keyIds())
                    + " &cin your main hand and right-click this crate.");
            return false;
        }

        return openChoice(player, crate, true, false);
    }

    /** Used by admin/test surfaces where no key should be consumed. */
    public boolean attemptBypassOpen(Player player, String crateId) {
        if (sessions.containsKey(player.getUniqueId())) return false;
        CrateDefinition crate = definitions.crate(crateId).orElse(null);
        return crate != null && openChoice(player, crate, false, true);
    }

    private boolean openChoice(Player player, CrateDefinition crate, boolean requireHeldKey, boolean bypassKey) {
        List<RewardDefinition> eligible = crate.rewards().stream()
                .filter(reward -> reward.permission() == null || reward.permission().isBlank()
                        || player.hasPermission(reward.permission()))
                .filter(reward -> definitions.rarity(reward.rarityId()).isPresent())
                .limit(REWARD_LIMIT)
                .toList();
        if (eligible.isEmpty()) {
            core.messages().send(player, "&cThis crate has no eligible rewards configured.");
            return false;
        }

        int requiredChoices = Math.max(1, Math.min(crate.winsPerOpen(), eligible.size()));
        ChoiceHolder holder = new ChoiceHolder(crate.id());
        Inventory inventory = Bukkit.createInventory(holder, 54,
                core.messages().parse(crate.displayName() + " &8- Choose Reward"));
        holder.bind(inventory);

        ChoiceSession session = new ChoiceSession(player, crate, eligible, requiredChoices,
                requireHeldKey, bypassKey, inventory);
        sessions.put(player.getUniqueId(), session);
        render(session);
        player.openInventory(inventory);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ChoiceHolder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        event.setCancelled(true);

        ChoiceSession session = sessions.get(player.getUniqueId());
        if (session == null || session.inventory != event.getView().getTopInventory()) return;
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= session.inventory.getSize()) return;

        if (rawSlot < session.eligible.size()) {
            RewardDefinition reward = session.eligible.get(rawSlot);
            toggleSelection(session, reward);
            render(session);
            return;
        }
        if (rawSlot == 48) {
            session.selected.clear();
            render(session);
            return;
        }
        if (rawSlot == 49) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            core.messages().send(player, "&eCrate choice cancelled. Your key was not consumed.");
            return;
        }
        if (rawSlot == 50) confirm(session);
    }

    private void toggleSelection(ChoiceSession session, RewardDefinition reward) {
        String id = reward.id();
        if (session.selected.remove(id)) return;
        if (session.requiredChoices == 1) session.selected.clear();
        if (session.selected.size() >= session.requiredChoices) {
            core.messages().send(session.player, "&eYou can choose only &f" + session.requiredChoices + " &ereward"
                    + (session.requiredChoices == 1 ? "" : "s") + ". Deselect one first.");
            return;
        }
        session.selected.add(id);
    }

    private void confirm(ChoiceSession session) {
        if (session.selected.size() != session.requiredChoices) {
            core.messages().send(session.player, "&eChoose exactly &f" + session.requiredChoices + " &ereward"
                    + (session.requiredChoices == 1 ? "" : "s") + " before confirming.");
            return;
        }

        String keyUsed = null;
        if (!session.bypassKey) {
            Optional<String> consumed = session.requireHeldKey
                    ? keys.consumeHeld(session.player, session.crate.keyIds())
                    : keys.consumeAny(session.player, session.crate.keyIds());
            if (consumed.isEmpty()) {
                core.messages().send(session.player, session.requireHeldKey
                        ? "&cYour matching crate key must still be in your main hand when you confirm."
                        : "&cYou no longer have a key accepted by this crate.");
                return;
            }
            keyUsed = consumed.get();
        }

        sessions.remove(session.player.getUniqueId());
        session.player.closeInventory();

        int successes = 0;
        for (RewardDefinition reward : session.eligible) {
            if (!session.selected.contains(reward.id())) continue;
            var rarity = definitions.rarity(reward.rarityId()).orElse(null);
            if (rarity == null) continue;
            RewardRoll roll = new RewardRoll(rarity, reward);
            if (grantAndAnnounce(session.player, session.crate, roll, keyUsed)) successes++;
        }

        if (successes > 0) {
            playerData.markOpened(session.player.getUniqueId(), session.crate.id());
        } else if (keyUsed != null) {
            keys.give(session.player, keyUsed, 1);
            core.messages().send(session.player, "&cNo selected rewards could be delivered. Your key was refunded.");
        }
    }

    private boolean grantAndAnnounce(Player player, CrateDefinition crate, RewardRoll roll, String keyUsed) {
        boolean granted = rewards.grant(player, roll);
        if (!granted) {
            core.messages().send(player, "&cOne of your selected crate rewards could not be delivered.");
            plugin.getLogger().warning("Failed to deliver chosen crate reward " + roll.reward().id()
                    + " to " + player.getName());
            return false;
        }

        history.record(player, crate.id(), roll, keyUsed);
        player.sendMessage(core.messages().prefix()
                .append(core.messages().parse("&aYou chose "))
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

    private void render(ChoiceSession session) {
        for (int slot = 0; slot < REWARD_LIMIT; slot++) session.inventory.setItem(slot, null);
        for (int i = 0; i < session.eligible.size(); i++) {
            RewardDefinition reward = session.eligible.get(i);
            session.inventory.setItem(i, choiceDisplay(session, reward));
        }

        session.inventory.setItem(48, GuiItems.item(Material.BUCKET,
                core.messages().parse("&eClear Selection"), List.of(
                        core.messages().parse("&7Selected: &f" + session.selected.size() + "&7/&f" + session.requiredChoices))));
        session.inventory.setItem(49, GuiItems.item(Material.BARRIER,
                core.messages().parse("&cCancel"), List.of(
                        core.messages().parse("&7No key is consumed until Confirm."))));
        boolean ready = session.selected.size() == session.requiredChoices;
        session.inventory.setItem(50, GuiItems.item(ready ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
                core.messages().parse(ready ? "&aConfirm Choices" : "&cChoose More Rewards"), List.of(
                        core.messages().parse("&7Choose exactly &f" + session.requiredChoices + " &7reward"
                                + (session.requiredChoices == 1 ? "." : "s.")),
                        core.messages().parse("&7Selected: &f" + session.selected.size() + "&7/&f" + session.requiredChoices),
                        core.messages().parse(ready ? "&eClick to consume the key and claim." : "&8The key has not been consumed."))));
    }

    private ItemStack choiceDisplay(ChoiceSession session, RewardDefinition reward) {
        ItemStack item = rewards.displayItem(session.player, session.crate, reward);
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.removeIf(line -> PlainTextComponentSerializer.plainText().serialize(line).startsWith("Chance: "));
        if (!lore.isEmpty()) lore.add(Component.empty());
        boolean selected = session.selected.contains(reward.id());
        lore.add(core.messages().parse(selected ? "&a✔ SELECTED" : "&eClick to select")
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        ChoiceSession session = sessions.get(player.getUniqueId());
        if (session != null && session.inventory == event.getInventory()) sessions.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        for (ChoiceSession session : new ArrayList<>(sessions.values())) {
            if (session.player.isOnline() && session.player.getOpenInventory().getTopInventory() == session.inventory) {
                session.player.closeInventory();
            }
        }
        sessions.clear();
    }

    private boolean hasAcceptedHeldKey(Player player, CrateDefinition crate) {
        String heldId = keys.identify(player.getInventory().getItemInMainHand()).orElse(null);
        if (heldId == null) return false;
        for (String rawId : crate.keyIds()) {
            String id = Ids.normalize(rawId);
            if (!id.equals(heldId)) continue;
            KeyDefinition definition = definitions.key(id).orElse(null);
            return definition != null && !definition.virtual();
        }
        return false;
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

    private static final class ChoiceSession {
        private final Player player;
        private final CrateDefinition crate;
        private final List<RewardDefinition> eligible;
        private final int requiredChoices;
        private final boolean requireHeldKey;
        private final boolean bypassKey;
        private final Inventory inventory;
        private final Set<String> selected = new LinkedHashSet<>();

        private ChoiceSession(Player player, CrateDefinition crate, List<RewardDefinition> eligible,
                              int requiredChoices, boolean requireHeldKey, boolean bypassKey, Inventory inventory) {
            this.player = player;
            this.crate = crate;
            this.eligible = List.copyOf(eligible);
            this.requiredChoices = requiredChoices;
            this.requireHeldKey = requireHeldKey;
            this.bypassKey = bypassKey;
            this.inventory = inventory;
        }
    }

    private static final class ChoiceHolder implements InventoryHolder {
        private final String crateId;
        private Inventory inventory;

        private ChoiceHolder(String crateId) {
            this.crateId = crateId;
        }

        private void bind(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            if (inventory == null) throw new IllegalStateException("Choice inventory has not been bound yet");
            return inventory;
        }
    }
}
