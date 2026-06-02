package nl.jdries.phantomheads.animation;

import nl.jdries.phantomheads.manager.HeadManager;
import nl.jdries.phantomheads.model.FloatingHead;
import nl.jdries.phantomheads.particle.ParticleEngine;
import nl.jdries.phantomheads.renderer.EntityRenderer;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Advances animation state for all active heads and spawns ambient particles.
 */
public class AnimationTask extends BukkitRunnable {

    // FLOATING constants
    private static final double BOB_AMPLITUDE  = 0.08;   // blocks
    private static final double BOB_SPEED      = 0.05;   // radians/tick
    private static final double FLOAT_ROT_DEG  = 3.5;    // degrees/tick

    // SLERPING constants — oscillates ±90° from base yaw (180° total arc)
    private static final double SLERP_ROT_AMP  = 90.0;   // degrees amplitude
    private static final double SLERP_SPEED    = 0.04;   // base radians/tick
    private static final double SLERP_BOB_AMP  = 0.04;   // subtle bob

    private final HeadManager manager;

    public AnimationTask(HeadManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        for (FloatingHead head : manager.getAllHeads()) {
            if (!head.isEnabled() || head.getLocation() == null) continue;

            double tick = head.getAnimTick();
            head.setAnimTick(tick + 1);

            double speed = head.getSpeedMultiplier();

            switch (head.getAnimationStyle().toUpperCase()) {
                case "SLERPING" -> animateSlerp(head, tick, speed);
                default         -> animateFloat(head, tick, speed);
            }

            // Particles every other tick for performance
            if (((long) tick & 1) == 0) {
                ParticleEngine.spawnAmbient(head, tick * speed * 0.1);
            }
        }
    }

    // -------------------------------------------------------------------------

    /**
     * FLOATING: sinusoidal up-down bob combined with constant yaw rotation.
     */
    private void animateFloat(FloatingHead head, double tick, double speed) {
        double newY  = head.getBaseY() + Math.sin(tick * BOB_SPEED * speed) * BOB_AMPLITUDE;
        float  newYaw = (float) ((head.getYaw() + tick * FLOAT_ROT_DEG * speed) % 360.0);

        Location base = head.getLocation();
        base.setY(newY);
        base.setYaw(newYaw);

        moveSkull(head, base);
        moveHolos(head, base);
    }

    /**
     * SLERPING: smooth side-to-side 180° oscillation with sinusoidal easing.
     * Uses sine for both the primary rotation and a subtle secondary bob.
     */
    private void animateSlerp(FloatingHead head, double tick, double speed) {
        double phase  = tick * SLERP_SPEED * speed;
        float  newYaw = (float) (head.getYaw() + SLERP_ROT_AMP * Math.sin(phase));
        double newY   = head.getBaseY() + Math.sin(phase * 0.5) * SLERP_BOB_AMP;

        Location base = head.getLocation();
        base.setY(newY);
        base.setYaw(newYaw);

        moveSkull(head, base);
        moveHolos(head, base);
    }

    private void moveSkull(FloatingHead head, Location loc) {
        ArmorStand skull = head.getSkullStand();
        if (skull != null && !skull.isDead()) skull.teleport(loc);
        for (ArmorStand personal : head.getPersonalStands().values()) {
            if (!personal.isDead()) personal.teleport(loc);
        }
    }

    private void moveHolos(FloatingHead head, Location base) {
        List<TextDisplay> displays = head.getHoloDisplays();
        for (int i = 0; i < displays.size(); i++) {
            TextDisplay td = displays.get(i);
            if (!td.isDead()) {
                td.teleport(base.clone().add(0, EntityRenderer.HOLO_BASE_OFFSET + i * EntityRenderer.LINE_GAP, 0));
            }
        }
    }
}
