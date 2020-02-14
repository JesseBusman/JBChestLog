package com.jesbus.jbchestlog;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Date;
import java.util.UUID;

import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class Utils
{
	static Container inventoryAndSlot_to_container(Inventory inventory, int inventorySlot)
	{
		if (inventory.getHolder() instanceof DoubleChest)
		{
			final DoubleChest doubleChest = (DoubleChest)inventory.getHolder();
			if (inventorySlot >= 0 && inventorySlot <= 26)
			{
				return (Container)doubleChest.getLeftSide();
			}
			else if (inventorySlot >= 27 && inventorySlot <= 53)
			{
				return (Container)doubleChest.getRightSide();
			}
			else
			{
				throw new Error("WTF: slot "+inventorySlot+" is out of range for double chest");
			}
		}
		else if (inventory.getHolder() instanceof Container)
		{
			return (Container)inventory.getHolder();
		}
        else
        {
            return null;
        }
	}

    static void writeRomanNumeral(int number, StringBuilder string)
    {
        if (number < 0)
        {
            string.append('-');
            number = -number;
        }

        if (number > 8000)
        {
            string.append(number);
            return;
        }

        while (number >= 1000) { string.append('M'); number -= 1000; }
        while (number >= 900) { string.append("CM"); number -= 900; }
        while (number >= 500) { string.append('D'); number -= 500; }
        while (number >= 400) { string.append("CD"); number -= 400; }
        while (number >= 100) { string.append('C'); number -= 100; }
        while (number >= 90) { string.append("XC"); number -= 90; }
        while (number >= 50) { string.append('L'); number -= 50; }
        while (number >= 40) { string.append("IL"); number -= 40; }
        while (number >= 10) { string.append('X'); number -= 10; }
        while (number >= 9) { string.append("IX"); number -= 9; }
        while (number >= 5) { string.append('V'); number -= 5; }
        while (number >= 4) { string.append("IV"); number -= 4; }
        while (number >= 1) { string.append('I'); number -= 1; }
    }

    static String getRomanNumeral(int number)
    {
        StringBuilder sb = new StringBuilder();
        writeRomanNumeral(number, sb);
        return sb.toString();
    }

    static String enchantmentToName(Enchantment e)
    {
        return WordUtils.capitalize(e.getName().replace('_', ' ').toLowerCase());
    }

    static String itemTypeToName(ItemStack itemStack)
    {
        return WordUtils.capitalize(itemStack.getType().name().replace('_', ' ').toLowerCase());
    }

	static String actorToString(final UUID actor)
	{
        return Utils.actorToString(actor, actor);
    }
    static String actorToString(final UUID actor, final UUID actor2)
    {
        String actor2genitive;
        if (actor.equals(actor2))
        {
            actor2genitive = "";
        }
        else
        {
            final OfflinePlayer player = Bukkit.getOfflinePlayer(actor2);
            if (player == null) actor2genitive = "Unknown's ";
            else actor2genitive = player.getName()+"'s ";
        }

        if (actor.equals(Constants.EXPLOSION_UUID)) return "[explosion]";
        
		if (actor.equals(Constants.HOPPER_ABOVE_THIS_CONTAINER_UUID)) return "[received]"; // return "[hopper above this container]";
		if (actor.equals(Constants.HOPPER_BELOW_THIS_CONTAINER_UUID)) return "[taken by hopper]"; // return "[hopper below this container]";
        if (actor.equals(Constants.HOPPER_MINECART_ABOVE_THIS_CONTAINER_UUID)) return "[received from "+actor2genitive+"hopper cart]"; // return "[hopper minecart above this container]";
        if (actor.equals(Constants.HOPPER_MINECART_BELOW_THIS_CONTAINER_UUID)) return "[taken by "+actor2genitive+"hopper cart]"; //"[hopper minecart below this container]";

        if (actor.equals(Constants.CONTAINER_BELOW_THIS_HOPPER_UUID)) return "[pushed]"; // return "[container below this hopper]";
        if (actor.equals(Constants.CONTAINER_BELOW_THIS_HOPPER_MINECART_UUID)) return "[pushed]"; // return "[container below this hopper minecart]";
        if (actor.equals(Constants.CONTAINER_ABOVE_THIS_HOPPER_UUID)) return "[pulled]"; // return "[container above this hopper]";
        if (actor.equals(Constants.CONTAINER_ABOVE_THIS_HOPPER_MINECART_UUID)) return "[pulled]"; // return "[container above this hopper minecart]";

        if (actor.equals(Constants.FURNACE_SMELTED_UUID)) return "[smelted]";
        if (actor.equals(Constants.FURNACE_BURNED_UUID)) return "[burned fuel]";
        if (actor.equals(Constants.BREWING_STAND_CONSUMED_UUID)) return "[brew fuel]";
        if (actor.equals(Constants.BREWING_STAND_BREWED_UUID)) return "[brewed]";


        if (actor.equals(Constants.DROPPED_ITEM_UUID)) return "[picked up item]";

		OfflinePlayer player = Bukkit.getOfflinePlayer(actor);
		if (player != null) return player.getName();
		return "["+actor2genitive+actor.toString()+"]";
	}

    static void readExactly(FileChannel fc, int amount, byte[] buff, int buffPos) throws IOException, EOFException
    {
        if (amount < 0) throw new IOException("readExactly called with amount="+amount);

        final long startPos = fc.position();

        final int theAmount = amount;

        final ByteBuffer bb = ByteBuffer.wrap(buff);

        while (amount > 0)
        {
            long amountRead = fc.read(bb);
            if (amountRead == -1) throw new EOFException("Reached end of stream before exact amount could be read!");
            if (amountRead > amount) throw new IOException("readExactly is.read read "+amountRead+" but it told to read up to "+amount);
            amount -= amountRead;
            buffPos += amountRead;
        }

        if (amount != 0) throw new IOException("Assertion failed. amount != 0 in readExactly");

        final long endPos = fc.position();

        if (endPos - startPos != theAmount) throw new IOException("Assertion failed. readExactly didn't read exactly "+theAmount+" bytes. startPos="+startPos+" endPos="+endPos);
    }

    private static void writeExactly(final FileChannel fc, final byte[] buff) throws IOException
    {
        final long startPos = fc.position();

        int totalAmountWritten = 0;
        while (totalAmountWritten < buff.length)
        {
            final int amount = buff.length - totalAmountWritten;
            final int amountWritten = fc.write(ByteBuffer.wrap(buff, totalAmountWritten, amount));
            if (amountWritten > amount) throw new IOException("readExactly fc.write wrote "+amountWritten+" but it told to write up to "+amount);
            totalAmountWritten += amountWritten;
        }

        if (totalAmountWritten != buff.length) throw new IOException("wtfff");

        if (fc.position() != startPos + buff.length) throw new IOException("WTF writeExactly didn't write exactly");
    }

    static void writeLong(FileChannel fc, long num) throws IOException
    {
        writeExactly(fc, longToBytes(num));
    }

    static FileLock tryLockFile(FileChannel fc)
    {
        JBChestLog.debugLog("Trying to lock file "+fc.toString());
        try
        {
            final FileLock fl = fc.lock();
            JBChestLog.debugLog("Locked file "+fc.toString());
            return fl;
        }
        catch (IOException e)
        {
            return null;
        }
    }
    static FileLock lockFile(FileChannel fc)
    {
        JBChestLog.debugLog("Waiting to lock file "+fc.toString());
        int i = 0;
        while (true)
        {
            try
            {
                final FileLock fl = fc.lock();
                JBChestLog.debugLog("Locked file "+fc.toString());
                return fl;
            }
            catch (Exception e)
            {
                try { Thread.sleep(10); } catch (Exception ee) { }
                i++;
                if (i == 100)
                {
                    JBChestLog.errorLog("lockFile is waiting more than 1 second");
                    e.printStackTrace();
                }
                continue;
            }
        }
    }

    static long readLong(FileChannel fc) throws IOException
    {
        final long startPos = fc.position();

        byte[] buff8 = new byte[8];
        readExactly(fc, 8, buff8, 0);

        final long endPos = fc.position();

        if (endPos - startPos != 8) throw new IOException("readLong did not read 8 bytes: "+startPos+" "+endPos);

        return bytesToLong(buff8);
    }

    static byte[] longToBytes(long x)
    {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(0, x);
        return buffer.array().clone();
    }

    static long bytesToLong(byte[] bytes)
    {
        if (bytes.length != 8) throw new Error("bytesToLong called with byte[] with length "+bytes.length);

        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.put(bytes, 0, 8);
        buffer.flip();
        return buffer.getLong();
    }

    static long bytesToLong(byte[] bytes, int start)
    {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.put(bytes, start, 8);
        buffer.flip();
        return buffer.getLong();
    }

    static UUID bytesToUUID(byte[] bytes)
    {
        return new UUID(bytesToLong(bytes, 0), bytesToLong(bytes, 8));
    }

    static byte[] UUIDtoBytes(UUID uuid)
    {
        byte[] ret = new byte[16];
        {
            byte[] ms = longToBytes(uuid.getMostSignificantBits());
            for (int i=0; i<8; i++) ret[i] = ms[i];
        }
        {
            byte[] ls = longToBytes(uuid.getMostSignificantBits());
            for (int i=0; i<8; i++) ret[8+i] = ls[i];
        }
        return ret;
    }
    
    // This is how real men write tests in Java
    static
    {
        final long testNum = 5857892478924579822L;
        if (bytesToLong(longToBytes(testNum)) != testNum) throw new Error("bytesToLong or longToBytes doesn't work!!");
    }

    static String timestampToText(long timestamp)
    {
        final long msAgo = new Date().getTime() - timestamp;
        final long secAgo = msAgo / 1000;
        final long minAgo = secAgo / 60;
        final long hrAgo = minAgo / 60;
        final long daysAgo = hrAgo / 24;

        String dateTimeString;
        if (secAgo < 120) dateTimeString = secAgo + " sec";
        else if (minAgo < 120) dateTimeString = minAgo + " min";
        else if (hrAgo < 72) dateTimeString = hrAgo + " hours";
        else dateTimeString = daysAgo + " days";

        return dateTimeString;
    }

}