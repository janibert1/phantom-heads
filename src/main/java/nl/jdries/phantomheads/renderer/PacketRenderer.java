package nl.jdries.phantomheads.renderer;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import nl.jdries.phantomheads.PhantomHeadsPlugin;
import nl.jdries.phantomheads.model.FloatingHead;
import nl.jdries.phantomheads.util.TextureUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Renders FloatingHeads using PacketEvents fake entities — no real Bukkit entities are spawned.
 * Skull: invisible ArmorStand + helmet equipment packet (per-player for dynamic textures).
 * Holo: invisible marker ArmorStand with custom nametag (one entity per line).
 */
public class PacketRenderer {

    public static final double LINE_GAP = 0.28;
    public static final double HOLO_BASE_OFFSET = 0.65;

    // ArmorStand metadata indices (MC 1.20+ / 26.x)
    private static final int META_FLAGS       = 0;   // Entity flags BYTE
    private static final int META_CUSTOM_NAME = 2;   // Optional<Component>
    private static final int META_NAME_VIS    = 3;   // BOOLEAN
    private static final int META_NO_GRAVITY  = 5;   // BOOLEAN
    private static final int META_AS_FLAGS    = 15;  // ArmorStand flags BYTE

    private static final byte FLAG_INVISIBLE  = 0x20; // Entity invisible bit
    private static final byte AS_NO_BASEPLATE = 0x08;
    private static final byte AS_MARKER       = 0x10; // no hitbox, used for holos

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Decrement from a high value to avoid colliding with real server entity IDs
    private static final AtomicInteger ID_GEN = new AtomicInteger(2_000_000_000);

    private final PhantomHeadsPlugin plugin;

