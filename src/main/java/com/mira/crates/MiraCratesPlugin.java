package com.mira.crates;

import com.mira.core.api.MiraCore;
import com.mira.core.api.MiraCoreProvider;
import com.mira.core.api.ModuleHealth;
import com.mira.crates.api.MiraCratesApi;
import com.mira.crates.api.MiraCratesApiImpl;
import com.mira.crates.command.MiraCratesCommand;
import com.mira.crates.gui.EditorMenuService;
import com.mira.crates.gui.PreviewService;
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
    private KeyService keys;
    private RewardEngine rewards;
    private HistoryService history;
    private OpeningService openings;
    private PreviewService previews;
    private EditorMenuService editor;
    private MiraCratesApi api;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        core = MiraCoreProvider.require();
        definitions = new DefinitionService(this);
        playerData = new PlayerDataService(this);
        locations = new CrateLocationService(this);
        keys = new KeyService(this, core, definitions, playerData);
        rewards = new RewardEngine(this, core, definitions, keys);
        history = new HistoryService(this);
        previews = new PreviewService(core, definitions, rewards);
        openings = new OpeningService(this, core, definitions, keys, rewards, playerData, history);
        editor = new EditorMenuService(core, definitions, locations, previews);
        api = new MiraCratesApiImpl(definitions, keys, openings);

        core.modules().register(this, "MiraCrates");
        core.services().register(MiraCratesApi.class, api);

        getServer().getPluginManager().registerEvents(new MenuListener(editor, previews), this);
        getServer().getPluginManager().registerEvents(new CrateListener(core, locations, previews, openings,
                getConfig().getBoolean("interaction.preview-on-left-click", true),
                getConfig().getBoolean("interaction.open-on-right-click", true)), this);

        MiraCratesCommand command = new MiraCratesCommand(this, core, definitions, keys, locations, rewards,
                openings, previews, editor);
        PluginCommand pluginCommand = getCommand("miracrates");
        if (pluginCommand == null) {
            core.modules().setHealth(this, ModuleHealth.UNHEALTHY, "miracrates command missing from plugin.yml");
            throw new IllegalStateException("miracrates command missing from plugin.yml");
        }
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        core.modules().setHealth(this, ModuleHealth.HEALTHY,
                "Crates, keys, weighted rewards, previews and opening sessions ready");
        getLogger().info("MiraCrates v" + getPluginMeta().getVersion() + " enabled with "
                + definitions.crates().size() + " crate definitions.");
    }

    @Override
    public void onDisable() {
        if (openings != null) openings.shutdown();
        if (playerData != null) playerData.save();
        if (core != null) {
            if (api != null) core.services().unregister(MiraCratesApi.class, api);
            core.modules().unregister(this);
        }
    }

    public void reloadPluginConfiguration() {
        reloadConfig();
        definitions.reload();
        locations.reload();
        playerData.reload();
    }

    public MiraCore core() {
        return core;
    }

    public boolean miraSpawnersAvailable() {
        try {
            Class<?> apiClass = Class.forName("com.mira.spawners.api.MiraSpawnersApi");
            return core.services().get(apiClass).isPresent();
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
