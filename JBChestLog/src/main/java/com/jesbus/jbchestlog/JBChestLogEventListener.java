package com.jesbus.jbchestlog;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Hopper;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventPriority;

class JBChestLogEventListener implements Listener
{
	static final HashMap<UUID, UUID> minecart_to_placer = new HashMap<>();

	private static List<MinecartSpawnWaiter> minecartSpawnWaiters = new ArrayList<>();
	private static class MinecartSpawnWaiter
	{
		final UUID world;
		final int x, y, z;
		final long timestamp;
		final UUID placer;
		MinecartSpawnWaiter(final UUID world, final int x, final int y, final int z, final UUID placer)
		{
			this.world = world;
			this.x = x;
			this.y = y;
			this.z = z;
			this.placer = placer;
			this.timestamp = new Date().getTime();
		}
	}
	
	@EventHandler(priority=EventPriority.LOWEST)
	void onContainerExplode(BlockExplodeEvent event)
	{
		if (event.isCancelled()) return;

		ContainerDifferenceCheck.processAll();

		final BlockState bs = event.getBlock().getState();
		if (!(bs instanceof Container)) return;
		final Container c = (Container)bs;
		final Inventory inv = c.getInventory();
		final ContainerDiffLog cdl = ContainerDiffLog.get(c);

		for (int i=0; i<inv.getSize(); i++)
		{
			cdl.newDiff(Constants.EXPLOSION_UUID, inv.getItem(i), ContainerDiffType.ITEM_REMOVED);
		}

		cdl.newDiff(Constants.EXPLOSION_UUID, new ItemStack(Material.AIR, 1), ContainerDiffType.ITEM_REMOVED);
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	void onContainerBroken(BlockBreakEvent event)
	{
		if (event.isCancelled()) return;

		ContainerDifferenceCheck.processAll();
		
		final BlockState bs = event.getBlock().getState();
		if (!(bs instanceof Container)) return;
		final Container c = (Container)bs;

		final ContainerDiffLog cdl = ContainerDiffLog.get(c);

		final UUID actor = event.getPlayer().getUniqueId();

		if (Constants.WHEN_PLAYER_BREAKS_CONTAINER_RECORD_THEM_AS_TAKING_THE_ITEMS)
		{
			final Inventory inv = c.getInventory();
			for (int i=0; i<inv.getSize(); i++)
			{
				ItemStack is = inv.getItem(i);
				if (is != null && is.getAmount() >= 1)
				{
					cdl.newDiff(actor, is, ContainerDiffType.ITEM_REMOVED);
				}
			}
		}

		cdl.newDiffContainerDestroyed(actor, event.getBlock().getType());
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	void onContainerPlaced(BlockPlaceEvent event)
	{
		if (event.isCancelled()) return;

		ContainerDifferenceCheck.processAll();

		final BlockState bs = event.getBlock().getState();
		if (!(bs instanceof Container)) return;
		final Container c = (Container)bs;
		final ContainerDiffLog cdl = ContainerDiffLog.get(c);
		final UUID actor = event.getPlayer().getUniqueId();

		cdl.newDiffContainerCreated(actor, event.getBlock().getType());
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	void onFurnaceSmelt(FurnaceSmeltEvent event)
	{
		ContainerDifferenceCheck.processAll();

		if (event.getBlock().getState() instanceof Container)
		{
			Container c = (Container)event.getBlock().getState();
			for (int slot=0; slot<c.getInventory().getSize(); slot++)
			{
				new ContainerDifferenceCheck(c.getInventory(), slot, Constants.FURNACE_SMELTED_UUID);
			}
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	void onFurnaceBurn(FurnaceBurnEvent event)
	{
		ContainerDifferenceCheck.processAll();

		if (event.getBlock().getState() instanceof Container)
		{
			Container c = (Container)event.getBlock().getState();
			for (int slot=0; slot<c.getInventory().getSize(); slot++)
			{
				new ContainerDifferenceCheck(c.getInventory(), slot, Constants.FURNACE_BURNED_UUID);
			}
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	void onBrewingStandFuel(BrewingStandFuelEvent event)
	{
		ContainerDifferenceCheck.processAll();

		if (event.getBlock().getState() instanceof Container)
		{
			Container c = (Container)event.getBlock().getState();
			for (int slot=0; slot<c.getInventory().getSize(); slot++)
			{
				new ContainerDifferenceCheck(c.getInventory(), slot, Constants.BREWING_STAND_CONSUMED_UUID);
			}
		}
	}

	@EventHandler(priority=EventPriority.HIGHEST)
	void onBrew(BrewEvent event)
	{
		ContainerDifferenceCheck.processAll();

		if (event.getBlock().getState() instanceof Container)
		{
			Container c = (Container)event.getBlock().getState();
			for (int slot=0; slot<c.getInventory().getSize(); slot++)
			{
				new ContainerDifferenceCheck(c.getInventory(), slot, Constants.BREWING_STAND_BREWED_UUID);
			}
		}
	}

	@EventHandler(priority=EventPriority.LOWEST)
	void onVehicleCreate(VehicleCreateEvent event)
	{
		if (event.isCancelled()) return;

		if (event.getVehicle().getType() == EntityType.MINECART_HOPPER ||
			event.getVehicle().getType() == EntityType.MINECART_CHEST)
		{
			final long now = new Date().getTime();
			for (int i=0; i<minecartSpawnWaiters.size(); i++)
			{
				final MinecartSpawnWaiter msw = minecartSpawnWaiters.get(i);
				final long dt = Math.abs(now - msw.timestamp);
				if (msw.world.equals(event.getVehicle().getWorld().getUID()) &&
					msw.x == event.getVehicle().getLocation().getBlockX() &&
					msw.y == event.getVehicle().getLocation().getBlockY() &&
					msw.z == event.getVehicle().getLocation().getBlockZ() &&
					dt < 10000)
				{
					minecart_to_placer.put(event.getVehicle().getUniqueId(), msw.placer);
					minecartSpawnWaiters.remove(i);
					return;
				}
				else if (dt > 30000)
				{
					minecartSpawnWaiters.remove(i);
					i--;
				}
			}
		}
	}

	@EventHandler(priority=EventPriority.LOWEST)
	void onPlayerInteract(PlayerInteractEvent event)
	{
		if (event.getHand() != EquipmentSlot.HAND) return;
		if (event.getClickedBlock() == null) return;

		final BlockState clickedBlockState = event.getClickedBlock().getState();

		ContainerDifferenceCheck.processAll();
		
		if (event.getAction() == Action.RIGHT_CLICK_BLOCK &&
			(event.getPlayer().getInventory().getItemInMainHand().getType() == Material.HOPPER_MINECART/* ||
			 event.getPlayer().getInventory().getItemInMainHand().getType() == Material.STORAGE_MINECART*/) &&
			(clickedBlockState.getType() == Material.ACTIVATOR_RAIL ||
			clickedBlockState.getType() == Material.POWERED_RAIL ||
			clickedBlockState.getType() == Material.RAILS ||
			clickedBlockState.getType() == Material.DETECTOR_RAIL))
		{
			minecartSpawnWaiters.add(new MinecartSpawnWaiter(clickedBlockState.getWorld().getUID(), clickedBlockState.getX(), clickedBlockState.getY(), clickedBlockState.getZ(), event.getPlayer().getUniqueId()));
			return;
		}

		if (clickedBlockState instanceof Container)
		{
			if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getPlayer().getInventory().getItemInMainHand().getType() == Material.STICK)
			{
				event.setCancelled(true);
			}

			final Container c = (Container)clickedBlockState;

			new PlayerInteractEventTodo(event.getAction(), event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand().getType(), event.getClickedBlock(), c).process();
		}
	}

	@EventHandler(priority=EventPriority.LOWEST)
	void onInventoryDrag(InventoryDragEvent event)
	{
		if (event.isCancelled()) return;
		
		ContainerDifferenceCheck.processAll();

		if (event.getInventory().getHolder() instanceof HumanEntity) return;
		if (event.getInventory().getType() == InventoryType.ENDER_CHEST) return;

		for (int slot : event.getInventorySlots())
		{
			if (slot >= event.getInventory().getSize()) JBChestLog.errorLog("Slot in InventoryDragSlot is beyond the given Inventory");
			else new ContainerDifferenceCheck(event.getInventory(), slot, event.getWhoClicked());
		}
	}

	@EventHandler(priority=EventPriority.LOWEST)
	void onBlockPhysicsEvent(BlockPhysicsEvent event)
	{
		ContainerDifferenceCheck.processAll();
	}




	@EventHandler(priority=EventPriority.LOWEST)
	public void onInventoryClick(InventoryClickEvent event)
	{
		if (event.isCancelled()) return;
		if (event.getClickedInventory() == null) return;
		if (event.getView().getType() == InventoryType.PLAYER) return;
		if (event.getView().getType() == InventoryType.CRAFTING) return;
		if (event.getView().getType() == InventoryType.CREATIVE) return;
		if (event.getAction() == InventoryAction.NOTHING) return;

		ContainerDifferenceCheck.processAll();

		JBChestLog.debugLog("InventoryClickEvent player="+event.getWhoClicked().getName()+" clickType="+event.getClick().toString()+" action="+event.getAction().toString()+" viewtype="+event.getView().getType().toString());

		final Inventory inv = event.getClickedInventory();

		if (inv.getHolder() instanceof StorageMinecart)
		{
		}
		else if (event.getClick() == ClickType.DOUBLE_CLICK)
		{
			final Inventory topInv = event.getView().getTopInventory();
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
				JBChestLog.errorLog("top inv type="+event.getView().getTopInventory().getType());
				JBChestLog.errorLog("bot inv type="+event.getView().getBottomInventory().getType());
				JBChestLog.errorLog("clicked inv type="+event.getClickedInventory().getType());
			}
			else if (topInv.getHolder() instanceof HumanEntity &&
					 event.getView().getBottomInventory() != null &&
					 event.getView().getBottomInventory().getHolder() instanceof HumanEntity)
			{
				JBChestLog.logger.info("double clicked, both top inv and bottom inv are HumanEntity. viewType="+event.getView().getType());
			}
			else
			{
				JBChestLog.errorLog("WTF double clicked, but topInv holder is neither doublechest nor container: "+topInv.getHolder().getClass().getName());
				if (event.getView().getBottomInventory() != null &&
				event.getView().getBottomInventory().getHolder() != null) JBChestLog.errorLog("bottomInv holder: "+event.getView().getBottomInventory().getHolder().getClass().getName());
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
				JBChestLog.errorLog("Unhandled ClickType on DoubleChest: "+event.getClick().toString()+" action="+event.getAction().toString());
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
				JBChestLog.errorLog("Unhandled ClickType on Container: clickType="+event.getClick().toString()+" action="+event.getAction().toString());
			}
		}
		else if (inv.getHolder() instanceof Villager)
		{
		}
		else if (inv.getHolder() instanceof HumanEntity)
		{
			if (event.getAction() == InventoryAction.PLACE_ALL)
			{
			}
			else if (event.getAction() == InventoryAction.PICKUP_HALF)
			{
			}
			else if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR)
			{
			}
			else if (event.getAction() == InventoryAction.HOTBAR_SWAP)
			{
			}
			else if (event.getAction() == InventoryAction.PICKUP_ONE)
			{
			}
			else if (event.getAction() == InventoryAction.PICKUP_ALL)
			{
			}
			else if (event.getAction() == InventoryAction.PLACE_ONE)
			{
			}
			else if (event.getView().getType() == InventoryType.CRAFTING)
			{
			}
			else if (event.getView().getType() == InventoryType.PLAYER)
			{
			}
			else if (event.getView().getType() == InventoryType.CREATIVE)
			{
			}
			else if (event.getView().getType() == InventoryType.WORKBENCH)
			{
			}
			else if (event.getView().getType() == InventoryType.ENCHANTING)
			{
			}
			else if (event.getView().getType() == InventoryType.ENDER_CHEST)
			{
			}
			else if (event.isShiftClick() && event.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY) && event.getView().getType() != InventoryType.PLAYER)
			{
				if (event.getView().getTopInventory().getHolder() instanceof HumanEntity)
				{
					JBChestLog.errorLog("WTF top inventory holder is HumanEntity action="+event.getAction()+" view="+event.getView().getType()+" top invholder="+(event.getView().getTopInventory() == null ? "null" : event.getView().getTopInventory().getHolder().getClass().getName())+"  bottom invholder="+(event.getView().getTopInventory().getHolder().getClass().getName() == null ? "null" : event.getView().getTopInventory().getHolder().getClass().getName()));
				}

				for (final Integer destinationSlot : InventoryBehaviourSimulations.findShiftClickDestinationSlots(event.getView().getTopInventory(), inv.getItem(event.getSlot())))
				{
					new ContainerDifferenceCheck(event.getView().getTopInventory(), destinationSlot, event.getWhoClicked());
				}
			}
			else
			{
				JBChestLog.errorLog("Unhandled ClickType on HumanEntity: "+event.getClick().toString()+" action="+event.getAction().toString()+" viewtype="+event.getView().getType().toString());
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
	
	@EventHandler(priority=EventPriority.LOWEST)
	public void onInventoryPickupItem(InventoryPickupItemEvent event)
	{
		if (event.isCancelled()) return;
		if (event.getInventory() == null) return;
		if (event.getItem() == null) return;
		if (event.getItem().getItemStack().getAmount() == 0) return;
		
		ContainerDifferenceCheck.processAll();
		
		final Inventory destInventory = event.getInventory();
		
		if (destInventory.getHolder() instanceof Hopper)
		{
			for (int i=0; i<destInventory.getSize(); i++)
			{
				if (destInventory.getItem(i) == null ||
					destInventory.getItem(i).getAmount() == 0 ||
					(destInventory.getItem(i).isSimilar(event.getItem().getItemStack()) && destInventory.getItem(i).getAmount() < destInventory.getItem(i).getMaxStackSize()))
				{
					new ContainerDifferenceCheck(destInventory, i, Constants.DROPPED_ITEM_UUID);
				}
			}
		}
	}
	
	@EventHandler(priority=EventPriority.LOWEST)
	public void onInventoryMoveItem(InventoryMoveItemEvent event)
	{
		if (event.isCancelled()) return;
		if (event.getDestination() == null) return;
		if (event.getSource() == null) return;
		if (event.getItem() == null) return;
		if (event.getItem().getAmount() == 0) return;
		
		ContainerDifferenceCheck.processAll();
		
		final Inventory sourceInventory = event.getSource();
		final Inventory destInventory = event.getDestination();
		final InventoryHolder sourceInventoryHolder = event.getSource().getHolder();
		final InventoryHolder destInventoryHolder = event.getDestination().getHolder();
		final ItemStack itemStack = event.getItem();
		final boolean initiatedBySourceInventory = event.getInitiator() == sourceInventory;
		
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
				actor2FromSourcePerspectiveUUID = minecart_to_placer.get(((HopperMinecart)destInventoryHolder).getUniqueId());
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
				actor2FromDestPerspectiveUUID = minecart_to_placer.get(((HopperMinecart)sourceInventoryHolder).getUniqueId());
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