    public PacketRenderer(PhantomHeadsPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Allocates entity IDs for a head. Does not send any packets.
     * HeadManager's visibility loop calls showTo() when players enter range.
     */
    public void allocateIds(FloatingHead head) {
        if (!head.isDynamicTexture()) {
            head.setSkullEntityId(nextId());
        }
        if (head.isHologramEnabled()) {
            allocateHoloIds(head);
        }
    }

    /** Sends all spawn packets for a head to a specific player. */
    public void showTo(FloatingHead head, Player player) {
        Location loc = head.getLocation();
        if (loc == null) return;

        if (head.isDynamicTexture()) {
            int id = head.getPersonalEntityIds().computeIfAbsent(
                    player.getUniqueId(), k -> nextId());
            sendSkullSpawn(player, id, loc, TextureUtil.skullFromPlayer(player));
        } else if (head.getSkullEntityId() != -1) {
            sendSkullSpawn(player, head.getSkullEntityId(), loc, head.getOrCreateSkullItem());
        }

        if (head.isHologramEnabled()) {
            List<Integer> holoIds  = head.getHoloEntityIds();
            List<String>  lines    = head.getLines();
            for (int i = 0; i < holoIds.size() && i < lines.size(); i++) {
                Location holoLoc = loc.clone().add(0, HOLO_BASE_OFFSET + i * LINE_GAP, 0);
                String resolved = head.getCachedResolvedLine(i);
                sendHoloSpawn(player, holoIds.get(i), holoLoc,
                        resolved != null ? resolved : lines.get(i));
            }
        }
    }

    /** Sends destroy packets to remove a head's entities from a specific player's view. */
    public void hideFrom(FloatingHead head, Player player) {
        List<Integer> ids = collectIds(head, player.getUniqueId());
        if (!ids.isEmpty()) sendDestroy(player, ids);
    }

    /** Sends destroy packets to all viewers and clears entity IDs from the head. */
    public void despawnAll(FloatingHead head, Iterable<Player> viewers) {
        List<Integer> allIds = new ArrayList<>();
        if (head.getSkullEntityId() != -1) allIds.add(head.getSkullEntityId());
        allIds.addAll(head.getHoloEntityIds());
        allIds.addAll(head.getPersonalEntityIds().values());

        if (!allIds.isEmpty()) {
            for (Player p : viewers) sendDestroy(p, allIds);
        }

        head.setSkullEntityId(-1);
        head.getHoloEntityIds().clear();
        head.getPersonalEntityIds().clear();
        head.clearResolvedLineCache();
    }

    /** Teleports all entities of a head to a new location for the given viewers. */
    public void teleport(FloatingHead head, Location loc, List<Player> viewers) {
        if (viewers.isEmpty()) return;

        Vector3d pos = toVec(loc);

        if (!head.isDynamicTexture() && head.getSkullEntityId() != -1) {
            PacketWrapper<?> tp = buildTeleport(head.getSkullEntityId(), pos, loc.getYaw());
            PacketWrapper<?> hl = new WrapperPlayServerEntityHeadLook(head.getSkullEntityId(), loc.getYaw());
            for (Player p : viewers) { send(p, tp); send(p, hl); }
        }

        for (Map.Entry<UUID, Integer> e : head.getPersonalEntityIds().entrySet()) {
            Player p = plugin.getServer().getPlayer(e.getKey());
            if (p == null) continue;
            PacketWrapper<?> tp = buildTeleport(e.getValue(), pos, loc.getYaw());
            PacketWrapper<?> hl = new WrapperPlayServerEntityHeadLook(e.getValue(), loc.getYaw());
            send(p, tp);
            send(p, hl);
        }

        List<Integer> holoIds = head.getHoloEntityIds();
        for (int i = 0; i < holoIds.size(); i++) {
            Vector3d holoPos = new Vector3d(loc.getX(), loc.getY() + HOLO_BASE_OFFSET + i * LINE_GAP, loc.getZ());
            PacketWrapper<?> tp = buildTeleport(holoIds.get(i), holoPos, 0f);
            for (Player p : viewers) send(p, tp);
        }
    }

    /**
     * Sends a metadata update for a single holo line.
     * Only called when the resolved text has actually changed (change-detection in HeadManager).
     */
    public void updateHoloText(FloatingHead head, int lineIndex, String miniText, Iterable<Player> viewers) {
        List<Integer> holoIds = head.getHoloEntityIds();
        if (lineIndex < 0 || lineIndex >= holoIds.size()) return;
        int entityId = holoIds.get(lineIndex);
        WrapperPlayServerEntityMetadata meta = buildNameMeta(entityId, miniText);
        for (Player p : viewers) send(p, meta);
    }

    /** Rebuilds holo entity IDs and re-shows them to current viewers after lines change. */
    public void refreshHolos(FloatingHead head, Iterable<Player> viewers) {
        List<Player> viewerList = new ArrayList<>();
        viewers.forEach(viewerList::add);

        // Destroy existing holo entities
        if (!head.getHoloEntityIds().isEmpty() && !viewerList.isEmpty()) {
            for (Player p : viewerList) sendDestroy(p, head.getHoloEntityIds());
        }
        head.getHoloEntityIds().clear();
        head.clearResolvedLineCache();

        if (!head.isHologramEnabled()) return;
        allocateHoloIds(head);

        Location loc = head.getLocation();
        if (loc == null) return;
        for (Player p : viewerList) {
            List<Integer> holoIds = head.getHoloEntityIds();
            List<String>  lines   = head.getLines();
            for (int i = 0; i < holoIds.size() && i < lines.size(); i++) {
                Location holoLoc = loc.clone().add(0, HOLO_BASE_OFFSET + i * LINE_GAP, 0);
                sendHoloSpawn(p, holoIds.get(i), holoLoc, lines.get(i));
            }
        }
    }

    /** Rebuilds the skull after a texture change. */
    public void refreshSkull(FloatingHead head, Iterable<Player> viewers) {
        if (head.isDynamicTexture()) return;
        List<Player> viewerList = new ArrayList<>();
        viewers.forEach(viewerList::add);

        int oldId = head.getSkullEntityId();
        int newId = nextId();
        head.setSkullEntityId(newId);

        Location loc = head.getLocation();
        ItemStack item = TextureUtil.skullFromBase64(head.getTexture());
        for (Player p : viewerList) {
            if (oldId != -1) sendDestroy(p, List.of(oldId));
            sendSkullSpawn(p, newId, loc, item);
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static int nextId() { return ID_GEN.decrementAndGet(); }

    private void allocateHoloIds(FloatingHead head) {
        for (int i = 0; i < head.getLines().size(); i++) {
            head.getHoloEntityIds().add(nextId());
        }
    }

    private List<Integer> collectIds(FloatingHead head, UUID playerUid) {
        List<Integer> ids = new ArrayList<>(head.getHoloEntityIds());
        if (head.isDynamicTexture()) {
            Integer pid = head.getPersonalEntityIds().remove(playerUid);
            if (pid != null) ids.add(pid);
        } else if (head.getSkullEntityId() != -1) {
            ids.add(head.getSkullEntityId());
        }
        return ids;
    }

    private void sendSkullSpawn(Player player, int entityId, Location loc, ItemStack headItem) {
        // Spawn invisible ArmorStand
        send(player, new WrapperPlayServerSpawnEntity(
                entityId, Optional.of(UUID.randomUUID()), EntityTypes.ARMOR_STAND,
                toVec(loc), 0f, loc.getYaw(), loc.getYaw(), 0, Optional.empty()));

        // Metadata: invisible, no gravity, no baseplate
        send(player, new WrapperPlayServerEntityMetadata(entityId, List.of(
                new EntityData(META_FLAGS,      EntityDataTypes.BYTE,    FLAG_INVISIBLE),
                new EntityData(META_NO_GRAVITY, EntityDataTypes.BOOLEAN, true),
                new EntityData(META_AS_FLAGS,   EntityDataTypes.BYTE,    AS_NO_BASEPLATE)
        )));

        // Equipment: skull as helmet
        send(player, new WrapperPlayServerEntityEquipment(entityId, List.of(
                new Equipment(EquipmentSlot.HELMET,
                        SpigotConversionUtil.fromBukkitItemStack(headItem))
        )));
    }

    private void sendHoloSpawn(Player player, int entityId, Location loc, String miniText) {
        // Spawn invisible marker ArmorStand at hologram position
        send(player, new WrapperPlayServerSpawnEntity(
                entityId, Optional.of(UUID.randomUUID()), EntityTypes.ARMOR_STAND,
                toVec(loc), 0f, 0f, 0f, 0, Optional.empty()));

        // Metadata: invisible, no gravity, custom nametag visible, marker (no hitbox)
        send(player, buildNameMeta(entityId, miniText));
    }

    private WrapperPlayServerEntityMetadata buildNameMeta(int entityId, String miniText) {
        return new WrapperPlayServerEntityMetadata(entityId, List.of(
                new EntityData(META_FLAGS,       EntityDataTypes.BYTE,              FLAG_INVISIBLE),
                new EntityData(META_CUSTOM_NAME, EntityDataTypes.OPTIONAL_COMPONENT,
                        Optional.of(MM.deserialize(miniText))),
                new EntityData(META_NAME_VIS,    EntityDataTypes.BOOLEAN,           true),
                new EntityData(META_NO_GRAVITY,  EntityDataTypes.BOOLEAN,           true),
                new EntityData(META_AS_FLAGS,    EntityDataTypes.BYTE,
                        (byte)(AS_NO_BASEPLATE | AS_MARKER))
        ));
    }

    private PacketWrapper<?> buildTeleport(int entityId, Vector3d pos, float yaw) {
        return new WrapperPlayServerEntityTeleport(entityId, pos, yaw, 0f, false);
    }

    private void sendDestroy(Player player, List<Integer> ids) {
        int[] arr = new int[ids.size()];
        for (int i = 0; i < ids.size(); i++) arr[i] = ids.get(i);
        send(player, new WrapperPlayServerDestroyEntities(arr));
    }

    private void send(Player player, PacketWrapper<?> packet) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    private static Vector3d toVec(Location loc) {
        return new Vector3d(loc.getX(), loc.getY(), loc.getZ());
    }
}
