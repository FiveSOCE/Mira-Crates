package com.mira.crates.listener;

import com.mira.core.api.MiraCore;
import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.model.CrateDefinition;
import com.mira.crates.model.CrateLocation;
import com.mira.crates.service.CrateHologramService;
import com.mira.crates.service.CrateItemService;
import com.mira.crates.service.CrateLocationService;
import com.mira.crates.service.DefinitionService;
import com.mira.crates.util.Ids;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Optional;

public final class AdminCrateChangeListener implements Listener {
    private final MiraCratesPlugin plugin;
    private final MiraCore core;
    private final DefinitionService definitions;
    private final CrateLocationService locations;
    private final CrateItemService crateItems;
    private final CrateHologramService holograms;

    public AdminCrateChangeListener(MiraCratesPlugin plugin, MiraCore core, DefinitionService definitions,
                                    CrateLocationService locations, CrateItemService crateItems,
                                    CrateHologramService holograms) {
        this.plugin = plugin;
        this.core = core;
        this.definitions = definitions;
        this.locations = locations;
        this.crateItems = crateItems;
        this.holograms = holograms;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null) return;
        String lower = message.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("/mcrate change ") && !lower.startsWith("/mcrates change ")
                && !lower.startsWith("/miracrates change ")) return;

        event.setCancelled(true);
        if (!event.getPlayer().hasPermission("miracrates.admin")) {
            core.messages().send(event.getPlayer(), "&cYou do not have permission to administer MiraCrates.");
            return;
        }

        String[] split = message.substring(1).trim().split("\\s+", 3);
        if (split.length < 3 || split[2].isBlank()) {
            core.messages().send(event.getPlayer(), "&eUsage: /mcrate change <crate name>");
            return;
        }

        String requested = crateIdFromQuery(split[2]);
        Optional<CrateDefinition> replacement = definitions.crate(requested);
        if (replacement.isEmpty()) {
            core.messages().send(event.getPlayer(), "&cUnknown crate: " + split[2]);
            return;
        }

        int distance = Math.max(1, plugin.getConfig().getInt("interaction.target-distance", 6));
        Block target = event.getPlayer().getTargetBlockExact(distance);
        if (target == null) {
            core.messages().send(event.getPlayer(), "&cLook directly at a placed MiraCrates crate within " + distance + " blocks.");
            return;
        }

        Optional<String> current = locations.at(target).map(CrateLocation::crateId);
        if (current.isEmpty()) current = crateItems.crateId(target);
        if (current.isEmpty() || !(target.getState() instanceof ShulkerBox oldBox)) {
            core.messages().send(event.getPlayer(), "&cThat block is not a placed MiraCrates crate.");
            return;
        }

        oldBox.getInventory().clear();
        holograms.remove(target);

        CrateDefinition crate = replacement.get();
        target.setType(crate.icon(), false);
        crateItems.markPlaced(target, crate.id());
        locations.set(target, crate.id());
        holograms.create(target, crate.id());

        core.messages().send(event.getPlayer(), "&aChanged placed crate from &f" + current.get()
                + " &ato &f" + crate.id() + "&a.");
    }

    private static String crateIdFromQuery(String query) {
        String plain = query.replaceAll("(?i)&[0-9A-FK-OR]", "").replaceAll("[^A-Za-z0-9 _-]", "");
        return Ids.normalize(plain);
    }
}
