package com.mira.crates.service;

import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.model.CrateLocation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class CrateHologramService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final MiraCratesPlugin plugin;
    private final DefinitionService definitions;
    private final CrateLocationService locations;
    private final NamespacedKey markerKey;
    private final NamespacedKey blockKey;
    private final boolean enabled;
    private final double height;

    public CrateHologramService(MiraCratesPlugin plugin, DefinitionService definitions, CrateLocationService locations) {
        this.plugin = plugin;
        this.definitions = definitions;
        this.locations = locations;
        this.markerKey = new NamespacedKey(plugin, "crate_hologram");
        this.blockKey = new NamespacedKey(plugin, "crate_hologram_block");
        this.enabled = plugin.getConfig().getBoolean("holograms.enabled", true);
        this.height = plugin.getConfig().getDouble("holograms.height", 1.65D);
    }

    public boolean available() {
        return enabled && Bukkit.getPluginManager().isPluginEnabled("Holograms");
    }

    public void syncAll() {
        removeAllOwned();
        if (!enabled) return;
        if (!available()) {
            plugin.getLogger().warning("Crate holograms are enabled, but the Holograms plugin is not installed/enabled. Crates will work without holograms.");
            return;
        }

        java.util.List<Block> stale = new java.util.ArrayList<>();
        for (CrateLocation crateLocation : new java.util.ArrayList<>(locations.all())) {
            World world = Bukkit.getWorld(crateLocation.world());
            if (world == null) continue;

            Block block = world.getBlockAt(crateLocation.x(), crateLocation.y(), crateLocation.z());
            boolean validBlock = block.getState() instanceof org.bukkit.block.ShulkerBox;
            boolean validDefinition = definitions.crate(crateLocation.crateId()).isPresent();
            if (!validBlock || !validDefinition) {
                stale.add(block);
                continue;
            }
            create(block, crateLocation.crateId());
        }

        for (Block block : stale) locations.remove(block);
    }

    public void create(Block block, String crateId) {
        if (!available()) return;
        remove(block);

        String displayName = definitions.crate(crateId)
                .map(crate -> crate.displayName())
                .orElse(crateId);
        Component text = LEGACY.deserialize(displayName);
        Location location = block.getLocation().add(0.5D, height, 0.5D);
        String key = blockKey(block);

        block.getWorld().spawn(location, ArmorStand.class, display -> {
            display.setCustomName(LegacyComponentSerializer.legacySection().serialize(text));
            display.setCustomNameVisible(true);
            display.setInvisible(true);
            display.setMarker(true);
            display.setSmall(true);
            display.setGravity(false);
            display.setPersistent(true);
            display.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
            display.getPersistentDataContainer().set(blockKey, PersistentDataType.STRING, key);
        });
    }

    public void remove(Block block) {
        String key = blockKey(block);
        for (ArmorStand display : block.getWorld().getEntitiesByClass(ArmorStand.class)) {
            String stored = display.getPersistentDataContainer().get(blockKey, PersistentDataType.STRING);
            if (key.equals(stored)) display.remove();
        }
    }

    public void removeAllOwned() {
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand display : world.getEntitiesByClass(ArmorStand.class)) {
                Byte marker = display.getPersistentDataContainer().get(markerKey, PersistentDataType.BYTE);
                if (marker != null && marker == (byte) 1) display.remove();
            }
        }
    }

    public void shutdown() {
        // Displays are regenerated from MiraCrates locations on startup. Removing them here prevents stale labels.
        removeAllOwned();
    }

    private static String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }
}
