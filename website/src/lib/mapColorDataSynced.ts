// AUTO-SYNCED from PNG-to-NBT/website/src/data/mapColors.ts
// Run `npm run sync:map-colors` to refresh.
// Exports: `SHADE_MULTIPLIERS`, `BASE_COLORS`, `WATER_BASE_INDEX`, `getColorLookup`, `getShadedRgb`.

// Minecraft map color data
// Base RGB values are the "light" shade (multiplier 255/255 = full brightness)
// Other shades: dark=180/255, flat=220/255, darkest=135/255

export const SHADE_MULTIPLIERS = [180, 220, 255, 135] as const;
// Index 0=dark, 1=flat, 2=light, 3=darkest

export interface BaseColor {
  name: string;
  r: number;
  g: number;
  b: number;
  blocks: string[]; // possible block IDs (without minecraft: prefix)
}

// WATER is the only base color with special logic; current hardcoding has it stored at index 12
export const WATER_BASE_INDEX = 12;

// 62 base colors (index 0 = transparent/NONE)
export const BASE_COLORS: BaseColor[] = [
  { name: "NONE", r: 0, g: 0, b: 0, blocks: ["glass", "glass_pane", "barrier", "chain", "end_rod", "ladder", "rail", "powered_rail", "detector_rail", "activator_rail", "lever", "torch", "wall_torch", "soul_torch", "soul_wall_torch", "redstone_wire", "repeater", "comparator", "tripwire_hook", "tripwire", "flower_pot", "cake", "stone_button", "oak_button", "spruce_button", "birch_button", "jungle_button", "acacia_button", "dark_oak_button", "mangrove_button", "cherry_button", "bamboo_button", "crimson_button", "warped_button", "pale_oak_button", "white_stained_glass_pane", "orange_stained_glass_pane", "magenta_stained_glass_pane", "light_blue_stained_glass_pane", "yellow_stained_glass_pane", "lime_stained_glass_pane", "pink_stained_glass_pane", "gray_stained_glass_pane", "light_gray_stained_glass_pane", "cyan_stained_glass_pane", "purple_stained_glass_pane", "blue_stained_glass_pane", "brown_stained_glass_pane", "green_stained_glass_pane", "red_stained_glass_pane", "black_stained_glass_pane", "structure_void", "light", "nether_portal", "player_head", "zombie_head", "skeleton_skull", "wither_skeleton_skull", "creeper_head", "dragon_head", "piglin_head"] },
  { name: "GRASS", r: 127, g: 178, b: 56, blocks: ["grass_block", "slime_block"] },
  { name: "SAND", r: 247, g: 233, b: 163, blocks: ["sand", "sandstone", "birch_planks", "glowstone", "end_stone", "end_stone_bricks", "bone_block", "scaffolding", "candle", "suspicious_sand", "birch_log", "birch_stripped_log", "birch_wood", "birch_stripped_wood", "birch_sign", "birch_pressure_plate", "birch_trapdoor", "birch_stairs", "birch_slab", "birch_fence_gate", "birch_fence", "birch_door", "turtle_egg", "ochre_froglight", "sandstone_slab", "sandstone_stairs", "sandstone_wall", "cut_sandstone", "cut_sandstone_slab", "chiseled_sandstone", "smooth_sandstone", "smooth_sandstone_slab", "smooth_sandstone_stairs", "end_stone_brick_slab", "end_stone_brick_stairs", "end_stone_brick_wall"] },
  { name: "WOOL", r: 199, g: 199, b: 199, blocks: ["mushroom_stem", "cobweb", "white_candle"] },
  { name: "FIRE", r: 255, g: 0, b: 0, blocks: ["tnt", "redstone_block", "lava", "fire"] },
  { name: "ICE", r: 160, g: 160, b: 255, blocks: ["ice", "packed_ice", "blue_ice", "frosted_ice"] },
  { name: "METAL", r: 167, g: 167, b: 167, blocks: ["iron_block", "iron_door", "iron_trapdoor", "iron_bars", "anvil", "brewing_stand", "heavy_weighted_pressure_plate", "lantern", "grindstone", "lodestone", "heavy_core", "pale_oak_leaves", "closed_eyeblossom"] },
  { name: "PLANT", r: 0, g: 124, b: 0, blocks: ["oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "lily_pad", "cactus", "vines", "sugar_cane", "fern", "short_grass", "tall_grass", "pink_petals"] },
  { name: "SNOW", r: 255, g: 255, b: 255, blocks: ["white_wool", "snow_block", "snow", "white_carpet", "white_concrete", "white_concrete_powder", "white_stained_glass", "white_glazed_terracotta", "powder_snow", "white_shulker_box"] },
  { name: "CLAY", r: 164, g: 168, b: 184, blocks: ["clay"] },
  { name: "DIRT", r: 151, g: 109, b: 77, blocks: ["dirt", "coarse_dirt", "jungle_planks", "granite", "polished_granite", "farmland", "jungle_slab", "jungle_stairs", "packed_mud", "dirt_path", "granite_slab", "granite_stairs", "granite_wall", "polished_granite_slab", "polished_granite_stairs", "jungle_log", "jungle_stripped_log", "jungle_wood", "jungle_stripped_wood", "jungle_sign", "jungle_pressure_plate", "jungle_trapdoor", "jungle_fence_gate", "jungle_fence", "jungle_door", "jukebox", "brown_mushroom_block", "rooted_dirt", "hanging_roots"] },
  { name: "STONE", r: 112, g: 112, b: 112, blocks: ["cobblestone", "stone", "stone_bricks", "andesite", "polished_andesite", "gravel", "furnace", "dispenser", "dropper", "observer", "hopper", "cobblestone_stairs", "stone_slab", "cobblestone_slab", "stone_brick_slab", "stonecutter", "spawner", "ender_chest", "cauldron", "stone_stairs", "bedrock", "gold_ore", "iron_ore", "coal_ore", "lapis_lazuli_ore", "diamond_ore", "mossy_cobblestone", "mossy_cobblestone_slab", "mossy_cobblestone_stairs", "mossy_cobblestone_wall", "stone_pressure_plate", "redstone_ore", "emerald_ore", "smooth_stone", "smooth_stone_slab", "smoker", "blast_furnace", "cobblestone_wall", "stone_brick_stairs", "stone_brick_wall", "mossy_stone_bricks", "mossy_stone_brick_slab", "mossy_stone_brick_stairs", "mossy_stone_brick_wall", "cracked_stone_bricks", "chiseled_stone_bricks", "andesite_slab", "andesite_stairs", "andesite_wall", "polished_andesite_slab", "polished_andesite_stairs", "copper_ore", "crafter", "pale_oak_wood", "pale_oak_log"] },
  { name: "WATER", r: 64, g: 64, b: 255, blocks: ["water", "oak_leaves[waterlogged=true]", "spruce_leaves[waterlogged=true]", "birch_leaves[waterlogged=true]", "jungle_leaves[waterlogged=true]", "acacia_leaves[waterlogged=true]", "dark_oak_leaves[waterlogged=true]", "cherry_leaves[waterlogged=true]", "pale_oak_leaves[waterlogged=true]", "mangrove_leaves[waterlogged=true]", "azalea_leaves[waterlogged=true]", "flowering_azalea_leaves[waterlogged=true]"] },
  { name: "WOOD", r: 143, g: 119, b: 72, blocks: ["oak_planks", "oak_slab", "oak_stairs", "oak_sign", "oak_pressure_plate", "oak_trapdoor", "oak_fence", "oak_fence_gate", "oak_door", "crafting_table", "bookshelf", "note_block", "chest", "trapped_chest", "daylight_detector", "loom", "composter", "lectern", "smithing_table", "fletching_table", "beehive", "oak_wood", "oak_stripped_log", "oak_stripped_wood", "barrel", "cartography_table", "chiseled_bookshelf", "petrified_oak_slab", "bamboo_sapling", "dead_bush"] },
  { name: "QUARTZ", r: 255, g: 252, b: 245, blocks: ["quartz_block", "smooth_quartz", "chiseled_quartz_block", "quartz_pillar", "quartz_slab", "quartz_stairs", "diorite", "polished_diorite", "sea_lantern", "target", "pale_oak_planks", "diorite_slab", "diorite_stairs", "diorite_wall", "polished_diorite_slab", "polished_diorite_stairs", "smooth_quartz_slab", "smooth_quartz_stairs", "pale_oak_slab", "pale_oak_stairs", "pale_oak_fence", "pale_oak_fence_gate", "pale_oak_door", "pale_oak_trapdoor", "pale_oak_sign", "pale_oak_pressure_plate", "pale_oak_stripped_log", "pale_oak_stripped_wood"] },
  { name: "COLOR_ORANGE", r: 216, g: 127, b: 51, blocks: ["acacia_planks", "acacia_slab", "acacia_stairs", "acacia_log", "pumpkin", "jack_o_lantern", "terracotta", "red_sand", "red_sandstone", "orange_wool", "orange_carpet", "orange_concrete", "orange_glazed_terracotta", "honey_block", "honeycomb_block", "lightning_rod", "copper_block", "acacia_sign", "acacia_trapdoor", "acacia_pressure_plate", "acacia_fence_gate", "acacia_fence", "acacia_door", "acacia_stripped_log", "acacia_stripped_wood", "orange_shulker_box", "orange_stained_glass", "orange_concrete_powder", "orange_candle", "carved_pumpkin", "red_sandstone_slab", "red_sandstone_stairs", "red_sandstone_wall", "cut_red_sandstone", "cut_red_sandstone_slab", "chiseled_red_sandstone", "smooth_red_sandstone", "smooth_red_sandstone_slab", "smooth_red_sandstone_stairs", "raw_copper_block", "creaking_heart", "open_eyeblossom", "waxed_copper_block", "cut_copper", "cut_copper_slab", "cut_copper_stairs", "waxed_cut_copper", "waxed_cut_copper_slab", "waxed_cut_copper_stairs"] },
  { name: "COLOR_MAGENTA", r: 178, g: 76, b: 216, blocks: ["magenta_wool", "magenta_carpet", "magenta_concrete", "magenta_glazed_terracotta", "purpur_block", "purpur_pillar", "purpur_slab", "purpur_stairs", "magenta_shulker_box", "magenta_stained_glass", "magenta_concrete_powder", "magenta_candle"] },
  { name: "COLOR_LIGHT_BLUE", r: 102, g: 153, b: 216, blocks: ["light_blue_wool", "light_blue_carpet", "light_blue_concrete", "light_blue_glazed_terracotta", "light_blue_shulker_box", "light_blue_stained_glass", "light_blue_concrete_powder", "light_blue_candle"] },
  { name: "COLOR_YELLOW", r: 229, g: 229, b: 51, blocks: ["yellow_wool", "yellow_carpet", "yellow_concrete", "yellow_glazed_terracotta", "sponge", "wet_sponge", "hay_block", "bee_nest", "bamboo_planks", "yellow_shulker_box", "yellow_stained_glass", "yellow_concrete_powder", "yellow_candle"] },
  { name: "COLOR_LIGHT_GREEN", r: 127, g: 204, b: 25, blocks: ["lime_wool", "lime_carpet", "lime_concrete", "lime_glazed_terracotta", "melon", "lime_shulker_box", "lime_stained_glass", "lime_concrete_powder", "lime_candle"] },
  { name: "COLOR_PINK", r: 242, g: 127, b: 165, blocks: ["pink_wool", "pink_carpet", "pink_concrete", "pink_glazed_terracotta", "brain_coral_block", "pearlescent_froglight", "cherry_leaves", "pink_shulker_box", "pink_stained_glass", "pink_concrete_powder", "pink_candle"] },
  { name: "COLOR_GRAY", r: 76, g: 76, b: 76, blocks: ["gray_wool", "gray_carpet", "gray_concrete", "gray_glazed_terracotta", "tinted_glass", "acacia_wood", "gray_shulker_box", "gray_stained_glass", "gray_concrete_powder", "gray_candle"] },
  { name: "COLOR_LIGHT_GRAY", r: 153, g: 153, b: 153, blocks: ["light_gray_wool", "light_gray_carpet", "light_gray_concrete", "light_gray_glazed_terracotta", "structure_block", "jigsaw_block", "pale_moss_block", "pale_moss_carpet", "light_gray_shulker_box", "light_gray_stained_glass", "light_gray_concrete_powder", "light_gray_candle"] },
  { name: "COLOR_CYAN", r: 76, g: 127, b: 153, blocks: ["cyan_wool", "cyan_carpet", "cyan_concrete", "cyan_glazed_terracotta", "prismarine", "sculk_sensor", "warped_roots", "nether_sprouts", "twisting_vines", "prismarine_slab", "prismarine_stairs", "prismarine_wall", "calibrated_sculk_sensor", "warped_fungus", "cyan_shulker_box", "cyan_stained_glass", "cyan_concrete_powder", "cyan_candle"] },
  { name: "COLOR_PURPLE", r: 127, g: 63, b: 178, blocks: ["purple_wool", "purple_carpet", "purple_concrete", "purple_glazed_terracotta", "mycelium", "chorus_plant", "chorus_flower", "budding_amethyst", "amethyst_block", "shulker_box", "bubble_coral_block", "purple_shulker_box", "purple_stained_glass", "purple_concrete_powder", "purple_candle"] },
  { name: "COLOR_BLUE", r: 51, g: 76, b: 178, blocks: ["blue_wool", "blue_carpet", "blue_concrete", "blue_glazed_terracotta", "tube_coral_block", "blue_shulker_box", "blue_stained_glass", "blue_concrete_powder", "blue_candle"] },
  { name: "COLOR_BROWN", r: 102, g: 76, b: 51, blocks: ["dark_oak_planks", "dark_oak_slab", "dark_oak_stairs", "dark_oak_log", "dark_oak_wood", "spruce_log", "soul_sand", "soul_soil", "brown_wool", "brown_carpet", "brown_concrete", "brown_glazed_terracotta", "brown_mushroom", "command_block", "dark_oak_stripped_log", "dark_oak_stripped_wood", "dark_oak_sign", "dark_oak_pressure_plate", "dark_oak_trapdoor", "dark_oak_fence_gate", "dark_oak_fence", "dark_oak_door", "brown_shulker_box", "brown_stained_glass", "brown_concrete_powder", "brown_candle", "leaf_litter"] },
  { name: "COLOR_GREEN", r: 102, g: 127, b: 51, blocks: ["green_wool", "green_carpet", "green_concrete", "green_glazed_terracotta", "end_portal_frame", "moss_block", "moss_carpet", "dried_kelp_block", "sea_pickle", "green_shulker_box", "green_stained_glass", "green_concrete_powder", "green_candle"] },
  { name: "COLOR_RED", r: 153, g: 51, b: 51, blocks: ["red_wool", "red_carpet", "red_concrete", "red_glazed_terracotta", "bricks", "brick_slab", "brick_stairs", "brick_wall", "nether_wart_block", "nether_wart", "enchanting_table", "red_mushroom_block", "red_mushroom", "shroomlight", "mangrove_planks", "mangrove_log", "sniffer_egg", "mangrove_slab", "mangrove_stairs", "mangrove_fence", "mangrove_fence_gate", "mangrove_door", "mangrove_trapdoor", "mangrove_sign", "mangrove_pressure_plate", "mangrove_stripped_log", "mangrove_wood", "mangrove_stripped_wood", "red_shulker_box", "red_stained_glass", "red_concrete_powder", "red_candle", "fire_coral_block"] },
  { name: "COLOR_BLACK", r: 25, g: 25, b: 25, blocks: ["black_wool", "black_carpet", "black_concrete", "black_glazed_terracotta", "obsidian", "coal_block", "dragon_egg", "blackstone", "polished_blackstone", "polished_blackstone_bricks", "netherite_block", "ancient_debris", "crying_obsidian", "respawn_anchor", "sculk", "sculk_catalyst", "sculk_shrieker", "sculk_vein", "basalt", "polished_basalt", "smooth_basalt", "black_shulker_box", "black_stained_glass", "black_concrete_powder", "black_candle"] },
  { name: "GOLD", r: 250, g: 238, b: 77, blocks: ["gold_block", "light_weighted_pressure_plate", "bell", "raw_gold_block"] },
  { name: "DIAMOND", r: 92, g: 219, b: 213, blocks: ["diamond_block", "prismarine_bricks", "dark_prismarine", "conduit", "beacon"] },
  { name: "LAPIS", r: 74, g: 128, b: 255, blocks: ["lapis_block"] },
  { name: "EMERALD", r: 0, g: 217, b: 58, blocks: ["emerald_block"] },
  { name: "PODZOL", r: 129, g: 86, b: 49, blocks: ["podzol", "spruce_planks", "spruce_slab", "spruce_stairs", "spruce_log", "spruce_wood", "spruce_fence", "spruce_fence_gate", "spruce_door", "spruce_trapdoor", "spruce_pressure_plate", "spruce_sign", "oak_log", "jungle_log", "campfire", "mangrove_roots", "muddy_mangrove_roots", "spruce_stripped_log", "spruce_stripped_wood", "soul_campfire"] },
  { name: "NETHER", r: 112, g: 2, b: 0, blocks: ["netherrack", "nether_bricks", "nether_brick_slab", "nether_brick_stairs", "nether_brick_fence", "nether_brick_wall", "red_nether_bricks", "nether_gold_ore", "nether_quartz_ore", "magma_block", "crimson_roots", "crimson_fungus", "weeping_vines", "chiseled_nether_bricks", "cracked_nether_bricks", "red_nether_brick_slab", "red_nether_brick_stairs", "red_nether_brick_wall"] },
  { name: "TERRACOTTA_WHITE", r: 209, g: 177, b: 161, blocks: ["white_terracotta", "calcite", "cherry_planks", "cherry_slab", "cherry_stairs", "cherry_fence", "cherry_fence_gate", "cherry_door", "cherry_trapdoor", "cherry_log", "cherry_sign", "cherry_pressure_plate"] },
  { name: "TERRACOTTA_ORANGE", r: 159, g: 82, b: 36, blocks: ["orange_terracotta", "redstone_lamp", "resin_block", "resin_bricks", "resin_brick_slab", "resin_brick_stairs", "resin_brick_wall", "resin_clump[down=true]"] },
  { name: "TERRACOTTA_MAGENTA", r: 149, g: 87, b: 108, blocks: ["magenta_terracotta"] },
  { name: "TERRACOTTA_LIGHT_BLUE", r: 112, g: 108, b: 138, blocks: ["light_blue_terracotta"] },
  { name: "TERRACOTTA_YELLOW", r: 186, g: 133, b: 36, blocks: ["yellow_terracotta"] },
  { name: "TERRACOTTA_LIGHT_GREEN", r: 103, g: 117, b: 53, blocks: ["lime_terracotta"] },
  { name: "TERRACOTTA_PINK", r: 160, g: 77, b: 78, blocks: ["pink_terracotta", "cherry_wood"] },
  { name: "TERRACOTTA_GRAY", r: 57, g: 41, b: 35, blocks: ["gray_terracotta", "tuff", "tuff_bricks", "polished_tuff", "tuff_slab", "tuff_brick_slab", "tuff_stairs", "tuff_brick_stairs", "chiseled_tuff", "chiseled_tuff_bricks", "cherry_log", "tuff_wall", "tuff_brick_wall", "polished_tuff_slab", "polished_tuff_stairs", "polished_tuff_wall"] },
  { name: "TERRACOTTA_LIGHT_GRAY", r: 135, g: 107, b: 98, blocks: ["light_gray_terracotta", "exposed_copper", "waxed_exposed_copper", "exposed_cut_copper", "mud_bricks", "mud_brick_slab", "mud_brick_stairs", "mud_brick_wall", "exposed_copper_trapdoor", "exposed_cut_copper_slab", "exposed_cut_copper_stairs", "waxed_exposed_cut_copper", "waxed_exposed_cut_copper_slab", "waxed_exposed_cut_copper_stairs"] },
  { name: "TERRACOTTA_CYAN", r: 87, g: 92, b: 92, blocks: ["cyan_terracotta", "mud"] },
  { name: "TERRACOTTA_PURPLE", r: 122, g: 73, b: 88, blocks: ["purple_terracotta"] },
  { name: "TERRACOTTA_BLUE", r: 76, g: 62, b: 92, blocks: ["blue_terracotta"] },
  { name: "TERRACOTTA_BROWN", r: 76, g: 50, b: 35, blocks: ["brown_terracotta", "dripstone_block", "pointed_dripstone"] },
  { name: "TERRACOTTA_GREEN", r: 76, g: 82, b: 42, blocks: ["green_terracotta"] },
  { name: "TERRACOTTA_RED", r: 142, g: 60, b: 46, blocks: ["red_terracotta", "decorated_pot"] },
  { name: "TERRACOTTA_BLACK", r: 37, g: 22, b: 16, blocks: ["black_terracotta"] },
  { name: "CRIMSON_NYLIUM", r: 189, g: 48, b: 49, blocks: ["crimson_nylium"] },
  { name: "CRIMSON_STEM", r: 148, g: 63, b: 97, blocks: ["crimson_planks", "crimson_stem", "stripped_crimson_stem", "crimson_slab", "crimson_stairs", "crimson_fence", "crimson_fence_gate", "crimson_door", "crimson_trapdoor", "crimson_sign", "crimson_pressure_plate"] },
  { name: "CRIMSON_HYPHAE", r: 92, g: 25, b: 29, blocks: ["crimson_hyphae", "stripped_crimson_hyphae"] },
  { name: "WARPED_NYLIUM", r: 22, g: 126, b: 134, blocks: ["warped_nylium", "oxidized_copper", "waxed_oxidized_copper", "oxidized_cut_copper", "oxidized_copper_trapdoor", "oxidized_cut_copper_slab", "oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_slab", "waxed_oxidized_cut_copper_stairs"] },
  { name: "WARPED_STEM", r: 58, g: 142, b: 140, blocks: ["warped_planks", "warped_stem", "stripped_warped_stem", "warped_slab", "warped_stairs", "warped_fence", "warped_fence_gate", "warped_door", "warped_trapdoor", "warped_sign", "warped_pressure_plate", "weathered_copper", "waxed_weathered_copper", "weathered_cut_copper", "weathered_cut_copper_slab", "weathered_cut_copper_stairs", "waxed_weathered_cut_copper", "waxed_weathered_cut_copper_slab", "waxed_weathered_cut_copper_stairs"] },
  { name: "WARPED_HYPHAE", r: 86, g: 44, b: 62, blocks: ["warped_hyphae", "stripped_warped_hyphae"] },
  { name: "WARPED_WART_BLOCK", r: 20, g: 180, b: 133, blocks: ["warped_wart_block"] },
  { name: "DEEPSLATE", r: 100, g: 100, b: 100, blocks: ["deepslate", "cobbled_deepslate", "deepslate_bricks", "deepslate_tiles", "polished_deepslate", "chiseled_deepslate", "cobbled_deepslate_slab", "deepslate_brick_slab", "deepslate_tile_slab", "reinforced_deepslate", "cobbled_deepslate_stairs", "cobbled_deepslate_wall", "deepslate_brick_stairs", "deepslate_brick_wall", "deepslate_tile_stairs", "deepslate_tile_wall", "cracked_deepslate_bricks", "cracked_deepslate_tiles", "deepslate_gold_ore", "deepslate_iron_ore", "deepslate_coal_ore", "deepslate_lapis_ore", "deepslate_diamond_ore", "deepslate_redstone_ore", "deepslate_emerald_ore", "deepslate_copper_ore"] },
  { name: "RAW_IRON", r: 216, g: 175, b: 147, blocks: ["raw_iron_block"] },
  { name: "GLOW_LICHEN", r: 127, g: 167, b: 150, blocks: ["glow_lichen[down=true]", "verdant_froglight"] },
];

// Build lookup: "r,g,b" → { baseIndex, shade }
export interface ColorMatch {
  baseIndex: number;
  shade: number; // 0=dark, 1=flat, 2=light, 3=darkest
}

let _colorLookup: Map<string, ColorMatch> | null = null;

export function getColorLookup(): Map<string, ColorMatch> {
  if (_colorLookup) return _colorLookup;
  _colorLookup = new Map();
  for (let i = 1; i < BASE_COLORS.length; ++i) {
    const { r, g, b } = BASE_COLORS[i];
    for (let s = 0; s < 4; ++s) {
      const mr = Math.floor(r * SHADE_MULTIPLIERS[s] / 255);
      const mg = Math.floor(g * SHADE_MULTIPLIERS[s] / 255);
      const mb = Math.floor(b * SHADE_MULTIPLIERS[s] / 255);
      _colorLookup.set(`${mr},${mg},${mb}`, { baseIndex: i, shade: s });
    }
  }
  return _colorLookup;
}

// Get the shaded RGB for display
export function getShadedRgb(baseIndex: number, shade: number): [number, number, number] {
  const { r, g, b } = BASE_COLORS[baseIndex];
  const m = SHADE_MULTIPLIERS[shade];
  return [Math.floor((r * m) / 255), Math.floor((g * m) / 255), Math.floor((b * m) / 255)];
}
