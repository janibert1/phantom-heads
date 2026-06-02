package nl.jdries.phantomheads.renderer;

import net.kyori.adventure.text.minimessage.MiniMessage;
import nl.jdries.phantomheads.PhantomHeadsPlugin;
import nl.jdries.phantomheads.model.FloatingHead;
import nl.jdries.phantomheads.util.TextureUtil;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

import java.util.List;

/**
 * Spawns and manages the virtual ArmorStand (skull) and TextDisplay (hologram)
 * entities for each FloatingHead. Entities are hidden from all players by default
 * and shown per-player via the Paper API.
 */
public class EntityRenderer {

    public static final double LINE_GAP = 0.28;
    public static final double HOLO_BASE_OFFSET = 0.65;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PhantomHeadsPlugin plugin;

    public EntityRenderer(PhantomHeadsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Spawns skull and hologram entities. Must be called on the main thread. */
    public void spawnAll(FloatingHead head) {
        Location loc = head.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        if (!head.isDynamicTexture()) {
            ArmorStand skull = spawnSkull(loc, TextureUtil.skullFromBase64(head.getTexture()));
            head.setSkullStand(skull);
        }

        if (head.isHologramEnabled()) {
            spawnHolos(head, loc);
        }
    }

    /** Removes all entities associated with a head. */
    public void despawnAll(FloatingHead head) {
        if (head.getSkullStand() != null && !head.getSkullStand().isDead()) {
            head.getSkullStand().remove();
            head.setSkullStand(null);
        }
        for (TextDisplay td : head.getHoloDisplays()) {
            if (!td.isDead()) td.remove();
        }
        head.getHoloDisplays().clear();
        for (ArmorStand personal : head.getPersonalStands().values()) {
            if (!personal.isDead()) personal.remove();
        }
        head.getPersonalStands().clear();
    }

    /**
     * Shows all entities of a head to a player entering view range.
     * For %player_name% heads, spawns or reuses a personal skull with their skin.
     */
    public void showTo(FloatingHead head, Player player) {
        if (head.isDynamicTexture()) {
            ArmorStand existing = head.getPersonalStands().get(player.getUniqueId());
            if (existing == null || existing.isDead()) {
                ItemStack skin = TextureUtil.skullFromPlayer(player);
                existing = spawnSkull(head.getLocation(), skin);
                head.getPersonalStands().put(player.getUniqueId(), existing);
            }
            player.showEntity(plugin, existing);
        } else if (head.getSkullStand() != null && !head.getSkullStand().isDead()) {
            player.showEntity(plugin, head.getSkullStand());
        }

        if (head.isHologramEnabled()) {
            for (TextDisplay td : head.getHoloDisplays()) {
                if (!td.isDead()) player.showEntity(plugin, td);
            }
        }
    }

    /** Hides all entities of a head from a player. */
    public void hideFrom(FloatingHead head, Player player) {
        if (head.isDynamicTexture()) {
            ArmorStand personal = head.getPersonalStands().get(player.getUniqueId());
            if (personal != null) player.hideEntity(plugin, personal);
        } else if (head.getSkullStand() != null) {
            player.hideEntity(plugin, head.getSkullStand());
        }
        for (TextDisplay td : head.getHoloDisplays()) {
            player.hideEntity(plugin, td);
        }
    }

    /**
     * Rebuilds hologram TextDisplay entities (e.g. after lines changed or PAPI refresh).
     * Current viewers have old displays hidden and new ones shown automatically.
     */
    public void refreshHolos(FloatingHead head, Iterable<Player> viewers) {
        for (TextDisplay td : head.getHoloDisplays()) {
            if (!td.isDead()) td.remove();
        }
        head.getHoloDisplays().clear();

        Location loc = head.getLocation();
        if (loc == null || !head.isHologramEnabled()) return;

        spawnHolos(head, loc);
        for (Player p : viewers) {
            for (TextDisplay td : head.getHoloDisplays()) {
                p.showEntity(plugin, td);
            }
        }
    }

    /** Refreshes the skull entity after a texture change (no-op for dynamic-texture heads). */
    public void refreshSkull(FloatingHead head, Iterable<Player> viewers) {
        if (head.isDynamicTexture()) return;
        if (head.getSkullStand() != null && !head.getSkullStand().isDead()) {
            head.getSkullStand().remove();
        }
        Location loc = head.getLocation();
        if (loc == null) return;
        ArmorStand skull = spawnSkull(loc, TextureUtil.skullFromBase64(head.getTexture()));
        head.setSkullStand(skull);
        for (Player p : viewers) {
            p.showEntity(plugin, skull);
        }
    }

    /** Teleports all entities to a new location (called by movehere and animation). */
    public void teleportEntities(FloatingHead head, Location newLoc) {
        if (head.getSkullStand() != null && !head.getSkullStand().isDead()) {
            head.getSkullStand().teleport(newLoc);
        }
        for (ArmorStand personal : head.getPersonalStands().values()) {
            if (!personal.isDead()) personal.teleport(newLoc);
        }
        repositionHolos(head, newLoc);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private ArmorStand spawnSkull(Location loc, ItemStack headItem) {
        return loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setVisible(false);
            as.setSmall(false);
            as.setArms(false);
            as.setBasePlate(false);
            as.setCollidable(false);
            as.setPersistent(false);
            as.setVisibleByDefault(false);
            as.getEquipment().setHelmet(headItem, true);
            as.setHeadPose(EulerAngle.ZERO);
        });
    }

    void spawnHolos(FloatingHead head, Location baseLoc) {
        List<String> lines = head.getLines();
        for (int i = 0; i < lines.size(); i++) {
            double yOffset = HOLO_BASE_OFFSET + (i * LINE_GAP);
            Location holoLoc = baseLoc.clone().add(0, yOffset, 0);
            TextDisplay td = spawnLine(holoLoc, lines.get(i));
            head.getHoloDisplays().add(td);
        }
    }

    private TextDisplay spawnLine(Location loc, String miniText) {
        return loc.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.text(MM.deserialize(miniText));
            td.setGravity(false);
            td.setPersistent(false);
            td.setVisibleByDefault(false);
            td.setBillboard(Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setDefaultBackground(false);
        });
    }

    /** Updates TextDisplay text in-place without re-spawning entities. */
    public void updateHoloText(FloatingHead head, int lineIndex, String miniText) {
        List<TextDisplay> displays = head.getHoloDisplays();
        if (lineIndex < 0 || lineIndex >= displays.size()) return;
        TextDisplay td = displays.get(lineIndex);
        if (!td.isDead()) {
            td.text(MM.deserialize(miniText));
        }
    }

    private void repositionHolos(FloatingHead head, Location newBase) {
        List<TextDisplay> displays = head.getHoloDisplays();
        for (int i = 0; i < displays.size(); i++) {
            TextDisplay td = displays.get(i);
            if (!td.isDead()) {
                td.teleport(newBase.clone().add(0, HOLO_BASE_OFFSET + i * LINE_GAP, 0));
            }
        }
    }
}
