package com.jesbus.jbchestlog;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

class ContainerDiffLogPrinter
{
    private static Map<CommandSender, Pair<Long, ContainerDiffLog, Integer>> playerToLastRequestedLog = new HashMap<>();
    
    public static void reportToIfAllowed(final ContainerDiffLog cdl, final CommandSender commandSender)
    {
        if (commandSender instanceof ConsoleCommandSender)
        {
            
        }
        else if (commandSender instanceof Player)
        {
			if (!commandSender.hasPermission(Constants.PERMISSION_VIEW_ANY))
			{
				if (!commandSender.hasPermission(Constants.PERMISSION_VIEW_OWN))
				{
                    commandSender.sendMessage(ChatColor.RED + "You don't have permission to view container history.");
                    return;
                }
				else
				{
					UUID placer = cdl.tryFindWhoPlacedThisContainer();
					if (placer == null)
					{
						commandSender.sendMessage(ChatColor.RED + "The server can't figure out who placed this container, so unfortunately you're not allowed to view its history.");
						return;
					}
					else if (!placer.equals(((Player)commandSender).getUniqueId()))
					{
						commandSender.sendMessage(ChatColor.RED + "You don't have permission to view this container's history because you didn't place it.");
						return;
					}
				}
			}
        }
        else
        {
            JBChestLog.logger.info("Unknown commandsender type: "+commandSender.getClass().getName());
            // TODO
        }

        reportTo(cdl, commandSender);
    }
    private static void reportTo(final ContainerDiffLog cdl, final CommandSender commandSender)
    {
        int theStart;
        int theAmount;
        {
            final long now = new Date().getTime();
            Pair<Long, ContainerDiffLog, Integer> plc = playerToLastRequestedLog.get(commandSender);
            if (plc == null || now - plc.first >= Constants.MAX_MS_BETWEEN_CLICKS_TO_SHOW_NEXT_LOG_PAGE)
            {
                theStart = 0;
                theAmount = 8;
                playerToLastRequestedLog.put(commandSender, new Pair<Long, ContainerDiffLog, Integer>(now, cdl, 0));
            }
            else
            {
                theStart = 8 + plc.third * 9;
                theAmount = 9;
                playerToLastRequestedLog.put(commandSender, new Pair<Long, ContainerDiffLog, Integer>(now, cdl, plc.third + 1));
            }

        }
        
        final int start = theStart;
        final int amount = theAmount;

        new Thread(new Runnable(){public void run(){
            if (start == 0)
            {
                commandSender.sendMessage("/---------------------------------------------------");
                commandSender.sendMessage("| History of container at "+cdl.x+","+cdl.y+","+cdl.z);
                commandSender.sendMessage("| - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -");
            }

            synchronized (cdl.diffs)
            {
                try
                {
                    cdl.cdl_file.waitLoad(Math.max(16, start + amount));
                }
                catch (IOException e)
                {
                    commandSender.sendMessage(ChatColor.RED+"IOException occurred while trying to load the chest log: "+e.getMessage());
                    e.printStackTrace();
                    return;
                }

                cdl.simplify(128);

                if (cdl.diffs.size() == 0)
                {
                    commandSender.sendMessage("| No history found :(");
                    commandSender.sendMessage("\\---------------------------------------------------");
                    playerToLastRequestedLog.remove(commandSender);
                }
                else
                {
                    /*
                    String cachedMessage = "";
                    int cachedMessageAmountOfParts = 0;
                    UUID cachedMessageActor = null;
                    long cachedMessageTimestamp = -1;
                    */

                    ContainerDiff lastDiffPrinted = null;

                    int i = cdl.diffs.size()-1-start;
                    
                    for (; i>=0 && i>=cdl.diffs.size()-start-amount; i--)
                    {
                        final ContainerDiff diff = cdl.diffs.get(i);
                        lastDiffPrinted = diff;
                        final String startDateTimeString = Utils.timestampToText(diff.startTimestamp);
                        final String endDateTimeString = Utils.timestampToText(diff.endTimestamp);
                        final String dateTimeString = startDateTimeString.equals(endDateTimeString) ? startDateTimeString : startDateTimeString + " - " + endDateTimeString;

                        final StringBuilder amountString = new StringBuilder();
                        {
                            //if (diff.amount < 0) amountString.append(ChatColor.RED);
                            //else amountString.append(ChatColor.GREEN);

                            //if (diff.actor.equals(Constants.CONTAINER_BELOW_THIS_HOPPER_UUID)) amountString.append(diff.amount < 0 ? "≤ " : "≥ ");

                            if (diff.amount < 0) amountString.append('-');
                            else amountString.append('+');

                            final int maxStackSize = (diff.itemType.getMaxStackSize() == 1) ? 64 : diff.itemType.getMaxStackSize();
                            final long stacks = Math.abs(diff.amount) / maxStackSize;
                            final long remainder = Math.abs(diff.amount) % maxStackSize;
                            if (stacks >= 1)
                            {
                                if (stacks >= 2)
                                {
                                    amountString.append(stacks);
                                    amountString.append('x');
                                }
                                amountString.append(maxStackSize);
                            }
                            if (remainder >= 1)
                            {
                                if (stacks >= 1) amountString.append(" + ");
                                amountString.append(remainder);
                            }
                        }

                        /*
                        TextComponent msg = new TextComponent("§aSome nice text here");
                        msg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§aThis text is shown on hover!").create()));
                        //msg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/yourcommand"));
                        p.sendMessage(msg);
                        */

                        /*
                        if (cachedMessageAmountOfParts >= Constants.MAX_AMOUNT_OF_DIFFS_IN_ONE_CHAT_LINE
                            ||
                            (
                                diff.amount == Long.MAX_VALUE
                                ||
                                diff.amount == Long.MIN_VALUE
                                ||
                                (
                                    cachedMessageAmountOfParts >= 1
                                    &&
                                    (
                                        !diff.actor.equals(cachedMessageActor)
                                        ||
                                        Math.abs(diff.endTimestamp-cachedMessageTimestamp) > 1000*Constants.MAX_SECONDS_DIFFERENCE_TO_MERGE_DIFFS
                                    )
                                )
                            )
                        )
                        {
                            if (cachedMessageAmountOfParts >= 1)
                            player.sendMessage(cachedMessage);
                            cachedMessageAmountOfParts = 0;
                            cachedMessage = "";
                        }
                        */

                        if (diff.amount == Long.MAX_VALUE)
                        {
                            commandSender.sendMessage("| ["+dateTimeString+", "+Utils.actorToString(diff.actor, diff.actor2)+"] "+ChatColor.GREEN+" placed "+diff.itemType.getType().name());
                        }
                        else if (diff.amount == Long.MIN_VALUE)
                        {
                            commandSender.sendMessage("| ["+dateTimeString+", "+Utils.actorToString(diff.actor, diff.actor2)+"] "+ChatColor.RED+" broke "+diff.itemType.getType().name());
                        }
                        else
                        {
                            final TextComponent msg = new TextComponent("| ["+dateTimeString+", "+Utils.actorToString(diff.actor, diff.actor2)+"] ");
                            {
                                final TextComponent amountPart = new TextComponent(amountString.toString());
                                if (diff.amount < 0) amountPart.setColor(net.md_5.bungee.api.ChatColor.RED);
                                else amountPart.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                                msg.addExtra(amountPart);
                            }
                            {
                                TextComponent itemPart = new TextComponent(Utils.itemTypeToName(diff.itemType));
                                if (diff.itemType.getItemMeta().hasDisplayName())
                                {
                                    itemPart.addExtra(" (\"" + diff.itemType.getItemMeta().getDisplayName() + "\")");
                                }
                                if (diff.itemType.getEnchantments().size() >= 1)
                                {
                                    itemPart.setColor(net.md_5.bungee.api.ChatColor.AQUA);

                                    StringBuilder hoverText = new StringBuilder();
                                    final Iterator<Map.Entry<Enchantment, Integer>> it = diff.itemType.getEnchantments().entrySet().iterator();
                                    while (it.hasNext())
                                    {
                                        if (hoverText.length() != 0) hoverText.append('\n');
                                        Map.Entry<Enchantment, Integer> entry = it.next();
                                        hoverText.append(Utils.enchantmentToName(entry.getKey()));
                                        if (entry.getKey().getMaxLevel() >= 2)
                                        {
                                            hoverText.append(' ');
                                            Utils.writeRomanNumeral(entry.getValue(), hoverText);
                                        }
                                    }
                                    TextComponent itemNameText = new TextComponent(diff.itemType.getItemMeta().getDisplayName());
                                    itemNameText.setColor(net.md_5.bungee.api.ChatColor.AQUA);

                                    TextComponent enchantmentsText = new TextComponent(hoverText.toString());
                                    enchantmentsText.setColor(net.md_5.bungee.api.ChatColor.GRAY);

                                    itemPart.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(itemNameText).append(enchantmentsText).create()));
                                }
                                
                                msg.addExtra(" ");
                                msg.addExtra(itemPart);
                            }
                            commandSender.spigot().sendMessage(msg);
                        }
                        /*
                        else if (cachedMessageAmountOfParts == 0)
                        {
                            cachedMessage = "| ["+dateTimeString+", "+Utils.actorToString(diff.actor)+"] ";
                            cachedMessage += amountString.toString()+ChatColor.WHITE+" "+diff.itemType.getType().name();
                            cachedMessageAmountOfParts = 1;
                            cachedMessageActor = diff.actor;
                            cachedMessageTimestamp = diff.endTimestamp;
                        }
                        else
                        {
                            cachedMessage += " " + amountString.toString()+ChatColor.WHITE+" "+diff.itemType.getType().name();
                            cachedMessageAmountOfParts++;
                        }
                        */
                    }

                    /*
                    if (cachedMessageAmountOfParts >= 1)
                    {
                        player.sendMessage(cachedMessage);
                        cachedMessageAmountOfParts = 0;
                        cachedMessage = "";
                    }
                    */
                    if (lastDiffPrinted != null && lastDiffPrinted.diffIndex == 0)
                    {
                        commandSender.sendMessage("\\---------------------------------------------------");
                        playerToLastRequestedLog.remove(commandSender);
                    }
                    else
                    {
                        //player.sendMessage("| ...");
                    }
                }
            }
        }}).start();
    }
}
