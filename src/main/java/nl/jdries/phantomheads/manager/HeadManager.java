package nl.jdries.phantomheads.manager;

import net.kyori.adventure.text.minimessage.MiniMessage;
import nl.jdries.phantomheads.PhantomHeadsPlugin;
import nl.jdries.phantomheads.animation.AnimationTask;
import nl.jdries.phantomheads.model.FloatingHead;
import nl.jdries.phantomheads.particle.ParticleEngine;
import nl.jdries.phantomheads.renderer.PacketRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HeadManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PhantomHeadsPlugin plugin;
    private final PacketRenderer renderer;
    private final Map<String, FloatingHead> heads     = new LinkedHashMap<>();
    private final Map<String, Set<UUID>>    viewers   = new ConcurrentHashMap<>();
    // entity ID → head — used by PacketClickListener for O(1) click lookup
    private final Map<Integer, FloatingHead> entityIndex = new ConcurrentHashMap<>();

    private BukkitTask animTask;
    private BukkitTask rangeTask;
    private BukkitTask holoTask;
    private File headsDir;

    public HeadManager(PhantomHeadsPlugin plugin, PacketRenderer renderer) {
        this.plugin   = plugin;
        this.renderer = renderer;
        this.headsDir = new File(plugin.getDataFolder(), "heads");
        headsDir.mkdirs();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void loadAll() {
        heads.clear();
        viewers.clear();
        entityIndex.clear();
        File[] files = headsDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            String id = file.getName().replace(".yml", "");
            FloatingHead head = FloatingHead.loadFrom(id, YamlConfiguration.loadConfiguration(file));
            heads.put(id, head);
        }
    }

    public void spawnAll() {
        for (FloatingHead head : heads.values()) {
            if (head.isEnabled()) {
                renderer.allocateIds(head);
                indexHead(head);
            }
        }
    }

    public void startTasks() {
        int animRate  = plugin.getConfig().getInt("animation-tick-rate", 1);
        int rangeRate = plugin.getConfig().getInt("range-check-interval", 20);
        int holoRate  = plugin.getConfig().getInt("hologram-refresh-interval", 40);

        animTask  = new AnimationTask(this).runTaskTimer(plugin, 0L, animRate);
        rangeTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateVisibility, 0L, rangeRate);
        holoTask  = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshPapi, 0L, holoRate);
    }

    public void shutdown() {
        if (animTask  != null) animTask.cancel();
        if (rangeTask != null) rangeTask.cancel();
        if (holoTask  != null) holoTask.cancel();
        for (FloatingHead head : heads.values()) {
            renderer.despawnAll(head, getViewers(head));
        }
        heads.clear();
        viewers.clear();
        entityIndex.clear();
    }

    public void reload() {
        shutdown();
        plugin.reloadConfig();
        headsDir = new File(plugin.getDataFolder(), "heads");
        headsDir.mkdirs();
        loadAll();
        spawnAll();
        startTasks();
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public FloatingHead create(String id, Location loc, String texture) {
        FloatingHead head = new FloatingHead(id);
        head.setLocation(loc);
        head.setTexture(texture);
        applyConfigDefaults(head);
        heads.put(id, head);
        viewers.put(id, new HashSet<>());
        save(head);
        renderer.allocateIds(head);
        indexHead(head);
        return head;
    }

    public boolean delete(String id) {
        FloatingHead head = heads.remove(id);
        if (head == null) return false;
        renderer.despawnAll(head, getViewers(head));
        unindexHead(head);
        viewers.remove(id);
        new File(headsDir, id + ".yml").delete();
        return true;
    }

    public FloatingHead clone(String sourceId, String newId, Location loc) {
        FloatingHead source = heads.get(sourceId);
        if (source == null) return null;
        YamlConfiguration tmp = new YamlConfiguration();
        source.saveTo(tmp);
        FloatingHead copy = FloatingHead.loadFrom(newId, tmp);
        copy.setLocation(loc);
        heads.put(newId, copy);
        viewers.put(newId, new HashSet<>());
        save(copy);
        renderer.allocateIds(copy);
        indexHead(copy);
        return copy;
    }

    public void save(FloatingHead head) {
        File file = new File(headsDir, head.getId() + ".yml");
        YamlConfiguration cfg = new YamlConfiguration();
        head.saveTo(cfg);
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save head " + head.getId() + ": " + e.getMessage());
        }
    }

    // ── Enable / disable helpers (called by PHCommand) ────────────────────────

    public void enableHead(FloatingHead head) {
        head.setEnabled(true);
        renderer.allocateIds(head);
        indexHead(head);
        save(head);
    }

    public void disableHead(FloatingHead head) {
        head.setEnabled(false);
        renderer.despawnAll(head, getViewers(head));
        unindexHead(head);
        viewers.getOrDefault(head.getId(), Collections.emptySet()).clear();
        save(head);
    }

    // ── Click handling (called by PacketClickListener) ────────────────────────

    public void handleEntityClick(Player player, int entityId) {
        FloatingHead head = entityIndex.get(entityId);
        if (head == null || !head.isEnabled()) return;

        if (!player.hasPermission("phantomheads.use")) {
            player.sendMessage(MM.deserialize("<red>You don't have permission to interact with this."));
            return;
        }
        if (head.isOnCooldown(player.getUniqueId())) {
            player.sendMessage(MM.deserialize("<yellow>Please wait before clicking that again."));
            return;
        }
        head.markCooldown(player.getUniqueId());

        ParticleEngine.spawnClick(head);

        for (String cmd : head.getPlayerCommands())
            Bukkit.dispatchCommand(player, replacePlaceholders(cmd, player));
        for (String cmd : head.getConsoleCommands())
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replacePlaceholders(cmd, player));
        for (String msg : head.getMessages())
            player.sendMessage(MM.deserialize(replacePlaceholders(msg, player)));
    }

    // ── Visibility range ─────────────────────────────────────────────────────

    private void updateVisibility() {
        double range  = plugin.getConfig().getDouble("view-range", 48.0);
        double range2 = range * range;

        for (FloatingHead head : heads.values()) {
            Set<UUID> current = viewers.computeIfAbsent(head.getId(), k -> new HashSet<>());

            if (!head.isEnabled()) {
                for (UUID uid : new HashSet<>(current)) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p != null) renderer.hideFrom(head, p);
                }
                current.clear();
                continue;
            }

            Location loc = head.getLocation();
            if (loc == null || loc.getWorld() == null) continue;

            Set<UUID> inRange = new HashSet<>();
            for (Player p : loc.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(loc) <= range2)
                    inRange.add(p.getUniqueId());
            }

            for (UUID uid : inRange) {
                if (!current.contains(uid)) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p != null) renderer.showTo(head, p);
                }
            }
            for (UUID uid : new HashSet<>(current)) {
                if (!inRange.contains(uid)) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p != null) renderer.hideFrom(head, p);
                }
            }
            viewers.put(head.getId(), inRange);
        }
    }

    // ── PAPI refresh with change detection ───────────────────────────────────

    private void refreshPapi() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return;
        Player ctx = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (ctx == null) return;

        for (FloatingHead head : heads.values()) {
            if (!head.isEnabled() || !head.isHologramEnabled()) continue;
            List<String> lines = head.getLines();
            boolean hasPapi = lines.stream().anyMatch(l -> l.contains("%"));
            if (!hasPapi) continue;

            for (int i = 0; i < lines.size(); i++) {
                String resolved = applyPapi(ctx, lines.get(i));
                String cached   = head.getCachedResolvedLine(i);
                // Only send the packet if the text actually changed
                if (!resolved.equals(cached)) {
                    head.setCachedResolvedLine(i, resolved);
                    renderer.updateHoloText(head, i, resolved, getViewers(head));
                }
            }
        }
    }

    /** Applies PlaceholderAPI via reflection (no compile-time dependency). */
    public static String applyPapi(Player player, String text) {
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return (String) papi.getMethod("setPlaceholders",
                    org.bukkit.OfflinePlayer.class, String.class).invoke(null, player, text);
        } catch (Exception ignored) {
            return text;
        }
    }

    public void onPlayerQuit(Player player) {
        for (Map.Entry<String, Set<UUID>> entry : viewers.entrySet()) {
            if (entry.getValue().remove(player.getUniqueId())) {
                FloatingHead head = heads.get(entry.getKey());
                if (head != null) {
                    renderer.hideFrom(head, player);
                    head.getPersonalEntityIds().remove(player.getUniqueId());
                }
            }
        }
    }

    // ── Entity index helpers ─────────────────────────────────────────────────

    private void indexHead(FloatingHead head) {
        if (head.getSkullEntityId() != -1)
            entityIndex.put(head.getSkullEntityId(), head);
        for (int id : head.getHoloEntityIds())
            entityIndex.put(id, head);
        for (int id : head.getPersonalEntityIds().values())
            entityIndex.put(id, head);
    }

    private void unindexHead(FloatingHead head) {
        entityIndex.remove(head.getSkullEntityId());
        head.getHoloEntityIds().forEach(entityIndex::remove);
        head.getPersonalEntityIds().values().forEach(entityIndex::remove);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public FloatingHead get(String id)            { return heads.get(id); }
    public boolean exists(String id)              { return heads.containsKey(id); }
    public Collection<FloatingHead> getAllHeads()  { return heads.values(); }
    public PacketRenderer getRenderer()           { return renderer; }

    public List<Player> getViewers(FloatingHead head) {
        Set<UUID> uids = viewers.getOrDefault(head.getId(), Collections.emptySet());
        List<Player> result = new ArrayList<>(uids.size());
        for (UUID uid : uids) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) result.add(p);
        }
        return result;
    }

    public List<FloatingHead> getNear(Location center, double radius) {
        double r2 = radius * radius;
        List<FloatingHead> result = new ArrayList<>();
        for (FloatingHead head : heads.values()) {
            Location loc = head.getLocation();
            if (loc == null || !loc.getWorld().getName().equals(center.getWorld().getName())) continue;
            if (loc.distanceSquared(center) <= r2) result.add(head);
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyConfigDefaults(FloatingHead head) {
        head.setAnimationStyle(plugin.getConfig().getString("defaults.animation", "FLOATING"));
        head.setSpeedMultiplier(plugin.getConfig().getDouble("defaults.speed-multiplier", 1.0));
        head.setParticleType(plugin.getConfig().getString("defaults.particle-type", "FLAME"));
        head.setParticleDensity(plugin.getConfig().getDouble("defaults.particle-density", 1.0));
        head.setAmbientEffect(plugin.getConfig().getString("defaults.ambient-effect", "circle"));
        head.setClickEffect(plugin.getConfig().getString("defaults.click-effect", "burst"));
        head.setCooldownSeconds(plugin.getConfig().getInt("defaults.cooldown", 3));
    }

    private String replacePlaceholders(String input, Player player) {
        String result = input.replace("%player_name%", player.getName());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
            result = applyPapi(player, result);
        return result;
    }
}
