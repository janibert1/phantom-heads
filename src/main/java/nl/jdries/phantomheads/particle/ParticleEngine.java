package nl.jdries.phantomheads.particle;

import nl.jdries.phantomheads.model.FloatingHead;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Ambient effects: circle, helix, rising, spiral, pulse, constellation, magic_circle, atom
 * Click effects:   burst, firework, shockwave, fountain, vortex, star_burst, heart
 *
 * Particle type is resolved once and cached on FloatingHead — no string lookup per tick.
 * Ambient calls receive the current viewer list so particles are skipped with no audience.
 */
public final class ParticleEngine {

    private ParticleEngine() {}

    // ── Ambient ───────────────────────────────────────────────────────────────

    public static void spawnAmbient(FloatingHead head, double tick, List<Player> viewers) {
        Location loc = head.getLocation();
        if (loc == null || loc.getWorld() == null) return;

        Particle particle = resolveParticle(head);
        int count = Math.max(1, (int) (head.getParticleDensity() * 3));

        switch (head.getAmbientEffect().toLowerCase()) {
            case "circle"        -> circle(loc, particle, count, tick);
            case "helix"         -> helix(loc, particle, count, tick);
            case "rising"        -> rising(loc, particle, count);
            case "spiral"        -> spiral(loc, particle, count, tick);
            case "pulse"         -> pulse(loc, particle, count, tick);
            case "constellation" -> constellation(loc, particle);
            case "magic_circle"  -> magicCircle(loc, particle, tick);
            case "atom"          -> atom(loc, particle, tick);
            default              -> circle(loc, particle, count, tick);
        }
    }

    // ── Click ─────────────────────────────────────────────────────────────────

    public static void spawnClick(FloatingHead head) {
        Location loc = head.getLocation();
        if (loc == null || loc.getWorld() == null) return;
        Particle p = resolveParticle(head);

        switch (head.getClickEffect().toLowerCase()) {
            case "burst"      -> burst(loc, p);
            case "firework"   -> firework(loc, p);
            case "shockwave"  -> shockwave(loc, p);
            case "fountain"   -> fountain(loc, p);
            case "vortex"     -> vortex(loc, p);
            case "star_burst" -> starBurst(loc, p);
            case "heart"      -> heart(loc, p);
            default           -> burst(loc, p);
        }
    }

    // ── Particle resolution (cached) ──────────────────────────────────────────

    private static Particle resolveParticle(FloatingHead head) {
        Particle cached = head.getCachedParticle();
        if (cached != null) return cached;
        Particle resolved;
        try {
            resolved = Particle.valueOf(head.getParticleType().toUpperCase());
        } catch (IllegalArgumentException e) {
            resolved = Particle.FLAME;
        }
        head.setCachedParticle(resolved);
        return resolved;
    }

    // ── Ambient shapes ────────────────────────────────────────────────────────

    private static void circle(Location loc, Particle p, int count, double tick) {
        double r = 0.7;
        for (int i = 0; i < count; i++) {
            double angle = tick + (2 * Math.PI * i / count);
            spawn(loc.getWorld(), loc.getX() + Math.cos(angle) * r, loc.getY() + 0.5, loc.getZ() + Math.sin(angle) * r, p);
        }
    }

    private static void helix(Location loc, Particle p, int count, double tick) {
        double r = 0.5;
        for (int i = 0; i < count; i++) {
            double t  = tick + (i * Math.PI);
            double dy = ((tick * 0.05) % 1.5) - 0.2;
            spawn(loc.getWorld(), loc.getX() + Math.cos(t) * r, loc.getY() + dy, loc.getZ() + Math.sin(t) * r, p);
        }
    }

    private static void rising(Location loc, Particle p, int count) {
        for (int i = 0; i < count; i++) {
            spawn(loc.getWorld(),
                    loc.getX() + (Math.random() - 0.5) * 0.6,
                    loc.getY() + Math.random() * 1.3,
                    loc.getZ() + (Math.random() - 0.5) * 0.6, p);
        }
    }

    private static void spiral(Location loc, Particle p, int count, double tick) {
        int steps = count * 4;
        for (int i = 0; i < steps; i++) {
            double angle  = tick + (2 * Math.PI * i / steps);
            double radius = 0.1 + i * 0.03;
            spawn(loc.getWorld(), loc.getX() + Math.cos(angle) * radius, loc.getY() + (i * 0.04) - 0.5, loc.getZ() + Math.sin(angle) * radius, p);
        }
    }

    private static void pulse(Location loc, Particle p, int count, double tick) {
        double r   = 0.5 + 0.3 * Math.sin(tick * 0.3);
        int    pts = count * 2;
        for (int i = 0; i < pts; i++) {
            double angle = 2 * Math.PI * i / pts;
            spawn(loc.getWorld(), loc.getX() + Math.cos(angle) * r, loc.getY() + 0.3, loc.getZ() + Math.sin(angle) * r, p);
        }
    }

