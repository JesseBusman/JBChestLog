package com.jesbus.jbchestlog;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;

class ContainerDiffLog
{
    private static final HashMap<UUID, HashMap<Integer, HashMap<Integer, HashMap<Integer, ContainerDiffLog>>>> wxyz_to_containerDiffLog = new HashMap<>();
    static final HashMap<UUID, HashMap<Integer, HashMap<Integer, HashMap<Integer, UUID>>>> wxyz_to_placer = new HashMap<>();

    final UUID world;
    final int x, y, z;

    final List<ContainerDiff> diffs = new ArrayList<>();
    public long totalDiffs = -1;

    boolean modifiedSinceLastSimplification = false;

    final ContainerDiffLogFile cdl_file;

    private ContainerDiffLog(UUID world, int x, int y, int z)
    {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.cdl_file = new ContainerDiffLogFile(this);

        synchronized (wxyz_to_containerDiffLog)
        {
            if (wxyz_to_containerDiffLog.containsKey(world) &&
                wxyz_to_containerDiffLog.get(world).containsKey(x) &&
                wxyz_to_containerDiffLog.get(world).get(x).containsKey(y) &&
                wxyz_to_containerDiffLog.get(world).get(x).get(y).containsKey(z))
            {
                throw new Error("This should never happen :(");
            }
        }
    }

    boolean trySaveRAM(boolean returnTrueToRemoveFromMapping)
    {
        synchronized (diffs)
        {
            synchronized (wxyz_to_containerDiffLog)
            {
                long now = new Date().getTime();
                if (cdl_file.isSaved() &&
                    (diffs.size() == 0 ||
                    now - diffs.get(diffs.size()-1).endTimestamp > Constants.SECONDS_BEFORE_CONTAINER_EVICTED_FROM_CACHE))
                {
                    if (returnTrueToRemoveFromMapping)
                    {
                        return true;
                    }
                    else
                    {
                        if (wxyz_to_containerDiffLog.get(this.world).get(this.x).get(this.y).get(z) != this) throw new Error("container log cache corruption");
                        wxyz_to_containerDiffLog.get(this.world).get(this.x).get(this.y).remove(z);
                        if (wxyz_to_containerDiffLog.get(this.world).get(this.x).get(this.y).size() == 0) wxyz_to_containerDiffLog.get(this.world).get(this.x).remove(y);
                        if (wxyz_to_containerDiffLog.get(this.world).get(this.x).size() == 0) wxyz_to_containerDiffLog.get(this.world).remove(x);
                    }
                }
                else
                {
                    for (int i=0; i < diffs.size() && diffs.get(i).diffIndex < this.cdl_file.totalDiffsSavedCorrectlyToFile; i++)
                    {
                        if (now - diffs.get(i).endTimestamp > Constants.SECONDS_BEFORE_CONTAINER_EVICTED_FROM_CACHE)
                        {
                            diffs.remove(i);
                            i--;
                        }
                        else
                        {
                            break;
                        }
                    }
                }
            }
        }
        return false;
    }

    void fixIndexes(int startIndex)
    {
        if (diffs.size() == 0)
        {
            return;
        }

        synchronized (diffs)
        {
            long firstDiffIndex = diffs.get(0).diffIndex;
            for (int i=Math.max(1, startIndex); i<diffs.size(); i++)
            {
                diffs.get(i).diffIndex = firstDiffIndex + i;
            }
        }
    }

    void deleteLogs()
    {
        synchronized (diffs)
        {
            diffs.clear();
            this.totalDiffs = 0;
            cdl_file.totalDiffsSavedCorrectlyToFile = 0;
        }
    }

