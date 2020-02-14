package com.jesbus.jbchestlog;

import java.util.Date;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;

class PlayerInteractEventTodo
{
	final Action action;
	final Player player;
	final Material itemTypeInHand;
	final Block clickedBlock;
	final Container container;
	final long timestamp;

	PlayerInteractEventTodo(Action action, Player player, Material itemTypeInHand, Block clickedBlock, Container container)
	{
		this.action = action;
		this.player = player;
		this.itemTypeInHand = itemTypeInHand;
		this.clickedBlock = clickedBlock;
		this.container = container;
		this.timestamp = new Date().getTime();

		/*
		synchronized(all)
		{
			all.push(this);
		*/
	}
	
	/*
	private static List<PlayerInteractEventTodo> all = new ArrayList<>();

	static void processAll()
	{
		synchronized (all)
		{
			for (int i=0; i<all.size(); i++)
			{

			}
		}
	}
	*/

	void process()
	{
		if (action == Action.LEFT_CLICK_BLOCK &&
			JBChestLog.playerToClickToClearCommandTimestamp.containsKey(player) &&
			timestamp - JBChestLog.playerToClickToClearCommandTimestamp.get(player) < 1000*Constants.SECONDS_BEFORE_CLEAR_COMMAND_EXPIRES)
		{
			final ContainerDiffLog cdl = ContainerDiffLog.get(container);

			JBChestLog.playerToClickToClearCommandTimestamp.remove(player);

			if (!player.hasPermission(Constants.PERMISSION_CLEAR_ANY))
			{
				if (!player.hasPermission(Constants.PERMISSION_CLEAR_OWN))
				{
					player.sendMessage(ChatColor.RED + "Your permission to clear container logs has just been revoked :(");
					return;
				}
				else
				{
					UUID placer = cdl.tryFindWhoPlacedThisContainer();
					if (placer == null)
					{
						player.sendMessage(ChatColor.RED + "The server can't figure out who placed this container, so unfortunately you're not allowed to clear its history");
						return;
					}
					else if (!placer.equals(player.getUniqueId()))
					{
						player.sendMessage(ChatColor.RED + "You did not place this container and you don't have permission to clear any container's history");
						return;
					}
				}
			}
			
			cdl.deleteLogs();
			player.sendMessage(ChatColor.GREEN + "Logs of container at "+clickedBlock.getX()+" "+clickedBlock.getY()+" "+clickedBlock.getZ()+" have been deleted");

			return;
		}
		


		if (action == Action.LEFT_CLICK_BLOCK && itemTypeInHand == Material.STICK)
		{
			final ContainerDiffLog cdl = ContainerDiffLog.get(container);

			if (!player.hasPermission(Constants.PERMISSION_VIEW_ANY))
			{
				if (!player.hasPermission(Constants.PERMISSION_VIEW_OWN))
				{
					return;
				}
				else
				{
					UUID placer = cdl.tryFindWhoPlacedThisContainer();
					if (placer == null)
					{
						player.sendMessage(ChatColor.RED + "The server can't figure out who placed this container, so unfortunately you're not allowed to view its history");
						return;
					}
					else if (!placer.equals(player.getUniqueId()))
					{
						player.sendMessage(ChatColor.RED + "You did not place this container. You don't have permission to view any container's history.");
						return;
					}
				}
			}
			
			ContainerDifferenceCheck.processAll();

			ContainerDiffLogPrinter.reportTo(cdl, player);

			return;
		}
	}
}
