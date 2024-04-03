package de.t14d3.redisentitybridge;

import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class PortalListener implements Listener {
    private final RedisEntityBridge plugin;

    public PortalListener(RedisEntityBridge plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityMove(EntityMoveEvent event) {
            if (isInPortalArea(event.getTo())) {
                Entity entity = event.getEntity();
                plugin.sendEntityData(entity);
            }
        }
    private boolean isInPortalArea(Location location) {
        Location pos1 = plugin.getPortalAreaPos1();
        Location pos2 = plugin.getPortalAreaPos2();

        double minX = Math.min(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double maxY = Math.max(pos1.getY(), pos2.getY());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());

        boolean inPortalArea = location.getWorld().equals(pos1.getWorld())
                && location.getX() >= minX && location.getX() <= maxX
                && location.getY() >= minY && location.getY() <= maxY
                && location.getZ() >= minZ && location.getZ() <= maxZ;

        return inPortalArea;
    }
}