    UUID tryFindWhoPlacedThisContainer()
    {
        synchronized (wxyz_to_placer)
        {
            if (wxyz_to_placer.containsKey(this.world) &&
                wxyz_to_placer.get(this.world).containsKey(this.x) &&
                wxyz_to_placer.get(this.world).get(this.x).containsKey(this.y) &&
                wxyz_to_placer.get(this.world).get(this.x).get(this.y).containsKey(this.z))
            {
                return wxyz_to_placer.get(this.world).get(this.x).get(this.y).get(this.z);
            }
        }
        synchronized (diffs)
        {
            int i = diffs.size()-1;
            while (true)
            {
                if (i == -1)
                {
                    final int oldSize = diffs.size();
                    /*try { */cdl_file.tryLoad(64); // } catch (IOException e) { JBChestLog.debugLog("findPlacer(): load(): error: "+e.getMessage()); e.printStackTrace(); }
                    final int newSize = diffs.size();
                    if (oldSize == newSize)
                    {
                        return null;
                    }
                    i += newSize - oldSize + 1;
                    continue;
                }
                else if (diffs.get(i).amount == Long.MAX_VALUE)
                {
                    final UUID ret = diffs.get(i).actor;

                    if (!wxyz_to_placer.containsKey(this.world))
                        wxyz_to_placer.put(this.world, new HashMap<Integer, HashMap<Integer, HashMap<Integer, UUID>>>());
                    
                    if (!wxyz_to_placer.get(this.world).containsKey(this.x))
                        wxyz_to_placer.get(this.world).put(this.x, new HashMap<Integer, HashMap<Integer, UUID>>());
                    
                    if (!wxyz_to_placer.get(this.world).get(this.x).containsKey(this.y))
                        wxyz_to_placer.get(this.world).get(this.x).put(this.y, new HashMap<Integer, UUID>());
                    
                    if (!wxyz_to_placer.get(this.world).get(this.x).get(this.y).containsKey(this.z))
                        wxyz_to_placer.get(this.world).get(this.x).get(this.y).put(this.z, ret);
                    
                    return ret;
                }
                else if (diffs.get(i).diffIndex == 0)
                {
                    return null;
                }
                else
                {
                    i--;
                    continue;
                }
            }
        }
    }

    static void deleteAllLogs()
    {
        synchronized (wxyz_to_containerDiffLog)
        {
            final Iterator<Map.Entry<UUID, HashMap<Integer, HashMap<Integer, HashMap<Integer, ContainerDiffLog>>>>> it1 = wxyz_to_containerDiffLog.entrySet().iterator();
            while (it1.hasNext())
            {
                final HashMap<Integer, HashMap<Integer, HashMap<Integer, ContainerDiffLog>>> xyz_to_containerDiffLog = it1.next().getValue();
                {
                    final Iterator<Map.Entry<Integer, HashMap<Integer, HashMap<Integer, ContainerDiffLog>>>> it2 = xyz_to_containerDiffLog.entrySet().iterator();
                    while (it2.hasNext())
                    {
                        final HashMap<Integer, HashMap<Integer, ContainerDiffLog>> yz_to_containerDiffLog = it2.next().getValue();
                        {
                            Iterator<Map.Entry<Integer, HashMap<Integer, ContainerDiffLog>>> it3 = yz_to_containerDiffLog.entrySet().iterator();
                            while (it3.hasNext())
                            {
                                final HashMap<Integer, ContainerDiffLog> z_to_containerDiffLog = it3.next().getValue();
                                {
                                    final Iterator<Map.Entry<Integer, ContainerDiffLog>> it4 = z_to_containerDiffLog.entrySet().iterator();
                                    while (it4.hasNext())
                                    {
                                        final ContainerDiffLog cdl = it4.next().getValue();

                                        if (cdl.trySaveRAM(true))
                                        {
                                            it4.remove();
                                        }
                                    }
                                }
                                if (z_to_containerDiffLog.size() == 0)
                                {
                                    it3.remove();
                                }
                            }
                        }
                        if (yz_to_containerDiffLog.size() == 0)
                        {
                            it2.remove();
                        }
                    }
                }
                if (xyz_to_containerDiffLog.size() == 0)
                {
                    it1.remove();
                }
            }
        }
    }

