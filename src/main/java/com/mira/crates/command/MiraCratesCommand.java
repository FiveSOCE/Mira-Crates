package com.mira.crates.command;

import com.mira.core.api.MiraCore;
import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.gui.EditorMenuService;
import com.mira.crates.gui.PreviewService;
import com.mira.crates.model.*;
import com.mira.crates.service.*;
import com.mira.crates.util.Ids;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

public final class MiraCratesCommand implements TabExecutor {
    private final MiraCratesPlugin plugin;
    private final MiraCore core;
    private final DefinitionService definitions;
    private final KeyService keys;
    private final CrateLocationService locations;
    private final RewardEngine rewards;
    private final OpeningService openings;
    private final PreviewService previews;
    private final EditorMenuService editor;

    public MiraCratesCommand(MiraCratesPlugin plugin, MiraCore core, DefinitionService definitions, KeyService keys,
                             CrateLocationService locations, RewardEngine rewards, OpeningService openings,
                             PreviewService previews, EditorMenuService editor) {
        this.plugin = plugin;
        this.core = core;
        this.definitions = definitions;
        this.keys = keys;
        this.locations = locations;
        this.rewards = rewards;
        this.openings = openings;
        this.previews = previews;
        this.editor = editor;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("miracrates.admin")) {
            core.messages().send(sender, "&cYou do not have permission to administer MiraCrates.");
            return true;
        }
        if (args.length == 0) {
            if (sender instanceof Player player) editor.openMain(player);
            else sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> sendHelp(sender);
            case "info" -> sendInfo(sender);
            case "test" -> runSelfTest(sender);
            case "reload" -> {
                plugin.reloadPluginConfiguration();
                core.messages().send(sender, "&aMiraCrates configuration and definitions reloaded.");
            }
            case "crate" -> crateCommand(sender, args);
            case "key" -> keyCommand(sender, args);
            case "rarity" -> rarityCommand(sender, args);
            case "reward" -> rewardCommand(sender, args);
            case "location" -> locationCommand(sender, args);
            case "preview" -> previewCommand(sender, args);
            case "open" -> openCommand(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void crateCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            core.messages().send(sender, "&eUsage: /mcrates crate <create|delete|cooldown|key> ...");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String crateId = Ids.normalize(args[2]);
        switch (action) {
            case "create" -> {
                String name = args.length >= 4 ? join(args, 3) : "&d" + pretty(crateId) + " Crate";
                if (!definitions.createCrate(crateId, name)) {
                    core.messages().send(sender, "&cCould not create crate. Check the ID or whether it already exists.");
                    return;
                }
                core.messages().send(sender, "&aCreated crate &f" + crateId + "&a.");
            }
            case "delete" -> core.messages().send(sender, definitions.deleteCrate(crateId)
                    ? "&aDeleted crate &f" + crateId + "&a."
                    : "&cUnknown crate: " + crateId);
            case "cooldown" -> {
                if (args.length < 4) { core.messages().send(sender, "&eUsage: /mcrates crate cooldown <crate> <seconds>"); return; }
                Long seconds = whole(args[3]);
                if (seconds == null || seconds < 0 || !definitions.setCooldown(crateId, seconds)) {
                    core.messages().send(sender, "&cCooldown must be a non-negative whole number and the crate must exist.");
                    return;
                }
                core.messages().send(sender, "&aSet &f" + crateId + "&a cooldown to &f" + seconds + "s&a.");
            }
            case "key" -> {
                if (args.length < 5) { core.messages().send(sender, "&eUsage: /mcrates crate key <crate> <add|remove> <key>"); return; }
                boolean success = args[3].equalsIgnoreCase("add")
                        ? definitions.attachKey(crateId, args[4])
                        : args[3].equalsIgnoreCase("remove") && definitions.detachKey(crateId, args[4]);
                core.messages().send(sender, success ? "&aUpdated accepted keys for &f" + crateId + "&a."
                        : "&cCould not update that crate/key combination.");
            }
            default -> core.messages().send(sender, "&cUnknown crate action: " + action);
        }
    }

    private void keyCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            core.messages().send(sender, "&eUsage: /mcrates key <create|createvirtual|delete|give> ...");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String keyId = Ids.normalize(args[2]);
        switch (action) {
            case "create", "createvirtual" -> {
                boolean virtual = action.equals("createvirtual");
                String name = args.length >= 4 ? join(args, 3) : "&f" + pretty(keyId) + " Key";
                if (!definitions.createKey(keyId, name, virtual)) {
                    core.messages().send(sender, "&cCould not create key. Check the ID or whether it already exists.");
                    return;
                }
                core.messages().send(sender, "&aCreated " + (virtual ? "virtual" : "physical") + " key &f" + keyId + "&a.");
            }
            case "delete" -> core.messages().send(sender, definitions.deleteKey(keyId)
                    ? "&aDeleted key &f" + keyId + "&a."
                    : "&cUnknown key: " + keyId);
            case "give" -> {
                if (!(sender instanceof Player player)) { core.messages().send(sender, "&cThis command must be run by a player."); return; }
                int amount = 1;
                if (args.length >= 4) {
                    Long parsed = whole(args[3]);
                    if (parsed == null || parsed < 1 || parsed > 100000) { core.messages().send(sender, "&cAmount must be 1-100000."); return; }
                    amount = parsed.intValue();
                }
                if (!keys.give(player, keyId, amount)) { core.messages().send(sender, "&cUnknown key: " + keyId); return; }
                core.messages().send(sender, "&aGave you &f" + amount + "x " + pretty(keyId) + " Key&a.");
            }
            default -> core.messages().send(sender, "&cUnknown key action: " + action);
        }
    }

