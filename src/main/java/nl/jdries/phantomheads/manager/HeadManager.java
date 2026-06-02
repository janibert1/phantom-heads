package nl.jdries.phantomheads.manager;

import net.kyori.adventure.text.minimessage.MiniMessage;
import nl.jdries.phantomheads.PhantomHeadsPlugin;
import nl.jdries.phantomheads.animation.AnimationTask;
import nl.jdries.phantomheads.model.FloatingHead;
import nl.jdries.phantomheads.renderer.EntityRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager: owns all FloatingHead instances, drives entity lifecycle,
 * manages per-player visibility, handles YAML persistence, and runs the
 * PlaceholderAPI hologram refresh task.
 */
public class HeadManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PhantomHeadsPlugin plugin;
    private final EntityRenderer renderer;
    private final Map<String, FloatingHead> heads = new LinkedHashMap<>();
    // head id → UUIDs of players currently seeing it
    private final Map<String, Set<UUID>> viewers = new ConcurrentHashMap<>();

    private BukkitTask animationTask;
    private BukkitTask rangeTask;
    private BukkitTask holoRefreshTask;

    private File headsDir;

    public HeadManager(PhantomHeadsPlugin plugin, EntityRenderer renderer) {
        this.plugin = plugin;
        this.renderer = renderer;
        this.headsDir = new File(plugin.getDataFolder(), "heads");
        headsDir.mkdirs();
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public void loadAll() {
        heads.clear();
        viewers.clear();
        File[] files = headsDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            String id = file.getName().replace(".yml", "");
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            FloatingHead head = FloatingHead.loadFrom(id, cfg);
            heads.put(id, head);
        }
    }

    public void spawnAll() {
        for (FloatingHead head : heads.values()) {
            if (head.isEnabled()) renderer.spawnAll(head);
        }
    }

    public void startTasks() {
        int animRate  = plugin.getConfig().getInt("animation-tick-rate", 1);
        int rangeRate = plugin.getConfig().getInt("range-check-interval", 20);
        int holoRate  = plugin.getConfig().getInt("hologram-refresh-interval", 40);

        animationTask  = new AnimationTask(this).runTaskTimer(plugin, 0L, animRate);
        rangeTask      = Bukkit.getScheduler().runTaskTimer(plugin, this::updateVisibility, 0L, rangeRate);
        holoRefreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshHologramsWithPapi, 0L, holoRate);
    }

    public void shutdown() {
        if (animationTask   != null) animationTask.cancel();
        if (rangeTask       != null) rangeTask.cancel();
        if (holoRefreshTask != null) holoRefreshTask.cancel();
        for (FloatingHead head : heads.values()) renderer.despawnAll(head);
        heads.clear();
        viewers.clear();
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

    // =========================================================================
    // CRUD
    // =========================================================================

    public FloatingHead create(String id, Location loc, String texture) {
        FloatingHead head = new FloatingHead(id);
        head.setLocation(loc);
        head.setTexture(texture);
        applyConfigDefaults(head);
        heads.put(id, head);
        viewers.put(id, new HashSet<>());
        save(head);
        renderer.spawnAll(head);
        return head;
    }

    public boolean delete(String id) {
        FloatingHead head = heads.remove(id);
        if (head == null) return false;
        renderer.despawnAll(head);
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
        renderer.spawnAll(copy);
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

    // =========================================================================
    // Visibility
    // =========================================================================

    private void updateVisibility() {
        double range   = plugin.getConfig().getDouble("view-range", 48.0);
        double rangeS  = range * range;

        for (FloatingHead head : heads.values()) {
            Set<UUID> current = viewers.computeIfAbsent(head.getId(), k -> new HashSet<>());

            if (!head.isEnabled()) {
                // Hide from everyone if head is disabled
                for (UUID uid : new HashSet<>(current)) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p != null) renderer.hideFrom(head, p);
                }
                current.clear();
                continue;
            }

            Location loc = head.getLocation();
            if (loc == null || loc.getWorld() == null) continue;

            Set<UUID> newViewers = new HashSet<>();
            for (Player player : loc.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(loc) <= rangeS) {
                    newViewers.add(player.getUniqueId());
                }
            }

            // Show to newly in-range players
            for (UUID uid : newViewers) {
                if (!current.contains(uid)) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p != null) renderer.showTo(head, p);
                }
            }

            // Hide from players who left range
            for (UUID uid : new HashSet<>(current)) {
                if (!newViewers.contains(uid)) {
                    Player p = Bukkit.getPlayer(uid);
                    if (p != null) renderer.hideFrom(head, p);
                }
            }

            viewers.put(head.getId(), newViewers);
        }
    }

    /**
     * Periodically re-renders hologram lines through PlaceholderAPI so that
     * live server statistics (e.g. top kills, economy balance) stay up to date.
     * Uses the first available online player as PAPI context for server-level placeholders.
     * Heads with no PAPI placeholders in their lines are skipped cheaply.
     */
    private void refreshHologramsWithPapi() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) return;

        // Use any online player as context for global/server placeholders
        Player ctx = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (ctx == null) return;

        for (FloatingHead head : heads.values()) {
            if (!head.isEnabled() || !head.isHologramEnabled()) continue;
            List<String> lines = head.getLines();

            // Quick check: only process heads that have a % in their line text
            boolean hasPapi = lines.stream().anyMatch(l -> l.contains("%"));
            if (!hasPapi) continue;

            for (int i = 0; i < lines.size() && i < head.getHoloDisplays().size(); i++) {
                String resolved = applyPapi(ctx, lines.get(i));
                renderer.updateHoloText(head, i, resolved);
            }
        }
    }

    /** Applies PlaceholderAPI to a string via reflection (no compile-time dependency). */
    public static String applyPapi(Player player, String text) {
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return (String) papi.getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class)
                    .invoke(null, player, text);
        } catch (Exception ignored) {
            return text;
        }
    }

    /** Called when a player quits — cleans up personal skull stands. */
    public void onPlayerQuit(Player player) {
        for (Map.Entry<String, Set<UUID>> entry : viewers.entrySet()) {
            if (entry.getValue().remove(player.getUniqueId())) {
                FloatingHead head = heads.get(entry.getKey());
                if (head != null) removePersonalStand(head, player);
            }
        }
    }

    private void removePersonalStand(FloatingHead head, Player player) {
        ArmorStand personal = head.getPersonalStands().remove(player.getUniqueId());
        if (personal != null && !personal.isDead()) personal.remove();
    }

    /** Returns the live Player objects currently viewing a head. */
    public List<Player> getViewers(FloatingHead head) {
        Set<UUID> uids = viewers.getOrDefault(head.getId(), Collections.emptySet());
        List<Player> result = new ArrayList<>(uids.size());
        for (UUID uid : uids) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null) result.add(p);
        }
        return result;
    }

    // =========================================================================
    // Queries
    // =========================================================================

    public FloatingHead get(String id) { return heads.get(id); }
    public boolean exists(String id)   { return heads.containsKey(id); }
    public Collection<FloatingHead> getAllHeads() { return heads.values(); }

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

    public EntityRenderer getRenderer() { return renderer; }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void applyConfigDefaults(FloatingHead head) {
        head.setAnimationStyle(plugin.getConfig().getString("defaults.animation", "FLOATING"));
        head.setSpeedMultiplier(plugin.getConfig().getDouble("defaults.speed-multiplier", 1.0));
        head.setParticleType(plugin.getConfig().getString("defaults.particle-type", "FLAME"));
        head.setParticleDensity(plugin.getConfig().getDouble("defaults.particle-density", 1.0));
        head.setAmbientEffect(plugin.getConfig().getString("defaults.ambient-effect", "circle"));
        head.setClickEffect(plugin.getConfig().getString("defaults.click-effect", "burst"));
        head.setCooldownSeconds(plugin.getConfig().getInt("defaults.cooldown", 3));
    }
}
