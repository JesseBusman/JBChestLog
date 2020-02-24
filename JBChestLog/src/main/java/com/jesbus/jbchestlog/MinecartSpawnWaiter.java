package com.jesbus.jbchestlog;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Vehicle;

class MinecartSpawnWaiter
{
	private static List<MinecartSpawnWaiter> all = new ArrayList<>();

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

		all.add(this);
	}

	static void notify(Vehicle vehicle)
	{
		final long now = new Date().getTime();
		for (int i=0; i<all.size(); i++)
		{
			final MinecartSpawnWaiter msw = all.get(i);
			final long dt = Math.abs(now - msw.timestamp);
			if (msw.world.equals(vehicle.getWorld().getUID()) &&
				msw.x == vehicle.getLocation().getBlockX() &&
				msw.y == vehicle.getLocation().getBlockY() &&
				msw.z == vehicle.getLocation().getBlockZ() &&
				dt < 10000)
			{
				JBChestLog.minecart_to_placer.put(vehicle.getUniqueId(), msw.placer);
				all.remove(i);
				return;
			}
			else if (dt > 30000)
			{
				all.remove(i);
				i--;
			}
		}
	}
}