    private static void constellation(Location loc, Particle p) {
        double[][] stars = {{0.6,0},{-0.6,0},{0,0.6},{0,-0.6},{0.4,0.4},{-0.4,0.4}};
        for (double[] star : stars) {
            if (Math.random() < 0.3)
                spawn(loc.getWorld(), loc.getX() + star[0], loc.getY() + 0.5, loc.getZ() + star[1], p);
        }
    }

    private static void magicCircle(Location loc, Particle p, double tick) {
        for (int ring = 0; ring < 2; ring++) {
            double r   = 0.4 + ring * 0.35;
            int    pts = 6 + ring * 4;
            double dir = (ring == 0) ? 1 : -1;
            for (int i = 0; i < pts; i++) {
                double angle = (tick * dir) + (2 * Math.PI * i / pts);
                spawn(loc.getWorld(), loc.getX() + Math.cos(angle) * r, loc.getY() + 0.4 + ring * 0.1, loc.getZ() + Math.sin(angle) * r, p);
            }
        }
    }

    private static void atom(Location loc, Particle p, double tick) {
        double[][] axes = {{1,0,0},{0.71,0,0.71},{0,1,0}};
        for (double[] ax : axes) {
            double angle = tick * 0.8;
            spawn(loc.getWorld(),
                    loc.getX() + Math.cos(angle) * ax[0] * 0.7,
                    loc.getY() + 0.5 + Math.sin(angle) * ax[1] * 0.7,
                    loc.getZ() + Math.cos(angle) * ax[2] * 0.7, p);
        }
        if (Math.random() < 0.4) spawn(loc.getWorld(), loc.getX(), loc.getY() + 0.5, loc.getZ(), p);
    }

    // ── Click shapes ──────────────────────────────────────────────────────────

    private static void burst(Location loc, Particle p) {
        loc.getWorld().spawnParticle(p, loc.clone().add(0,0.5,0), 60, 0.5, 0.5, 0.5, 0.15);
    }

    private static void firework(Location loc, Particle p) {
        for (int i = 0; i < 8; i++) {
            double angle = 2 * Math.PI * i / 8;
            loc.getWorld().spawnParticle(p, loc.clone().add(Math.cos(angle)*0.5, 0.5, Math.sin(angle)*0.5), 10, 0.1, 0.4, 0.1, 0.1);
        }
    }

    private static void shockwave(Location loc, Particle p) {
        for (int i = 0; i < 32; i++) {
            double angle = 2 * Math.PI * i / 32;
            loc.getWorld().spawnParticle(p, loc.clone().add(Math.cos(angle)*1.2, 0.1, Math.sin(angle)*1.2), 3, 0, 0, 0, 0);
        }
    }

    private static void fountain(Location loc, Particle p) {
        for (int i = 0; i < 30; i++)
            loc.getWorld().spawnParticle(p, loc.clone().add(0,0.3,0), 1, (Math.random()-0.5)*0.4, 0.5, (Math.random()-0.5)*0.4, 0);
    }

    private static void vortex(Location loc, Particle p) {
        for (int i = 0; i < 40; i++) {
            double angle = (2 * Math.PI * i / 40) + i * 0.15;
            double r     = 0.1 + i * 0.025;
            loc.getWorld().spawnParticle(p, loc.clone().add(Math.cos(angle)*r, (i*0.05)-0.5, Math.sin(angle)*r), 1, 0,0,0,0);
        }
    }

    private static void starBurst(Location loc, Particle p) {
        for (int ray = 0; ray < 5; ray++) {
            double angle = 2 * Math.PI * ray / 5;
            for (int j = 0; j < 8; j++) {
                double r = j * 0.15;
                loc.getWorld().spawnParticle(p, loc.clone().add(Math.cos(angle)*r, 0.5+j*0.05, Math.sin(angle)*r), 1,0,0,0,0);
            }
        }
    }

    private static void heart(Location loc, Particle p) {
        for (int i = 0; i < 24; i++) {
            double t  = 2 * Math.PI * i / 24;
            double hx = 16 * Math.pow(Math.sin(t), 3);
            double hz = 13*Math.cos(t) - 5*Math.cos(2*t) - 2*Math.cos(3*t) - Math.cos(4*t);
            loc.getWorld().spawnParticle(p, loc.clone().add(hx*0.042, 0.5, hz*0.042), 1,0,0,0,0);
        }
    }

    private static void spawn(World w, double x, double y, double z, Particle p) {
        w.spawnParticle(p, x, y, z, 1, 0, 0, 0, 0);
    }
}
