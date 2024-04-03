package de.t14d3.redisentitybridge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class CommandHandler implements CommandExecutor {

    private final RedisEntityBridge plugin;

    public CommandHandler(RedisEntityBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /redisentitybridge <send|test|reload|arrival>");
            return false;
        }
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "send":
                return handleSendCommand(sender, args);
            case "test":
                return handleTestCommand(sender);
            case "reload":
                return handleReloadCommand(sender);
            case "arrival":
                return handleArrivalCommand(sender);
            default:
                sender.sendMessage("Invalid sub-command. Usage: /redisentitybridge <send|test|reload|arrival>");
                return false;
        }
    }
    public void registerAliases(Command command) {
        command.setAliases(Arrays.asList("reb"));

    }


    private boolean handleSendCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("redisentitybridge.send")) {
            sender.sendMessage("You don't have permission to use this command.");
            return false;
        }

        if (args.length != 5) {
            sender.sendMessage("Usage: /redisentitybridge send <X> <Y> <Z> <TargetServer>");
            return false;
        }

        double x = Double.parseDouble(args[1]);
        double y = Double.parseDouble(args[2]);
        double z = Double.parseDouble(args[3]);
        String targetServer = args[4];

        Location location = new Location(Bukkit.getWorlds().get(0), x, y, z);
        for (Entity entity : location.getWorld().getEntities()) {
            if (entity instanceof Player) continue; // Skip players
            if (entity.getLocation().distance(location) <= 3) {
                plugin.sendEntityData(entity, targetServer);
            }
        }
        return true;
    }

    private boolean handleTestCommand(CommandSender sender) {
        if (!sender.hasPermission("redisentitybridge.test")) {
            sender.sendMessage("You don't have permission to use this command.");
            return false;
        }

        if (plugin.testRedisConnection()) {
            sender.sendMessage("Connected to Redis successfully.");
        } else {
            sender.sendMessage("Failed to connect to Redis. Check your Redis configuration.");
        }
        return true;
    }

    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("redisentitybridge.reload")) {
            sender.sendMessage("You don't have permission to use this command.");
            return false;
        }

        plugin.reloadConfig();
        plugin.loadConfigurations();
        sender.sendMessage("Configurations reloaded.");
        return true;
    }

    private boolean handleArrivalCommand(CommandSender sender) {
        if (!sender.hasPermission("redisentitybridge.arrival")) {
            sender.sendMessage("You don't have permission to use this command.");
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return false;
        }

        Player player = (Player) sender;
        plugin.getConfig().set("arrivalPosition.x", player.getLocation().getX());
        plugin.getConfig().set("arrivalPosition.y", player.getLocation().getY());
        plugin.getConfig().set("arrivalPosition.z", player.getLocation().getZ());
        plugin.getConfig().set("arrivalPosition.world", player.getWorld().getName());
        plugin.saveConfig();
        plugin.reloadConfig();

        sender.sendMessage("Arrival position set to your current location.");

        return true;
    }
}
