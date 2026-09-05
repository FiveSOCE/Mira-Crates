package com.mira.crates.service;

import com.mira.core.api.MiraCore;
import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.model.*;
import com.mira.crates.util.WeightedPicker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class RewardEngine {
    private final MiraCratesPlugin plugin;
    private final MiraCore core;
    private final DefinitionService definitions;
    private final KeyService keys;

    public RewardEngine(MiraCratesPlugin plugin, MiraCore core, DefinitionService definitions, KeyService keys) {
        this.plugin = plugin;
        this.core = core;
        this.definitions = definitions;
        this.keys = keys;
    }

    public Optional<RewardRoll> roll(Player player, CrateDefinition crate) {
        List<RewardDefinition> eligible = crate.rewards().stream()
                .filter(reward -> reward.weight() > 0.0D)
                .filter(reward -> reward.permission() == null || reward.permission().isBlank()
                        || player.hasPermission(reward.permission()))
                .filter(reward -> definitions.rarity(reward.rarityId()).isPresent())
                .toList();
        if (eligible.isEmpty()) return Optional.empty();

        // Reward weight is the actual public chance authority. Rarity is presentation
        // metadata (sounds/broadcast classification), not a hidden second probability roll.
        return WeightedPicker.pick(eligible, RewardDefinition::weight, ThreadLocalRandom.current())
                .flatMap(reward -> definitions.rarity(reward.rarityId())
                        .map(rarity -> new RewardRoll(rarity, reward)));
    }

    public boolean grant(Player player, RewardRoll roll) {
        RewardDefinition reward = roll.reward();
        boolean granted = switch (reward.type()) {
            case ITEM -> grantItem(player, reward);
            case COMMAND -> grantCommand(player, reward);
            case MIRA_SPAWNER -> grantMiraSpawner(player, reward);
            case KEY -> keys.give(player, reward.data(), reward.amount());
            case XP_LEVELS -> {
                player.giveExpLevels(reward.amount());
                yield true;
            }
        };
        if (granted && reward.broadcast()) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                core.messages().send(online, "&d" + player.getName() + " &fwon " + reward.displayName() + " &ffrom a crate!");
            }
        }
        return granted;
    }

    public double chance(Player player, CrateDefinition crate, RewardDefinition target) {
        if (target.weight() <= 0.0D) return 0.0D;
        if (target.permission() != null && !target.permission().isBlank()
                && !player.hasPermission(target.permission())) return 0.0D;
        if (definitions.rarity(target.rarityId()).isEmpty()) return 0.0D;

        double total = crate.rewards().stream()
                .filter(reward -> reward.weight() > 0.0D)
                .filter(reward -> reward.permission() == null || reward.permission().isBlank()
                        || player.hasPermission(reward.permission()))
                .filter(reward -> definitions.rarity(reward.rarityId()).isPresent())
                .mapToDouble(RewardDefinition::weight)
                .sum();
        return total <= 0.0D ? 0.0D : target.weight() / total;
    }

    public Component rewardNameComponent(RewardDefinition reward) {
        if (reward.type() == RewardType.ITEM && reward.item() != null) {
            ItemStack item = reward.item();
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName() && meta.displayName() != null) {
                return meta.displayName().decoration(TextDecoration.ITALIC, false);
            }
            return Component.translatable(item.getType().translationKey()).decoration(TextDecoration.ITALIC, false);
        }
        return core.messages().parse(reward.displayName()).decoration(TextDecoration.ITALIC, false);
    }

    public ItemStack displayItem(Player player, CrateDefinition crate, RewardDefinition reward) {
        ItemStack base = reward.item();
        ItemStack item = base == null
                ? new ItemStack(reward.icon() == null ? Material.CHEST : reward.icon())
                : base.clone();
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), reward.amount())));

        ItemMeta meta = item.getItemMeta();
        if (base == null) {
            meta.displayName(core.messages().parse(reward.displayName()).decoration(TextDecoration.ITALIC, false));
        }

        // Preserve the real reward's existing name/lore/enchants/PDC/model data for previews.
        // MiraCrates only appends the public chance. Rarity is intentionally hidden.
        List<net.kyori.adventure.text.Component> lore =
                meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        if (!lore.isEmpty()) lore.add(net.kyori.adventure.text.Component.empty());
        lore.add(core.messages().parse(String.format(Locale.ROOT, "&7Chance: &f%.3f%%",
                chance(player, crate, reward) * 100.0D)).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private boolean grantItem(Player player, RewardDefinition reward) {
        ItemStack item = reward.item();
        if (item == null) return false;
        int remaining = reward.amount();
        while (remaining > 0) {
            ItemStack part = item.clone();
            int amount = Math.min(part.getMaxStackSize(), remaining);
            part.setAmount(amount);
            giveOrDrop(player, part);
            remaining -= amount;
        }
        return true;
    }

    private boolean grantCommand(Player player, RewardDefinition reward) {
        if (reward.data() == null || reward.data().isBlank()) return false;
        String command = reward.data().replace("{player}", player.getName()).replace("%player%", player.getName());
        if (command.startsWith("/")) command = command.substring(1);
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private boolean grantMiraSpawner(Player player, RewardDefinition reward) {
        try {
            Class<?> apiClass = Class.forName("com.mira.spawners.api.MiraSpawnersApi");
            Optional<?> service = core.services().get(apiClass);
            if (service.isEmpty()) return false;
            Object api = service.get();
            Method maxMethod = apiClass.getMethod("maxSpawnerStack");
            Method createMethod = apiClass.getMethod("createSpawner", EntityType.class, int.class);
            int max = (int) maxMethod.invoke(api);
            EntityType type = EntityType.valueOf(reward.data().toUpperCase(Locale.ROOT));
            int remaining = reward.amount();
            while (remaining > 0) {
                int part = Math.min(max, remaining);
                ItemStack spawner = (ItemStack) createMethod.invoke(api, type, part);
                giveOrDrop(player, spawner);
                remaining -= part;
            }
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException ex) {
            plugin.getLogger().warning("Could not grant MiraSpawners reward '" + reward.id() + "': " + ex.getMessage());
            return false;
        }
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }
}
