package com.jesbus.jbchestlog;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class JBChestLog extends JavaPlugin
{
    JBChestLogEventListener listener;

    static File dataFolder;
    static Logger logger;

    public static void debugLog(String msg)
    {
        if (Constants.DEBUGGING) logger.info(msg);
    }

    public static void errorLog(String msg)
    {
        logger.info("[error, please submit bug report] "+msg);
    }

    @Override
    public void onEnable()
    {
        try { (dataFolder = getDataFolder()).mkdirs(); } catch (Exception e) { dataFolder = null; errorLog("Could not create data folder; JBChestLog plugin disabled. Error: "+e.getMessage()); return; }
        logger = getLogger();
        ContainerDiffLog.pluginStarting();
        listener = new JBChestLogEventListener();
        Bukkit.getScheduler().runTaskTimer(this, new Runnable(){
            @Override
            public void run()
            {
                ContainerDifferenceCheck.processAll();
                NewDiffTodo.tryProcessAll();
            }
        }, 1, 1);
        Bukkit.getScheduler().runTaskTimer(this, new Runnable(){
            @Override
            public void run()
            {
                ContainerDiffLogFile.tryFixAll();
                ContainerDiffLog.trySaveAll();
            }
        }, 20 * Constants.SECONDS_BETWEEN_SAVING_ALL_CHANGES_TO_DISK, 20 * Constants.SECONDS_BETWEEN_SAVING_ALL_CHANGES_TO_DISK);
        Bukkit.getScheduler().runTaskTimer(this, new Runnable(){
            @Override
            public void run()
            {
                ContainerDiffLog.saveAllRAM();
            }
        }, 20 * Constants.SECONDS_BEFORE_CONTAINER_EVICTED_FROM_CACHE, 20 * Constants.SECONDS_BEFORE_CONTAINER_EVICTED_FROM_CACHE);

        getServer().getPluginManager().registerEvents(listener, this);

        logger.info("JBChestLog has started");
    }

    @Override
    public void onDisable()
    {
        if (dataFolder == null) return;
        try
        {
            try { Bukkit.getScheduler().cancelTasks(this); } catch (Exception e) { logger.info("Error during shutdown in cancelTasks(), please submit bug report: "+e.getMessage()); e.printStackTrace(); }
            try { ContainerDifferenceCheck.processAll(); } catch (Exception e) { logger.info("Error during shutdown in processTodos(), please submit bug report: "+e.getMessage()); e.printStackTrace(); }
            try { HandlerList.unregisterAll(listener); } catch (Exception e) { logger.info("Error during shutdown in unregisterAll(), please submit bug report: "+e.getMessage()); e.printStackTrace(); }
            try { ContainerDiffLog.pluginShuttingDown(); } catch (Exception e) { logger.info("Error during shutdown in clearCache(), please submit a bug report: "+e.getMessage()); e.printStackTrace(); }
            logger.info("JBChestLog has shut down");
        }
        catch (Exception e)
        {
            listener = null;
            logger = null;
            getLogger().log(Level.WARNING, "Error during JBChestLog onDisable(). Please submit a bug report: "+e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static Map<Player, Long> playerToClickToClearCommandTimestamp = new HashMap<>();
    public static Map<CommandSender, Integer> playerToClearAllCommandCode = new HashMap<>();
    public static Random random = new Random();

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args)
    {
        if (cmd.getName().equalsIgnoreCase("jbcl") ||
            cmd.getName().equalsIgnoreCase("JBChestLog"))
        {
            if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("help")))
            {
                sender.sendMessage("/----------------------------------------------------");
                sender.sendMessage("| Usage of JBChestLog");
                sender.sendMessage("| "+ChatColor.LIGHT_PURPLE+"Left click a container with a stick"+ChatColor.WHITE+" to view its item history");
                sender.sendMessage("| "+ChatColor.LIGHT_PURPLE+"/"+cmd.getName()+" clear"+ChatColor.WHITE+" to clear the item history of a container");
                sender.sendMessage("| "+ChatColor.LIGHT_PURPLE+"/"+cmd.getName()+" clear all"+ChatColor.WHITE+" to clear the item history of all containers");
                sender.sendMessage("\\----------------------------------------------------");
                return true;
            }
            else if (args.length == 1)
            {
                if (args[0].equalsIgnoreCase("clear"))
                {
                    if (!(sender instanceof Player))
                    {
                        sender.sendMessage(ChatColor.RED + "Only in-game players can use /jbcl clear");
                        return true;
                    }
                    else if (sender.hasPermission(Constants.PERMISSION_CLEAR_OWN) || sender.hasPermission(Constants.PERMISSION_CLEAR_ANY))
                    {
                        sender.sendMessage(ChatColor.GREEN + "Left-click a container to delete its item history");
                        playerToClickToClearCommandTimestamp.put((Player)sender, new Date().getTime());
                        return true;
                    }
                    else
                    {
                        sender.sendMessage(ChatColor.RED + "You need permission JBChestLog.clear.own or JBChestLog.clear.any or op to clear a container's logs");
                        return true;
                    }
                }
                else
                {
                    sender.sendMessage(ChatColor.RED + "That command does not exist. Type "+ChatColor.LIGHT_PURPLE+"/jbcl help"+ChatColor.RED+" to RTFM");
                    return true;
                }
            }
            else if (args.length >= 2)
            {
                if (args[0].equalsIgnoreCase("clear") && args[1].equalsIgnoreCase("all"))
                {
                    if (sender.hasPermission(Constants.PERMISSION_CLEAR_ALL))
                    {
                        if (args.length == 2)
                        {
                            final int code = 1000 + random.nextInt(9000);
                            playerToClearAllCommandCode.put(sender, code);
                            sender.sendMessage(ChatColor.GREEN + "Are you sure you want to delete the item history logs of all containers on all maps on the entire server? If you are sure, type: "+ChatColor.DARK_RED+"/clear all "+code);
                            return true;
                        }
                        else if (args[2].equals(Integer.toString(playerToClearAllCommandCode.get(sender))))
                        {
                            playerToClearAllCommandCode.remove(sender);
                            ContainerDiffLog.deleteAllLogs();
                            sender.sendMessage(ChatColor.GREEN + "All container logs have been deleted");
                            return true;
                        }
                        else
                        {
                            if (playerToClearAllCommandCode.containsKey(sender)) playerToClearAllCommandCode.remove(sender);
                            sender.sendMessage(ChatColor.RED + "Invalid usage of command "+ChatColor.LIGHT_PURPLE+"/clear all");
                            return true;
                        }
                    }
                    else
                    {
                        if (playerToClearAllCommandCode.containsKey(sender)) playerToClearAllCommandCode.remove(sender);
                        sender.sendMessage(ChatColor.RED + "You don't have permission to delete all container logs");
                        return true;
                    }
                }
                else
                {
                    sender.sendMessage(ChatColor.RED + "That command does not exist. Type "+ChatColor.LIGHT_PURPLE+"/jbcl help"+ChatColor.RED+" to RTFM");
                    return true;
                }
            }
            else
            {
                sender.sendMessage(ChatColor.RED + "That command does not exist. Type "+ChatColor.LIGHT_PURPLE+"/jbcl help"+ChatColor.RED+" to RTFM");
                return true;
            }
        }
        else
        {
            return false;
        }
    }

}
