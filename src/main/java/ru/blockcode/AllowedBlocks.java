package ru.blockcode;

import java.util.Set;

/**
 * Какие блоки агент может ставить. Тот же список — в выпадающем меню редактора
 * (editor/editor.js, BLOCK_CHOICES). Сервер проверяет его сам, чтобы через
 * подделанный пакет нельзя было поставить, например, командный блок.
 */
public final class AllowedBlocks {
    public static final Set<String> IDS = Set.of(
            "oak_planks", "spruce_planks", "birch_planks", "dark_oak_planks",
            "oak_log", "spruce_log",
            "cobblestone", "stone", "stone_bricks", "bricks", "sandstone", "quartz_block",
            "glass", "glass_pane", "white_wool", "red_wool", "blue_wool", "yellow_wool",
            "oak_leaves", "dirt", "grass_block", "sand",
            "oak_fence", "torch", "glowstone", "sea_lantern"
    );

    private AllowedBlocks() {}
}
