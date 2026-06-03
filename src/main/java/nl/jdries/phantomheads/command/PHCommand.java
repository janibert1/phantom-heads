package nl.jdries.phantomheads.command;

import net.kyori.adventure.text.minimessage.MiniMessage;
import nl.jdries.phantomheads.manager.HeadManager;
import nl.jdries.phantomheads.model.FloatingHead;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Full /ph command tree.
 *
 * Head management:
 *   /ph create <id> [texture]
 *   /ph delete <id>
 *   /ph clone <id> <new_id>
 *   /ph reload
 *   /ph info <id>
 *   /ph list
 *   /ph near [radius]
 *
 * Visibility & teleport:
 *   /ph enable <id>
 *   /ph disable <id>
 *   /ph toggle <id>
 *   /ph hologram <enable|disable> <id>
 *   /ph movehere <id>
 *   /ph teleport <id>
 *
 * Hologram lines:
 *   /ph line add <id> <text>
 *   /ph line set <id> <index> <text>
 *   /ph line remove <id> <index>
 *   /ph line swap <id> <index1> <index2>
 *   /ph line list <id>
 *
 * Actions:
 *   /ph action <id> add <player|console|message> <value>
 *   /ph action <id> remove <player|console|message> <index>
 *   /ph action <id> list <player|console|message>
 */
