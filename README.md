# PhantomHeads

A Paper plugin that places animated floating skull NPCs in your world. Each head displays a hologram, emits particles, and can run commands or send messages when clicked.

## Requirements

- Paper **1.21.11** or **26.x** (MC 26.1+)
- Java 21+
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) *(optional — enables `%placeholder%` in hologram lines)*

## Installation

1. Download `PhantomHeads-<version>.jar` from the [Releases](../../releases) page.
2. Drop it into your server's `plugins/` folder.
3. Restart the server.

A `plugins/PhantomHeads/config.yml` is created on first run with defaults you can adjust.

## Commands

All commands require the `phantomheads.admin` permission (default: op).

### Head management

| Command | Description |
|---|---|
| `/ph create <id> [texture]` | Spawn a head at your location. `texture` is a Base64 skin string, or `%player_name%` to mirror each viewer's own skin. |
| `/ph delete <id>` | Permanently delete a head. |
| `/ph clone <id> <new_id>` | Duplicate a head at your location, copying all settings. |
| `/ph info <id>` | Show all properties of a head. |
| `/ph list` | List all heads with their locations and status. |
| `/ph near [radius]` | List heads within `radius` blocks of you (default: 32). |
| `/ph reload` | Reload config and all heads from disk. |

### Visibility & position

| Command | Description |
|---|---|
| `/ph enable <id>` | Make the head visible to players in range. |
| `/ph disable <id>` | Hide the head from all players. |
| `/ph toggle <id>` | Toggle enabled/disabled. |
| `/ph hologram <enable\|disable> <id>` | Show or hide the hologram lines without affecting the skull. |
| `/ph movehere <id>` | Move the head to your current position. |
| `/ph teleport <id>` | Teleport yourself to the head. |

### Hologram lines

Lines support MiniMessage formatting (e.g. `<red>Hello</red>`, `<gradient:#ff0000:#0000ff>Text</gradient>`). If PlaceholderAPI is installed, `%placeholder%` tags are resolved every 2 seconds.

| Command | Description |
|---|---|
| `/ph line add <id> <text>` | Append a new hologram line. |
| `/ph line set <id> <index> <text>` | Replace an existing line (0-indexed). |
| `/ph line remove <id> <index>` | Remove a line. |
| `/ph line swap <id> <i> <j>` | Swap two lines. |
| `/ph line list <id>` | Show all lines with their indices. |

### Click actions

Actions run when a player clicks the head (right-click / left-click). Placeholders: `{player}` is replaced with the clicking player's name.

| Command | Description |
|---|---|
| `/ph action <id> add player <cmd>` | Run `/<cmd>` as the clicking player. |
| `/ph action <id> add console <cmd>` | Run `/<cmd>` from the console. |
| `/ph action <id> add message <text>` | Send a message to the clicking player (MiniMessage). |
| `/ph action <id> remove <type> <index>` | Remove an action at the given index. |
| `/ph action <id> list <type>` | List actions of the given type. |

## Permissions

| Permission | Description | Default |
|---|---|---|
| `phantomheads.admin` | Full access to all `/ph` subcommands | op |
| `phantomheads.use` | Ability to click and interact with heads | everyone |

## Configuration

`plugins/PhantomHeads/config.yml`

```yaml
# Distance (blocks) at which heads become visible to a player
view-range: 48.0

# How often the animation position is updated (ticks, lower = smoother)
animation-tick-rate: 1

# How often the visibility range is checked (ticks)
range-check-interval: 20

# How often hologram lines are refreshed via PlaceholderAPI (ticks)
hologram-refresh-interval: 40

defaults:
  animation: FLOATING        # FLOATING or SLERPING
  speed-multiplier: 1.0
  particle-type: FLAME       # any Bukkit Particle enum name
  particle-density: 1.0      # multiplier applied to particle count
  ambient-effect: circle     # see Ambient Effects below
  click-effect: burst        # see Click Effects below
  cooldown: 3                # seconds between clicks per player
```

## Animations

| Style | Description |
|---|---|
| `FLOATING` | Smooth sinusoidal up-down bob with continuous yaw rotation. |
| `SLERPING` | Slow 180° side-to-side oscillation with subtle vertical bob. |

`speed-multiplier` scales how fast the animation runs.

## Particle Effects

`particle-type` accepts any value from the [Bukkit Particle enum](https://jd.papermc.io/paper/1.21.1/org/bukkit/Particle.html) (e.g. `FLAME`, `HEART`, `END_ROD`, `CRIT`, `ENCHANT`).

### Ambient effects (continuous)

| Name | Description |
|---|---|
| `circle` | Rotating ring of particles around the head. |
| `helix` | Two-strand helix that rises and loops. |
| `rising` | Random particles drifting upward. |
| `spiral` | Expanding inward spiral cone. |
| `pulse` | Ring that breathes in and out. |
| `constellation` | Sparse random star cluster. |
| `magic_circle` | Two counter-rotating concentric rings. |
| `atom` | Three orbital paths on perpendicular planes. |

### Click effects (one-shot on interact)

| Name | Description |
|---|---|
| `burst` | Sphere explosion of particles. |
| `firework` | Eight radial jets. |
| `shockwave` | Flat ring expanding outward. |
| `fountain` | Upward fountain spray. |
| `vortex` | Rising vortex spiral. |
| `star_burst` | Five-pointed star pattern. |
| `heart` | Heart-shaped outline. |

## Per-head data

Each head is stored as `plugins/PhantomHeads/heads/<id>.yml`. You can edit these directly and run `/ph reload`. Fields:

```yaml
world: world
x: 0.0
y: 64.0
z: 0.0
yaw: 0.0
texture: null               # Base64 skin string, or "%player_name%"
enabled: true
hologram-enabled: true
lines:
  - "<yellow>Hello!"
animation: FLOATING
speed-multiplier: 1.0
particle-type: FLAME
particle-density: 1.0
ambient-effect: circle
click-effect: burst
player-commands: []
console-commands: []
messages: []
cooldown: 3
```

## Building from source

Requires Java 21 and Maven.

```bash
git clone https://github.com/janibert1/phantom-heads.git
cd phantom-heads
mvn package
# Output: target/PhantomHeads-<version>.jar
```
