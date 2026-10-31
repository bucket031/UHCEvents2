package dev.events.uhc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/** Special items. None of them have a crafting recipe - they are only handed out. */
final class Gear {
    private Gear() {}

    static ItemStack godSword(UHCPlugin plugin) {
        ItemStack it = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("God Sword", NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Forged for a single champion.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        m.addEnchant(Enchantment.SHARPNESS, 10, true);
        m.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        m.addEnchant(Enchantment.LOOTING, 5, true);
        m.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
        m.addEnchant(Enchantment.UNBREAKING, 10, true);
        m.setUnbreakable(true);
        m.getPersistentDataContainer().set(new NamespacedKey(plugin, "god_sword"), PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    static ItemStack mace() {
        return new ItemStack(Material.MACE);
    }
}