public class PHCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String PFX = "<gray>[<gradient:#a855f7:#ec4899>PhantomHeads</gradient>]</gray> ";

    private final HeadManager manager;

    public PHCommand(HeadManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("phantomheads.admin")) {
            msg(sender, "<red>You don't have permission to use /ph.");
            return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            // Head management
            case "create"   -> cmdCreate(sender, args);
            case "delete"   -> cmdDelete(sender, args);
            case "clone"    -> cmdClone(sender, args);
            case "reload"   -> cmdReload(sender);
            case "info"     -> cmdInfo(sender, args);
            case "list"     -> cmdList(sender);
            case "near"     -> cmdNear(sender, args);
            // Visibility & teleport
            case "enable"   -> cmdSetEnabled(sender, args, true);
            case "disable"  -> cmdSetEnabled(sender, args, false);
            case "toggle"   -> cmdToggle(sender, args);
            case "hologram" -> cmdHologram(sender, args);
            case "movehere" -> cmdMoveHere(sender, args);
            case "teleport", "tp" -> cmdTeleport(sender, args);
            // Hologram lines
            case "line"     -> cmdLine(sender, args);
            // Actions
            case "action"   -> cmdAction(sender, args);
            default -> msg(sender, "<red>Unknown subcommand. Use /ph for help.");
        }
        return true;
    }

    // =========================================================================
    // Head management
    // =========================================================================

    private void cmdCreate(CommandSender sender, String[] args) {
        Player p = requirePlayer(sender); if (p == null) return;
        if (args.length < 2) { msg(sender, "<yellow>Usage: /ph create <id> [texture]"); return; }
        String id = args[1];
        if (manager.exists(id)) { msg(sender, "<red>A head with ID <white>" + id + "</white> already exists."); return; }
        String texture = args.length >= 3 ? args[2] : null;
        manager.create(id, p.getLocation(), texture);
        msg(sender, "<green>Head <white>" + id + "</white> created at your location.");
    }

    private void cmdDelete(CommandSender sender, String[] args) {
        if (args.length < 2) { msg(sender, "<yellow>Usage: /ph delete <id>"); return; }
        if (!manager.delete(args[1])) { notFound(sender, args[1]); return; }
        msg(sender, "<red>Head <white>" + args[1] + "</white> deleted.");
    }

    private void cmdClone(CommandSender sender, String[] args) {
        Player p = requirePlayer(sender); if (p == null) return;
        if (args.length < 3) { msg(sender, "<yellow>Usage: /ph clone <id> <new_id>"); return; }
        String src = args[1], dst = args[2];
        if (!manager.exists(src)) { notFound(sender, src); return; }
        if (manager.exists(dst)) { msg(sender, "<red>ID <white>" + dst + "</white> is already in use."); return; }
        manager.clone(src, dst, p.getLocation());
        msg(sender, "<green>Cloned <white>" + src + "</white> → <white>" + dst + "</white> at your location.");
    }

    private void cmdReload(CommandSender sender) {
        manager.reload();
        msg(sender, "<green>PhantomHeads reloaded.");
    }

    private void cmdInfo(CommandSender sender, String[] args) {
        if (args.length < 2) { msg(sender, "<yellow>Usage: /ph info <id>"); return; }
        FloatingHead h = get(sender, args[1]); if (h == null) return;
        Location loc = h.getLocation();
        msg(sender, "<yellow>--- <white>" + h.getId() + "</white> ---");
        msg(sender, "  Status:    " + (h.isEnabled() ? "<green>Enabled" : "<red>Disabled"));
        msg(sender, "  Hologram:  " + (h.isHologramEnabled() ? "<green>On" : "<red>Off"));
        if (loc != null) msg(sender, "  Location:  <white>" + h.getWorld() + " " + f(loc.getX()) + " " + f(loc.getY()) + " " + f(loc.getZ()));
        msg(sender, "  Animation: <white>" + h.getAnimationStyle() + " (speed " + h.getSpeedMultiplier() + ")");
        msg(sender, "  Particle:  <white>" + h.getParticleType() + " density=" + h.getParticleDensity());
        msg(sender, "  Ambient:   <white>" + h.getAmbientEffect() + "  Click: <white>" + h.getClickEffect());
        msg(sender, "  Cooldown:  <white>" + h.getCooldownSeconds() + "s");
        msg(sender, "  Lines (" + h.getLines().size() + "):");
        for (int i = 0; i < h.getLines().size(); i++) msg(sender, "    <white>" + i + ": " + h.getLines().get(i));
        msg(sender, "  Actions — player: <white>" + h.getPlayerCommands().size()
                + "<gray>  console: <white>" + h.getConsoleCommands().size()
                + "<gray>  message: <white>" + h.getMessages().size());
    }

    private void cmdList(CommandSender sender) {
        Collection<FloatingHead> all = manager.getAllHeads();
        if (all.isEmpty()) { msg(sender, "<yellow>No heads created yet."); return; }
        msg(sender, "<yellow>All heads (" + all.size() + "):");
        for (FloatingHead h : all) {
            Location loc = h.getLocation();
            String locStr = loc == null ? "unknown" : h.getWorld() + " " + f(loc.getX()) + " " + f(loc.getY()) + " " + f(loc.getZ());
            msg(sender, "  <white>" + h.getId() + " " + (h.isEnabled() ? "<green>ON" : "<red>OFF") + " <gray>@ " + locStr);
        }
    }

    private void cmdNear(CommandSender sender, String[] args) {
        Player p = requirePlayer(sender); if (p == null) return;
        double radius = 32;
        if (args.length >= 2) {
            try { radius = Double.parseDouble(args[1]); }
            catch (NumberFormatException e) { msg(sender, "<red>Invalid radius."); return; }
        }
        List<FloatingHead> near = manager.getNear(p.getLocation(), radius);
        if (near.isEmpty()) { msg(sender, "<yellow>No heads within " + radius + " blocks."); return; }
        msg(sender, "<yellow>Heads within " + radius + " blocks (" + near.size() + "):");
        for (FloatingHead h : near) {
            Location loc = h.getLocation();
            double dist = loc != null ? loc.distance(p.getLocation()) : 0;
            msg(sender, "  <white>" + h.getId() + " <gray>(" + f(dist) + " blocks)");
        }
    }

    // =========================================================================
    // Visibility & teleport
    // =========================================================================

    private void cmdSetEnabled(CommandSender sender, String[] args, boolean enable) {
        if (args.length < 2) { msg(sender, "<yellow>Usage: /ph " + (enable ? "enable" : "disable") + " <id>"); return; }
        FloatingHead h = get(sender, args[1]); if (h == null) return;
        if (enable) manager.enableHead(h); else manager.disableHead(h);
        msg(sender, "<white>" + h.getId() + "</white> " + (enable ? "<green>enabled" : "<red>disabled") + ".");
    }

    private void cmdToggle(CommandSender sender, String[] args) {
        if (args.length < 2) { msg(sender, "<yellow>Usage: /ph toggle <id>"); return; }
        FloatingHead h = get(sender, args[1]); if (h == null) return;
        // Delegate to cmdSetEnabled with the toggled state — keep args so id is at args[1]
        cmdSetEnabled(sender, args, !h.isEnabled());
    }

    private void cmdHologram(CommandSender sender, String[] args) {
        // /ph hologram <enable|disable> <id>
        if (args.length < 3) { msg(sender, "<yellow>Usage: /ph hologram <enable|disable> <id>"); return; }
        boolean enable = args[1].equalsIgnoreCase("enable");
        FloatingHead h = get(sender, args[2]); if (h == null) return;
        h.setHologramEnabled(enable);
        manager.getRenderer().refreshHolos(h, manager.getViewers(h));
        manager.save(h);
        msg(sender, "Hologram for <white>" + h.getId() + "</white> " + (enable ? "<green>enabled" : "<red>disabled") + ".");
    }

    private void cmdMoveHere(CommandSender sender, String[] args) {
        Player p = requirePlayer(sender); if (p == null) return;
        if (args.length < 2) { msg(sender, "<yellow>Usage: /ph movehere <id>"); return; }
        FloatingHead h = get(sender, args[1]); if (h == null) return;
        h.setLocation(p.getLocation());
        manager.getRenderer().teleport(h, p.getLocation(), manager.getViewers(h));
        manager.save(h);
        msg(sender, "<white>" + h.getId() + "</white> moved to your location.");
    }

    private void cmdTeleport(CommandSender sender, String[] args) {
        Player p = requirePlayer(sender); if (p == null) return;
        if (args.length < 2) { msg(sender, "<yellow>Usage: /ph teleport <id>"); return; }
        FloatingHead h = get(sender, args[1]); if (h == null) return;
        Location loc = h.getLocation();
        if (loc == null) { msg(sender, "<red>Head has no valid location."); return; }
        p.teleport(loc);
        msg(sender, "Teleported to <white>" + h.getId() + "</white>.");
    }

    // =========================================================================
    // Hologram line editing
    // =========================================================================

    private void cmdLine(CommandSender sender, String[] args) {
        // /ph line <add|set|remove|swap|list> <id> [args...]
        if (args.length < 3) { msg(sender, "<yellow>Usage: /ph line <add|set|remove|swap|list> <id> [...]"); return; }
        String action = args[1].toLowerCase(Locale.ROOT);
        FloatingHead h = get(sender, args[2]); if (h == null) return;
        List<String> lines = h.getLines();

        switch (action) {
            case "add" -> {
                if (args.length < 4) { msg(sender, "<yellow>Usage: /ph line add <id> <text>"); return; }
                lines.add(String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                msg(sender, "<green>Line " + (lines.size() - 1) + " added.");
            }
            case "set" -> {
                if (args.length < 5) { msg(sender, "<yellow>Usage: /ph line set <id> <index> <text>"); return; }
                int idx = parseInt(sender, args[3]); if (idx < 0) return;
                if (idx >= lines.size()) { msg(sender, "<red>Index out of range (0–" + (lines.size()-1) + ")."); return; }
                lines.set(idx, String.join(" ", Arrays.copyOfRange(args, 4, args.length)));
                msg(sender, "<green>Line " + idx + " updated.");
            }
            case "remove" -> {
                if (args.length < 4) { msg(sender, "<yellow>Usage: /ph line remove <id> <index>"); return; }
                int idx = parseInt(sender, args[3]); if (idx < 0) return;
                if (idx >= lines.size()) { msg(sender, "<red>Index out of range."); return; }
                lines.remove(idx);
                msg(sender, "<red>Line " + idx + " removed.");
            }
            case "swap" -> {
                if (args.length < 5) { msg(sender, "<yellow>Usage: /ph line swap <id> <index1> <index2>"); return; }
                int i = parseInt(sender, args[3]); if (i < 0) return;
                int j = parseInt(sender, args[4]); if (j < 0) return;
                if (i >= lines.size() || j >= lines.size()) { msg(sender, "<red>Index out of range."); return; }
                Collections.swap(lines, i, j);
                msg(sender, "<green>Lines " + i + " and " + j + " swapped.");
            }
            case "list" -> {
                msg(sender, "<yellow>Lines for <white>" + h.getId() + "</white>:");
                if (lines.isEmpty()) { msg(sender, "  <gray>(none)"); return; }
                for (int i = 0; i < lines.size(); i++) msg(sender, "  <white>" + i + ": " + lines.get(i));
                return; // skip refresh — no change
            }
            default -> { msg(sender, "<red>Unknown line action. Use add/set/remove/swap/list."); return; }
        }

        manager.getRenderer().refreshHolos(h, manager.getViewers(h));
        manager.save(h);
    }

    // =========================================================================
    // Action configuration
    // =========================================================================

    private void cmdAction(CommandSender sender, String[] args) {
        // /ph action <id> <add|remove|list> <player|console|message> [value/index]
        if (args.length < 4) { msg(sender, "<yellow>Usage: /ph action <id> <add|remove|list> <player|console|message> [value]"); return; }
        FloatingHead h = get(sender, args[1]); if (h == null) return;
        String op   = args[2].toLowerCase(Locale.ROOT);
        String type = args[3].toLowerCase(Locale.ROOT);
        List<String> list = switch (type) {
            case "player"  -> h.getPlayerCommands();
            case "console" -> h.getConsoleCommands();
            case "message" -> h.getMessages();
            default -> null;
        };
        if (list == null) { msg(sender, "<red>Type must be player, console, or message."); return; }

        switch (op) {
            case "add" -> {
                if (args.length < 5) { msg(sender, "<yellow>Provide a value to add."); return; }
                String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
                list.add(value);
                msg(sender, "<green>Added " + type + " action: <white>" + value);
            }
            case "remove" -> {
                if (args.length < 5) { msg(sender, "<yellow>Provide an index to remove."); return; }
                int idx = parseInt(sender, args[4]); if (idx < 0) return;
                if (idx >= list.size()) { msg(sender, "<red>Index out of range."); return; }
                list.remove(idx);
                msg(sender, "<red>Removed " + type + " action at index " + idx + ".");
            }
            case "list" -> {
                msg(sender, "<yellow>" + type + " actions for <white>" + h.getId() + "</white>:");
                if (list.isEmpty()) { msg(sender, "  <gray>(none)"); return; }
                for (int i = 0; i < list.size(); i++) msg(sender, "  <white>" + i + ": " + list.get(i));
                return;
            }
            default -> { msg(sender, "<red>Op must be add, remove, or list."); return; }
        }
        manager.save(h);
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("phantomheads.admin")) return List.of();
        List<String> ids = manager.getAllHeads().stream().map(FloatingHead::getId).toList();

        if (args.length == 1) {
            return filter(args[0], "create","delete","clone","reload","info","list","near",
                    "enable","disable","toggle","hologram","movehere","teleport","line","action");
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "delete","info","enable","disable","toggle","movehere","teleport","near"
                    -> args.length == 2 ? filter(args[1], ids) : List.of();
            case "clone"
                    -> args.length == 2 ? filter(args[1], ids) : List.of();
            case "hologram" -> {
                if (args.length == 2) yield filter(args[1], "enable", "disable");
                if (args.length == 3) yield filter(args[2], ids);
                yield List.of();
            }
            case "line" -> {
                if (args.length == 2) yield filter(args[1], "add","set","remove","swap","list");
                if (args.length == 3) yield filter(args[2], ids);
                yield List.of();
            }
            case "action" -> {
                if (args.length == 2) yield filter(args[1], ids);
                if (args.length == 3) yield filter(args[2], "add","remove","list");
                if (args.length == 4) yield filter(args[3], "player","console","message");
                yield List.of();
            }
            default -> List.of();
        };
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void msg(CommandSender s, String mini) {
        s.sendMessage(MM.deserialize(PFX + mini));
    }

    private Player requirePlayer(CommandSender s) {
        if (s instanceof Player p) return p;
        msg(s, "<red>This command requires a player.");
        return null;
    }

    private FloatingHead get(CommandSender s, String id) {
        FloatingHead h = manager.get(id);
        if (h == null) notFound(s, id);
        return h;
    }

    private void notFound(CommandSender s, String id) {
        msg(s, "<red>No head found with ID: <white>" + id + "</white>.");
    }

    private int parseInt(CommandSender s, String raw) {
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException e) { msg(s, "<red>Not a valid number: " + raw); return -1; }
    }

    private String f(double v) { return String.format("%.1f", v); }

    private List<String> filter(String prefix, String... options) { return filter(prefix, Arrays.asList(options)); }

    private List<String> filter(String prefix, List<String> options) {
        String lp = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lp)).toList();
    }

    private void sendHelp(CommandSender s) {
        msg(s, "<yellow>PhantomHeads Commands <gray>(/ph)");
        msg(s, "  <white>create <id> [texture]  <gray>— spawn a head at your location");
        msg(s, "  <white>delete|info|enable|disable|toggle|movehere|teleport <id>");
        msg(s, "  <white>clone <id> <new_id>  <gray>|  <white>hologram <enable|disable> <id>");
        msg(s, "  <white>line <add|set|remove|swap|list> <id> [...]");
        msg(s, "  <white>action <id> <add|remove|list> <player|console|message> [value]");
        msg(s, "  <white>list  <gray>|  <white>near [radius]  <gray>|  <white>reload");
    }
}
