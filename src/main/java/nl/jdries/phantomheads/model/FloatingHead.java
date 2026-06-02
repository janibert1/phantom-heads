package nl.jdries.phantomheads.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FloatingHead {

    // --- Persisted ---
    private String id;
    private String world;
    private double x, y, z;
    private float yaw;
    private String texture;
    private boolean enabled;
    private boolean hologramEnabled;
    private List<String> lines;
    private String animationStyle;
    private double speedMultiplier;
    private String particleType;
    private double particleDensity;
    private String ambientEffect;
    private String clickEffect;
    private List<String> playerCommands;
    private List<String> consoleCommands;
    private List<String> messages;
    private int cooldownSeconds;

    // --- Runtime packet entity IDs (never saved) ---
    private transient int skullEntityId = -1;
    private transient final List<Integer> holoEntityIds = new ArrayList<>();
    // Per-viewer skull for %player_name% dynamic texture
    private transient final Map<UUID, Integer> personalEntityIds = new ConcurrentHashMap<>();
    // Cooldowns
    private transient final Map<UUID, Long> cooldowns = new HashMap<>();
    // Animation state
    private transient double animTick = 0.0;
    private transient double baseY;
    // PAPI change-detection cache — last resolved text per holo line
    private transient final List<String> cachedResolvedLines = new ArrayList<>();
    // Resolved Particle enum, cached once on load/create so it's not looked up every tick
    private transient Particle cachedParticle = null;

    public FloatingHead(String id) {
        this.id = id;
        this.enabled = true;
        this.hologramEnabled = true;
        this.lines = new ArrayList<>();
        this.animationStyle = "FLOATING";
        this.speedMultiplier = 1.0;
        this.particleType = "FLAME";
        this.particleDensity = 1.0;
        this.ambientEffect = "circle";
        this.clickEffect = "burst";
        this.playerCommands = new ArrayList<>();
        this.consoleCommands = new ArrayList<>();
        this.messages = new ArrayList<>();
        this.cooldownSeconds = 3;
    }

    public Location getLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, 0f);
    }

    public void setLocation(Location loc) {
        this.world = loc.getWorld().getName();
        this.x = loc.getX();
        this.y = loc.getY();
        this.z = loc.getZ();
        this.yaw = loc.getYaw();
        this.baseY = loc.getY();
    }

    public boolean isOnCooldown(UUID uuid) {
        long last = cooldowns.getOrDefault(uuid, 0L);
        return System.currentTimeMillis() - last < cooldownSeconds * 1000L;
    }

    public void markCooldown(UUID uuid) {
        cooldowns.put(uuid, System.currentTimeMillis());
    }

    public boolean isDynamicTexture() {
        return "%player_name%".equalsIgnoreCase(texture);
    }

    // --- Entity ID helpers ---

    public int getSkullEntityId()             { return skullEntityId; }
    public void setSkullEntityId(int id)      { this.skullEntityId = id; }
    public List<Integer> getHoloEntityIds()   { return holoEntityIds; }
    public Map<UUID, Integer> getPersonalEntityIds() { return personalEntityIds; }

    // --- PAPI line cache ---

    public String getCachedResolvedLine(int index) {
        if (index < 0 || index >= cachedResolvedLines.size()) return null;
        return cachedResolvedLines.get(index);
    }

    public void setCachedResolvedLine(int index, String text) {
        while (cachedResolvedLines.size() <= index) cachedResolvedLines.add(null);
        cachedResolvedLines.set(index, text);
    }

    public void clearResolvedLineCache() { cachedResolvedLines.clear(); }

    // --- Particle cache ---

    public Particle getCachedParticle()             { return cachedParticle; }
    public void setCachedParticle(Particle particle) { this.cachedParticle = particle; }

    // --- YAML persistence ---

    public void saveTo(ConfigurationSection s) {
        s.set("world", world);
        s.set("x", x);
        s.set("y", y);
        s.set("z", z);
        s.set("yaw", (double) yaw);
        s.set("texture", texture);
        s.set("enabled", enabled);
        s.set("hologram-enabled", hologramEnabled);
        s.set("lines", lines);
        s.set("animation", animationStyle);
        s.set("speed-multiplier", speedMultiplier);
        s.set("particle-type", particleType);
        s.set("particle-density", particleDensity);
        s.set("ambient-effect", ambientEffect);
        s.set("click-effect", clickEffect);
        s.set("player-commands", playerCommands);
        s.set("console-commands", consoleCommands);
        s.set("messages", messages);
        s.set("cooldown", cooldownSeconds);
    }

    public static FloatingHead loadFrom(String id, ConfigurationSection s) {
        FloatingHead h = new FloatingHead(id);
        h.world = s.getString("world", "world");
        h.x = s.getDouble("x");
        h.y = s.getDouble("y");
        h.z = s.getDouble("z");
        h.yaw = (float) s.getDouble("yaw");
        h.texture = s.getString("texture");
        h.enabled = s.getBoolean("enabled", true);
        h.hologramEnabled = s.getBoolean("hologram-enabled", true);
        h.lines = new ArrayList<>(s.getStringList("lines"));
        h.animationStyle = s.getString("animation", "FLOATING");
        h.speedMultiplier = s.getDouble("speed-multiplier", 1.0);
        h.particleType = s.getString("particle-type", "FLAME");
        h.particleDensity = s.getDouble("particle-density", 1.0);
        h.ambientEffect = s.getString("ambient-effect", "circle");
        h.clickEffect = s.getString("click-effect", "burst");
        h.playerCommands = new ArrayList<>(s.getStringList("player-commands"));
        h.consoleCommands = new ArrayList<>(s.getStringList("console-commands"));
        h.messages = new ArrayList<>(s.getStringList("messages"));
        h.cooldownSeconds = s.getInt("cooldown", 3);
        h.baseY = h.y;
        return h;
    }

    // --- Getters / setters ---

    public String getId()       { return id; }
    public String getWorld()    { return world; }
    public double getX()        { return x; }
    public double getY()        { return y; }
    public double getZ()        { return z; }
    public float getYaw()       { return yaw; }

    public String getTexture()              { return texture; }
    public void setTexture(String texture)  { this.texture = texture; }

    public boolean isEnabled()              { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isHologramEnabled()              { return hologramEnabled; }
    public void setHologramEnabled(boolean v)       { this.hologramEnabled = v; }

    public List<String> getLines()          { return lines; }

    public String getAnimationStyle()               { return animationStyle; }
    public void setAnimationStyle(String s)         { this.animationStyle = s; }

    public double getSpeedMultiplier()              { return speedMultiplier; }
    public void setSpeedMultiplier(double v)        { this.speedMultiplier = v; }

    public String getParticleType()                 { return particleType; }
    public void setParticleType(String v)           { this.particleType = v; cachedParticle = null; }

    public double getParticleDensity()              { return particleDensity; }
    public void setParticleDensity(double v)        { this.particleDensity = v; }

    public String getAmbientEffect()                { return ambientEffect; }
    public void setAmbientEffect(String v)          { this.ambientEffect = v; }

    public String getClickEffect()                  { return clickEffect; }
    public void setClickEffect(String v)            { this.clickEffect = v; }

    public List<String> getPlayerCommands()         { return playerCommands; }
    public List<String> getConsoleCommands()        { return consoleCommands; }
    public List<String> getMessages()               { return messages; }

    public int getCooldownSeconds()                 { return cooldownSeconds; }
    public void setCooldownSeconds(int v)           { this.cooldownSeconds = v; }

    public double getAnimTick()                     { return animTick; }
    public void setAnimTick(double v)               { this.animTick = v; }

    public double getBaseY()                        { return baseY; }
    public void setBaseY(double v)                  { this.baseY = v; }
}