    private void rarityCommand(CommandSender sender, String[] args) {
        if (args.length < 3) { core.messages().send(sender, "&eUsage: /mcrates rarity <create|delete> ..."); return; }
        String action = args[1].toLowerCase(Locale.ROOT);
        String id = Ids.normalize(args[2]);
        if (action.equals("delete")) {
            core.messages().send(sender, definitions.deleteRarity(id) ? "&aDeleted rarity &f" + id + "&a." : "&cUnknown rarity: " + id);
            return;
        }
        if (!action.equals("create") || args.length < 4) {
            core.messages().send(sender, "&eUsage: /mcrates rarity create <id> <weight> [display name]");
            return;
        }
        Double weight = decimal(args[3]);
        String name = args.length >= 5 ? join(args, 4) : "&f" + pretty(id);
        if (weight == null || weight < 0.0D || !definitions.createRarity(id, weight, name)) {
            core.messages().send(sender, "&cCould not create rarity. Weight must be non-negative and ID unique.");
            return;
        }
        core.messages().send(sender, "&aCreated rarity &f" + id + " &awith weight &f" + weight + "&a.");
    }

    private void rewardCommand(CommandSender sender, String[] args) {
        if (args.length < 2) { rewardUsage(sender); return; }
        String type = args[1].toLowerCase(Locale.ROOT);
        if (type.equals("remove")) {
            if (args.length < 4) { core.messages().send(sender, "&eUsage: /mcrates reward remove <crate> <rewardId>"); return; }
            core.messages().send(sender, definitions.removeReward(args[2], args[3]) ? "&aReward removed." : "&cCrate or reward not found.");
            return;
        }
        if (args.length < 6) { rewardUsage(sender); return; }
        String crate = Ids.normalize(args[2]);
        String rarity = Ids.normalize(args[3]);
        Double weight = decimal(args[4]);
        String id = Ids.normalize(args[5]);
        if (weight == null || weight < 0.0D || definitions.crate(crate).isEmpty() || definitions.rarity(rarity).isEmpty() || !Ids.valid(id)) {
            core.messages().send(sender, "&cInvalid crate, rarity, reward ID or weight.");
            return;
        }

        RewardDefinition reward;
        switch (type) {
            case "item" -> {
                if (!(sender instanceof Player player)) { core.messages().send(sender, "&cItem rewards must be captured by a player."); return; }
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType().isAir()) { core.messages().send(sender, "&cHold the reward item in your main hand."); return; }
                int amount = held.getAmount();
                if (args.length >= 7) {
                    Long parsed = whole(args[6]);
                    if (parsed == null || parsed < 1 || parsed > 100000) { core.messages().send(sender, "&cAmount must be 1-100000."); return; }
                    amount = parsed.intValue();
                }
                ItemStack stored = held.clone();
                stored.setAmount(1);
                reward = new RewardDefinition(id, RewardType.ITEM, rarity, weight, "&f" + pretty(id), held.getType(), amount, "", false, stored, "");
            }
            case "command" -> {
                if (args.length < 7) { core.messages().send(sender, "&eUsage: /mcrates reward command <crate> <rarity> <weight> <id> <command...>"); return; }
                reward = new RewardDefinition(id, RewardType.COMMAND, rarity, weight, "&f" + pretty(id), Material.COMMAND_BLOCK, 1, "", false, null, join(args, 6));
            }
            case "spawner" -> {
                if (args.length < 7) { core.messages().send(sender, "&eUsage: /mcrates reward spawner <crate> <rarity> <weight> <id> <mob> [amount]"); return; }
                EntityType entity;
                try { entity = EntityType.valueOf(args[6].toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException ex) { core.messages().send(sender, "&cUnknown entity type: " + args[6]); return; }
                if (!entity.isSpawnable()) { core.messages().send(sender, "&cThat entity cannot be used by a normal spawner."); return; }
                int amount = optionalAmount(args, 7, sender);
                if (amount < 1) return;
                reward = new RewardDefinition(id, RewardType.MIRA_SPAWNER, rarity, weight,
                        "&f" + pretty(entity.name()) + " Spawner x" + amount, Material.SPAWNER, amount, "", false, null, entity.name());
            }
            case "key" -> {
                if (args.length < 7 || definitions.key(args[6]).isEmpty()) { core.messages().send(sender, "&cSpecify an existing key ID."); return; }
                int amount = optionalAmount(args, 7, sender);
                if (amount < 1) return;
                reward = new RewardDefinition(id, RewardType.KEY, rarity, weight, "&f" + pretty(args[6]) + " Key x" + amount,
                        Material.TRIPWIRE_HOOK, amount, "", false, null, Ids.normalize(args[6]));
            }
            case "xp" -> {
                if (args.length < 7) { core.messages().send(sender, "&eUsage: /mcrates reward xp <crate> <rarity> <weight> <id> <levels>"); return; }
                Long levels = whole(args[6]);
                if (levels == null || levels < 1 || levels > 100000) { core.messages().send(sender, "&cLevels must be 1-100000."); return; }
                reward = new RewardDefinition(id, RewardType.XP_LEVELS, rarity, weight, "&a" + levels + " XP Levels",
                        Material.EXPERIENCE_BOTTLE, levels.intValue(), "", false, null, "");
            }
            default -> { rewardUsage(sender); return; }
        }
        if (!definitions.addReward(crate, reward)) { core.messages().send(sender, "&cCould not save reward."); return; }
        core.messages().send(sender, "&aSaved reward &f" + id + " &ato crate &f" + crate + "&a.");
    }

