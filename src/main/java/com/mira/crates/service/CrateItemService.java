package com.mira.crates.service;

import com.mira.core.api.MiraCore;
import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.model.CrateDefinition;
import com.mira.crates.util.ShulkerMaterials;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CrateItemService {
    private final MiraCore core;
    private final DefinitionService definitions;
    private final NamespacedKey itemCrateKey;
    private final NamespacedKey blockCrateKey;

    public CrateItemService(MiraCratesPlugin plugin, MiraCore core, DefinitionService definitions) {
        this.core = core;
        this.definitions = definitions;
        this.itemCrateKey = new NamespacedKey(plugin, "crate-id");
        this.blockCrateKey = new NamespacedKey(plugin, "placed-crate-id");
    }

    public Optional<ItemStack> create(String crateId) {
        return definitions.crate(crateId).map(this::create);
    }

    public ItemStack create(CrateDefinition crate) {
        ItemStack item = new ItemStack(ShulkerMaterials.normalise(crate.icon()));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(core.messages().parse(crate.displayName()).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                core.messages().parse("&7MiraCrates").decoration(TextDecoration.ITALIC, false),
                core.messages().parse("&7Place this shulker to deploy the crate.").decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(itemCrateKey, PersistentDataType.STRING, crate.id());
        item.setItemMeta(meta);
        return item;
    }

    public Optional<String> crateId(ItemStack item) {
        if (item == null || item.getType().isAir() || !ShulkerMaterials.isCrateShulker(item.getType())) return Optional.empty();
        ItemMeta meta = item.getItemMeta();
        String value = meta.getPersistentDataContainer().get(itemCrateKey, PersistentDataType.STRING);
        if (value == null || definitions.crate(value).isEmpty()) return Optional.empty();
        return Optional.of(value);
    }

    public void markPlaced(Block block, String crateId) {
        if (!(block.getState() instanceof TileState tileState)) return;
        tileState.getPersistentDataContainer().set(blockCrateKey, PersistentDataType.STRING, crateId);
        tileState.update(true, false);
    }

    public Optional<String> crateId(Block block) {
        if (block == null || !ShulkerMaterials.isCrateShulker(block.getType())) return Optional.empty();
        if (!(block.getState() instanceof TileState tileState)) return Optional.empty();
        String value = tileState.getPersistentDataContainer().get(blockCrateKey, PersistentDataType.STRING);
        if (value == null || definitions.crate(value).isEmpty()) return Optional.empty();
        return Optional.of(value);
    }

    public boolean give(Player player, String crateId) {
        Optional<ItemStack> created = create(crateId);
        if (created.isEmpty()) return false;
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(created.get());
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        return true;
    }
}
