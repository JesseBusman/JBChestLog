package com.jesbus.jbchestlog;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

class InventoryBehaviourSimulations
{
	static List<Integer> findShiftClickDestinationSlots(Inventory destinationInventory, ItemStack shiftClickedItem)
	{
		List<Integer> ret = new ArrayList<>();
		if (destinationInventory.getHolder() instanceof DoubleChest ||
			destinationInventory.getHolder() instanceof Chest ||
			destinationInventory.getHolder() instanceof ShulkerBox)
		{
			int amountToPlace = shiftClickedItem.getAmount();
			final int maxStackSize = shiftClickedItem.getMaxStackSize();

			if (maxStackSize == -1) throw new Error("Max stack size of "+shiftClickedItem.getType().name()+" is unknown!");

			for (int i=0; i<destinationInventory.getSize(); i++)
			{
				if (amountToPlace == 0) break;

				if (destinationInventory.getItem(i) != null && destinationInventory.getItem(i).isSimilar(shiftClickedItem))
				{
					final int spotsAvailableHere = maxStackSize - destinationInventory.getItem(i).getAmount();
					final int amountToAddHere = (spotsAvailableHere > amountToPlace) ? amountToPlace : spotsAvailableHere;
					if (spotsAvailableHere >= 1)
					{
						amountToPlace -= amountToAddHere;
						ret.add(i);
					}
				}
			}

			if (amountToPlace > 0)
			{
				final int firstEmptySlot = destinationInventory.firstEmpty();

				if (firstEmptySlot != -1)
				{
					ret.add(firstEmptySlot);
				}
			}
		}
		else if (destinationInventory.getHolder() == null)
		{
			JBChestLog.errorLog("in findShiftClickDestinationSlot: destinationInventory.getHolder() == null");
		}
		else
		{
			for (int i=0; i<destinationInventory.getSize(); i++)
			{
				ret.add(i);
			}
		}
		return ret;
	}
}
