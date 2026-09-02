package com.mira.crates.listener;

import com.mira.core.api.MiraCore;
import com.mira.crates.gui.PreviewService;
import com.mira.crates.model.CrateLocation;
import com.mira.crates.service.CrateHologramService;
import com.mira.crates.service.CrateItemService;
import com.mira.crates.service.CrateLocationService;
import com.mira.crates.service.OpeningService;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Optional;

public final class CrateListener implements Listener {
    private final MiraCore core;
    private final CrateLocationService locations;
    private final CrateItemService crateItems;
    private final CrateHologramService holograms;
    private final PreviewService previews;
    private final OpeningService openings;
    private final boolean previewLeft;
    private final boolean openRight;

    public CrateListener(MiraCore core, CrateLocationService locations, CrateItemService crateItems,
                         CrateHologramService holograms, PreviewService previews, OpeningService openings,
                         boolean previewLeft, boolean openRight) {
        this.core = core;
        this.locations = locations;
        this.crateItems = crateItems;
        this.holograms = holograms;
        this.previews = previews;
        this.openings = openings;
        this.previewLeft = previewLeft;
        this.openRight = openRight;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Optional<String> crateId = crateItems.crateId(event.getItemInHand());
        if (crateId.isEmpty()) return;
        locations.set(event.getBlockPlaced(), crateId.get());
        crateItems.markPlaced(event.getBlockPlaced(), crateId.get());
        holograms.create(event.getBlockPlaced(), crateId.get());
        core.messages().send(event.getPlayer(), "&aDeployed crate &f" + crateId.get() + "&a.");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) return;
        Optional<String> crateId = crateAt(event.getClickedBlock());
        if (crateId.isEmpty()) return;
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && previewLeft) {
            event.setCancelled(true);
            if (!event.getPlayer().hasPermission("miracrates.preview")) {
                core.messages().send(event.getPlayer(), "&cYou do not have permission to preview crates.");
                return;
            }
            previews.open(event.getPlayer(), crateId.get(), 0);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && openRight) {
            event.setCancelled(true);
            boolean quickOpen = event.getPlayer().isSneaking();
            openings.attemptPhysicalOpen(event.getPlayer(), crateId.get(), quickOpen);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Optional<String> crateId = crateAt(event.getBlock());
        if (crateId.isEmpty()) return;
        if (!event.getPlayer().hasPermission("miracrates.admin")) {
            event.setCancelled(true);
            core.messages().send(event.getPlayer(), "&cThis MiraCrates shulker is protected.");
            return;
        }
        event.setDropItems(false);
        event.setExpToDrop(0);
        if (event.getBlock().getState() instanceof ShulkerBox shulkerBox) shulkerBox.getInventory().clear();
        holograms.remove(event.getBlock());
        locations.remove(event.getBlock());
        crateItems.give(event.getPlayer(), crateId.get());
        core.messages().send(event.getPlayer(), "&aPicked up crate &f" + crateId.get() + "&a.");
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> crateAt(block).isPresent());
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> crateAt(block).isPresent());
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> crateAt(block).isPresent())) event.setCancelled(true);
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> crateAt(block).isPresent())) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        openings.finishNow(event.getPlayer());
    }

    private Optional<String> crateAt(Block block) {
        Optional<CrateLocation> linked = locations.at(block);
        if (linked.isPresent()) return Optional.of(linked.get().crateId());
        Optional<String> embedded = crateItems.crateId(block);
        embedded.ifPresent(crateId -> {
            locations.set(block, crateId);
            holograms.create(block, crateId);
        });
        return embedded;
    }
}
