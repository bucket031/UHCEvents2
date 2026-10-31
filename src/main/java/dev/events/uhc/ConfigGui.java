package dev.events.uhc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Clickable chest-GUI version of /uhcconfig. Main menu -> category menus. */
public final class ConfigGui implements Listener {
    private enum Type { BOOL, INT, DOUBLE, EFFECT, INFO }

    private record Setting(String path, Type type, Material icon, String name, String desc,
                           double step, double min, double max) {}

    private static final class Menu implements InventoryHolder {
        final String category; // null = main menu
        final Map<Integer, Setting> slots = new HashMap<>();
        final Map<Integer, String> cats = new HashMap<>();
        Inventory inv;

        Menu(String category) {
            this.category = category;
        }

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    private static final Map<String, Material> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("Scenarios", Material.BOOK);
        CATEGORIES.put("Teams", Material.WHITE_BANNER);
        CATEGORIES.put("Game", Material.GOLDEN_APPLE);
        CATEGORIES.put("Potions", Material.POTION);
        CATEGORIES.put("Timers & Border", Material.CLOCK);
        CATEGORIES.put("Recipes", Material.CRAFTING_TABLE);
        CATEGORIES.put("World Generation", Material.OAK_SAPLING);
        CATEGORIES.put("Mobs", Material.ZOMBIE_HEAD);
        CATEGORIES.put("Drops & Extras", Material.IRON_PICKAXE);
    }

    private final UHCPlugin plugin;

