package com.mira.crates.command;

import com.mira.core.api.MiraCore;
import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.gui.EditorMenuService;
import com.mira.crates.gui.PreviewService;
import com.mira.crates.model.*;
import com.mira.crates.service.*;
import com.mira.crates.util.Ids;
import com.mira.crates.util.ShulkerMaterials;
import org.bukkit.Material;
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
    private final RewardEngine rewards;
    private final OpeningService openings;
    private final PreviewService previews;
    private final EditorMenuService editor;
    private final CrateItemService crateItems;
    private final CrateLocationService locations;

    public MiraCratesCommand(MiraCratesPlugin plugin, MiraCore core, DefinitionService definitions, KeyService keys,
                             RewardEngine rewards, OpeningService openings, PreviewService previews,
                             EditorMenuService editor, CrateItemService crateItems, CrateLocationService locations) {
        this.plugin = plugin;
        this.core = core;
        this.definitions = definitions;
        this.keys = keys;
        this.rewards = rewards;
        this.openings = openings;
        this.previews = previews;
        this.editor = editor;
        this.crateItems = crateItems;
        this.locations = locations;
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
            case "create" -> createGui(sender);
            case "givecrate" -> giveCrate(sender, args);
            case "help" -> sendHelp(sender);
            case "info" -> sendInfo(sender);
            case "test" -> runSelfTest(sender);
            case "reload" -> {
                plugin.reloadPluginConfiguration();
                core.messages().send(sender, "&aMiraCrates configuration and definitions reloaded.");
            }
            case "preview" -> previewCommand(sender, args);
            case "open" -> openCommand(sender, args);
            case "crate" -> crateAdvanced(sender, args);
            case "key" -> keyAdvanced(sender, args);
            case "rarity" -> rarityAdvanced(sender, args);
            case "reward" -> rewardAdvanced(sender, args);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void createGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            core.messages().send(sender, "&cThe crate creator GUI must be opened by a player.");
            return;
        }
        editor.openCreate(player);
    }

    private void giveCrate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            core.messages().send(sender, "&cThis command must be run by a player.");
            return;
        }
        if (args.length < 2) {
            core.messages().send(sender, "&eUsage: /mcrates givecrate <crate name>");
            return;
        }
        String id = crateIdFromQuery(join(args, 1));
        if (!crateItems.give(player, id)) {
            core.messages().send(sender, "&cUnknown crate: " + join(args, 1));
            return;
        }
        core.messages().send(sender, "&aGave you crate &f" + id + "&a. Place the shulker anywhere to deploy it.");
    }

    private void previewCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            core.messages().send(sender, "&cThis command must be run by a player.");
            return;
        }
        if (args.length < 2 || !previews.open(player, crateIdFromQuery(join(args, 1)), 0)) {
            core.messages().send(sender, "&cUsage: /mcrates preview <crate>");
        }
    }

    private void openCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            core.messages().send(sender, "&cThis command must be run by a player.");
            return;
        }
        if (args.length < 2) {
            core.messages().send(sender, "&eUsage: /mcrates open <crate>");
            return;
        }
        openings.attemptOpen(player, crateIdFromQuery(join(args, 1)), true);
    }

    private void crateAdvanced(CommandSender sender, String[] args) {
        if (args.length < 3) {
            core.messages().send(sender, "&eAdvanced: /mcrates crate <delete|cooldown|key> ...");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String crateId = Ids.normalize(args[2]);
        switch (action) {
            case "delete" -> core.messages().send(sender, definitions.deleteCrate(crateId)
                    ? "&aDeleted crate &f" + crateId + "&a."
                    : "&cUnknown crate: " + crateId);
            case "cooldown" -> {
                if (args.length < 4) {
                    core.messages().send(sender, "&eUsage: /mcrates crate cooldown <crate> <seconds>");
                    return;
                }
                Long seconds = whole(args[3]);
                if (seconds == null || seconds < 0 || !definitions.setCooldown(crateId, seconds)) {
                    core.messages().send(sender, "&cCooldown must be a non-negative whole number and the crate must exist.");
                    return;
                }
                core.messages().send(sender, "&aUpdated crate cooldown.");
            }
            case "key" -> {
                if (args.length < 5) {
                    core.messages().send(sender, "&eUsage: /mcrates crate key <crate> <add|remove> <key>");
                    return;
                }
                boolean success = args[3].equalsIgnoreCase("add")
                        ? definitions.attachKey(crateId, args[4])
                        : args[3].equalsIgnoreCase("remove") && definitions.detachKey(crateId, args[4]);
                core.messages().send(sender, success ? "&aUpdated accepted keys." : "&cCould not update that crate/key combination.");
            }
            default -> core.messages().send(sender, "&cUnknown advanced crate action: " + action);
        }
    }

    private void keyAdvanced(CommandSender sender, String[] args) {
        if (args.length < 3) {
            core.messages().send(sender, "&eAdvanced: /mcrates key <create|createvirtual|delete|give> ...");
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
                if (!(sender instanceof Player player)) {
                    core.messages().send(sender, "&cThis command must be run by a player.");
                    return;
                }
                int amount = optionalAmount(args, 3, sender);
                if (amount < 1) return;
                if (!keys.give(player, keyId, amount)) {
                    core.messages().send(sender, "&cUnknown key: " + keyId);
                    return;
                }
                core.messages().send(sender, "&aGave you &f" + amount + "x " + pretty(keyId) + " Key&a.");
            }
            default -> core.messages().send(sender, "&cUnknown key action: " + action);
        }
    }

    private void rarityAdvanced(CommandSender sender, String[] args) {
        if (args.length < 3) {
            core.messages().send(sender, "&eAdvanced: /mcrates rarity <create|delete> ...");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        String id = Ids.normalize(args[2]);
        if (action.equals("delete")) {
            core.messages().send(sender, definitions.deleteRarity(id)
                    ? "&aDeleted rarity &f" + id + "&a." : "&cUnknown rarity: " + id);
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
        core.messages().send(sender, "&aCreated rarity &f" + id + "&a.");
    }

    private void rewardAdvanced(CommandSender sender, String[] args) {
        if (args.length < 2) {
            rewardUsage(sender);
            return;
        }
        String type = args[1].toLowerCase(Locale.ROOT);
        if (type.equals("remove")) {
            if (args.length < 4) {
                core.messages().send(sender, "&eUsage: /mcrates reward remove <crate> <rewardId>");
                return;
            }
            core.messages().send(sender, definitions.removeReward(args[2], args[3]) ? "&aReward removed." : "&cCrate or reward not found.");
            return;
        }
        if (args.length < 6) {
            rewardUsage(sender);
            return;
        }
        String crate = Ids.normalize(args[2]);
        String rarity = Ids.normalize(args[3]);
        Double weight = decimal(args[4]);
        String id = Ids.normalize(args[5]);
        if (weight == null || weight < 0.0D || definitions.crate(crate).isEmpty()
                || definitions.rarity(rarity).isEmpty() || !Ids.valid(id)) {
            core.messages().send(sender, "&cInvalid crate, rarity, reward ID or weight.");
            return;
        }

        RewardDefinition reward;
        switch (type) {
            case "item" -> {
                if (!(sender instanceof Player player)) {
                    core.messages().send(sender, "&cItem rewards must be captured by a player.");
                    return;
                }
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType().isAir()) {
                    core.messages().send(sender, "&cHold the reward item in your main hand.");
                    return;
                }
                int amount = args.length >= 7 ? optionalAmount(args, 6, sender) : held.getAmount();
                if (amount < 1) return;
                ItemStack stored = held.clone();
                stored.setAmount(1);
                reward = new RewardDefinition(id, RewardType.ITEM, rarity, weight, "&f" + pretty(id),
                        held.getType(), amount, "", false, stored, "");
            }
            case "command" -> {
                if (args.length < 7) {
                    core.messages().send(sender, "&eUsage: /mcrates reward command <crate> <rarity> <weight> <id> <command...>");
                    return;
                }
                reward = new RewardDefinition(id, RewardType.COMMAND, rarity, weight, "&f" + pretty(id),
                        Material.COMMAND_BLOCK, 1, "", false, null, join(args, 6));
            }
            case "spawner" -> {
                if (args.length < 7) {
                    core.messages().send(sender, "&eUsage: /mcrates reward spawner <crate> <rarity> <weight> <id> <mob> [amount]");
                    return;
                }
                EntityType entity;
                try {
                    entity = EntityType.valueOf(args[6].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    core.messages().send(sender, "&cUnknown entity type: " + args[6]);
                    return;
                }
                if (!entity.isSpawnable()) {
                    core.messages().send(sender, "&cThat entity cannot be used by a normal spawner.");
                    return;
                }
                int amount = optionalAmount(args, 7, sender);
                if (amount < 1) return;
                reward = new RewardDefinition(id, RewardType.MIRA_SPAWNER, rarity, weight,
                        "&f" + pretty(entity.name()) + " Spawner x" + amount, Material.SPAWNER, amount,
                        "", false, null, entity.name());
            }
            case "key" -> {
                if (args.length < 7 || definitions.key(args[6]).isEmpty()) {
                    core.messages().send(sender, "&cSpecify an existing key ID.");
                    return;
                }
                int amount = optionalAmount(args, 7, sender);
                if (amount < 1) return;
                reward = new RewardDefinition(id, RewardType.KEY, rarity, weight,
                        "&f" + pretty(args[6]) + " Key x" + amount, Material.TRIPWIRE_HOOK, amount,
                        "", false, null, Ids.normalize(args[6]));
            }
            case "xp" -> {
                if (args.length < 7) {
                    core.messages().send(sender, "&eUsage: /mcrates reward xp <crate> <rarity> <weight> <id> <levels>");
                    return;
                }
                Long levels = whole(args[6]);
                if (levels == null || levels < 1 || levels > 100000) {
                    core.messages().send(sender, "&cLevels must be 1-100000.");
                    return;
                }
                reward = new RewardDefinition(id, RewardType.XP_LEVELS, rarity, weight,
                        "&a" + levels + " XP Levels", Material.EXPERIENCE_BOTTLE, levels.intValue(),
                        "", false, null, "");
            }
            default -> {
                rewardUsage(sender);
                return;
            }
        }
        if (!definitions.addReward(crate, reward)) {
            core.messages().send(sender, "&cCould not save reward.");
            return;
        }
        core.messages().send(sender, "&aSaved advanced reward &f" + id + "&a.");
    }

    private void sendInfo(CommandSender sender) {
        core.messages().send(sender, "&dMiraCrates v" + plugin.getPluginMeta().getVersion());
        core.messages().send(sender, "&7Crates: &f" + definitions.crates().size()
                + " &7Keys: &f" + definitions.keys().size()
                + " &7Rarities: &f" + definitions.rarities().size()
                + " &7Placed: &f" + locations.all().size());
        core.messages().send(sender, "&7MiraSpawners integration: " + (plugin.miraSpawnersAvailable() ? "&aAvailable" : "&eNot loaded"));
    }

    private void runSelfTest(CommandSender sender) {
        List<String> failures = new ArrayList<>();
        if (!plugin.isEnabled()) failures.add("Plugin enabled");
        if (definitions.rarities().isEmpty()) failures.add("Rarity definitions loaded");
        if (definitions.crates().stream().anyMatch(crate -> !ShulkerMaterials.isCrateShulker(crate.icon()))) failures.add("All crate definitions use shulkers");
        if (locations.all().stream().anyMatch(location -> definitions.crate(location.crateId()).isEmpty())) failures.add("Placed crate locations reference valid definitions");
        File data = plugin.getDataFolder();
        if (!new File(data, "crates.yml").isFile()) failures.add("crates.yml exists");
        if (!new File(data, "playerdata.yml").isFile()) failures.add("playerdata.yml exists");
        if (failures.isEmpty()) {
            core.messages().send(sender, "&aMiraCrates Self-Test: 6/6 passed.");
        } else {
            core.messages().send(sender, "&cMiraCrates Self-Test failed: " + String.join(", ", failures));
        }
    }

    private void sendHelp(CommandSender sender) {
        core.messages().send(sender, "&dMiraCrates &fGUI-first commands");
        core.messages().send(sender, "&f/mcrates &7- Open the editor");
        core.messages().send(sender, "&f/mcrates create &7- Create a crate through the GUI");
        core.messages().send(sender, "&f/mcrates givecrate <crate name> &7- Give yourself its deployable shulker");
        core.messages().send(sender, "&f/mcrates info|test|reload &7- Diagnostics/admin recovery");
        core.messages().send(sender, "&8Advanced key/reward commands remain available for integrations not yet exposed in the GUI.");
    }

    private void rewardUsage(CommandSender sender) {
        core.messages().send(sender, "&eAdvanced reward usage: /mcrates reward <item|command|spawner|key|xp|remove> ...");
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

    private String crateIdFromQuery(String query) {
        String plain = query.replaceAll("(?i)&[0-9A-FK-OR]", "").replaceAll("[^A-Za-z0-9 _-]", "");
        return Ids.normalize(plain);
    }

    private static Long whole(String input) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double decimal(String input) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String join(String[] args, int from) {
        return String.join(" ", Arrays.copyOfRange(args, from, args.length));
    }

    private static String pretty(String raw) {
        StringBuilder out = new StringBuilder();
        for (String part : raw.toLowerCase(Locale.ROOT).replace('-', '_').split("_")) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("miracrates.admin")) return List.of();
        if (args.length == 1) {
            return match(args[0], List.of("create", "givecrate", "help", "info", "test", "reload", "preview", "open", "crate", "key", "rarity", "reward"));
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        if ((root.equals("givecrate") || root.equals("preview") || root.equals("open")) && args.length == 2) {
            return match(args[1], definitions.crates().stream().map(CrateDefinition::id).sorted().toList());
        }
        if (root.equals("key") && args.length == 2) return match(args[1], List.of("create", "createvirtual", "delete", "give"));
        if (root.equals("key") && args.length == 3 && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("delete"))) {
            return match(args[2], definitions.keys().stream().map(KeyDefinition::id).sorted().toList());
        }
        if (root.equals("crate") && args.length == 2) return match(args[1], List.of("delete", "cooldown", "key"));
        if (root.equals("crate") && args.length == 3) return match(args[2], definitions.crates().stream().map(CrateDefinition::id).sorted().toList());
        if (root.equals("rarity") && args.length == 2) return match(args[1], List.of("create", "delete"));
        if (root.equals("reward") && args.length == 2) return match(args[1], List.of("item", "command", "spawner", "key", "xp", "remove"));
        return List.of();
    }

    private static List<String> match(String prefix, Collection<String> values) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).limit(50).toList();
    }
}