    void newDiffContainerDestroyed(UUID actor, Material containerType)
    {
        newDiff(actor, actor, new ItemStack(containerType, 1), Long.MIN_VALUE);
    }

    void newDiffContainerCreated(UUID actor, Material containerType)
    {
        newDiff(actor, actor, new ItemStack(containerType, 1), Long.MAX_VALUE);

		if (!wxyz_to_placer.containsKey(this.world))
			wxyz_to_placer.put(this.world, new HashMap<Integer, HashMap<Integer, HashMap<Integer, UUID>>>());
		
		if (!wxyz_to_placer.get(this.world).containsKey(this.x))
			wxyz_to_placer.get(this.world).put(this.x, new HashMap<Integer, HashMap<Integer, UUID>>());
		
		if (!wxyz_to_placer.get(this.world).get(this.x).containsKey(this.y))
			wxyz_to_placer.get(this.world).get(this.x).put(this.y, new HashMap<Integer, UUID>());
		
		if (!wxyz_to_placer.get(this.world).get(this.x).get(this.y).containsKey(this.z))
			wxyz_to_placer.get(this.world).get(this.x).get(this.y).put(this.z, actor);
    }

    void newDiff(UUID actor, ItemStack item, ContainerDiffType type)
    {
        this.newDiff(actor, actor, item, type);
    }

    void newDiff(UUID actor, UUID actor2, ItemStack item, ContainerDiffType type)
    {
        if (item.getAmount() == 0) return;
        else if (type == ContainerDiffType.ITEM_ADDED) this.newDiff(actor, actor2, new ItemStack(item), item.getAmount());
        else if (type == ContainerDiffType.ITEM_REMOVED) this.newDiff(actor, actor2, new ItemStack(item), - item.getAmount());
        else throw new Error("fatal error ContainerDiffType."+type.toString());
    }

    private void newDiff(UUID actor, UUID actor2, ItemStack itemType, long amount)
    {
        new NewDiffTodo(this, actor, actor2, itemType, amount);
    }

    void simplify(int maxAmount)
    {
        if (!modifiedSinceLastSimplification) return;
        modifiedSinceLastSimplification = false;

        int lowestArrayIndexChanged = Integer.MAX_VALUE;

        synchronized (diffs)
        {
            this.fixIndexes(0);
            
            for (int i = Math.max(0, diffs.size() - maxAmount); i < diffs.size(); i++)
            {
                final ContainerDiff iDiff = diffs.get(i);
                final ItemStack iType = iDiff.itemType;

                // Don't combine chest placements & deletions
                if (iDiff.amount == Long.MAX_VALUE || iDiff.amount == Long.MIN_VALUE)
                {
                    continue;
                }

                if (iDiff.amount == 0)
                {
                    cdl_file.totalDiffsSavedCorrectlyToFile = Math.min(cdl_file.totalDiffsSavedCorrectlyToFile, diffs.get(i).diffIndex);
                    this.totalDiffs--;
                    diffs.remove(i);
                    i--;
                    lowestArrayIndexChanged = i;
                    continue;
                }

                for (int j = i+1; j < diffs.size() && j < i+Constants.SIMPLIFIER_VIEW_RANGE; j++)
                {
                    final ContainerDiff jDiff = diffs.get(j);

                    // Don't combine chest placements & deletions
                    if (jDiff.amount == Long.MAX_VALUE || jDiff.amount == Long.MIN_VALUE)
                    {
                        continue;
                    }

                    final ItemStack jType = jDiff.itemType;
                    if (Math.abs(iDiff.endTimestamp - jDiff.startTimestamp) < Constants.MAX_SECONDS_DIFFERENCE_TO_MERGE_DIFFS * 1000 &&
                        iDiff.actor.equals(jDiff.actor) &&
                        iDiff.actor2.equals(jDiff.actor2) &&
                        iType.isSimilar(jType))
                    {
                        if (iDiff.diffIndex == jDiff.diffIndex)
                        {
                            break;
                        }
                        diffs.set(i, iDiff.add(jDiff));
                        diffs.remove(j);
                        cdl_file.totalDiffsSavedCorrectlyToFile = Math.min(cdl_file.totalDiffsSavedCorrectlyToFile, diffs.get(i).diffIndex);
                        this.totalDiffs--;
                        lowestArrayIndexChanged = i;
                        i--;
                        break;
                    }
                }
            }

            if (lowestArrayIndexChanged < Integer.MAX_VALUE) this.fixIndexes(lowestArrayIndexChanged);
        }
    }

