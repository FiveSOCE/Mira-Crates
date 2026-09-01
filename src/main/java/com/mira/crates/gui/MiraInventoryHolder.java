package com.mira.crates.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class MiraInventoryHolder implements InventoryHolder {
    public enum Type {
        MAIN,
        CRATES,
        KEYS,
        RARITIES,
        PREVIEW,
        OPENING,
        CRATE_EDITOR,
        CRATE_NAME,
        CRATE_CHANCE
    }

    private final Type type;
    private final String context;
    private final int page;
    private Inventory inventory;

    public MiraInventoryHolder(Type type, String context, int page) {
        this.type = type;
        this.context = context;
        this.page = page;
    }

    public Type type() {
        return type;
    }

    public String context() {
        return context;
    }

    public int page() {
        return page;
    }

    public void bind(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) throw new IllegalStateException("Inventory holder has not been bound yet");
        return inventory;
    }
}
