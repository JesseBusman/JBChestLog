package com.jesbus.jbchestlog;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

/**
// File format for ContainerDiffLog's:
long amountOfDiffs;
struct ContainerDiff
{
	long MAGIC_DELIMITER;
	long diffIndex;
	long startTimestamp;
	long endTimestamp;
	UUID actor;
	long amount;
	ItemStack itemType;
}[amountOfDiffs];
**/

class ContainerDiff
{
	long diffIndex;

	final long startTimestamp;
	final long endTimestamp;
	final UUID actor;
	final UUID actor2;
	final long amount;
	final ItemStack itemType;

	ContainerDiff(final FileChannel fc) throws IOException
	{
		JBChestLog.debugLog("Reading ContainerDiff starting at file position "+fc.position());

		// read:    long MAGIC_DELIMITER;
		final long magicDelimiter = Utils.readLong(fc);
		if (magicDelimiter != Constants.MAGIC_DELIMITER) throw new IOException("Chest log file is corrupted. Excepted magic delimiter "+Long.toHexString(Constants.MAGIC_DELIMITER)+" but got "+Long.toHexString(magicDelimiter)+"!");

		// read:    long diffIndex;
		this.diffIndex = Utils.readLong(fc);

		// read:    long startTimestamp;
		this.startTimestamp = Utils.readLong(fc);

		// read:    long endTimestamp;
		this.endTimestamp = Utils.readLong(fc);

		// read:    UUID actor;
		{
			final long ms = Utils.readLong(fc);
			final long ls = Utils.readLong(fc);
			this.actor = new UUID(ms, ls);
		}

		// read:    UUID actor2;
		{
			final long ms = Utils.readLong(fc);
			final long ls = Utils.readLong(fc);
			this.actor2 = new UUID(ms, ls);
		}

		// read:    long amount;
		this.amount = Utils.readLong(fc);

		// read:    ItemStack itemType;
		Object obj;

		@SuppressWarnings("resource")
		final BukkitObjectInputStream bois = new BukkitObjectInputStream(Channels.newInputStream(fc));
		try { obj = bois.readObject(); }
		catch (final ClassNotFoundException cnfe)
		{
			cnfe.printStackTrace();
			throw new IOException("Failed to read container diff from file! ClassNotFoundException in readObject(): "+cnfe.getMessage());
		}
		if (obj instanceof ItemStack) this.itemType = (ItemStack)obj;
		else throw new IOException("Deserialized object is not an ItemStack, it's a: "+obj.getClass().getName());

		JBChestLog.debugLog("ContainerDiff(is): diffIndex="+diffIndex+" amount="+amount+" type="+itemType.getType().name());
	}
	
	void serializeAndWrite(final FileChannel fc) throws IOException
	{
		JBChestLog.debugLog("Writing ContainerDiff starting at file position "+fc.position());

		Utils.writeLong(fc, Constants.MAGIC_DELIMITER);
		Utils.writeLong(fc, diffIndex);
		Utils.writeLong(fc, startTimestamp);
		Utils.writeLong(fc, endTimestamp);
		Utils.writeLong(fc, actor.getMostSignificantBits());
		Utils.writeLong(fc, actor.getLeastSignificantBits());
		Utils.writeLong(fc, actor2.getMostSignificantBits());
		Utils.writeLong(fc, actor2.getLeastSignificantBits());
		Utils.writeLong(fc, amount);
		//os.flush();

		@SuppressWarnings("resource")
		final BukkitObjectOutputStream boos = new BukkitObjectOutputStream(Channels.newOutputStream(fc));
		boos.writeObject(itemType);
		boos.flush();
	}

	ContainerDiff(final long timestamp, final UUID actor, final ItemStack itemType, final long amount, final long diffIndex)
	{
		this.diffIndex = diffIndex;
		this.startTimestamp = timestamp;
		this.endTimestamp = timestamp;
		this.actor = actor;
		this.actor2 = actor;
		this.itemType = itemType;
		this.amount = amount;
	}
	ContainerDiff(final long timestamp, final UUID actor, final UUID actor2, final ItemStack itemType, final long amount, final long diffIndex)
	{
		this.diffIndex = diffIndex;
		this.startTimestamp = timestamp;
		this.endTimestamp = timestamp;
		this.actor = actor;
		this.actor2 = actor2;
		this.itemType = itemType;
		this.amount = amount;
	}
	ContainerDiff(final long startTimestamp, final long endTimestamp, final UUID actor, final UUID actor2, final ItemStack itemType, final long amount, final long diffIndex)
	{
		if (startTimestamp > endTimestamp) throw new Error("startTimestamp > endtimestamp");
		this.diffIndex = diffIndex;
		this.startTimestamp = startTimestamp;
		this.endTimestamp = endTimestamp;
		this.actor = actor;
		this.actor2 = actor2;
		this.itemType = itemType;
		this.amount = amount;
	}

	ContainerDiff add(final ContainerDiff other)
	{
		if (Constants.DEBUGGING)
		{
			if (!this.actor.equals(other.actor)) throw new Error("Can't add ContainerDiff's with different actors.");
			if (!this.actor2.equals(other.actor2)) throw new Error("Can't add ContainerDiff's with different actor2's.");
			if (!this.itemType.isSimilar(other.itemType)) throw new Error("Can't add ContainerDiff's with different item types.");
			if (this.diffIndex >= other.diffIndex) throw new Error("When adding ContainerDiffs, left hand one must have the lower diffIndex");
		}

		return new ContainerDiff(Math.min(this.startTimestamp, other.startTimestamp), Math.max(this.endTimestamp, other.endTimestamp), this.actor, this.actor2, this.itemType, this.amount + other.amount, this.diffIndex);
	}
}
