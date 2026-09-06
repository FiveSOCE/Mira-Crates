package com.mira.crates.service;

import com.mira.core.api.MiraCore;
import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.model.KeyDefinition;
import com.mira.crates.util.Ids;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class KeyService {
    private final MiraCratesPlugin plugin;
    private final MiraCore core;
    private final DefinitionService definitions;
    private final PlayerDataService playerData;
    private final NamespacedKey keyIdKey;

    public KeyService(MiraCratesPlugin plugin, MiraCore core, DefinitionService definitions, PlayerDataService playerData) {
        this.plugin = plugin;
        this.core = core;
        this.definitions = definitions;
        this.playerData = playerData;
        this.keyIdKey = new NamespacedKey(plugin, "crate_key_id");
    }

    public Optional<ItemStack> create(String keyId, int amount) {
        Optional<KeyDefinition> definition = definitions.key(keyId);
        if (definition.isEmpty() || definition.get().virtual() || amount <= 0) return Optional.empty();
        KeyDefinition key = definition.get();
        ItemStack item = new ItemStack(key.material(), Math.min(64, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(core.messages().parse(key.displayName()).decoration(TextDecoration.ITALIC, false));
        List<String> loreLines = key.lore().isEmpty() ? defaultLore(key.id()) : key.lore();
        if (!loreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) lore.add(core.messages().parse(line).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
        }
        meta.getPersistentDataContainer().set(keyIdKey, PersistentDataType.STRING, key.id());
        item.setItemMeta(meta);
        return Optional.of(item);
    }

    public Optional<String> identify(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        String id = item.getItemMeta().getPersistentDataContainer().get(keyIdKey, PersistentDataType.STRING);
        return id == null ? Optional.empty() : Optional.of(Ids.normalize(id));
    }

    public boolean give(Player player, String keyId, int amount) {
        KeyDefinition key = definitions.key(keyId).orElse(null);
        if (key == null || amount <= 0) return false;
        if (key.virtual()) {
            playerData.addVirtualKeys(player.getUniqueId(), key.id(), amount);
            return true;
        }
        int remaining = amount;
        while (remaining > 0) {
            int part = Math.min(64, remaining);
            ItemStack item = create(key.id(), part).orElseThrow();
            giveOrDrop(player, item);
            remaining -= part;
        }
        return true;
    }

    public Optional<String> consumeHeld(Player player, List<String> acceptedKeyIds) {
        ItemStack held = player.getInventory().getItemInMainHand();
        String heldId = identify(held).orElse(null);
        if (heldId == null) return Optional.empty();

        boolean accepted = acceptedKeyIds.stream().map(Ids::normalize).anyMatch(heldId::equals);
        if (!accepted) return Optional.empty();

        KeyDefinition key = definitions.key(heldId).orElse(null);
        if (key == null || key.virtual()) return Optional.empty();

        if (held.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else held.setAmount(held.getAmount() - 1);
        return Optional.of(heldId);
    }

    public Optional<String> consumeAny(Player player, List<String> acceptedKeyIds) {
        for (String rawId : acceptedKeyIds) {
            KeyDefinition key = definitions.key(rawId).orElse(null);
            if (key == null || !key.virtual()) continue;
            if (playerData.consumeVirtualKey(player.getUniqueId(), key.id())) return Optional.of(key.id());
        }
        for (String rawId : acceptedKeyIds) {
            KeyDefinition key = definitions.key(rawId).orElse(null);
            if (key == null || key.virtual()) continue;
            if (consumePhysical(player, key.id())) return Optional.of(key.id());
        }
        return Optional.empty();
    }

    public String primaryKeyDisplayName(List<String> acceptedKeyIds) {
        for (String rawId : acceptedKeyIds) {
            KeyDefinition key = definitions.key(rawId).orElse(null);
            if (key != null) return key.displayName();
        }
        return "&fcrate key";
    }

    private List<String> defaultLore(String keyId) {
        return definitions.crates().stream()
                .filter(crate -> crate.keyIds().stream().map(Ids::normalize).anyMatch(Ids.normalize(keyId)::equals))
                .findFirst()
                .map(crate -> List.of(
                        "&7Key for " + crate.displayName() + "&7.",
                        "&8Right-click the matching crate to use."))
                .orElse(List.of("&7MiraCrates key.", "&8Use on its matching crate."));
    }

    private boolean consumePhysical(Player player, String keyId) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = storage[slot];
            if (identify(item).filter(keyId::equals).isEmpty()) continue;
            if (item.getAmount() <= 1) player.getInventory().setItem(slot, null);
            else item.setAmount(item.getAmount() - 1);
            return true;
        }
        return false;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }
}
