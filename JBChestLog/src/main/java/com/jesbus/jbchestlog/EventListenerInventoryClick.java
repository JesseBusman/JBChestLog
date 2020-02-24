package com.jesbus.jbchestlog;

import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Villager;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;

class EventListenerInventoryClick implements Listener
{
	@EventHandler(priority=EventPriority.LOWEST)
	public void onInventoryClick(InventoryClickEvent event)
	{
		ContainerDifferenceCheck.processAll();

		final InventoryView view = event.getView();
		final InventoryType type = view.getType();
		final InventoryAction action = event.getAction();
		final Inventory inv = event.getClickedInventory();

		// If nothing happened, quit
		if (action == InventoryAction.NOTHING) return;
		if (event.isCancelled()) return;
		if (inv == null) return;

		// If the event happened in any of these screens, quit.
		// No event in these screens can have impacted a logged container.
		if (type == InventoryType.PLAYER) return;
		if (type == InventoryType.CRAFTING) return;
		if (type == InventoryType.CREATIVE) return;
		if (type == InventoryType.WORKBENCH) return;
		if (type == InventoryType.ANVIL) return;
		if (type == InventoryType.ENCHANTING) return;
		if (type == InventoryType.ENDER_CHEST) return;
		if (type == InventoryType.MERCHANT) return;
		if (inv.getHolder() instanceof StorageMinecart) return;
		if (inv.getHolder() instanceof HopperMinecart) return;
		if (inv.getHolder() instanceof Villager) return;

		JBChestLog.debugLog("InventoryClickEvent player="+event.getWhoClicked().getName()+" clickType="+event.getClick().toString()+" action="+action.toString()+" viewtype="+type.toString());


		// 
		if (event.getClick() == ClickType.DOUBLE_CLICK)
		{
			final Inventory topInv = view.getTopInventory();
			if (topInv != null &&
				(topInv.getHolder() instanceof DoubleChest ||
				 topInv.getHolder() instanceof Container))
			{
				ItemStack[] clickedItemTypes = new ItemStack[]{event.getClickedInventory().getItem(event.getSlot()), event.getCursor(), event.getCurrentItem()};

				for (ItemStack clickedItemType : clickedItemTypes)
				{
					if (clickedItemType == null) continue;
					if (clickedItemType.getAmount() == 0) continue;

					for (int i=0; i<topInv.getSize(); i++)
					{
						final ItemStack currentItem = topInv.getItem(i);
						if (currentItem != null && currentItem.isSimilar(clickedItemType))
						{							
							new ContainerDifferenceCheck(topInv, i, event.getWhoClicked());
						}
					}
				}

				if (topInv != null)
				{
					for (ItemStack clickedItemType : clickedItemTypes)
					{
						if (clickedItemType == null) continue;
						if (clickedItemType.getAmount() == 0) continue;

						for (int i=0; i<topInv.getSize(); i++)
						{
							final ItemStack currentItem = topInv.getItem(i);
							if (currentItem != null && currentItem.isSimilar(clickedItemType))
							{								
								new ContainerDifferenceCheck(topInv, event.getSlot(), event.getWhoClicked());
							}
						}
					}
				}
			}
			else if (topInv.getHolder() == null)
			{
				JBChestLog.errorLog("WTF double clicked, but topInv holder is neither doublechest nor container: null");
				JBChestLog.errorLog("top inv type="+view.getTopInventory().getType());
				JBChestLog.errorLog("bot inv type="+view.getBottomInventory().getType());
				JBChestLog.errorLog("clicked inv type="+event.getClickedInventory().getType());
			}
			else if (topInv.getHolder() instanceof HumanEntity &&
					 view.getBottomInventory() != null &&
					 view.getBottomInventory().getHolder() instanceof HumanEntity)
			{
				JBChestLog.logger.info("double clicked, both top inv and bottom inv are HumanEntity. viewType="+type);
			}
			else
			{
				JBChestLog.errorLog("WTF double clicked, but topInv holder is neither doublechest nor container: "+topInv.getHolder().getClass().getName());
				if (view.getBottomInventory() != null &&
				view.getBottomInventory().getHolder() != null) JBChestLog.errorLog("bottomInv holder: "+view.getBottomInventory().getHolder().getClass().getName());
			}
		}
		else if (inv.getHolder() instanceof DoubleChest)
		{
			if (event.getClick() == ClickType.LEFT ||
				event.getClick() == ClickType.RIGHT ||
				event.getClick() == ClickType.SHIFT_LEFT ||
				event.getClick() == ClickType.CONTROL_DROP ||
				event.getClick() == ClickType.NUMBER_KEY ||
				event.getClick() == ClickType.SHIFT_RIGHT)
			{
				new ContainerDifferenceCheck(inv, event.getSlot(), event.getWhoClicked());
			}
			else
			{
				JBChestLog.errorLog("Unhandled ClickType on DoubleChest: "+event.getClick().toString()+" action="+action.toString()+" viewType="+view.getType());
			}
		}
		else if (inv.getHolder() instanceof Container)
		{
			if (event.getClick() == ClickType.LEFT ||
				event.getClick() == ClickType.RIGHT ||
				event.getClick() == ClickType.SHIFT_LEFT ||
				event.getClick() == ClickType.CONTROL_DROP ||
				event.getClick() == ClickType.NUMBER_KEY ||
				event.getClick() == ClickType.SHIFT_RIGHT)
			{
				new ContainerDifferenceCheck(inv, event.getSlot(), event.getWhoClicked());
			}
			else
			{
				JBChestLog.errorLog("Unhandled ClickType on Container: clickType="+event.getClick().toString()+" action="+action.toString()+" viewType="+view.getType());
			}
		}
		else if (inv.getHolder() instanceof HumanEntity)
		{
			// Most inventory clicks in the player's inventory can be discarded,
			// because they don't change anything in any logged container...
			if (action == InventoryAction.PLACE_ALL) { }
			else if (action == InventoryAction.PICKUP_HALF) { }
			else if (action == InventoryAction.SWAP_WITH_CURSOR) { }
			else if (action == InventoryAction.HOTBAR_SWAP) { }
			else if (action == InventoryAction.PICKUP_ONE) { }
			else if (action == InventoryAction.PICKUP_ALL) { }
			else if (action == InventoryAction.PLACE_ONE) { }
			
			// ... except for shift+click which can move the clicked item from the player's to the container's inventory
			else if (event.isShiftClick() && action.equals(InventoryAction.MOVE_TO_OTHER_INVENTORY) && type != InventoryType.PLAYER)
			{
				for (final Integer destinationSlot : InventoryBehaviourSimulations.findShiftClickDestinationSlots(view.getTopInventory(), inv.getItem(event.getSlot())))
				{
					new ContainerDifferenceCheck(view.getTopInventory(), destinationSlot, event.getWhoClicked());
				}
			}
			else
			{
				JBChestLog.errorLog("Unhandled ClickType on HumanEntity: "+event.getClick().toString()+" action="+action.toString()+" viewtype="+type.toString());
			}
		}
		else if (inv.getHolder() == null)
		{
		}
		else
		{
			JBChestLog.errorLog("WTF: Clicked inventory is not double chest, container or human entity. It's: "+inv.getHolder().toString()+" "+event.getClickedInventory().getHolder().getClass().getName());
		}
	}
}
