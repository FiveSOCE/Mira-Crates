package com.mira.crates.service;

import com.mira.crates.MiraCratesPlugin;
import com.mira.crates.model.*;
import com.mira.crates.util.Ids;
import com.mira.crates.util.ShulkerMaterials;
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
        ensureBuiltInRarities();
        loadKeys();
        loadCrates();
        ensureEveryCrateHasKey();
    }

    private void ensureBuiltInRarities() {
        boolean changed = false;
        if (!rarities.containsKey("common")) {
            rarities.put("common", new RarityDefinition("common", "&fCommon", 1.0D, Material.WHITE_DYE));
            changed = true;
        }
        if (!rarities.containsKey("rare")) {
            rarities.put("rare", new RarityDefinition("rare", "&bRare", 1.0D, Material.DIAMOND));
            changed = true;
        }
        if (!rarities.containsKey("legendary")) {
            rarities.put("legendary", new RarityDefinition("legendary", "&6Legendary", 1.0D, Material.NETHER_STAR));
            changed = true;
        }
        if (!rarities.containsKey("mythic")) {
            rarities.put("mythic", new RarityDefinition("mythic", "&dMythic", 1.0D, Material.AMETHYST_SHARD));
            changed = true;
        }
        if (changed) saveRarities();
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
        return createCrate(id, displayName, Material.PURPLE_SHULKER_BOX, List.of());
    }

    public boolean createCrate(String id, String displayName, Material shulkerMaterial, List<RewardDefinition> rewards) {
        return createCrate(id, displayName, shulkerMaterial, 1, rewards);
    }

    public boolean createCrate(String id, String displayName, Material shulkerMaterial, int winsPerOpen,
                               List<RewardDefinition> rewards) {
        String normalized = Ids.normalize(id);
        if (!Ids.valid(normalized) || crates.containsKey(normalized) || !ShulkerMaterials.isCrateShulker(shulkerMaterial)) return false;

        String companionKeyId = companionKeyId(normalized);
        if (!keys.containsKey(companionKeyId)) {
            keys.put(companionKeyId, new KeyDefinition(companionKeyId, companionKeyName(displayName),
                    Material.TRIPWIRE_HOOK, false, companionKeyLore(displayName)));
            saveKeys();
        }

        crates.put(normalized, new CrateDefinition(normalized, displayName, shulkerMaterial,
                List.of(companionKeyId), 0L, winsPerOpen, rewards));
        saveCrates();
        return true;
    }

    public boolean updateCrate(String id, String displayName, Material shulkerMaterial, List<RewardDefinition> rewards) {
        CrateDefinition existing = crates.get(Ids.normalize(id));
        return updateCrate(id, displayName, shulkerMaterial,
                existing == null ? 1 : existing.winsPerOpen(), rewards);
    }

    public boolean updateCrate(String id, String displayName, Material shulkerMaterial, int winsPerOpen,
                               List<RewardDefinition> rewards) {
        String normalized = Ids.normalize(id);
        CrateDefinition existing = crates.get(normalized);
        if (existing == null || displayName == null || displayName.isBlank() || !ShulkerMaterials.isCrateShulker(shulkerMaterial)) return false;

        List<String> keyIds = existing.keyIds().isEmpty()
                ? List.of(ensureCompanionKey(existing.id(), displayName))
                : existing.keyIds();
        crates.put(normalized, new CrateDefinition(existing.id(), displayName, shulkerMaterial, keyIds,
                existing.cooldownSeconds(), winsPerOpen, rewards));
        saveCrates();
        return true;
    }

    public boolean deleteCrate(String id) {
        CrateDefinition removed = crates.remove(Ids.normalize(id));
        if (removed == null) return false;

        String companionKeyId = companionKeyId(removed.id());
        boolean keyRemoved = removed.keyIds().contains(companionKeyId)
                && crates.values().stream().noneMatch(crate -> crate.keyIds().contains(companionKeyId))
                && keys.remove(companionKeyId) != null;
        saveCrates();
        if (keyRemoved) saveKeys();
        return true;
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
                    crate.cooldownSeconds(), crate.winsPerOpen(), crate.rewards()));
        }
        saveKeys();
        saveCrates();
        ensureEveryCrateHasKey();
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
                crate.cooldownSeconds(), crate.winsPerOpen(), crate.rewards()));
        saveCrates();
        return true;
    }

    public boolean detachKey(String crateId, String keyId) {
        CrateDefinition crate = crates.get(Ids.normalize(crateId));
        if (crate == null) return false;
        List<String> updated = new ArrayList<>(crate.keyIds());
        if (!updated.remove(Ids.normalize(keyId))) return false;
        if (updated.isEmpty()) updated.add(ensureCompanionKey(crate.id(), crate.displayName()));
        crates.put(crate.id(), new CrateDefinition(crate.id(), crate.displayName(), crate.icon(), updated,
                crate.cooldownSeconds(), crate.winsPerOpen(), crate.rewards()));
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
                seconds, crate.winsPerOpen(), crate.rewards()));
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
            Material icon = ShulkerMaterials.normalise(material(yaml.getString(path + ".icon"), Material.PURPLE_SHULKER_BOX));
            List<String> keyIds = yaml.getStringList(path + ".keys").stream().map(Ids::normalize).toList();
            long cooldown = Math.max(0L, yaml.getLong(path + ".cooldown-seconds", 0L));
            int winsPerOpen = Math.max(1, Math.min(5, yaml.getInt(path + ".wins-per-open", 1)));
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
            crates.put(id, new CrateDefinition(id, name, icon, keyIds, cooldown, winsPerOpen, rewards));
        }
    }

    private void ensureEveryCrateHasKey() {
        boolean keysChanged = false;
        boolean cratesChanged = false;
        for (CrateDefinition crate : new ArrayList<>(crates.values())) {
            String companionId = companionKeyId(crate.id());
            KeyDefinition companion = keys.get(companionId);
            if (companion != null) {
                String expectedName = companionKeyName(crate.displayName());
                List<String> expectedLore = companionKeyLore(crate.displayName());
                if (!expectedName.equals(companion.displayName()) || companion.lore().isEmpty()) {
                    keys.put(companionId, new KeyDefinition(companion.id(), expectedName, companion.material(),
                            companion.virtual(), companion.lore().isEmpty() ? expectedLore : companion.lore()));
                    keysChanged = true;
                }
            }

            List<String> validKeys = crate.keyIds().stream().filter(keys::containsKey).distinct().toList();
            if (!validKeys.isEmpty()) {
                if (!validKeys.equals(crate.keyIds())) {
                    crates.put(crate.id(), new CrateDefinition(crate.id(), crate.displayName(), crate.icon(), validKeys,
                            crate.cooldownSeconds(), crate.winsPerOpen(), crate.rewards()));
                    cratesChanged = true;
                }
                continue;
            }

            String keyId = companionKeyId(crate.id());
            if (!keys.containsKey(keyId)) {
                keys.put(keyId, new KeyDefinition(keyId, companionKeyName(crate.displayName()), Material.TRIPWIRE_HOOK,
                        false, companionKeyLore(crate.displayName())));
                keysChanged = true;
            }
            crates.put(crate.id(), new CrateDefinition(crate.id(), crate.displayName(), crate.icon(), List.of(keyId),
                    crate.cooldownSeconds(), crate.winsPerOpen(), crate.rewards()));
            cratesChanged = true;
        }
        if (keysChanged) saveKeys();
        if (cratesChanged) saveCrates();
    }

    private String ensureCompanionKey(String crateId, String displayName) {
        String keyId = companionKeyId(crateId);
        if (!keys.containsKey(keyId)) {
            keys.put(keyId, new KeyDefinition(keyId, companionKeyName(displayName), Material.TRIPWIRE_HOOK,
                    false, companionKeyLore(displayName)));
            saveKeys();
        }
        return keyId;
    }

    private static String companionKeyId(String crateId) {
        String base = Ids.normalize(crateId);
        if (base.endsWith("_crate") && base.length() > "_crate".length()) {
            base = base.substring(0, base.length() - "_crate".length());
        }
        return base + "_key";
    }

    private static String companionKeyName(String crateDisplayName) {
        String name = crateDisplayName == null ? "" : crateDisplayName.trim();
        if (name.isBlank()) name = "&fCrate";
        return name + " Key";
    }

    private static List<String> companionKeyLore(String crateDisplayName) {
        String name = crateDisplayName == null || crateDisplayName.isBlank() ? "&fCrate" : crateDisplayName.trim();
        return List.of(
                "&7Key for " + name + "&7.",
                "&8Right-click the matching crate to use."
        );
    }

    private void saveCrates() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (CrateDefinition crate : crates.values()) {
            String path = "crates." + crate.id();
            yaml.set(path + ".name", crate.displayName());
            yaml.set(path + ".icon", ShulkerMaterials.normalise(crate.icon()).name());
            yaml.set(path + ".keys", crate.keyIds());
            yaml.set(path + ".cooldown-seconds", crate.cooldownSeconds());
            yaml.set(path + ".wins-per-open", crate.winsPerOpen());
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
