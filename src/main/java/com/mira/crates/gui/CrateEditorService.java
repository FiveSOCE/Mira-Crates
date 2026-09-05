package com.mira.crates.gui;

import com.mira.core.api.MiraCore;
import com.mira.crates.model.CrateDefinition;
import com.mira.crates.model.RewardDefinition;
import com.mira.crates.model.RewardType;
import com.mira.crates.service.CrateItemService;
import com.mira.crates.service.DefinitionService;
import com.mira.crates.util.Ids;
import com.mira.crates.util.ShulkerMaterials;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CrateEditorService {
    private static final int REWARD_FIRST_SLOT = 27;
    private static final int REWARD_SLOTS = 18;
    private static final double CHANCE_TOLERANCE = 0.001D;

    private final MiraCore core;
    private final DefinitionService definitions;
    private final CrateItemService crateItems;
    private final Map<UUID, Draft> sessions = new HashMap<>();
    private final Set<UUID> awaitingNameInput = ConcurrentHashMap.newKeySet();
    private final Set<UUID> awaitingCommandInput = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> awaitingCommandNameInput = new ConcurrentHashMap<>();

    public CrateEditorService(MiraCore core, DefinitionService definitions, CrateItemService crateItems) {
        this.core = core;
        this.definitions = definitions;
        this.crateItems = crateItems;
    }

    public void startCreate(Player player) {
        if (!player.hasPermission("miracrates.admin")) {
            core.messages().send(player, "&cYou do not have permission to administer MiraCrates.");
            return;
        }
        awaitingNameInput.remove(player.getUniqueId());
        sessions.put(player.getUniqueId(), new Draft(null, "&fNew Crate", Material.PURPLE_SHULKER_BOX,
                new ArrayList<>(), new ArrayList<>()));
        openEditor(player);
    }

    public boolean openEdit(Player player, String crateId) {
        if (!player.hasPermission("miracrates.admin")) {
            core.messages().send(player, "&cYou do not have permission to administer MiraCrates.");
            return false;
        }
        Optional<CrateDefinition> found = definitions.crate(crateId);
        if (found.isEmpty()) return false;
        CrateDefinition crate = found.get();
        List<DraftReward> itemRewards = new ArrayList<>();
        List<RewardDefinition> preserved = new ArrayList<>();
        for (RewardDefinition reward : crate.rewards()) {
            if (reward.type() == RewardType.ITEM && reward.item() != null) {
                ItemStack item = reward.item().clone();
                item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), reward.amount())));
                itemRewards.add(DraftReward.item(item, reward.weight(), reward.id(), reward.rarityId()));
            } else if (reward.type() == RewardType.COMMAND) {
                itemRewards.add(DraftReward.command(reward.displayName(), reward.data(), reward.icon(),
                        reward.weight(), reward.id(), reward.rarityId()));
            } else {
                preserved.add(reward);
            }
        }
        awaitingNameInput.remove(player.getUniqueId());
        sessions.put(player.getUniqueId(), new Draft(crate.id(), crate.displayName(),
                ShulkerMaterials.normalise(crate.icon()), itemRewards, preserved));
        openEditor(player);
        return true;
    }

    public boolean isAwaitingName(UUID playerId) {
        return awaitingNameInput.contains(playerId);
    }

    public boolean isAwaitingCommand(UUID playerId) {
        return awaitingCommandInput.contains(playerId);
    }

    public boolean isAwaitingCommandName(UUID playerId) {
        return awaitingCommandNameInput.containsKey(playerId);
    }

    public void submitChatCommand(Player player, String message) {
        UUID playerId = player.getUniqueId();
        if (!awaitingCommandInput.remove(playerId)) return;
        Draft draft = sessions.get(playerId);
        if (draft == null) {
            core.messages().send(player, "&cThat crate editing session expired.");
            return;
        }
        String command = message == null ? "" : message.trim();
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isBlank()) {
            awaitingCommandInput.add(playerId);
            core.messages().send(player, "&cCommand cannot be blank. Type the console command in chat.");
            return;
        }
        if (draft.rewards.size() >= REWARD_SLOTS) {
            core.messages().send(player, "&cThis crate already has the maximum number of editable rewards.");
            openEditor(player);
            return;
        }
        draft.rewards.add(DraftReward.command("&fCommand Reward", command, Material.PAPER,
                0.0D, nextRewardId(draft, "command"), defaultRarityId()));
        int index = draft.rewards.size() - 1;
        awaitingCommandNameInput.put(playerId, index);
        core.messages().send(player, "&aCommand saved: &f/" + command);
        core.messages().send(player, "&eNow type the reward display name in chat. &7Colour codes like &6 or &d are supported.");
    }

    public void submitChatCommandName(Player player, String message) {
        UUID playerId = player.getUniqueId();
        Integer index = awaitingCommandNameInput.remove(playerId);
        Draft draft = sessions.get(playerId);
        if (index == null || draft == null || index < 0 || index >= draft.rewards.size()) {
            core.messages().send(player, "&cThat command reward editing session expired.");
            return;
        }

        DraftReward reward = draft.rewards.get(index);
        if (reward.type != RewardType.COMMAND) {
            openEditor(player);
            return;
        }

        String name = message == null ? "" : message.trim();
        if (name.isBlank()) {
            awaitingCommandNameInput.put(playerId, index);
            core.messages().send(player, "&cReward name cannot be blank. Type the display name in chat.");
            return;
        }
        if (name.length() > 64) name = name.substring(0, 64);
        reward.displayName = containsLegacyColour(name) ? name : "&f" + name;
        reward.refreshCommandPaper();
        core.messages().send(player, "&aCommand reward name set to " + reward.displayName + "&a.");
        openEditor(player);
    }

    public void submitChatName(Player player, String message) {
        UUID playerId = player.getUniqueId();
        if (!awaitingNameInput.remove(playerId)) return;
        if (!player.hasPermission("miracrates.admin")) {
            sessions.remove(playerId);
            core.messages().send(player, "&cYou do not have permission to administer MiraCrates.");
            return;
        }

        Draft draft = sessions.get(playerId);
        if (draft == null) {
            core.messages().send(player, "&cThat crate editing session expired. Run /mcrates create again.");
            return;
        }

        String rename = message == null ? "" : message.trim();
        if (rename.isBlank()) {
            awaitingNameInput.add(playerId);
            core.messages().send(player, "&cCrate name cannot be blank. Type a crate name in chat.");
            return;
        }
        if (rename.length() > 48) rename = rename.substring(0, 48);

        draft.displayName = containsLegacyColour(rename) ? rename : "&f" + rename;
        core.messages().send(player, "&aCrate name set to &f" + stripLegacy(draft.displayName) + "&a.");
        openEditor(player);
    }

    public void cancelNameInput(UUID playerId) {
        awaitingNameInput.remove(playerId);
        awaitingCommandInput.remove(playerId);
        awaitingCommandNameInput.remove(playerId);
    }

    public void handleClick(Player player, MiraInventoryHolder holder, InventoryClickEvent event) {
        if (!player.hasPermission("miracrates.admin")) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            core.messages().send(player, "&cYou do not have permission to administer MiraCrates.");
            return;
        }
        Draft draft = sessions.get(player.getUniqueId());
        if (draft == null) {
            player.closeInventory();
            core.messages().send(player, "&cThat crate editing session expired. Run /mcrates create again.");
            return;
        }

        switch (holder.type()) {
            case CRATE_EDITOR -> handleEditorClick(player, event, draft);
            case CRATE_CHANCE -> handleChanceClick(player, holder, event, draft);
            default -> { }
        }
    }

    private void handleEditorClick(Player player, InventoryClickEvent event, Draft draft) {
        int slot = event.getRawSlot();
        if (slot == 10) {
            awaitingNameInput.add(player.getUniqueId());
            player.closeInventory();
            core.messages().send(player, "&eType the crate name in chat. &7Your message will be hidden from everyone and used only as the crate name.");
            return;
        }
        if (slot == 12) {
            int direction = event.getClick().isRightClick() ? -1 : 1;
            draft.shulker = ShulkerMaterials.cycle(draft.shulker, direction);
            openEditor(player);
            return;
        }
        if (slot == 14) {
            ItemStack cursor = event.getCursor();
            if (cursor == null || cursor.getType().isAir()) {
                core.messages().send(player, "&ePut the reward item on your cursor, then click Add Reward.");
                return;
            }
            if (draft.rewards.size() >= REWARD_SLOTS) {
                core.messages().send(player, "&cThis editor currently supports up to " + REWARD_SLOTS + " item rewards per crate.");
                return;
            }
            ItemStack captured = cursor.clone();
            draft.rewards.add(DraftReward.item(captured, 0.0D, nextRewardId(draft, "item"), defaultRarityId()));
            openEditor(player);
            return;
        }
        if (slot == 15) {
            if (draft.rewards.size() >= REWARD_SLOTS) {
                core.messages().send(player, "&cThis editor currently supports up to " + REWARD_SLOTS + " editable rewards per crate.");
                return;
            }
            awaitingCommandInput.add(player.getUniqueId());
            player.closeInventory();
            core.messages().send(player, "&eType the console command in chat. &7Use %player% for the winner. Do not include the leading slash.");
            return;
        }
        if (slot == 16) {
            if (draft.rewards.isEmpty()) {
                core.messages().send(player, "&cAdd at least one reward first.");
                return;
            }
            autoBalance(draft);
            openEditor(player);
            return;
        }
        if (slot >= REWARD_FIRST_SLOT && slot < REWARD_FIRST_SLOT + REWARD_SLOTS) {
            int index = slot - REWARD_FIRST_SLOT;
            if (index >= draft.rewards.size()) return;
            if (event.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_LEFT) {
                cycleRarity(player, draft.rewards.get(index));
                openEditor(player);
            } else if (event.getClick().isRightClick()) {
                draft.rewards.remove(index);
                openEditor(player);
            } else {
                openChanceEditor(player, index);
            }
            return;
        }
        if (slot == 48) {
            save(player, draft);
            return;
        }
        if (slot == 50) {
            awaitingNameInput.remove(player.getUniqueId());
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            core.messages().send(player, "&eCrate edit cancelled.");
        }
    }

    private void handleChanceClick(Player player, MiraInventoryHolder holder, InventoryClickEvent event, Draft draft) {
        int index;
        try {
            index = Integer.parseInt(holder.context());
        } catch (NumberFormatException ex) {
            openEditor(player);
            return;
        }
        if (index < 0 || index >= draft.rewards.size()) {
            openEditor(player);
            return;
        }
        DraftReward reward = draft.rewards.get(index);
        if (event.getRawSlot() == 4 && reward.type == RewardType.COMMAND) {
            awaitingCommandNameInput.put(player.getUniqueId(), index);
            player.closeInventory();
            core.messages().send(player, "&eType the new command reward display name in chat. &7Colour codes are supported.");
            return;
        }
        double delta = switch (event.getRawSlot()) {
            case 9 -> -10.0D;
            case 10 -> -1.0D;
            case 11 -> -0.01D;
            case 15 -> 0.01D;
            case 16 -> 1.0D;
            case 17 -> 10.0D;
            default -> 0.0D;
        };
        if (delta != 0.0D) {
            reward.chance = roundChance(Math.max(0.0D, Math.min(100.0D, reward.chance + delta)));
            openChanceEditor(player, index);
            return;
        }
        if (event.getRawSlot() == 22) openEditor(player);
    }

    private void openEditor(Player player) {
        Draft draft = sessions.get(player.getUniqueId());
        if (draft == null) return;
        MiraInventoryHolder holder = new MiraInventoryHolder(MiraInventoryHolder.Type.CRATE_EDITOR,
                draft.existingId == null ? "new" : draft.existingId, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54, core.messages().parse(
                draft.existingId == null ? "&5Create Crate" : "&5Edit Crate &8- &f" + draft.existingId));
        holder.bind(inventory);

        ItemStack preview = crateItems.create(new CrateDefinition("preview", draft.displayName, draft.shulker,
                List.of(), 0L, List.of()));
        ItemMeta previewMeta = preview.getItemMeta();
        previewMeta.lore(List.of(
                line("&7This is the physical crate appearance."),
                line("&7Use Manage to receive a deployable copy when needed.")
        ));
        preview.setItemMeta(previewMeta);
        inventory.setItem(4, preview);

        inventory.setItem(10, GuiItems.item(Material.NAME_TAG, core.messages().parse("&fCrate Name"), List.of(
                line("&7Current: " + draft.displayName),
                line("&eClick, then type the new name in chat"),
                line("&7The chat message will not be broadcast."))));
        inventory.setItem(12, GuiItems.item(draft.shulker, core.messages().parse("&fShulker Colour"), List.of(
                line("&7Current: &f" + ShulkerMaterials.pretty(draft.shulker)),
                line("&eLeft-click: next colour"),
                line("&eRight-click: previous colour"))));
        inventory.setItem(14, GuiItems.item(Material.LIME_DYE, core.messages().parse("&aAdd Reward Item"), List.of(
                line("&7Put an item on your cursor"),
                line("&7then click this button."),
                line("&7Existing reward chances are not changed."),
                line("&7The item is copied, not consumed."))));
        inventory.setItem(15, GuiItems.item(Material.COMMAND_BLOCK, core.messages().parse("&dAdd Command Reward"), List.of(
                line("&7Executes as the server console when won."),
                line("&7Use &f%player% &7for the winning player's name."),
                line("&eClick, then type the command in chat."))));
        inventory.setItem(16, GuiItems.item(Material.COMPARATOR, core.messages().parse("&fAuto Balance Chances"), List.of(
                line("&7Splits 100% evenly between all item rewards."),
                line("&7Only use this when you want to overwrite current chances."))));

        for (int index = 0; index < draft.rewards.size() && index < REWARD_SLOTS; index++) {
            inventory.setItem(REWARD_FIRST_SLOT + index, rewardDisplay(draft.rewards.get(index)));
        }

        double total = chanceTotal(draft);
        boolean valid = !draft.rewards.isEmpty() && Math.abs(total - 100.0D) <= CHANCE_TOLERANCE;
        inventory.setItem(48, GuiItems.item(valid ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
                core.messages().parse(valid ? "&aSave Crate" : "&cCannot Save Yet"), List.of(
                        line(String.format(Locale.ROOT, "&7Total chance: &f%.2f%%", total)),
                        line(valid ? "&7Click to save." : "&7Reward chances must total exactly 100.00%."))));
        inventory.setItem(49, GuiItems.item(Material.PAPER, core.messages().parse("&fReward Chances"), List.of(
                line(String.format(Locale.ROOT, "&7Total: &f%.2f%% / 100.00%%", total)),
                line("&7Left-click a reward to edit its chance."),
                line("&7Shift-left-click a reward to change rarity."),
                line("&7Right-click a reward to remove it."))));
        inventory.setItem(50, GuiItems.item(Material.BARRIER, core.messages().parse("&cCancel"), List.of(
                line("&7Discard this editing session."))));
        player.openInventory(inventory);
    }

    private void openChanceEditor(Player player, int index) {
        Draft draft = sessions.get(player.getUniqueId());
        if (draft == null || index < 0 || index >= draft.rewards.size()) return;
        DraftReward reward = draft.rewards.get(index);
        MiraInventoryHolder holder = new MiraInventoryHolder(MiraInventoryHolder.Type.CRATE_CHANCE, Integer.toString(index), 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, core.messages().parse("&5Reward Chance"));
        holder.bind(inventory);
        if (reward.type == RewardType.COMMAND) {
            inventory.setItem(4, GuiItems.item(Material.NAME_TAG, core.messages().parse("&fEdit Reward Name"), List.of(
                    line("&7Current: " + reward.displayName),
                    line("&eClick, then type the new name in chat."))));
        }
        inventory.setItem(9, GuiItems.item(Material.RED_DYE, core.messages().parse("&c-10%"), List.of()));
        inventory.setItem(10, GuiItems.item(Material.REDSTONE, core.messages().parse("&c-1%"), List.of()));
        inventory.setItem(11, GuiItems.item(Material.REDSTONE_TORCH, core.messages().parse("&c-0.01%"), List.of()));
        ItemStack display = rewardDisplay(reward);
        ItemMeta meta = display.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(line(String.format(Locale.ROOT, "&fSelected chance: &a%.2f%%", reward.chance)));
        meta.lore(lore);
        display.setItemMeta(meta);
        inventory.setItem(13, display);
        inventory.setItem(15, GuiItems.item(Material.GLOWSTONE_DUST, core.messages().parse("&a+0.01%"), List.of()));
        inventory.setItem(16, GuiItems.item(Material.SUGAR, core.messages().parse("&a+1%"), List.of()));
        inventory.setItem(17, GuiItems.item(Material.LIME_DYE, core.messages().parse("&a+10%"), List.of()));
        inventory.setItem(22, GuiItems.item(Material.ARROW, core.messages().parse("&fBack"), List.of()));
        player.openInventory(inventory);
    }

    private void save(Player player, Draft draft) {
        if (draft.rewards.isEmpty()) {
            core.messages().send(player, "&cA crate needs at least one reward item.");
            return;
        }
        double total = chanceTotal(draft);
        if (Math.abs(total - 100.0D) > CHANCE_TOLERANCE) {
            core.messages().send(player, String.format(Locale.ROOT,
                    "&cReward chances must total 100.00%%. Current total: %.2f%%", total));
            return;
        }
        if (definitions.rarity("common").isEmpty()) definitions.createRarity("common", 100.0D, "&fCommon");

        List<RewardDefinition> rewards = new ArrayList<>(draft.preservedRewards);
        for (DraftReward draftReward : draft.rewards) {
            if (draftReward.type == RewardType.COMMAND) {
                rewards.add(new RewardDefinition(draftReward.id, RewardType.COMMAND, draftReward.rarityId,
                        roundChance(draftReward.chance), draftReward.displayName, Material.PAPER,
                        1, "", false, null, draftReward.data));
                continue;
            }
            ItemStack stored = draftReward.item.clone();
            int amount = Math.max(1, stored.getAmount());
            stored.setAmount(1);
            String display = stored.hasItemMeta() && stored.getItemMeta().hasDisplayName()
                    ? "&fReward Item"
                    : "&f" + pretty(stored.getType()) + (amount > 1 ? " x" + amount : "");
            rewards.add(new RewardDefinition(draftReward.id, RewardType.ITEM, draftReward.rarityId,
                    roundChance(draftReward.chance), display, stored.getType(), amount, "", false, stored, ""));
        }

        String crateId = draft.existingId;
        boolean saved;
        if (crateId == null) {
            crateId = idFromName(draft.displayName);
            if (!Ids.valid(crateId)) {
                core.messages().send(player, "&cThat name cannot produce a valid crate ID. Use letters/numbers in the crate name.");
                return;
            }
            saved = definitions.createCrate(crateId, draft.displayName, draft.shulker, rewards);
            if (!saved && definitions.crate(crateId).isPresent()) {
                core.messages().send(player, "&cA crate with that name already exists. Choose another name.");
                return;
            }
        } else {
            saved = definitions.updateCrate(crateId, draft.displayName, draft.shulker, rewards);
        }
        if (!saved) {
            core.messages().send(player, "&cCould not save that crate.");
            return;
        }

        awaitingNameInput.remove(player.getUniqueId());
        sessions.remove(player.getUniqueId());
        player.closeInventory();
        core.messages().send(player, "&aSaved crate &f" + crateId + "&a.");
    }

    private ItemStack rewardDisplay(DraftReward reward) {
        ItemStack item = reward.item.clone();
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), item.getAmount())));
        ItemMeta meta = item.getItemMeta();
        if (reward.type == RewardType.COMMAND) {
            meta.displayName(line(reward.displayName));
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(Component.empty());
        if (reward.type == RewardType.COMMAND) {
            lore.add(line("&7Type: &dConsole Command"));
            lore.add(line("&7Command: &f/" + reward.data));
        }
        lore.add(line(String.format(Locale.ROOT, "&7Chance: &f%.2f%%", reward.chance)));
        definitions.rarity(reward.rarityId).ifPresent(rarity ->
                lore.add(line("&7Rarity: " + rarity.displayName())));
        lore.add(line("&eLeft-click to edit chance"));
        lore.add(line("&eShift-left-click to change rarity"));
        lore.add(line("&cRight-click to remove"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String defaultRarityId() {
        return definitions.rarity("common").map(rarity -> rarity.id())
                .orElseGet(() -> definitions.rarities().stream().findFirst()
                        .map(rarity -> rarity.id()).orElse("common"));
    }

    private void cycleRarity(Player player, DraftReward reward) {
        List<com.mira.crates.model.RarityDefinition> rarities = new ArrayList<>(definitions.rarities());
        if (rarities.isEmpty()) {
            definitions.createRarity("common", 100.0D, "&fCommon");
            reward.rarityId = "common";
            core.messages().send(player, "&eNo rarities existed, so Common was created and selected.");
            return;
        }

        int current = -1;
        for (int i = 0; i < rarities.size(); i++) {
            if (rarities.get(i).id().equalsIgnoreCase(reward.rarityId)) {
                current = i;
                break;
            }
        }
        com.mira.crates.model.RarityDefinition next = rarities.get((current + 1 + rarities.size()) % rarities.size());
        reward.rarityId = next.id();
        core.messages().send(player, "&aReward rarity set to " + next.displayName() + "&a.");
    }

    private void autoBalance(Draft draft) {
        int size = draft.rewards.size();
        if (size == 0) return;
        double base = Math.floor((100.0D / size) * 100.0D) / 100.0D;
        double used = 0.0D;
        for (int i = 0; i < size; i++) {
            double chance = i == size - 1 ? roundChance(100.0D - used) : base;
            draft.rewards.get(i).chance = chance;
            used = roundChance(used + chance);
        }
    }

    private static double chanceTotal(Draft draft) {
        return roundChance(draft.rewards.stream().mapToDouble(reward -> reward.chance).sum());
    }

    private static double roundChance(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static String nextRewardId(Draft draft, String prefix) {
        int next = 1;
        Set<String> existing = new HashSet<>();
        draft.rewards.forEach(reward -> existing.add(reward.id));
        draft.preservedRewards.forEach(reward -> existing.add(reward.id()));
        while (existing.contains(prefix + "_" + next)) next++;
        return prefix + "_" + next;
    }

    private static boolean containsLegacyColour(String input) {
        return input.matches(".*&[0-9a-fA-Fk-oK-OrR].*");
    }

    private static String stripLegacy(String input) {
        return input == null ? "" : input.replaceAll("(?i)&[0-9A-FK-OR]", "");
    }

    private static String idFromName(String displayName) {
        String plain = stripLegacy(displayName).replaceAll("[^A-Za-z0-9 _-]", "").trim();
        return Ids.normalize(plain);
    }

    private static String pretty(Material material) {
        StringBuilder out = new StringBuilder();
        for (String part : material.name().toLowerCase(Locale.ROOT).split("_")) {
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    private Component line(String legacy) {
        return core.messages().parse(legacy).decoration(TextDecoration.ITALIC, false);
    }

    private static final class Draft {
        private final String existingId;
        private String displayName;
        private Material shulker;
        private final List<DraftReward> rewards;
        private final List<RewardDefinition> preservedRewards;

        private Draft(String existingId, String displayName, Material shulker,
                      List<DraftReward> rewards, List<RewardDefinition> preservedRewards) {
            this.existingId = existingId;
            this.displayName = displayName;
            this.shulker = shulker;
            this.rewards = rewards;
            this.preservedRewards = preservedRewards;
        }
    }

    private static final class DraftReward {
        private final RewardType type;
        private final ItemStack item;
        private double chance;
        private final String id;
        private String rarityId;
        private String displayName;
        private final String data;

        private DraftReward(RewardType type, ItemStack item, double chance, String id, String rarityId,
                            String displayName, String data) {
            this.type = type;
            this.item = item.clone();
            this.chance = roundChance(chance);
            this.id = id;
            this.rarityId = rarityId == null || rarityId.isBlank() ? "common" : rarityId;
            this.displayName = displayName == null || displayName.isBlank() ? "&fReward" : displayName;
            this.data = data == null ? "" : data;
        }

        private static DraftReward item(ItemStack item, double chance, String id, String rarityId) {
            return new DraftReward(RewardType.ITEM, item, chance, id, rarityId, "&fReward Item", "");
        }

        private static DraftReward command(String displayName, String command, Material icon,
                                           double chance, String id, String rarityId) {
            ItemStack display = new ItemStack(Material.PAPER);
            DraftReward reward = new DraftReward(RewardType.COMMAND, display, chance, id, rarityId, displayName, command);
            reward.refreshCommandPaper();
            return reward;
        }

        private void refreshCommandPaper() {
            if (type != RewardType.COMMAND) return;
            item.setType(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(stripLegacy(displayName)).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
    }
}
