package nl.jdries.phantomheads.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

public final class TextureUtil {

    private TextureUtil() {}

    /**
     * Creates a PLAYER_HEAD ItemStack with the given Base64 texture applied.
     * Returns a plain Steve head if texture is null, empty, or %player_name%.
     */
    public static ItemStack skullFromBase64(String base64) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        if (base64 == null || base64.isBlank() || "%player_name%".equalsIgnoreCase(base64)) {
            return skull;
        }
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "PH_" + Integer.toHexString(base64.hashCode()));
            profile.getProperties().add(new ProfileProperty("textures", base64));
            meta.setOwnerProfile(profile);
            skull.setItemMeta(meta);
        } catch (Exception e) {
            // Return unmodified skull — better than crashing
        }
        return skull;
    }

    /**
     * Creates a PLAYER_HEAD using a player's live skin.
     * Falls back to a plain skull if the profile has no textures.
     */
    public static ItemStack skullFromPlayer(Player player) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;
        try {
            PlayerProfile profile = player.getPlayerProfile();
            // complete() is non-blocking for online players — their skin is already cached
            if (!profile.hasTextures()) {
                profile.complete(false);
            }
            meta.setOwnerProfile(profile);
            skull.setItemMeta(meta);
        } catch (Exception ignored) {}
        return skull;
    }
}