    private int optionalAmount(String[] args, int index, CommandSender sender) {
        if (args.length <= index) return 1;
        Long parsed = whole(args[index]);
        if (parsed == null || parsed < 1 || parsed > 100000) {
            core.messages().send(sender, "&cAmount must be 1-100000.");
            return -1;
        }
        return parsed.intValue();
    }

    private void locationCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { core.messages().send(sender, "&cThis command must be run by a player."); return; }
        if (args.length < 2) { core.messages().send(sender, "&eUsage: /mcrates location <set <crate>|remove>"); return; }
        Block target = player.getTargetBlockExact(Math.max(1, plugin.getConfig().getInt("interaction.target-distance", 6)));
        if (target == null) { core.messages().send(sender, "&cLook directly at the block you want to edit."); return; }
        if (args[1].equalsIgnoreCase("remove")) {
            core.messages().send(sender, locations.remove(target) ? "&aCrate location removed." : "&cThat block is not linked to a crate.");
            return;
        }
        if (!args[1].equalsIgnoreCase("set") || args.length < 3 || definitions.crate(args[2]).isEmpty()) {
            core.messages().send(sender, "&eUsage: /mcrates location set <crate>");
            return;
        }
        locations.set(target, Ids.normalize(args[2]));
        core.messages().send(sender, "&aLinked the targeted block to crate &f" + Ids.normalize(args[2]) + "&a.");
    }

    private void previewCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { core.messages().send(sender, "&cThis command must be run by a player."); return; }
        if (args.length < 2 || !previews.open(player, args[1], 0)) core.messages().send(sender, "&cUsage: /mcrates preview <crate>");
    }

    private void openCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { core.messages().send(sender, "&cThis command must be run by a player."); return; }
        if (args.length < 2) { core.messages().send(sender, "&eUsage: /mcrates open <crate>"); return; }
        openings.attemptOpen(player, args[1], true);
    }

    private void sendInfo(CommandSender sender) {
        core.messages().send(sender, "&dMiraCrates &fv" + plugin.getPluginMeta().getVersion());
        core.messages().send(sender, "&7Crates: &f" + definitions.crates().size() + " &7Keys: &f" + definitions.keys().size()
                + " &7Rarities: &f" + definitions.rarities().size() + " &7Locations: &f" + locations.all().size());
        core.messages().send(sender, "&7MiraSpawners provider: " + (plugin.miraSpawnersAvailable() ? "&aAvailable" : "&eNot installed"));
    }

    private void runSelfTest(CommandSender sender) {
        List<Result> results = List.of(
                new Result("MiraCore API", plugin.core() != null),
                new Result("MiraCore module registration", core.modules().get(plugin.getName()).isPresent()),
                new Result("Definition service", definitions != null),
                new Result("Rarity definitions", !definitions.rarities().isEmpty()),
                new Result("Location persistence", locations != null),
                new Result("Reward engine", rewards != null),
                new Result("Data directory", new File(plugin.getDataFolder(), "crates.yml").exists())
        );
        long passed = results.stream().filter(Result::passed).count();
        core.messages().send(sender, "&dMiraCrates Self-Test &7(" + passed + "/" + results.size() + ")");
        for (Result result : results) core.messages().send(sender, (result.passed ? "&a✔ " : "&c✘ ") + "&f" + result.name);
    }

    private void sendHelp(CommandSender sender) {
        core.messages().send(sender, "&dMiraCrates Commands");
        core.messages().send(sender, "&f/mcrates &7- Open the editor dashboard");
        core.messages().send(sender, "&f/mcrates crate create <id> [name]");
        core.messages().send(sender, "&f/mcrates crate delete <id>");
        core.messages().send(sender, "&f/mcrates crate cooldown <crate> <seconds>");
        core.messages().send(sender, "&f/mcrates crate key <crate> <add|remove> <key>");
        core.messages().send(sender, "&f/mcrates key <create|createvirtual> <id> [name]");
        core.messages().send(sender, "&f/mcrates key give <key> [amount]");
        core.messages().send(sender, "&f/mcrates rarity create <id> <weight> [name]");
        core.messages().send(sender, "&f/mcrates reward <item|command|spawner|key|xp|remove> ...");
        core.messages().send(sender, "&f/mcrates location <set <crate>|remove>");
        core.messages().send(sender, "&f/mcrates preview <crate> &7- Preview calculated chances");
        core.messages().send(sender, "&f/mcrates open <crate> &7- Admin test-open without a key");
        core.messages().send(sender, "&f/mcrates info|test|reload");
    }

    private void rewardUsage(CommandSender sender) {
        core.messages().send(sender, "&eReward syntax:");
        core.messages().send(sender, "&f/mcrates reward item <crate> <rarity> <weight> <id> [amount]");
        core.messages().send(sender, "&f/mcrates reward command <crate> <rarity> <weight> <id> <command...>");
        core.messages().send(sender, "&f/mcrates reward spawner <crate> <rarity> <weight> <id> <mob> [amount]");
        core.messages().send(sender, "&f/mcrates reward key <crate> <rarity> <weight> <id> <key> [amount]");
        core.messages().send(sender, "&f/mcrates reward xp <crate> <rarity> <weight> <id> <levels>");
        core.messages().send(sender, "&f/mcrates reward remove <crate> <id>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("miracrates.admin")) return List.of();
        if (args.length == 1) return filter(List.of("crate", "key", "rarity", "reward", "location", "preview", "open", "info", "test", "reload", "help"), args[0]);
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "crate" -> filter(List.of("create", "delete", "cooldown", "key"), args[1]);
                case "key" -> filter(List.of("create", "createvirtual", "delete", "give"), args[1]);
                case "rarity" -> filter(List.of("create", "delete"), args[1]);
                case "reward" -> filter(List.of("item", "command", "spawner", "key", "xp", "remove"), args[1]);
                case "location" -> filter(List.of("set", "remove"), args[1]);
                case "preview", "open" -> filter(crateIds(), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("preview") || args[0].equalsIgnoreCase("open"))) return List.of();
        if (args.length == 3 && args[0].equalsIgnoreCase("location") && args[1].equalsIgnoreCase("set")) return filter(crateIds(), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("key") && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("delete"))) return filter(keyIds(), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("crate") && !args[1].equalsIgnoreCase("create")) return filter(crateIds(), args[2]);
        if (args.length == 4 && args[0].equalsIgnoreCase("crate") && args[1].equalsIgnoreCase("key")) return filter(List.of("add", "remove"), args[3]);
        if (args.length == 5 && args[0].equalsIgnoreCase("crate") && args[1].equalsIgnoreCase("key")) return filter(keyIds(), args[4]);
        if (args.length == 3 && args[0].equalsIgnoreCase("reward") && !args[1].equalsIgnoreCase("remove")) return filter(crateIds(), args[2]);
        if (args.length == 4 && args[0].equalsIgnoreCase("reward") && !args[1].equalsIgnoreCase("remove")) return filter(rarityIds(), args[3]);
        if (args.length == 3 && args[0].equalsIgnoreCase("reward") && args[1].equalsIgnoreCase("remove")) return filter(crateIds(), args[2]);
        if (args.length == 7 && args[0].equalsIgnoreCase("reward") && args[1].equalsIgnoreCase("key")) return filter(keyIds(), args[6]);
        return List.of();
    }

    private List<String> crateIds() { return definitions.crates().stream().map(CrateDefinition::id).sorted().toList(); }
    private List<String> keyIds() { return definitions.keys().stream().map(KeyDefinition::id).sorted().toList(); }
    private List<String> rarityIds() { return definitions.rarities().stream().map(RarityDefinition::id).sorted().toList(); }

    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    private static String join(String[] args, int start) { return String.join(" ", Arrays.copyOfRange(args, start, args.length)); }
    private static Long whole(String value) { try { return Long.parseLong(value); } catch (NumberFormatException ex) { return null; } }
    private static Double decimal(String value) { try { return Double.parseDouble(value); } catch (NumberFormatException ex) { return null; } }
    private static String pretty(String id) {
        return Arrays.stream(Ids.normalize(id).split("_")).filter(part -> !part.isEmpty())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1)).reduce((a, b) -> a + " " + b).orElse(id);
    }

    private record Result(String name, boolean passed) { }
}
