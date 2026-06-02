package nl.jdries.phantomheads;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import nl.jdries.phantomheads.command.PHCommand;
import nl.jdries.phantomheads.listener.PacketClickListener;
import nl.jdries.phantomheads.listener.PlayerQuitListener;
import nl.jdries.phantomheads.manager.HeadManager;
import nl.jdries.phantomheads.renderer.PacketRenderer;
import org.bukkit.plugin.java.JavaPlugin;

public class PhantomHeadsPlugin extends JavaPlugin {

    private HeadManager headManager;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .reEncodeByDefault(false)
                .checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        PacketEvents.getAPI().init();

        saveDefaultConfig();

        PacketRenderer renderer = new PacketRenderer(this);
        headManager = new HeadManager(this, renderer);
        headManager.loadAll();
        headManager.spawnAll();
        headManager.startTasks();

        PacketEvents.getAPI().getEventManager()
                .registerListener(new PacketClickListener(headManager));
        getServer().getPluginManager()
                .registerEvents(new PlayerQuitListener(headManager), this);

        PHCommand phCmd = new PHCommand(headManager);
        var cmd = getCommand("ph");
        cmd.setExecutor(phCmd);
        cmd.setTabCompleter(phCmd);

        getLogger().info("PhantomHeads enabled — " + headManager.getAllHeads().size() + " heads loaded.");
    }

    @Override
    public void onDisable() {
        if (headManager != null) headManager.shutdown();
        PacketEvents.getAPI().terminate();
        getLogger().info("PhantomHeads disabled.");
    }

    public HeadManager getHeadManager() { return headManager; }
}
