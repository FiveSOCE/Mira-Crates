package com.mira.crates.util;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

public final class ShulkerMaterials {
    private static final List<Material> COLOURS = List.of(
            Material.WHITE_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX,
            Material.GRAY_SHULKER_BOX,
            Material.BLACK_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX,
            Material.RED_SHULKER_BOX,
            Material.ORANGE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX,
            Material.GREEN_SHULKER_BOX,
            Material.CYAN_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX,
            Material.PINK_SHULKER_BOX
    );

    private ShulkerMaterials() {
    }

    public static List<Material> colours() {
        return COLOURS;
    }

    public static boolean isCrateShulker(Material material) {
        return material != null && COLOURS.contains(material);
    }

    public static Material normalise(Material material) {
        return isCrateShulker(material) ? material : Material.PURPLE_SHULKER_BOX;
    }

    public static Material cycle(Material current, int direction) {
        int index = COLOURS.indexOf(normalise(current));
        int next = Math.floorMod(index + direction, COLOURS.size());
        return COLOURS.get(next);
    }

    public static String pretty(Material material) {
        String raw = normalise(material).name().replace("_SHULKER_BOX", "").toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (String part : raw.split("_")) {
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }
}
