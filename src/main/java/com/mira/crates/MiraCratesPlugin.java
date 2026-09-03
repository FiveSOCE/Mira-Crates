package com.mira.crates;

import com.mira.core.api.MiraCore;
import com.mira.core.api.MiraCoreProvider;
import com.mira.core.api.ModuleHealth;
import com.mira.crates.api.MiraCratesApi;
import com.mira.crates.api.MiraCratesApiImpl;
import com.mira.crates.command.MiraCratesCommand;
import com.mira.crates.gui.CrateEditorService;
import com.mira.crates.gui.EditorMenuService;
import com.mira.crates.gui.PreviewService;
import com.mira.crates.listener.AdminCrateChangeListener;
import com.mira.crates.listener.CrateListener;
import com.mira.crates.listener.MenuListener;
import com.mira.crates.service.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class MiraCratesPlugin extends JavaPlugin {
    private MiraCore core;
    private DefinitionService definitions;
    private PlayerDataService playerData;
    private CrateLocationService locations;
    private CrateHologramService holograms;
    private KeyService keys;
    private RewardEngine rewards;
    private HistoryService history;
    private OpeningService openings;
    private PreviewService previews;
    private CrateItemService crateItems;
    private CrateEditorService crateEditor;
    private EditorMenuService editor;
    private JackpotService jackpots;
    private SeasonalCrateService seasons;
    private MiraCratesApi api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfiguration();
        core = MiraCoreProvider.require();
        definitions = new DefinitionService(this);
        playerData = new PlayerDataService(this);
        locations = new CrateLocationService(this);
        holograms = new CrateHologramService(this, definitions, locations);
        keys = new KeyService(this, core, definitions, playerData);
        rewards = new RewardEngine(this, core, definitions, keys);
        history = new HistoryService(this);
        jackpots = new JackpotService(this);
        seasons = new SeasonalCrateService(this);
        previews = new PreviewService(core, definitions, rewards);
        openings = new OpeningService(this, core, definitions, keys, rewards, playerData, history, jackpots, seasons);
        crateItems = new CrateItemService(this, core, definitions);
        crateEditor = new CrateEditorService(core, definitions, crateItems);
        editor = new EditorMenuService(core, definitions, locations, previews, crateEditor, crateItems, keys);
        api = new MiraCratesApiImpl(definitions, keys, openings);

        core.modules().register(this, "MiraCrates");
        core.services().register(MiraCratesApi.class, api);

        getServer().getPluginManager().registerEvents(new MenuListener(this, editor, crateEditor, previews), this);
        getServer().getPluginManager().registerEvents(new CrateListener(core, locations, crateItems, holograms, previews, openings,
                getConfig().getBoolean("interaction.preview-on-left-click", true),
                getConfig().getBoolean("interaction.open-on-right-click", true)), this);
        getServer().getPluginManager().registerEvents(new AdminCrateChangeListener(this, core, definitions, locations, crateItems, holograms), this);

        MiraCratesCommand command = new MiraCratesCommand(this, core, definitions, keys, rewards, openings, previews, editor, crateItems, locations);
        PluginCommand pluginCommand = getCommand("miracrates");
        if (pluginCommand == null) {
            core.modules().setHealth(this, ModuleHealth.UNHEALTHY, "miracrates command missing from plugin.yml");
            throw new IllegalStateException("miracrates command missing from plugin.yml");
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) new CratesPlaceholderExpansion(this, jackpots).register();
        getServer().getScheduler().runTask(this, holograms::syncAll);

        core.modules().setHealth(this, ModuleHealth.HEALTHY,
                "Crates, rare-win broadcasts, jackpot data and seasonal windows ready");
        getLogger().info("MiraCrates v" + getPluginMeta().getVersion() + " enabled with " + definitions.crates().size() + " crate definitions.");
    }

    @Override
    public void onDisable() {
        if (holograms != null) holograms.shutdown();
        if (openings != null) openings.shutdown();
        if (playerData != null) playerData.save();
        if (core != null) {
            if (api != null) core.services().unregister(MiraCratesApi.class, api);
            core.modules().unregister(this);
        }
    }

    public void reloadPluginConfiguration() {
        reloadConfig();
        migrateConfiguration();
        definitions.reload();
        locations.reload();
        playerData.reload();
        if (holograms != null) getServer().getScheduler().runTask(this, holograms::syncAll);
    }

    public MiraCore core() { return core; }

    public boolean miraSpawnersAvailable() {
        try {
            Class<?> apiClass = Class.forName("com.mira.spawners.api.MiraSpawnersApi");
            return core.services().get(apiClass).isPresent();
        } catch (ClassNotFoundException ex) { return false; }
    }

    private void migrateConfiguration() {
        int version = getConfig().getInt("config-version", 1);
        if (version < 2 && getConfig().getInt("opening.animation-ticks", 60) == 60) getConfig().set("opening.animation-ticks", 120);
        if (version < 3) {
            if (!getConfig().contains("holograms.enabled")) getConfig().set("holograms.enabled", true);
            if (!getConfig().contains("holograms.height")) getConfig().set("holograms.height", 1.65D);
        }
        if (version < 4) {
            if (!getConfig().contains("rare-win.rarities")) getConfig().set("rare-win.rarities", java.util.List.of("legendary", "mythic", "jackpot"));
            if (!getConfig().contains("rare-win.message")) getConfig().set("rare-win.message", "&6[Jackpot] &f%player% &7won %reward% &7from %crate%&7!");
            getConfig().set("config-version", 4);
            saveConfig();
        }
    }
}
