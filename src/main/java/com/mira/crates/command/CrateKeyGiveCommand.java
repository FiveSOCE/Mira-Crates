package com.mira.crates.command;

import com.mira.core.api.MiraCore;
import com.mira.crates.model.KeyDefinition;
import com.mira.crates.service.DefinitionService;
import com.mira.crates.service.KeyService;
import com.mira.crates.util.Ids;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CrateKeyGiveCommand implements TabExecutor {
    private final MiraCore core;
    private final DefinitionService definitions;
    private final KeyService keys;

    public CrateKeyGiveCommand(MiraCore core, DefinitionService definitions, KeyService keys) {
        this.core = core;
        this.definitions = definitions;
        this.keys = keys;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("miracrates.admin")) {
            core.messages().send(sender, "&cYou do not have permission to administer MiraCrates.");
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            core.messages().send(sender, "&eUsage: /mcrateskey <player> <key> [amount]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            core.messages().send(sender, "&cPlayer '&f" + args[0] + "&c' is not online.");
            return true;
        }

        String keyId = Ids.normalize(args[1]);
        if (definitions.key(keyId).isEmpty()) {
            core.messages().send(sender, "&cUnknown key: &f" + args[1]);
            return true;
        }

        int amount = 1;
        if (args.length == 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                core.messages().send(sender, "&cAmount must be a whole number between 1 and 100000.");
                return true;
            }
            if (amount < 1 || amount > 100000) {
                core.messages().send(sender, "&cAmount must be a whole number between 1 and 100000.");
                return true;
            }
        }

        if (!keys.give(target, keyId, amount)) {
            core.messages().send(sender, "&cCould not give that key.");
            return true;
        }

        core.messages().send(sender, "&aGave &f" + amount + "x " + pretty(keyId) + " Key &ato &f" + target.getName() + "&a.");
        core.messages().send(target, "&aYou received &f" + amount + "x " + pretty(keyId) + " Key&a.");
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("miracrates.admin")) return List.of();
        if (args.length == 1) {
            return match(args[0], Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList());
        }
        if (args.length == 2) {
            return match(args[1], definitions.keys().stream().map(KeyDefinition::id).sorted().toList());
        }
        return List.of();
    }

    private static List<String> match(String prefix, List<String> values) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).limit(50).toList();
    }

    private static String pretty(String raw) {
        return Arrays.stream(raw.toLowerCase(Locale.ROOT).replace('-', '_').split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .reduce((a, b) -> a + " " + b).orElse(raw);
    }
}
