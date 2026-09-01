package com.mira.crates.listener;

import com.mira.core.api.MiraCore;
import com.mira.crates.gui.PreviewService;
import com.mira.crates.model.CrateLocation;
import com.mira.crates.service.CrateLocationService;
import com.mira.crates.service.OpeningService;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

public final class CrateListener implements Listener {
    private final MiraCore core;
    private final CrateLocationService locations;
    private final PreviewService previews;
    private final OpeningService openings;
    private final boolean previewLeft;
    private final boolean openRight;

    public CrateListener(MiraCore core, CrateLocationService locations, PreviewService previews, OpeningService openings,
                         boolean previewLeft, boolean openRight) {
        this.core = core;
        this.locations = locations;
        this.previews = previews;
        this.openings = openings;
        this.previewLeft = previewLeft;
        this.openRight = openRight;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) return;
        Optional<CrateLocation> linked = locations.at(event.getClickedBlock());
        if (linked.isEmpty()) return;
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && previewLeft) {
            event.setCancelled(true);
            if (!event.getPlayer().hasPermission("miracrates.preview")) {
                core.messages().send(event.getPlayer(), "&cYou do not have permission to preview crates.");
                return;
            }
            previews.open(event.getPlayer(), linked.get().crateId(), 0);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && openRight) {
            event.setCancelled(true);
            openings.attemptOpen(event.getPlayer(), linked.get().crateId(), false);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (locations.at(event.getBlock()).isEmpty()) return;
        event.setCancelled(true);
        core.messages().send(event.getPlayer(), "&cThis block is linked to a crate. Use /mcrates location remove first.");
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> locations.at(block).isPresent());
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> locations.at(block).isPresent());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        openings.finishNow(event.getPlayer());
    }
}
