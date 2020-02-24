package com.jesbus.jbchestlog;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Date;
import java.util.UUID;

class EventListenerPlayerInteract implements Listener
{
	@EventHandler(priority=EventPriority.LOWEST)
	public void onPlayerInteract(PlayerInteractEvent event)
	{
		ContainerDifferenceCheck.processAll();

		final Player player = event.getPlayer();
		final Action action = event.getAction();
		
		if (event.getHand() != EquipmentSlot.HAND) return;
		if (event.getClickedBlock() == null) return;

		final BlockState clickedBlockState = event.getClickedBlock().getState();
		final Material itemTypeInHand = event.getPlayer().getInventory().getItemInMainHand().getType();

		if (action == Action.RIGHT_CLICK_BLOCK &&
			(itemTypeInHand == Material.HOPPER_MINECART/* ||
			 event.getPlayer().getInventory().getItemInMainHand().getType() == Material.STORAGE_MINECART*/) &&
			(clickedBlockState.getType() == Material.ACTIVATOR_RAIL ||
			clickedBlockState.getType() == Material.POWERED_RAIL ||
			clickedBlockState.getType() == Material.RAILS ||
			clickedBlockState.getType() == Material.DETECTOR_RAIL))
		{
			new MinecartSpawnWaiter(clickedBlockState.getWorld().getUID(), clickedBlockState.getX(), clickedBlockState.getY(), clickedBlockState.getZ(), player.getUniqueId());
			return;
		}

		if (clickedBlockState instanceof Container)
		{
			if (action == Action.LEFT_CLICK_BLOCK && event.getPlayer().getInventory().getItemInMainHand().getType() == Material.STICK)
			{
				event.setCancelled(true);
			}

			final Container container = (Container)clickedBlockState;

			if (action == Action.LEFT_CLICK_BLOCK &&
				JBChestLog.playerToClickToClearCommandTimestamp.containsKey(player) &&
				new Date().getTime() - JBChestLog.playerToClickToClearCommandTimestamp.get(player) < 1000*Constants.SECONDS_BEFORE_CLEAR_COMMAND_EXPIRES)
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
				player.sendMessage(ChatColor.GREEN + "Logs of container at "+clickedBlockState.getX()+" "+clickedBlockState.getY()+" "+clickedBlockState.getZ()+" have been deleted");

				return;
			}



			if (action == Action.LEFT_CLICK_BLOCK && itemTypeInHand == Material.STICK)
			{
				final ContainerDiffLog cdl = ContainerDiffLog.get(container);

				ContainerDiffLogPrinter.reportToIfAllowed(cdl, player);

				return;
			}
		}
	}
}
