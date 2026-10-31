package dev.events.uhc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** /party: pick your own teammates in the lobby. Parties are used when the next game's teams are formed. */
public final class PartyManager implements TabExecutor {
    private static final List<String> SUBS = List.of("invite", "accept", "deny", "leave", "list", "kick", "disband", "chat");

    private static final class Party {
        UUID leader;
        final Set<UUID> members = new LinkedHashSet<>();
    }

    private final UHCPlugin plugin;
    private final Map<UUID, Party> partyOf = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> invites = new HashMap<>(); // invitee -> inviter -> expiry (ms)

    PartyManager(UHCPlugin plugin) {
        this.plugin = plugin;
    }

    private int maxSize() {
        return Math.max(1, plugin.getConfig().getInt("teams.size", 1));
    }

    private String gameLock() {
        return plugin.game().state() != GameManager.State.LOBBY ? "Parties can't change while a game is running." : null;
    }

    private String sizeLock() {
        String lock = gameLock();
        if (lock != null) return lock;
        return maxSize() < 2 ? "Parties are only used in duos/trios (teams.size is 1)." : null;
    }

    private void tell(Party party, String text) {
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) m.sendMessage(Util.msg(text));
        }
    }

    // ------------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return true;
        }
        String sub = a.length == 0 ? "list" : a[0].toLowerCase(Locale.ROOT);
        String arg = a.length > 1 ? a[1] : null;
        String err = null;
        switch (sub) {
            case "invite", "add" -> err = invite(p, arg);
            case "accept", "join" -> err = accept(p, arg);
            case "deny", "decline" -> err = deny(p, arg);
            case "leave" -> err = leave(p);
            case "kick" -> err = kick(p, arg);
            case "disband" -> err = disband(p);
            case "chat" -> err = chat(p, a.length > 1 ? String.join(" ", Arrays.copyOfRange(a, 1, a.length)) : null);
            case "list" -> list(p);
            default -> p.sendMessage(Util.msg("/party invite <player> | accept [player] | deny [player] | leave | list | kick <player> | disband | chat <msg>"));
        }
        if (err != null) p.sendMessage(Util.err(err));
        return true;
    }

    private String invite(Player p, String name) {
        String lock = sizeLock();
        if (lock != null) return lock;
        if (name == null) return "Usage: /party invite <player>";
        Player t = Bukkit.getPlayerExact(name);
        if (t == null) return name + " isn't online.";
        if (t.equals(p)) return "You can't invite yourself.";
        Party mine = partyOf.get(p.getUniqueId());
        if (mine != null && !mine.leader.equals(p.getUniqueId())) return "Only the party leader can invite.";
        if (mine != null && mine.members.size() >= maxSize()) return "Your party is full (" + maxSize() + " max).";
        if (partyOf.containsKey(t.getUniqueId())) return t.getName() + " is already in a party.";

        long ttl = plugin.getConfig().getInt("party.invite-seconds", 60) * 1000L;
        invites.computeIfAbsent(t.getUniqueId(), k -> new HashMap<>()).put(p.getUniqueId(), System.currentTimeMillis() + ttl);
        p.sendMessage(Util.msg("Invited " + t.getName() + " to your party."));
        t.sendMessage(Util.msg(p.getName() + " invited you to a party. ")
                .append(Component.text("[ACCEPT]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/party accept " + p.getName()))));
        return null;
    }

    private Map<UUID, Long> validInvites(UUID id) {
        Map<UUID, Long> inv = invites.get(id);
        if (inv == null) return new HashMap<>();
        long now = System.currentTimeMillis();
        inv.values().removeIf(exp -> exp < now);
        return inv;
    }

    private UUID pickInviter(Player p, String name, Map<UUID, Long> inv) {
        if (name != null) {
            Player ip = Bukkit.getPlayerExact(name);
            return ip != null && inv.containsKey(ip.getUniqueId()) ? ip.getUniqueId() : null;
        }
        return inv.size() == 1 ? inv.keySet().iterator().next() : null;
    }

    private String accept(Player p, String name) {
        String lock = sizeLock();
        if (lock != null) return lock;
        if (partyOf.containsKey(p.getUniqueId())) return "You're already in a party. Use /party leave first.";
        Map<UUID, Long> inv = validInvites(p.getUniqueId());
        UUID from = pickInviter(p, name, inv);
        if (from == null) return inv.isEmpty() ? "You have no pending invites." : "Specify who: /party accept <player>";
        Player inviter = Bukkit.getPlayer(from);
        if (inviter == null) return "That player went offline.";

        Party party = partyOf.get(from);
        if (party != null && !party.leader.equals(from)) return "That invite is no longer valid.";
        if (party == null) {
            party = new Party();
            party.leader = from;
            party.members.add(from);
            partyOf.put(from, party);
        }
        if (party.members.size() >= maxSize()) return "That party is full.";
        party.members.add(p.getUniqueId());
        partyOf.put(p.getUniqueId(), party);
        invites.remove(p.getUniqueId());
        tell(party, p.getName() + " joined the party.");
        return null;
    }

    private String deny(Player p, String name) {
        Map<UUID, Long> inv = validInvites(p.getUniqueId());
        UUID from = pickInviter(p, name, inv);
        if (from == null) return inv.isEmpty() ? "You have no pending invites." : "Specify who: /party deny <player>";
        inv.remove(from);
        p.sendMessage(Util.msg("Invite declined."));
        Player inviter = Bukkit.getPlayer(from);
        if (inviter != null) inviter.sendMessage(Util.msg(p.getName() + " declined your party invite."));
        return null;
    }

    private String leave(Player p) {
        String lock = gameLock();
        if (lock != null) return lock;
        Party party = partyOf.get(p.getUniqueId());
        if (party == null) return "You're not in a party.";
        removeMember(party, p.getUniqueId(), p.getName() + " left the party.");
        p.sendMessage(Util.msg("You left the party."));
        return null;
    }

    private String kick(Player p, String name) {
        String lock = gameLock();
        if (lock != null) return lock;
        Party party = partyOf.get(p.getUniqueId());
        if (party == null) return "You're not in a party.";
        if (!party.leader.equals(p.getUniqueId())) return "Only the party leader can kick.";
        if (name == null) return "Usage: /party kick <player>";
        for (UUID id : new ArrayList<>(party.members)) {
            String n = Bukkit.getOfflinePlayer(id).getName();
            if (n != null && n.equalsIgnoreCase(name) && !id.equals(p.getUniqueId())) {
                Player kicked = Bukkit.getPlayer(id);
                if (kicked != null) kicked.sendMessage(Util.msg("You were removed from the party."));
                removeMember(party, id, n + " was removed from the party.");
                return null;
            }
        }
        return name + " isn't in your party.";
    }

    private String disband(Player p) {
        String lock = gameLock();
        if (lock != null) return lock;
        Party party = partyOf.get(p.getUniqueId());
        if (party == null) return "You're not in a party.";
        if (!party.leader.equals(p.getUniqueId())) return "Only the party leader can disband.";
        tell(party, "The party was disbanded.");
        for (UUID id : party.members) partyOf.remove(id);
        party.members.clear();
        return null;
    }

    private String chat(Player p, String text) {
        Party party = partyOf.get(p.getUniqueId());
        if (party == null) return "You're not in a party.";
        if (text == null || text.isBlank()) return "Usage: /party chat <message>";
        Component line = Component.text("[Party] ", NamedTextColor.AQUA)
                .append(Component.text(p.getName() + ": " + text, NamedTextColor.WHITE));
        for (UUID id : party.members) {
            Player m = Bukkit.getPlayer(id);
            if (m != null) m.sendMessage(line);
        }
        return null;
    }

    private void list(Player p) {
        Party party = partyOf.get(p.getUniqueId());
        if (party == null) {
            p.sendMessage(Util.msg("You're not in a party. Use /party invite <player>."));
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (UUID id : party.members) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(Bukkit.getOfflinePlayer(id).getName());
            if (id.equals(party.leader)) sb.append(" (leader)");
        }
        p.sendMessage(Util.msg("Party (" + party.members.size() + "/" + maxSize() + "): " + sb));
    }

    private void removeMember(Party party, UUID id, String message) {
        party.members.remove(id);
        partyOf.remove(id);
        if (party.members.size() <= 1) {
            for (UUID m : party.members) {
                partyOf.remove(m);
                Player mp = Bukkit.getPlayer(m);
                if (mp != null) mp.sendMessage(Util.msg("The party was disbanded."));
            }
            party.members.clear();
            return;
        }
        if (party.leader.equals(id)) party.leader = party.members.iterator().next();
        tell(party, message);
    }

    /** Players leaving the server drop out of their party (only while no game is running). */
    void onQuit(Player p) {
        invites.remove(p.getUniqueId());
        for (Map<UUID, Long> m : invites.values()) m.remove(p.getUniqueId());
        if (plugin.game().state() != GameManager.State.LOBBY) return;
        Party party = partyOf.get(p.getUniqueId());
        if (party != null) removeMember(party, p.getUniqueId(), p.getName() + " left the server and the party.");
    }

    // ------------------------------------------------------------------ team forming

    /**
     * Builds the teams for a game: parties stay together (split if bigger than teamSize), smaller parties are
     * optionally topped up with random solo players, everybody else is grouped randomly.
     */
    List<List<Player>> form(List<Player> players, int teamSize, boolean fill) {
        Map<UUID, Player> byId = new HashMap<>();
        for (Player p : players) byId.put(p.getUniqueId(), p);

        List<List<Player>> teams = new ArrayList<>();
        List<Player> singles = new ArrayList<>();
        Set<Party> seen = new HashSet<>();
        for (Player p : players) {
            Party party = partyOf.get(p.getUniqueId());
            if (party == null) {
                singles.add(p);
                continue;
            }
            if (!seen.add(party)) continue;
            List<Player> present = new ArrayList<>();
            for (UUID id : party.members) {
                Player m = byId.get(id);
                if (m != null) present.add(m);
            }
            while (present.size() > teamSize) singles.add(present.remove(present.size() - 1));
            teams.add(present);
        }

        Collections.shuffle(singles);
        if (fill) {
            for (List<Player> t : teams) {
                while (t.size() < teamSize && !singles.isEmpty()) t.add(singles.remove(singles.size() - 1));
            }
        }
        for (int i = 0; i < singles.size(); i += teamSize) {
            teams.add(new ArrayList<>(singles.subList(i, Math.min(i + teamSize, singles.size()))));
        }
        return teams;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 1) return SUBS.stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        if (a.length == 2 && !a[0].equalsIgnoreCase("chat") && !a[0].equalsIgnoreCase("list")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
