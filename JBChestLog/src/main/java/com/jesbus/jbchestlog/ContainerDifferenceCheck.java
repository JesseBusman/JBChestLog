package com.jesbus.jbchestlog;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.block.Container;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

class ContainerDifferenceCheck
{
	public static enum Type
	{
		SUBTRACT_BEFORE__ADD_AFTER,
		SUBTRACT_EXPECTED_DIFFERENCE,
		ADD_EXPECTED_DIFFERENCE,
	}
	
	private static List<ContainerDifferenceCheck> pendingContainerDifferenceChecks = new ArrayList<>();

	static void processAll()
	{
		if (pendingContainerDifferenceChecks.size() == 0) return;

		synchronized (pendingContainerDifferenceChecks)
		{
			final int amount = pendingContainerDifferenceChecks.size();
			for (int i=0; i<amount; i++)
			{
				pendingContainerDifferenceChecks.get(i).process();
			}
			pendingContainerDifferenceChecks.clear();
		}
	}

	private boolean processed = false;

	final Inventory inventory;
	final int inventorySlot;
	final UUID actor;
	final UUID actor2;
	final Container containerBlock;
	final ItemStack contentsBeforeChange;
	final ItemStack differenceShouldEqualThis;
	
	final Container applyOppositeDifferenceToThisContainer;
	final UUID applyOppositeDifferenceToThisContainer_actor;
	final UUID applyOppositeDifferenceToThisContainer_actor2;
	
	ContainerDifferenceCheck(Inventory inventory, int inventorySlot, HumanEntity actor)
	{
		this(inventory, inventorySlot, actor.getUniqueId());
	}
	ContainerDifferenceCheck(Inventory inventory, int inventorySlot, UUID actor)
	{
		this(inventory, inventorySlot, actor, null);
	}
	ContainerDifferenceCheck(Inventory inventory, int inventorySlot, UUID actor, ItemStack differenceShouldEqualThis)
	{
		this(inventory, inventorySlot, actor, differenceShouldEqualThis, null, null);
	}
	ContainerDifferenceCheck(Inventory inventory, int inventorySlot, UUID actor, UUID actor2, ItemStack differenceShouldEqualThis)
	{
		this(inventory, inventorySlot, actor, actor2, differenceShouldEqualThis, null, null, null);
	}
	ContainerDifferenceCheck(Inventory inventory, int inventorySlot, UUID actor, Container applyOppositeDifferenceToThisContainer, UUID applyOppositeDifferenceToThisContainer_actor)
	{
		this(inventory, inventorySlot, actor, null, applyOppositeDifferenceToThisContainer, applyOppositeDifferenceToThisContainer_actor);
	}
	ContainerDifferenceCheck(Inventory inventory, int inventorySlot, UUID actor, ItemStack differenceShouldEqualThis, Container applyOppositeDifferenceToThisContainer, UUID applyOppositeDifferenceToThisContainer_actor)
	{
		this(inventory, inventorySlot, actor, actor, differenceShouldEqualThis, applyOppositeDifferenceToThisContainer, applyOppositeDifferenceToThisContainer_actor, applyOppositeDifferenceToThisContainer_actor);
	}
	ContainerDifferenceCheck(Inventory inventory, int inventorySlot, UUID actor, UUID actor2, ItemStack differenceShouldEqualThis, Container applyOppositeDifferenceToThisContainer, UUID applyOppositeDifferenceToThisContainer_actor, UUID applyOppositeDifferenceToThisContainer_actor2)
	{
		this.inventory = inventory;
		this.inventorySlot = inventorySlot;
		this.actor = actor;
		this.actor2 = actor2;

		this.differenceShouldEqualThis = differenceShouldEqualThis == null ? null : new ItemStack(differenceShouldEqualThis);
		
		this.applyOppositeDifferenceToThisContainer = applyOppositeDifferenceToThisContainer;
		this.applyOppositeDifferenceToThisContainer_actor = applyOppositeDifferenceToThisContainer_actor;
		this.applyOppositeDifferenceToThisContainer_actor2 = applyOppositeDifferenceToThisContainer_actor2;

		if (inventory == null)
		{
			this.processed = true;
			this.contentsBeforeChange = null;
			this.containerBlock = null;
			JBChestLog.errorLog("WTF: inventory=null in check constrcutor");
			return;
		}

		this.containerBlock = Utils.inventoryAndSlot_to_container(inventory, inventorySlot);
		if (this.containerBlock == null)
		{
			this.processed = true;
			this.contentsBeforeChange = null;
			if (inventory.getType() == InventoryType.ENDER_CHEST) return;
			if (inventory.getHolder() == null) JBChestLog.errorLog("WTF: inventory holder is null type="+inventory.getType());
			else JBChestLog.errorLog("WTF: inventory holder isn't double chest or container: "+inventory.getHolder().toString()+" "+inventory.getHolder().getClass().getName()+" invType="+inventory.getType());
			return;
		}

		{
			final ItemStack c = inventory.getItem(inventorySlot);
			this.contentsBeforeChange = (c == null || c.getAmount() == 0) ? null : new ItemStack(c);
		}

		synchronized (pendingContainerDifferenceChecks)
		{
			pendingContainerDifferenceChecks.add(this);
		}
	}

