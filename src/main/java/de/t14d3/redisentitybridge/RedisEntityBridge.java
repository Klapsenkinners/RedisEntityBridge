package de.t14d3.redisentitybridge;

import de.tr7zw.nbtapi.NBTContainer;
import de.tr7zw.nbtapi.NBTEntity;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RedisEntityBridge extends JavaPlugin {

    private String redisHost;
    private int redisPort;
    private String redisPassword;
    private String serverName;
    private String targetName;
    private Location portalAreaPos1;
    private Location portalAreaPos2;
    private Location arrivalLocation;

    @Override
    public void onEnable() {
        loadConfigurations();
        registerEvents();
        registerCommand();
        startListeningForNBTData();
        getLogger().info("Plugin enabled.");
    }

    public void loadConfigurations() {
        saveDefaultConfig();
        redisHost = getConfig().getString("redis.host");
        redisPort = Integer.parseInt(getConfig().getString("redis.port"));
        redisPassword = getConfig().getString("redis.password");
        serverName = getConfig().getString("localName");
        targetName = getConfig().getString("remoteName");
        portalAreaPos1 = loadLocationFromConfig("portalArea.x1", "portalArea.y1", "portalArea.z1", "portalArea.world");
        portalAreaPos2 = loadLocationFromConfig("portalArea.x2", "portalArea.y2", "portalArea.z2", "portalArea.world");
        arrivalLocation = loadLocationFromConfig("arrivalPosition.x", "arrivalPosition.y", "arrivalPosition.z", "arrivalPosition.world");
    }

    private Location loadLocationFromConfig(String xPath, String yPath, String zPath, String worldPath) {
        return new Location(
                Bukkit.getWorld(getConfig().getString(worldPath)),
                getConfig().getDouble(xPath),
                getConfig().getDouble(yPath),
                getConfig().getDouble(zPath)
        );
    }


    private void registerEvents() {
        getServer().getPluginManager().registerEvents(new PortalListener(this), this);
    }

    private void registerCommand() {
        getCommand("redisentitybridge").setExecutor(new CommandHandler(this));
        getCommand("redisentitybridge").setTabCompleter(new SendEntityTabCompleter());

        Command cmd = getCommand("redisentitybridge");
        if (cmd != null) {
            cmd.setAliases(Arrays.asList("reb")); // Set alias
            new CommandHandler(this).registerAliases(cmd); // Register tab completions
        }
    }




    public void sendEntityData(Entity entity, String targetServer) {
        NBTEntity nbtEntity = new NBTEntity(entity);
        String nbtData = nbtEntity.toString();
        nbtData = stripLocationData(nbtData);
        String dataToSend = targetServer + "|" + entity.getType().name() + "|" + nbtData;
        sendNBTDataToRedis(dataToSend);
        entity.remove();
    }
    public void sendEntityData(Entity entity) {
        sendEntityData(entity, targetName);
    }

    boolean testRedisConnection() {
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            if (redisPassword != null && !redisPassword.isEmpty()) {
                jedis.auth(redisPassword);
            }
            return jedis.ping().equalsIgnoreCase("PONG");
        } catch (Exception e) {
            getLogger().warning("Error testing Redis connection: " + e.getMessage());
            return false;
        }
    }

    private String stripLocationData(String nbtData) {
        nbtData = nbtData.replaceAll(",Pos:\\[.+d,.+d,.+d\\]", "");
        nbtData = nbtData.replaceAll(",Rotation:\\[.+f,.+f\\]", "");
        nbtData = nbtData.replaceAll(",WorldUUIDLeast:.\\d+L", "");
        nbtData = nbtData.replaceAll(",WorldUUIDMost:.\\d+L", "");
        nbtData = nbtData.replaceAll(",UUID:\\[I;.\\d+,.\\d+,.\\d+,.\\d+\\]", "");
        return nbtData;
    }

    private void sendNBTDataToRedis(String nbtData) {
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            if (redisPassword != null && !redisPassword.isEmpty()) {
                jedis.auth(redisPassword);
            }
            jedis.publish("entity_nbt", nbtData);
        } catch (Exception e) {
            getLogger().warning("Error sending NBT data to Redis: " + e.getMessage());
        }
    }

    private void startListeningForNBTData() {
        new Thread(() -> {
            try (Jedis jedis = new Jedis(redisHost, redisPort)) {
                if (redisPassword != null && !redisPassword.isEmpty()) {
                    jedis.auth(redisPassword);
                }
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        handleNBTDataMessage(message);
                    }
                }, "entity_nbt");
            } catch (Exception e) {
                getLogger().warning("Error subscribing to Redis channel: " + e.getMessage());
            }
        }).start();
    }

    private void handleNBTDataMessage(String message) {
        int firstPipeIndex = message.indexOf('|');
        if (firstPipeIndex >= 0) {
            String serverNamePart = message.substring(0, firstPipeIndex);
            if (serverNamePart.equals(serverName)) {
                processNBTData(message.substring(firstPipeIndex + 1));
            }
        }
    }

    private void processNBTData(String data) {
        int secondPipeIndex = data.indexOf('|');
        if (secondPipeIndex >= 0) {
            String entityTypeString = data.substring(0, secondPipeIndex);
            String nbtData = data.substring(secondPipeIndex + 1);
            EntityType entityType = EntityType.valueOf(entityTypeString);
            spawnEntityWithNBTData(entityType, nbtData);
        } else {
            getLogger().warning("Invalid message format. Expected at least 2 parts separated by '|'.");
        }
    }

    private void spawnEntityWithNBTData(EntityType entityType, String nbtData) {
        Bukkit.getScheduler().runTask(this, () -> {
            Entity entity = arrivalLocation.getWorld().spawnEntity(arrivalLocation, entityType);
            applyNBTDataToEntity(entity, nbtData);
        });
    }

    private void applyNBTDataToEntity(Entity entity, String nbtData) {
        NBTEntity nbtEntity = new NBTEntity(entity);
        NBTContainer nbtContainer = new NBTContainer(nbtData);
        nbtEntity.mergeCompound(nbtContainer);
    }





    public Location getPortalAreaPos1() {
        return portalAreaPos1;
    }

    public Location getPortalAreaPos2() {
        return portalAreaPos2;
    }

    @Override
    public void onDisable() {
        // Close the Redis connection
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            if (redisPassword != null && !redisPassword.isEmpty()) {
                jedis.auth(redisPassword);
            }
            jedis.close();
        } catch (Exception e) {
            getLogger().warning("Error closing Redis connection: " + e.getMessage());
        }
        getLogger().info("Plugin disabled.");
    }
    private static class SendEntityTabCompleter implements TabCompleter {

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            List<String> completions = new ArrayList<>();
            if (args.length == 1) {
                // Provide tab completions for the main commands
                completions.add("send");
                completions.add("test");
                completions.add("reload");
                completions.add("arrival");
            }
            return completions;
        }


    }
}
