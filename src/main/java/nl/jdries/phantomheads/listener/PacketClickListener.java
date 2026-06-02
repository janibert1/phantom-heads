package nl.jdries.phantomheads.listener;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import nl.jdries.phantomheads.manager.HeadManager;
import org.bukkit.entity.Player;

/**
 * Intercepts USE_ENTITY packets to detect clicks on fake packet-based head entities.
 * Real Bukkit events are never fired for packet-only entities, so this is the only way.
 */
public class PacketClickListener extends PacketListenerAbstract {

    private final HeadManager manager;

    public PacketClickListener(HeadManager manager) {
        super(PacketListenerPriority.NORMAL);
        this.manager = manager;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;

        WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);
        WrapperPlayClientInteractEntity.InteractAction action = packet.getAction();
        if (action != WrapperPlayClientInteractEntity.InteractAction.INTERACT
                && action != WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT
                && action != WrapperPlayClientInteractEntity.InteractAction.ATTACK) return;

        Player player = (Player) event.getPlayer();
        if (player == null) return;

        manager.handleEntityClick(player, packet.getEntityId());
    }
}