	void process()
	{
		try
		{
			if (processed) return;
			processed = true;
			
			final ItemStack c = inventory.getItem(inventorySlot);
			final ItemStack contentsAfterChange = (c == null || c.getAmount() == 0) ? null : new ItemStack(c);
	
			if (this.differenceShouldEqualThis != null)
			{
				if ((contentsAfterChange  == null || contentsAfterChange .getAmount() == 0 || contentsBeforeChange == null || contentsBeforeChange.getAmount() == 0 || contentsAfterChange.isSimilar(contentsBeforeChange)) &&
					(contentsAfterChange  == null || contentsAfterChange .getAmount() == 0 || contentsAfterChange .isSimilar(differenceShouldEqualThis)) &&
					(contentsBeforeChange == null || contentsBeforeChange.getAmount() == 0 || contentsBeforeChange.isSimilar(differenceShouldEqualThis)))
				{
					final int amountBeforeChange = contentsBeforeChange == null ? 0 : contentsBeforeChange.getAmount();
					final int amountAfterChange  = contentsAfterChange  == null ? 0 : contentsAfterChange .getAmount();
					if (Math.abs(amountAfterChange - amountBeforeChange) == this.differenceShouldEqualThis.getAmount())
					{
						JBChestLog.debugLog("Difference is correct!");
					}
					else
					{
						JBChestLog.debugLog("contentsBeforeChange="+contentsBeforeChange+" amountBeforeChange="+amountBeforeChange+" contentsAfterChange="+contentsAfterChange+" amountAfterChange="+amountAfterChange+" differenceShouldEqualThis="+differenceShouldEqualThis);
					}
				}
				else
				{
					JBChestLog.debugLog("contentsBeforeChange="+contentsBeforeChange+" contentsAfterChange="+contentsAfterChange+" differenceShouldEqualThis="+differenceShouldEqualThis);
				}
			}

			if (contentsBeforeChange != null && contentsBeforeChange.getAmount() != 0)
			{
				JBChestLog.debugLog(Utils.actorToString(actor) + " took " + contentsBeforeChange.getAmount() + " " + contentsBeforeChange.getType().name() + " fron container at " + containerBlock.getX() + "," + containerBlock.getY() + "," + containerBlock.getZ());
				ContainerDiffLog.get(containerBlock).newDiff(actor, actor2, contentsBeforeChange, ContainerDiffType.ITEM_REMOVED);
			}
			if (contentsAfterChange != null && contentsAfterChange.getAmount() != 0)
			{
				JBChestLog.debugLog(Utils.actorToString(actor) + " placed " + contentsAfterChange.getAmount() + " " + contentsAfterChange.getType().name() + " into container at " + containerBlock.getX() + "," + containerBlock.getY() + "," + containerBlock.getZ());
				ContainerDiffLog.get(containerBlock).newDiff(actor, actor2, contentsAfterChange, ContainerDiffType.ITEM_ADDED);
			}

			if (applyOppositeDifferenceToThisContainer != null)
			{
				if (contentsBeforeChange != null && contentsBeforeChange.getAmount() != 0)
				{
					JBChestLog.debugLog("According to opposite difference applier: "+Utils.actorToString(applyOppositeDifferenceToThisContainer_actor) + " placed " + contentsBeforeChange.getAmount() + " " + contentsBeforeChange.getType().name() + " fron container at " + applyOppositeDifferenceToThisContainer.getX() + "," + applyOppositeDifferenceToThisContainer.getY() + "," + applyOppositeDifferenceToThisContainer.getZ());
					ContainerDiffLog.get(applyOppositeDifferenceToThisContainer).newDiff(applyOppositeDifferenceToThisContainer_actor, applyOppositeDifferenceToThisContainer_actor2, contentsBeforeChange, ContainerDiffType.ITEM_ADDED);
				}
				if (contentsAfterChange != null && contentsAfterChange.getAmount() != 0)
				{
					JBChestLog.debugLog("According to opposite difference applier: "+Utils.actorToString(applyOppositeDifferenceToThisContainer_actor) + " took " + contentsAfterChange.getAmount() + " " + contentsAfterChange.getType().name() + " into container at " + applyOppositeDifferenceToThisContainer.getX() + "," + applyOppositeDifferenceToThisContainer.getY() + "," + applyOppositeDifferenceToThisContainer.getZ());
					ContainerDiffLog.get(applyOppositeDifferenceToThisContainer).newDiff(applyOppositeDifferenceToThisContainer_actor, applyOppositeDifferenceToThisContainer_actor2, contentsAfterChange, ContainerDiffType.ITEM_REMOVED);
				}
			}
		}
		catch (Exception e)
		{
			JBChestLog.errorLog("Failed to process ContainerDifferenceCheck due to exception: "+e.getMessage());
			e.printStackTrace();
		}
	}
}
