package com.jesbus.jbchestlog;

import java.util.UUID;

import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Dropper;
import org.bukkit.block.Hopper;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

class EventListenerInventoryMoveItem implements Listener
{
	@EventHandler(priority=EventPriority.LOWEST)
	public void onInventoryMoveItem(final InventoryMoveItemEvent event)
	{
		ContainerDifferenceCheck.processAll();

		if (event.isCancelled()) return;
		if (event.getDestination() == null) return;
		if (event.getSource() == null) return;
		if (event.getItem() == null) return;
		if (event.getItem().getAmount() == 0) return;
		
		final Inventory sourceInventory = event.getSource();
		final Inventory destInventory = event.getDestination();
		final InventoryHolder sourceInventoryHolder = event.getSource().getHolder();
		final InventoryHolder destInventoryHolder = event.getDestination().getHolder();
		final ItemStack itemStack = event.getItem();
		final boolean initiatedBySourceInventory = event.getInitiator() == sourceInventory;

		if (sourceInventoryHolder instanceof Dropper)
		{
			// TODO droppers
			return;
		}
		
		if ((!initiatedBySourceInventory && destInventoryHolder instanceof Hopper) ||
			(!initiatedBySourceInventory && destInventoryHolder instanceof HopperMinecart) ||
			( initiatedBySourceInventory && sourceInventoryHolder instanceof Hopper) ||
			( initiatedBySourceInventory && sourceInventoryHolder instanceof HopperMinecart))
		{
			UUID actorFromSourcePerspectiveUUID = null;
			UUID actor2FromSourcePerspectiveUUID = null;
			if (!initiatedBySourceInventory && destInventoryHolder instanceof Hopper) actorFromSourcePerspectiveUUID = Constants.HOPPER_BELOW_THIS_CONTAINER_UUID;
			else if (!initiatedBySourceInventory && destInventoryHolder instanceof HopperMinecart)
			{
				actorFromSourcePerspectiveUUID = Constants.HOPPER_MINECART_BELOW_THIS_CONTAINER_UUID;
				actor2FromSourcePerspectiveUUID = JBChestLog.minecart_to_placer.get(((HopperMinecart)destInventoryHolder).getUniqueId());
			}
			else if (initiatedBySourceInventory && sourceInventoryHolder instanceof Hopper) actorFromSourcePerspectiveUUID = Constants.CONTAINER_BELOW_THIS_HOPPER_UUID;
			else if (initiatedBySourceInventory && sourceInventoryHolder instanceof HopperMinecart) actorFromSourcePerspectiveUUID = Constants.CONTAINER_BELOW_THIS_HOPPER_MINECART_UUID;
			
			UUID actorFromDestPerspectiveUUID = null;
			UUID actor2FromDestPerspectiveUUID = null;
			if (!initiatedBySourceInventory && destInventoryHolder instanceof Hopper) actorFromDestPerspectiveUUID = Constants.CONTAINER_ABOVE_THIS_HOPPER_UUID;
			else if (!initiatedBySourceInventory && destInventoryHolder instanceof HopperMinecart) actorFromDestPerspectiveUUID = Constants.CONTAINER_ABOVE_THIS_HOPPER_MINECART_UUID;
			else if (initiatedBySourceInventory && sourceInventoryHolder instanceof Hopper) actorFromDestPerspectiveUUID = Constants.HOPPER_ABOVE_THIS_CONTAINER_UUID;
			else if (initiatedBySourceInventory && sourceInventoryHolder instanceof HopperMinecart)
			{
				actorFromDestPerspectiveUUID = Constants.HOPPER_MINECART_ABOVE_THIS_CONTAINER_UUID;
				actor2FromDestPerspectiveUUID = JBChestLog.minecart_to_placer.get(((HopperMinecart)sourceInventoryHolder).getUniqueId());
			}
			
			if (actor2FromSourcePerspectiveUUID == null) actor2FromSourcePerspectiveUUID = actorFromSourcePerspectiveUUID;
			if (actor2FromDestPerspectiveUUID == null) actor2FromDestPerspectiveUUID = actorFromDestPerspectiveUUID;
			
			Container applyOppositeDifferenceToThisContainer = null;
			
			if (sourceInventoryHolder instanceof Hopper || destInventoryHolder instanceof Hopper)
			{
				if (sourceInventoryHolder instanceof DoubleChest)
				{
					final Hopper hopper = (Hopper)destInventoryHolder;
					final Container leftSide = (Container)((DoubleChest)sourceInventoryHolder).getLeftSide();
					final Container rightSide = (Container)((DoubleChest)sourceInventoryHolder).getRightSide();
					
					// TODO
					// This is wrong.
					// A hopper below the left side of double chest doesn't necessarily pull items from the top half of the double chest's inventory.
					// We somehow need a way to check from which slot the items came.
					// I can't think of any reasonably efficient way to do it, because when InventoryMoveItemEvent executes the source inventory
					// has already been modified.
					if (leftSide.getX() == hopper.getX() && leftSide.getZ() == hopper.getZ()) applyOppositeDifferenceToThisContainer = leftSide;
					else if (rightSide.getX() == hopper.getX() && rightSide.getZ() == hopper.getZ()) applyOppositeDifferenceToThisContainer = rightSide;
					else
					{
						JBChestLog.errorLog("WTF hopper below double chest.");
						return;
					}
				}
				else
				{
					applyOppositeDifferenceToThisContainer = (Container)sourceInventoryHolder;
				}
			}
			else if (sourceInventoryHolder instanceof HopperMinecart)
			{
			}
			else
			{
				for (int i=0; i<sourceInventory.getSize(); i++)
				{
					if (sourceInventory.getItem(i) != null &&
						sourceInventory.getItem(i).getAmount() != 0 &&
						sourceInventory.getItem(i).isSimilar(itemStack))
					{
						new ContainerDifferenceCheck(sourceInventory, i, actorFromSourcePerspectiveUUID, actor2FromSourcePerspectiveUUID, itemStack);
					}
				}
			}
			
			if (destInventoryHolder instanceof HopperMinecart)
			{
			}
			else
			{
				for (int i=0; i<destInventory.getSize(); i++)
				{
					if (destInventory.getItem(i) == null ||
						destInventory.getItem(i).getAmount() == 0 ||
						(destInventory.getItem(i).isSimilar(itemStack) && destInventory.getItem(i).getAmount() < destInventory.getItem(i).getType().getMaxStackSize()))
					{
						new ContainerDifferenceCheck(destInventory, i, actorFromDestPerspectiveUUID, actor2FromDestPerspectiveUUID, itemStack, applyOppositeDifferenceToThisContainer, actorFromSourcePerspectiveUUID, actor2FromSourcePerspectiveUUID);
					}
				}
			}
		}
		else
		{
			JBChestLog.errorLog("Unhandled InventoryMoveItemEvent: initiator="+(initiatedBySourceInventory ? "source" : "dest")+" destHolder="+destInventoryHolder.getClass().getName()+" sourceHolder="+sourceInventoryHolder.getClass().getName()+" destType="+destInventory.getType()+" sourceType="+sourceInventory.getType());
		}
	}
}
