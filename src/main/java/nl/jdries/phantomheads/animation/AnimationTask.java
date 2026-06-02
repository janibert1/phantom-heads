package nl.jdries.phantomheads.animation;

import nl.jdries.phantomheads.manager.HeadManager;
import nl.jdries.phantomheads.model.FloatingHead;
import nl.jdries.phantomheads.particle.ParticleEngine;
import nl.jdries.phantomheads.renderer.PacketRenderer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

public class AnimationTask extends BukkitRunnable {

    private static final double BOB_AMPLITUDE = 0.08;
    private static final double BOB_SPEED     = 0.05;
    private static final double FLOAT_ROT_DEG = 3.5;

    private static final double SLERP_ROT_AMP = 90.0;
    private static final double SLERP_SPEED   = 0.04;
    private static final double SLERP_BOB_AMP = 0.04;

    private final HeadManager manager;

    public AnimationTask(HeadManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        for (FloatingHead head : manager.getAllHeads()) {
            if (!head.isEnabled() || head.getLocation() == null) continue;

            double tick  = head.getAnimTick();
            double speed = head.getSpeedMultiplier();
            head.setAnimTick(tick + 1);

            List<Player> viewers = manager.getViewers(head);
            // No viewers — advance the tick counter but skip all location/packet work
            if (viewers.isEmpty()) continue;

            switch (head.getAnimationStyle().toUpperCase()) {
                case "SLERPING" -> animateSlerp(head, tick, speed, viewers);
                default         -> animateFloat(head, tick, speed, viewers);
            }

            if (((long) tick & 3) == 0) {
                ParticleEngine.spawnAmbient(head, tick * speed * 0.1, viewers);
            }
        }
    }

    private void animateFloat(FloatingHead head, double tick, double speed, List<Player> viewers) {
        Location base = head.getLocation();
        base.setY(head.getBaseY() + Math.sin(tick * BOB_SPEED * speed) * BOB_AMPLITUDE);
        base.setYaw((float) ((head.getYaw() + tick * FLOAT_ROT_DEG * speed) % 360.0));
        manager.getRenderer().teleport(head, base, viewers);
    }

    private void animateSlerp(FloatingHead head, double tick, double speed, List<Player> viewers) {
        double phase = tick * SLERP_SPEED * speed;
        Location base = head.getLocation();
        base.setY(head.getBaseY() + Math.sin(phase * 0.5) * SLERP_BOB_AMP);
        base.setYaw((float) (head.getYaw() + SLERP_ROT_AMP * Math.sin(phase)));
        manager.getRenderer().teleport(head, base, viewers);
    }
}
