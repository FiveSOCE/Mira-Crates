package com.mira.crates.service;

import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.model.*;
import com.mira.crates.util.Ids;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class DefinitionService {
    private final MiraCratesPlugin plugin;
    private final File cratesFile;
    private final File keysFile;
    private final File raritiesFile;
    private final Map<String, CrateDefinition> crates = new LinkedHashMap<>();
    private final Map<String, KeyDefinition> keys = new LinkedHashMap<>();
    private final Map<String, RarityDefinition> rarities = new LinkedHashMap<>();

    public DefinitionService(MiraCratesPlugin plugin) {
        this.plugin = plugin;
        this.cratesFile = new File(plugin.getDataFolder(), "crates.yml");
        this.keysFile = new File(plugin.getDataFolder(), "keys.yml");
        this.raritiesFile = new File(plugin.getDataFolder(), "rarities.yml");
        ensureResource("crates.yml", cratesFile);
        ensureResource("keys.yml", keysFile);
        ensureResource("rarities.yml", raritiesFile);
        reload();
    }

    private void ensureResource(String name, File target) {
        if (!target.exists()) plugin.saveResource(name, false);
    }

    public void reload() {
        crates.clear();
        keys.clear();
        rarities.clear();
        loadRarities();
        loadKeys();
        loadCrates();
    }

    public Collection<CrateDefinition> crates() {
        return Collections.unmodifiableCollection(crates.values());
    }

    public Collection<KeyDefinition> keys() {
        return Collections.unmodifiableCollection(keys.values());
    }

    public Collection<RarityDefinition> rarities() {
        return Collections.unmodifiableCollection(rarities.values());
    }

    public Optional<CrateDefinition> crate(String id) {
        return Optional.ofNullable(crates.get(Ids.normalize(id)));
    }

    public Optional<KeyDefinition> key(String id) {
        return Optional.ofNullable(keys.get(Ids.normalize(id)));
    }

    public Optional<RarityDefinition> rarity(String id) {
        return Optional.ofNullable(rarities.get(Ids.normalize(id)));
    }

    public boolean createCrate(String id, String displayName) {
        String normalized = Ids.normalize(id);
        if (!Ids.valid(normalized) || crates.containsKey(normalized)) return false;
        crates.put(normalized, new CrateDefinition(normalized, displayName, Material.CHEST, List.of(), 0L, List.of()));
        saveCrates();
        return true;
    }

    public boolean deleteCrate(String id) {
        boolean removed = crates.remove(Ids.normalize(id)) != null;
        if (removed) saveCrates();
        return removed;
    }

    public boolean createKey(String id, String displayName, boolean virtual) {
        String normalized = Ids.normalize(id);
        if (!Ids.valid(normalized) || keys.containsKey(normalized)) return false;
        keys.put(normalized, new KeyDefinition(normalized, displayName, Material.TRIPWIRE_HOOK, virtual, List.of()));
        saveKeys();
        return true;
    }

    public boolean deleteKey(String id) {
        String normalized = Ids.normalize(id);
        if (keys.remove(normalized) == null) return false;
        for (CrateDefinition crate : new ArrayList<>(crates.values())) {
            if (!crate.keyIds().contains(normalized)) continue;
            List<String> updated = new ArrayList<>(crate.keyIds());
            updated.remove(normalized);
            crates.put(crate.id(), new CrateDefinition(crate.id(), crate.displayName(), crate.icon(), updated,
                    crate.cooldownSeconds(), crate.rewards()));
        }
        saveKeys();
        saveCrates();
        return true;
    }

    public boolean createRarity(String id, double weight, String displayName) {
        String normalized = Ids.normalize(id);
        if (!Ids.valid(normalized) || rarities.containsKey(normalized) || weight < 0.0D) return false;
        rarities.put(normalized, new RarityDefinition(normalized, displayName, weight, Material.NETHER_STAR));
        saveRarities();
        return true;
    }

    public boolean deleteRarity(String id) {
        boolean removed = rarities.remove(Ids.normalize(id)) != null;
        if (removed) saveRarities();
        return removed;
    }

    public boolean attachKey(String crateId, String keyId) {
        CrateDefinition crate = crates.get(Ids.normalize(crateId));
        String normalizedKey = Ids.normalize(keyId);
        if (crate == null || !keys.containsKey(normalizedKey)) return false;
        List<String> updated = new ArrayList<>(crate.keyIds());
        if (!updated.contains(normalizedKey)) updated.add(normalizedKey);
        crates.put(crate.id(), new CrateDefinition(crate.id(), crate.displayName(), crate.icon(), updated,
                crate.cooldownSeconds(), crate.rewards()));
        saveCrates();
        return true;
    }

    public boolean detachKey(String crateId, String keyId) {
        CrateDefinition crate = crates.get(Ids.normalize(crateId));
        if (crate == null) return false;
        List<String> updated = new ArrayList<>(crate.keyIds());
        if (!updated.remove(Ids.normalize(keyId))) return false;
        crates.put(crate.id(), new CrateDefinition(crate.id(), crate.displayName(), crate.icon(), updated,
                crate.cooldownSeconds(), crate.rewards()));
        saveCrates();
        return true;
    }

    public boolean addReward(String crateId, RewardDefinition reward) {
        CrateDefinition crate = crates.get(Ids.normalize(crateId));
        if (crate == null || !Ids.valid(reward.id()) || rarity(reward.rarityId()).isEmpty()) return false;
        List<RewardDefinition> updated = new ArrayList<>(crate.rewards());
        updated.removeIf(existing -> existing.id().equalsIgnoreCase(reward.id()));
        updated.add(reward);
        crates.put(crate.id(), new CrateDefinition(crate.id(), crate.displayName(), crate.icon(), crate.keyIds(),
                crate.cooldownSeconds(), updated));
        saveCrates();
        return true;
    }

    public boolean removeReward(String crateId, String rewardId) {
        CrateDefinition crate = crates.get(Ids.normalize(crateId));
        if (crate == null) return false;
        List<RewardDefinition> updated = new ArrayList<>(crate.rewards());
        if (!updated.removeIf(reward -> reward.id().equalsIgnoreCase(Ids.normalize(rewardId)))) return false;
        crates.put(crate.id(), new CrateDefinition(crate.id(), crate.displayName(), crate.icon(), crate.keyIds(),
                crate.cooldownSeconds(), updated));
        saveCrates();
        return true;
    }

    public boolean setCooldown(String crateId, long seconds) {
        CrateDefinition crate = crates.get(Ids.normalize(crateId));
        if (crate == null || seconds < 0L) return false;
        crates.put(crate.id(), new CrateDefinition(crate.id(), crate.displayName(), crate.icon(), crate.keyIds(),
                seconds, crate.rewards()));
        saveCrates();
        return true;
    }

    private void loadRarities() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(raritiesFile);
        ConfigurationSection root = yaml.getConfigurationSection("rarities");
        if (root == null) return;
        for (String rawId : root.getKeys(false)) {
            String id = Ids.normalize(rawId);
            String path = "rarities." + rawId;
            String name = yaml.getString(path + ".name", id);
            double weight = Math.max(0.0D, yaml.getDouble(path + ".weight", 1.0D));
            Material icon = material(yaml.getString(path + ".icon"), Material.NETHER_STAR);
            rarities.put(id, new RarityDefinition(id, name, weight, icon));
        }
    }

    private void loadKeys() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(keysFile);
        ConfigurationSection root = yaml.getConfigurationSection("keys");
        if (root == null) return;
        for (String rawId : root.getKeys(false)) {
            String id = Ids.normalize(rawId);
            String path = "keys." + rawId;
            String name = yaml.getString(path + ".name", id + " Key");
            Material material = material(yaml.getString(path + ".material"), Material.TRIPWIRE_HOOK);
            boolean virtual = yaml.getBoolean(path + ".virtual", false);
            List<String> lore = yaml.getStringList(path + ".lore");
            keys.put(id, new KeyDefinition(id, name, material, virtual, lore));
        }
    }

    private void loadCrates() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(cratesFile);
        ConfigurationSection root = yaml.getConfigurationSection("crates");
        if (root == null) return;
        for (String rawId : root.getKeys(false)) {
            String id = Ids.normalize(rawId);
            String path = "crates." + rawId;
            String name = yaml.getString(path + ".name", id + " Crate");
            Material icon = material(yaml.getString(path + ".icon"), Material.CHEST);
            List<String> keyIds = yaml.getStringList(path + ".keys").stream().map(Ids::normalize).toList();
            long cooldown = Math.max(0L, yaml.getLong(path + ".cooldown-seconds", 0L));
            List<RewardDefinition> rewards = new ArrayList<>();
            ConfigurationSection rewardRoot = yaml.getConfigurationSection(path + ".rewards");
            if (rewardRoot != null) {
                for (String rawRewardId : rewardRoot.getKeys(false)) {
                    String rewardId = Ids.normalize(rawRewardId);
                    String rewardPath = path + ".rewards." + rawRewardId;
                    RewardType type;
                    try {
                        type = RewardType.valueOf(yaml.getString(rewardPath + ".type", "ITEM").toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("Skipping unknown reward type at " + rewardPath);
                        continue;
                    }
                    String rarity = Ids.normalize(yaml.getString(rewardPath + ".rarity", "common"));
                    double weight = Math.max(0.0D, yaml.getDouble(rewardPath + ".weight", 1.0D));
                    String display = yaml.getString(rewardPath + ".name", rewardId);
                    Material rewardIcon = material(yaml.getString(rewardPath + ".icon"), defaultIcon(type));
                    int amount = Math.max(1, yaml.getInt(rewardPath + ".amount", 1));
                    String permission = yaml.getString(rewardPath + ".permission", "");
                    boolean broadcast = yaml.getBoolean(rewardPath + ".broadcast", false);
                    ItemStack item = yaml.getItemStack(rewardPath + ".item");
                    String data = yaml.getString(rewardPath + ".data", "");
                    rewards.add(new RewardDefinition(rewardId, type, rarity, weight, display, rewardIcon, amount,
                            permission, broadcast, item, data));
                }
            }
            crates.put(id, new CrateDefinition(id, name, icon, keyIds, cooldown, rewards));
        }
    }

    private void saveCrates() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (CrateDefinition crate : crates.values()) {
            String path = "crates." + crate.id();
            yaml.set(path + ".name", crate.displayName());
            yaml.set(path + ".icon", crate.icon().name());
            yaml.set(path + ".keys", crate.keyIds());
            yaml.set(path + ".cooldown-seconds", crate.cooldownSeconds());
            for (RewardDefinition reward : crate.rewards()) {
                String rewardPath = path + ".rewards." + reward.id();
                yaml.set(rewardPath + ".type", reward.type().name());
                yaml.set(rewardPath + ".rarity", reward.rarityId());
                yaml.set(rewardPath + ".weight", reward.weight());
                yaml.set(rewardPath + ".name", reward.displayName());
                yaml.set(rewardPath + ".icon", reward.icon().name());
                yaml.set(rewardPath + ".amount", reward.amount());
                yaml.set(rewardPath + ".permission", reward.permission());
                yaml.set(rewardPath + ".broadcast", reward.broadcast());
                yaml.set(rewardPath + ".item", reward.item());
                yaml.set(rewardPath + ".data", reward.data());
            }
        }
        save(yaml, cratesFile);
    }

    private void saveKeys() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (KeyDefinition key : keys.values()) {
            String path = "keys." + key.id();
            yaml.set(path + ".name", key.displayName());
            yaml.set(path + ".material", key.material().name());
            yaml.set(path + ".virtual", key.virtual());
            yaml.set(path + ".lore", key.lore());
        }
        save(yaml, keysFile);
    }

    private void saveRarities() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (RarityDefinition rarity : rarities.values()) {
            String path = "rarities." + rarity.id();
            yaml.set(path + ".name", rarity.displayName());
            yaml.set(path + ".weight", rarity.weight());
            yaml.set(path + ".icon", rarity.icon().name());
        }
        save(yaml, raritiesFile);
    }

    private void save(YamlConfiguration yaml, File file) {
        try {
            yaml.save(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save " + file.getName(), ex);
        }
    }

    private static Material material(String value, Material fallback) {
        if (value == null) return fallback;
        Material found = Material.matchMaterial(value);
        return found == null ? fallback : found;
    }

    private static Material defaultIcon(RewardType type) {
        return switch (type) {
            case ITEM -> Material.CHEST;
            case COMMAND -> Material.COMMAND_BLOCK;
            case MIRA_SPAWNER -> Material.SPAWNER;
            case KEY -> Material.TRIPWIRE_HOOK;
            case XP_LEVELS -> Material.EXPERIENCE_BOTTLE;
        };
    }
}
