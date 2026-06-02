package nl.jdries.phantomheads;

import nl.jdries.phantomheads.command.PHCommand;
import nl.jdries.phantomheads.listener.HeadClickListener;
import nl.jdries.phantomheads.manager.HeadManager;
import nl.jdries.phantomheads.renderer.EntityRenderer;
import org.bukkit.plugin.java.JavaPlugin;

public class PhantomHeadsPlugin extends JavaPlugin {

    private HeadManager headManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        EntityRenderer renderer = new EntityRenderer(this);
        headManager = new HeadManager(this, renderer);
        headManager.loadAll();
        headManager.spawnAll();
        headManager.startTasks();

        PHCommand phCmd = new PHCommand(headManager);
        var cmd = getCommand("ph");
        cmd.setExecutor(phCmd);
        cmd.setTabCompleter(phCmd);

        getServer().getPluginManager().registerEvents(new HeadClickListener(this, headManager), this);

        getLogger().info("PhantomHeads enabled — " + headManager.getAllHeads().size() + " heads loaded.");
    }

    @Override
    public void onDisable() {
        if (headManager != null) headManager.shutdown();
        getLogger().info("PhantomHeads disabled.");
    }

    public HeadManager getHeadManager() { return headManager; }
}
