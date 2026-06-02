package nl.jdries.phantomheads.listener;

import net.kyori.adventure.text.minimessage.MiniMessage;
import nl.jdries.phantomheads.PhantomHeadsPlugin;
import nl.jdries.phantomheads.manager.HeadManager;
import nl.jdries.phantomheads.model.FloatingHead;
import nl.jdries.phantomheads.particle.ParticleEngine;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles clicks on floating head ArmorStand entities.
 * Right-click → PlayerInteractAtEntityEvent
 * Left-click  → EntityDamageByEntityEvent (cancelled; action still executes)
 */
public class HeadClickListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PhantomHeadsPlugin plugin;
    private final HeadManager manager;

    public HeadClickListener(PhantomHeadsPlugin plugin, HeadManager manager) {
        this.plugin  = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onRightClick(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand)) return;
        FloatingHead head = findHead(event.getRightClicked());
        if (head == null) return;
        event.setCancelled(true);
        handleClick(event.getPlayer(), head);
    }

    @EventHandler
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof ArmorStand)) return;
        FloatingHead head = findHead(event.getEntity());
        if (head == null) return;
        event.setCancelled(true);
        handleClick(player, head);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.onPlayerQuit(event.getPlayer());
    }

    // -------------------------------------------------------------------------

    private FloatingHead findHead(Entity entity) {
        for (FloatingHead head : manager.getAllHeads()) {
            if (!head.isEnabled()) continue;
            if (entity.equals(head.getSkullStand())) return head;
            if (head.getPersonalStands().containsValue(entity)) return head;
        }
        return null;
    }

    private void handleClick(Player player, FloatingHead head) {
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

        for (String cmd : head.getPlayerCommands()) {
            Bukkit.dispatchCommand(player, replacePlaceholders(cmd, player));
        }
        for (String cmd : head.getConsoleCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replacePlaceholders(cmd, player));
        }
        for (String msg : head.getMessages()) {
            player.sendMessage(MM.deserialize(replacePlaceholders(msg, player)));
        }
    }

    private String replacePlaceholders(String input, Player player) {
        String result = input.replace("%player_name%", player.getName());
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            result = HeadManager.applyPapi(player, result);
        }
        return result;
    }
}
