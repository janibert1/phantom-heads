package nl.jdries.phantomheads.listener;

import nl.jdries.phantomheads.manager.HeadManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final HeadManager manager;

    public PlayerQuitListener(HeadManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.onPlayerQuit(event.getPlayer());
    }
}