    public static ContainerDiffLog get(Container container)
    {
        final UUID w = container.getWorld().getUID();
        final int x = container.getX(), y = container.getY(), z = container.getZ();

        ContainerDiffLog cdl;

        synchronized (wxyz_to_containerDiffLog)
        {
            if (!wxyz_to_containerDiffLog.containsKey(w)) wxyz_to_containerDiffLog.put(w, new HashMap<Integer, HashMap<Integer, HashMap<Integer, ContainerDiffLog>>>());
            if (!wxyz_to_containerDiffLog.get(w).containsKey(x)) wxyz_to_containerDiffLog.get(w).put(x, new HashMap<Integer, HashMap<Integer, ContainerDiffLog>>());
            if (!wxyz_to_containerDiffLog.get(w).get(x).containsKey(y)) wxyz_to_containerDiffLog.get(w).get(x).put(y, new HashMap<Integer, ContainerDiffLog>());
            if (!wxyz_to_containerDiffLog.get(w).get(x).get(y).containsKey(z)) wxyz_to_containerDiffLog.get(w).get(x).get(y).put(z, new ContainerDiffLog(w, x, y, z));

            cdl = wxyz_to_containerDiffLog.get(w).get(x).get(y).get(z);
        }

        cdl.cdl_file.tryLoad(16);

        return cdl;
    }

    public static void saveAllRAM()
    {
        //List<IOException> ioExceptions = new ArrayList<IOException>();

        synchronized (wxyz_to_containerDiffLog)
        {
            final Iterator<Map.Entry<UUID, HashMap<Integer, HashMap<Integer, HashMap<Integer, ContainerDiffLog>>>>> it1 = wxyz_to_containerDiffLog.entrySet().iterator();
            while (it1.hasNext())
            {
                final HashMap<Integer, HashMap<Integer, HashMap<Integer, ContainerDiffLog>>> xyz_to_containerDiffLog = it1.next().getValue();
                {
                    final Iterator<Map.Entry<Integer, HashMap<Integer, HashMap<Integer, ContainerDiffLog>>>> it2 = xyz_to_containerDiffLog.entrySet().iterator();
                    while (it2.hasNext())
                    {
                        final HashMap<Integer, HashMap<Integer, ContainerDiffLog>> yz_to_containerDiffLog = it2.next().getValue();
                        {
                            Iterator<Map.Entry<Integer, HashMap<Integer, ContainerDiffLog>>> it3 = yz_to_containerDiffLog.entrySet().iterator();
                            while (it3.hasNext())
                            {
                                final HashMap<Integer, ContainerDiffLog> z_to_containerDiffLog = it3.next().getValue();
                                {
                                    final Iterator<Map.Entry<Integer, ContainerDiffLog>> it4 = z_to_containerDiffLog.entrySet().iterator();
                                    while (it4.hasNext())
                                    {
                                        final ContainerDiffLog cdl = it4.next().getValue();

                                        if (cdl.trySaveRAM(true))
                                        {
                                            it4.remove();
                                        }
                                    }
                                }
                                if (z_to_containerDiffLog.size() == 0)
                                {
                                    it3.remove();
                                }
                            }
                        }
                        if (yz_to_containerDiffLog.size() == 0)
                        {
                            it2.remove();
                        }
                    }
                }
                if (xyz_to_containerDiffLog.size() == 0)
                {
                    it1.remove();
                }
            }
        }
        
        /*
        if (ioExceptions.size() >= 1)
        {
            JBChestLog.errorLog("Failed to save RAM from "+ioExceptions.size()+" container log objects due to IOExceptions:");
            for (int i=0; i<ioExceptions.size() && i<5; i++)
            {
                JBChestLog.errorLog("- "+ioExceptions.get(i).getMessage());
                ioExceptions.get(i).printStackTrace();
            }
        }
        */
    }