    ConfigGui(UHCPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ opening

    public void open(Player p) {
        Menu m = new Menu(null);
        m.inv = Bukkit.createInventory(m, 36, Component.text("UHC Config"));
        int i = 0;
        for (Map.Entry<String, Material> e : CATEGORIES.entrySet()) {
            int slot = i < 7 ? 10 + i : 19 + (i - 7);
            m.cats.put(slot, e.getKey());
            m.inv.setItem(slot, make(e.getValue(), t(e.getKey(), NamedTextColor.GOLD),
                    List.of(t("Click to open", NamedTextColor.GRAY))));
            i++;
        }
        fill(m.inv);
        p.openInventory(m.inv);
    }

    private void openCategory(Player p, String cat) {
        List<Setting> list = settings(cat);
        int size = Math.min(54, ((list.size() + 1 + 8) / 9) * 9); // +1 for the back button
        Menu m = new Menu(cat);
        m.inv = Bukkit.createInventory(m, size, Component.text("UHC Config - " + cat));
        for (int i = 0; i < list.size() && i < size - 1; i++) m.slots.put(i, list.get(i));
        render(m);
        p.openInventory(m.inv);
    }

    private void render(Menu m) {
        m.inv.clear();
        for (Map.Entry<Integer, Setting> e : m.slots.entrySet()) m.inv.setItem(e.getKey(), item(e.getValue()));
        m.inv.setItem(m.inv.getSize() - 1, make(Material.ARROW, t("Back", NamedTextColor.WHITE), List.of()));
        fill(m.inv);
    }

    private void fill(Inventory inv) {
        ItemStack pane = make(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, pane);
    }

    // ------------------------------------------------------------------ settings per category

    private List<Setting> settings(String cat) {
        FileConfiguration c = plugin.getConfig();
        List<Setting> l = new ArrayList<>();
        switch (cat) {
            case "Scenarios" -> {
                l.add(bool("modes.no-saturation", Material.COOKED_BEEF, "No Saturation", "Food gives no saturation boost."));
                l.add(bool("modes.one-mace", Material.MACE, "One Mace",
                        "Only one mace can be crafted per game. Boosts heavy cores in ominous vaults."));
                l.add(num("modes.one-mace-heavy-core-chance", Type.DOUBLE, Material.HEAVY_CORE, "Heavy Core Chance",
                        "Chance an ominous vault gives a heavy core (One Mace).", 0.05, 0, 1));
                l.add(bool("modes.random-effects", Material.SPLASH_POTION, "Random Effects",
                        "Every player gets their own random infinite good effects."));
                l.add(num("modes.random-effects-count", Type.INT, Material.GLASS_BOTTLE, "Random Effects Count", "Effects per player.", 1, 1, 5));
                l.add(bool("modes.god-sword", Material.NETHERITE_SWORD, "God Sword", "One random player starts with the God Sword. No recipe."));
                l.add(bool("modes.random-kits", Material.CHEST, "Random Kits", "Random armor, weapons, tools, enchants and gaps for everyone."));
                l.add(num("modes.kit-gaps-min", Type.INT, Material.GOLDEN_APPLE, "Kit Gaps (min)", "", 1, 0, 64));
                l.add(num("modes.kit-gaps-max", Type.INT, Material.ENCHANTED_GOLDEN_APPLE, "Kit Gaps (max)", "", 1, 0, 64));
                l.add(bool("modes.random-mace", Material.MACE, "Random Mace", "One random player starts with a mace."));
                l.add(bool("modes.anonymous", Material.BLACK_DYE, "Anonymous Mode", "Hides names (tags, tab, chat, deaths) until someone wins."));
            }
            case "Teams" -> {
                l.add(num("teams.size", Type.INT, Material.WHITE_BANNER, "Team Size",
                        "1 = solo, 2 = duos, 3 = trios. Random teams; teammates spawn together.", 1, 1, 5));
                l.add(bool("teams.friendly-fire", Material.IRON_SWORD, "Friendly Fire", "Teammates can hurt each other."));
                l.add(bool("party.fill-teams", Material.PLAYER_HEAD, "Fill Small Parties",
                        "Parties smaller than the team size get random teammates."));
            }
            case "Game" -> {
                l.add(num("game.min-players", Type.INT, Material.PLAYER_HEAD, "Minimum Players", "", 1, 1, 100));
                l.add(num("game.countdown-seconds", Type.INT, Material.CLOCK, "Countdown (seconds)", "", 5, 0, 600));
                l.add(num("game.win-screen-seconds", Type.INT, Material.FIREWORK_ROCKET, "Win Screen (seconds)", "", 1, 1, 60));
                l.add(num("game.disconnect-timeout-seconds", Type.INT, Material.ENDER_PEARL, "Disconnect Timeout (seconds)",
                        "Offline this long = eliminated.", 30, 0, 3600));
                l.add(bool("game.locator-bar", Material.COMPASS, "Locator Bar", ""));
                l.add(bool("game.disable-portals", Material.OBSIDIAN, "Disable Portals", ""));
                l.add(bool("game.delete-world-after-game", Material.TNT, "Delete World After Game", ""));
            }
            case "Potions" -> {
                l.add(new Setting("modes.effect", Type.EFFECT, Material.POTION, "Special Effect",
                        "Infinite good effect for everyone: NONE, RANDOM or a specific one.", 0, 0, 0));
                l.add(num("modes.effect-amplifier", Type.INT, Material.GLOWSTONE_DUST, "Effect Level", "0 = level I", 1, 0, 4));
            }
            case "Timers & Border" -> {
                l.add(num("game.grace-minutes", Type.INT, Material.SHIELD, "Grace Period (minutes)", "No PvP.", 1, 0, 120));
                l.add(num("border.start-size", Type.INT, Material.FILLED_MAP, "Start Border Size", "", 100, 50, 20000));
                l.add(num("border.final-size", Type.INT, Material.BARRIER, "Final Border Size", "", 10, 10, 1000));
                l.add(num("border.shrink-minutes", Type.INT, Material.REPEATER, "Shrink Duration (minutes)", "", 1, 1, 240));
                l.add(num("border.shrink-delay-minutes", Type.INT, Material.COMPARATOR, "Shrink Delay (minutes)",
                        "After PvP turns on.", 1, 0, 120));
                l.add(num("teleport.edge-margin", Type.INT, Material.STRING, "Spawn Edge Margin",
                        "Players never spawn closer than this to the border.", 10, 0, 1000));
                l.add(num("teleport.min-distance", Type.INT, Material.SPYGLASS, "Spawn Spacing",
                        "Preferred minimum distance between players.", 25, 0, 2000));
            }
            case "Recipes" -> {
                ConfigurationSection custom = c.getConfigurationSection("recipes.custom");
                if (custom != null) {
                    for (String id : custom.getKeys(false)) {
                        Material result = Material.matchMaterial(custom.getString(id + ".result", ""));
                        String desc = custom.getInt(id + ".amount", 1) + "x " + custom.getString(id + ".result", "?")
                                + " from " + String.join(", ", custom.getStringList(id + ".ingredients"));
                        l.add(bool("recipes.custom." + id + ".enabled", result == null ? Material.BARRIER : result,
                                pretty(id), desc));
                    }
                }
                l.add(info(Material.PAPER, "Add or edit recipes", "Edit recipes.custom in config.yml, then /uhcconfig reload."));
            }
            case "World Generation" -> {
                l.add(bool("worldgen.structures-enabled", Material.GRASS_BLOCK, "Generate Structures", "Master switch. Applies instantly."));
                for (String name : WorldgenPack.names()) {
                    l.add(bool("worldgen.structures." + name, structureIcon(name), pretty(name), "Needs a server restart."));
                }
            }
            case "Mobs" -> {
                l.add(num("mobs.monster-cap-multiplier", Type.DOUBLE, Material.ZOMBIE_HEAD, "Monster Cap Multiplier",
                        "Scales the monster spawn cap: 1 = normal, 0.5 = half, 0 = none. Applies to the next game.", 0.1, 0, 3));
                ConfigurationSection sc = c.getConfigurationSection("mobs.spawn-chance");
                if (sc != null) {
                    for (String k : sc.getKeys(false)) {
                        Material egg = Material.matchMaterial(k + "_SPAWN_EGG");
                        l.add(num("mobs.spawn-chance." + k, Type.DOUBLE, egg == null ? Material.BONE : egg,
                                pretty(k) + " Spawn Chance", "Share of natural spawns allowed: 1 = all, 0 = none.", 0.1, 0, 1));
                    }
                }
                l.add(info(Material.PAPER, "More mobs", "/uhcconfig set mobs.spawn-chance.ENDERMAN 0.5"));
            }
            case "Drops & Extras" -> {
                l.add(num("drops.apple-chance", Type.DOUBLE, Material.APPLE, "Apple Chance (leaves)",
                        "Per oak/dark oak leaf. 0.005 = vanilla.", 0.005, 0, 1));
                l.add(info(Material.BONE, "Mob drops",
                        "Use /uhcconfig set drops.mobs.ZOMBIE.ROTTEN_FLESH 0 (0 = none, 2 = double)."));
                l.add(bool("extras.veinminer.enabled", Material.IRON_PICKAXE, "Veinminer", "Pickaxes mine veins, axes fell trees."));
                l.add(num("extras.veinminer.max-blocks", Type.INT, Material.IRON_ORE, "Veinminer Max Blocks", "", 8, 1, 512));
                l.add(bool("extras.veinminer.require-sneak", Material.LEATHER_BOOTS, "Veinminer: Sneak Required", ""));
                l.add(bool("extras.autosmelt", Material.FURNACE, "Auto Smelt", "Ores drop their smelted result."));
            }
            default -> { }
        }
        return l;
    }

    private static Setting bool(String path, Material icon, String name, String desc) {
        return new Setting(path, Type.BOOL, icon, name, desc, 0, 0, 0);
    }

    private static Setting num(String path, Type type, Material icon, String name, String desc, double step, double min, double max) {
        return new Setting(path, type, icon, name, desc, step, min, max);
    }

    private static Setting info(Material icon, String name, String desc) {
        return new Setting("", Type.INFO, icon, name, desc, 0, 0, 0);
    }

    private static Material structureIcon(String n) {
        return switch (n) {
            case "stronghold" -> Material.ENDER_EYE;
            case "trial_chambers" -> Material.TRIAL_KEY;
            case "woodland_mansion" -> Material.DARK_OAK_LOG;
            case "ancient_city" -> Material.SCULK_SHRIEKER;
            case "village" -> Material.BELL;
            case "pillager_outpost" -> Material.CROSSBOW;
            case "ocean_monument" -> Material.PRISMARINE;
            case "mineshaft" -> Material.RAIL;
            case "desert_pyramid" -> Material.SANDSTONE;
            case "jungle_temple" -> Material.MOSSY_COBBLESTONE;
            case "igloo" -> Material.SNOW_BLOCK;
            case "swamp_hut" -> Material.LILY_PAD;
            case "shipwreck" -> Material.OAK_BOAT;
            case "buried_treasure" -> Material.CHEST;
            case "ruined_portal" -> Material.CRYING_OBSIDIAN;
            case "ocean_ruin" -> Material.SEA_LANTERN;
            case "trail_ruins" -> Material.BRUSH;
            default -> Material.GRASS_BLOCK;
        };
    }

    // ------------------------------------------------------------------ item rendering

    private ItemStack item(Setting s) {
        FileConfiguration c = plugin.getConfig();
        ItemStack it = new ItemStack(s.icon());
        ItemMeta m = it.getItemMeta();
        List<Component> lore = new ArrayList<>();

        switch (s.type()) {
            case BOOL -> {
                boolean on = c.getBoolean(s.path());
                m.displayName(t(s.name(), on ? NamedTextColor.GREEN : NamedTextColor.RED));
                m.setEnchantmentGlintOverride(on);
                lore.add(t(on ? "Enabled" : "Disabled", on ? NamedTextColor.GREEN : NamedTextColor.RED));
                lore.add(t("Click to toggle", NamedTextColor.GRAY));
            }
            case INT, DOUBLE -> {
                m.displayName(t(s.name(), NamedTextColor.GOLD));
                double v = s.type() == Type.INT ? c.getInt(s.path()) : c.getDouble(s.path());
                lore.add(t("Value: " + fmt(v), NamedTextColor.YELLOW));
                lore.add(t("Left click: +" + fmt(s.step()) + "   Right click: -" + fmt(s.step()), NamedTextColor.GRAY));
                lore.add(t("Hold shift: x10", NamedTextColor.GRAY));
            }
            case EFFECT -> {
                m.displayName(t(s.name(), NamedTextColor.GOLD));
                lore.add(t("Value: " + c.getString(s.path(), "NONE"), NamedTextColor.YELLOW));
                lore.add(t("Left / right click: cycle", NamedTextColor.GRAY));
            }
            case INFO -> m.displayName(t(s.name(), NamedTextColor.AQUA));
        }
        if (!s.desc().isEmpty()) lore.add(t(s.desc(), NamedTextColor.DARK_GRAY));
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack make(Material mat, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(name);
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(m);
        return it;
    }

    private static Component t(String s, TextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static String pretty(String s) {
        StringBuilder b = new StringBuilder();
        for (String w : s.split("[_\\- ]")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ clicking

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        if (!p.hasPermission("uhc.admin")) {
            p.closeInventory();
            return;
        }
        int slot = e.getSlot();

        if (m.category == null) {
            String cat = m.cats.get(slot);
            if (cat != null) Bukkit.getScheduler().runTask(plugin, () -> openCategory(p, cat));
        } else if (slot == m.inv.getSize() - 1) {
            Bukkit.getScheduler().runTask(plugin, () -> open(p));
        } else {
            Setting s = m.slots.get(slot);
            if (s != null && s.type() != Type.INFO) change(p, m, s, e.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    private void change(Player p, Menu m, Setting s, ClickType click) {
        FileConfiguration c = plugin.getConfig();
        boolean right = click.isRightClick();
        double delta = s.step() * (click.isShiftClick() ? 10 : 1) * (right ? -1 : 1);

        switch (s.type()) {
            case BOOL -> c.set(s.path(), !c.getBoolean(s.path()));
            case INT -> c.set(s.path(), (int) Math.max(s.min(), Math.min(s.max(), c.getInt(s.path()) + delta)));
            case DOUBLE -> {
                double v = Math.max(s.min(), Math.min(s.max(), c.getDouble(s.path()) + delta));
                c.set(s.path(), Math.round(v * 100000.0) / 100000.0);
            }
            case EFFECT -> {
                List<String> opts = new ArrayList<>(List.of("NONE", "RANDOM"));
                for (String x : c.getStringList("modes.effect-pool")) opts.add(x.toLowerCase(Locale.ROOT));
                int i = 0;
                String cur = c.getString(s.path(), "NONE");
                for (int k = 0; k < opts.size(); k++) if (opts.get(k).equalsIgnoreCase(cur)) i = k;
                i = (i + (right ? -1 : 1) + opts.size()) % opts.size();
                c.set(s.path(), opts.get(i));
            }
            default -> { return; }
        }
        plugin.saveConfig();

        if (s.path().startsWith("recipes.")) {
            plugin.recipes().reload();
        } else if (s.path().startsWith("worldgen.structures.") && WorldgenPack.sync(plugin)) {
            p.sendMessage(Util.msg("Restart the server for per-structure changes to apply."));
        }
        render(m);
    }
}
