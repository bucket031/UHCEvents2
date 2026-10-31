package dev.events.uhc;

import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** "Random kits" scenario: random armor, weapons, tools (all randomly enchanted) and a random number of gaps. */
final class KitGenerator {
    private static final String[] ARMOR = {"LEATHER", "GOLDEN", "CHAINMAIL", "IRON", "DIAMOND", "NETHERITE"};
    private static final int[] ARMOR_W = {6, 4, 6, 8, 4, 1};
    private static final String[] TOOL = {"WOODEN", "GOLDEN", "STONE", "IRON", "DIAMOND"};
    private static final int[] WEAPON_W = {1, 2, 4, 5, 3};
    private static final int[] PICK_W = {0, 1, 4, 5, 3};
    private static final int[] AUX_W = {1, 1, 3, 4, 2};

    private KitGenerator() {}

    private static int pick(int[] weights) {
        int total = 0;
        for (int w : weights) total += w;
        int roll = ThreadLocalRandom.current().nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) return i;
        }
        return weights.length - 1;
    }

    private static ItemStack item(String tier, String piece) {
        return new ItemStack(Material.valueOf(tier + "_" + piece));
    }

    static void give(Player p, FileConfiguration c) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int maxEnch = Math.max(0, c.getInt("modes.kit-max-enchants", 3));
        PlayerInventory inv = p.getInventory();

        // armor: each slot has a small chance to be empty
        for (String slot : new String[]{"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"}) {
            if (r.nextInt(100) >= 85) continue;
            ItemStack a = item(ARMOR[pick(ARMOR_W)], slot);
            enchant(a, maxEnch);
            switch (slot) {
                case "HELMET" -> inv.setHelmet(a);
                case "CHESTPLATE" -> inv.setChestplate(a);
                case "LEGGINGS" -> inv.setLeggings(a);
                default -> inv.setBoots(a);
            }
        }

        // weapons
        ItemStack weapon = item(TOOL[pick(WEAPON_W)], r.nextInt(100) < 65 ? "SWORD" : "AXE");
        enchant(weapon, maxEnch);
        inv.addItem(weapon);
        if (r.nextInt(100) < 50) {
            ItemStack bow = new ItemStack(Material.BOW);
            enchant(bow, maxEnch);
            inv.addItem(bow);
            inv.addItem(new ItemStack(Material.ARROW, 16 + r.nextInt(33)));
        }
        if (r.nextInt(100) < 30) {
            ItemStack shield = new ItemStack(Material.SHIELD);
            enchant(shield, maxEnch);
            inv.setItemInOffHand(shield);
        }

        // tools
        ItemStack pickaxe = item(TOOL[pick(PICK_W)], "PICKAXE");
        enchant(pickaxe, maxEnch);
        inv.addItem(pickaxe);
        if (r.nextInt(100) < 70) {
            ItemStack axe = item(TOOL[pick(AUX_W)], "AXE");
            enchant(axe, maxEnch);
            inv.addItem(axe);
        }
        if (r.nextInt(100) < 50) {
            ItemStack shovel = item(TOOL[pick(AUX_W)], "SHOVEL");
            enchant(shovel, maxEnch);
            inv.addItem(shovel);
        }

        // gaps (golden apples)
        int lo = Math.max(0, c.getInt("modes.kit-gaps-min", 1));
        int hi = Math.max(lo, c.getInt("modes.kit-gaps-max", 6));
        int gaps = lo + r.nextInt(hi - lo + 1);
        if (gaps > 0) inv.addItem(new ItemStack(Material.GOLDEN_APPLE, gaps));
    }

    /** Adds 0..max random, non-conflicting, non-cursed enchantments with random levels. */
    private static void enchant(ItemStack it, int max) {
        if (max <= 0) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Enchantment> pool = Registry.ENCHANTMENT.stream()
                .filter(e -> !e.isCursed() && e.canEnchantItem(it))
                .collect(Collectors.toList());
        Collections.shuffle(pool);
        int count = r.nextInt(max + 1);
        List<Enchantment> chosen = new ArrayList<>();
        for (Enchantment e : pool) {
            if (chosen.size() >= count) break;
            if (chosen.stream().anyMatch(o -> o.conflictsWith(e))) continue;
            chosen.add(e);
        }
        if (chosen.isEmpty()) return;
        ItemMeta m = it.getItemMeta();
        for (Enchantment e : chosen) m.addEnchant(e, 1 + r.nextInt(e.getMaxLevel()), true);
        it.setItemMeta(m);
    }
}
