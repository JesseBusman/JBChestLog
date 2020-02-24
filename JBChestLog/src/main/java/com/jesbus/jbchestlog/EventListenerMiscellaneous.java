package com.jesbus.jbchestlog;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.event.inventory.BrewingStandFuelEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.EntityType;

import java.util.UUID;

class EventListenerMiscellaneous implements Listener
{
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
			MinecartSpawnWaiter.notify(event.getVehicle());

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



































}
