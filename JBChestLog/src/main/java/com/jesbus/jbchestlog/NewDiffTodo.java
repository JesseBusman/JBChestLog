package com.jesbus.jbchestlog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

class NewDiffTodo
{
	final long timestamp = new Date().getTime();
	final ContainerDiffLog cdl;
	final UUID actor;
	final UUID actor2;
	final ItemStack itemType;
	final long amount;

	NewDiffTodo(ContainerDiffLog cdl, UUID actor, UUID actor2, ItemStack itemType, long amount)
	{
		this.cdl = cdl;
		this.actor = actor;
		this.actor2 = actor2;
		this.itemType = itemType;
		this.amount = amount;

		synchronized (all)
		{
			all.add(this);
		}
	}

	private static List<NewDiffTodo> all = new ArrayList<>();

	static void tryProcessAll()
	{
		synchronized (all)
		{
			for (int i=0; i<all.size(); i++)
			{
				final NewDiffTodo ndt = all.get(i);

				synchronized (ndt.cdl.diffs)
				{
					// If we don't know how many diffs there are yet, find out
					if (ndt.cdl.totalDiffs == -1)
					{
						if (!ndt.cdl.cdl_file.tryLoad(1)) continue;
					}
					if (ndt.cdl.totalDiffs == -1)
					{
						JBChestLog.errorLog("Could not add diff, totalDiffs == -1 and could not load");
						continue;
					}

					ndt.cdl.diffs.add(new ContainerDiff(ndt.timestamp, ndt.actor, ndt.actor2, ndt.itemType, ndt.amount, ndt.cdl.totalDiffs));
					ndt.cdl.totalDiffs++;
		
					ndt.cdl.modifiedSinceLastSimplification = true;
					ndt.cdl.simplify(Math.min(16, ndt.cdl.diffs.size()));
				}

				all.remove(i);
				i--;
			}
		}
	}
	
	static void waitProcessAll()
	{
		int waitCounter = 0;
		while (true)
		{
			tryProcessAll();

			if (all.size() == 0) return;

			waitCounter++;
			if (waitCounter == 100)
			{
				JBChestLog.errorLog("waitProcessAll() waited > 1s");
			}
			try { Thread.sleep(10); } catch (Exception e) { }
		}
	}
}