    public static void waitSaveAll()
    {
        trySaveAll();

        List<Exception> exceptions = new ArrayList<Exception>();

        synchronized (wxyz_to_containerDiffLog)
        {
            Iterator<Map.Entry<UUID, HashMap<Integer, HashMap<Integer, HashMap<Integer, ContainerDiffLog>>>>> it1 = wxyz_to_containerDiffLog.entrySet().iterator();
            while (it1.hasNext())
            {
                Iterator<Map.Entry<Integer, HashMap<Integer, HashMap<Integer, ContainerDiffLog>>>> it2 = it1.next().getValue().entrySet().iterator();
                while (it2.hasNext())
                {
                    Iterator<Map.Entry<Integer, HashMap<Integer, ContainerDiffLog>>> it3 = it2.next().getValue().entrySet().iterator();
                    while (it3.hasNext())
                    {
                        Iterator<Map.Entry<Integer, ContainerDiffLog>> it4 = it3.next().getValue().entrySet().iterator();
                        while (it4.hasNext())
                        {
                            ContainerDiffLog cdl = it4.next().getValue();

                            try
                            {
                                cdl.cdl_file.waitSave();
                            }
                            catch (Exception e)
                            {
                                exceptions.add(e);
                            }
                        }
                    }
                }
            }
        }

        if (exceptions.size() >= 1)
        {
            JBChestLog.errorLog("Failed to save "+exceptions.size()+" container log files due to Exceptions:");
            for (int i=0; i<exceptions.size() && i<5; i++)
            {
                JBChestLog.errorLog("- "+exceptions.get(i).getMessage());
                exceptions.get(i).printStackTrace();
            }
        }
    }

    public static void trySaveAll()
    {
        synchronized (wxyz_to_containerDiffLog)
        {
            Iterator<Map.Entry<UUID, HashMap<Integer, HashMap<Integer, HashMap<Integer, ContainerDiffLog>>>>> it1 = wxyz_to_containerDiffLog.entrySet().iterator();
            while (it1.hasNext())
            {
                Iterator<Map.Entry<Integer, HashMap<Integer, HashMap<Integer, ContainerDiffLog>>>> it2 = it1.next().getValue().entrySet().iterator();
                while (it2.hasNext())
                {
                    Iterator<Map.Entry<Integer, HashMap<Integer, ContainerDiffLog>>> it3 = it2.next().getValue().entrySet().iterator();
                    while (it3.hasNext())
                    {
                        Iterator<Map.Entry<Integer, ContainerDiffLog>> it4 = it3.next().getValue().entrySet().iterator();
                        while (it4.hasNext())
                        {
                            ContainerDiffLog cdl = it4.next().getValue();

                            cdl.cdl_file.trySave();
                        }
                    }
                }
            }
        }
    }

    public static void pluginStarting()
    {
        synchronized (wxyz_to_containerDiffLog)
        {
            if (wxyz_to_containerDiffLog.size() != 0 || wxyz_to_placer.size() != 0)
            {
                JBChestLog.errorLog("pluginStarting() calling while wxyz_to_containerDiffLog or wxyz_to_placer was not empty!");
                wxyz_to_containerDiffLog.clear();
                wxyz_to_placer.clear();
            }
        }
    }

    public static void pluginShuttingDown()
    {
        waitSaveAll();

        synchronized (wxyz_to_containerDiffLog)
        {
            wxyz_to_containerDiffLog.clear();
            wxyz_to_placer.clear();
        }
    }
}
