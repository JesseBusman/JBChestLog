package com.jesbus.jbchestlog;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;
import java.util.List;

class ContainerDiffLogFile
{
    private File dir = null;
    private File file = null;

    long totalDiffsSavedToFile = -1;
    long totalDiffsSavedCorrectlyToFile = -1;


    private boolean fileCorruptedShouldBeFixed = false;

    static List<ContainerDiffLogFile> filesThatShouldBeFixed = new ArrayList<>();

	final ContainerDiffLog cdl;
	ContainerDiffLogFile(ContainerDiffLog cdl)
	{
		this.cdl = cdl;
	}

    private void getFilePathIfNotHave()
    {
        if (this.dir == null)
        {
            this.dir = new File(JBChestLog.dataFolder.toPath().toString() + "/" + cdl.world + "/" + cdl.x + "/" + cdl.y);
        }

        if (this.file == null)
        {
            this.file = new File(this.dir.toPath().toString() + "/" + cdl.z);
        }
    }

    private void createFileIfNotExists() throws IOException
    {
        getFilePathIfNotHave();

        synchronized (dir)
        {
            synchronized (file)
            {
                if (!dir.exists())
                {
                    dir.mkdirs();
                }
                
                if (!file.exists())
                {
                    file.createNewFile();
                    if (cdl.totalDiffs == -1) cdl.totalDiffs = 0;
                    this.totalDiffsSavedToFile = 0;
                    this.totalDiffsSavedCorrectlyToFile = 0;

                    @SuppressWarnings("resource")
                    final FileChannel fc = new RandomAccessFile(file, "rw").getChannel();
                    final FileLock fileLock = Utils.lockFile(fc);

                    fc.position(0);
                    Utils.writeLong(fc, 0);

                    fileLock.release();
                    fc.close();
                }
            }
        }
	}

    private void markThisFileAsCorrupted()
    {
        this.fileCorruptedShouldBeFixed = true;
        synchronized (filesThatShouldBeFixed)
        {
            filesThatShouldBeFixed.add(this);
        }
    }

    static void tryFixAll()
    {
        synchronized (filesThatShouldBeFixed)
        {
            for (int i=0; i<filesThatShouldBeFixed.size(); i++)
            {
                final ContainerDiffLogFile file = filesThatShouldBeFixed.get(i);
                file.tryFixFile();
            }
        }
    }
    
    private boolean tryFixFile()
    {
        if (!this.fileCorruptedShouldBeFixed)
        {
            JBChestLog.errorLog("fixFile() called on "+cdl.x+" "+cdl.y+" "+cdl.z+" but fileCorruptedShouldBeFixed == false");
            synchronized (filesThatShouldBeFixed)
            {
                filesThatShouldBeFixed.remove(this);
            }
            return true;
        }

        JBChestLog.errorLog("Trying to fix chest log file "+cdl.x+" "+cdl.y+" "+cdl.z);
        synchronized (file)
        {
            synchronized (cdl.diffs)
            {
                RandomAccessFile raf = null;
                FileChannel fc = null;
                FileLock fileLock = null;

                try
                {
                    try
                    {
                        raf = new RandomAccessFile(file, "rw");
                    }
                    catch (FileNotFoundException e)
                    {
                        JBChestLog.errorLog("FileNotFoundException! Trying to create the file...");

                        createFileIfNotExists();

                        raf = new RandomAccessFile(file, "rw");
                    }

                    fc = raf.getChannel();

                    fileLock = Utils.tryLockFile(fc);
                    if (fileLock == null)
                    {
                        try { fc.close(); } catch (Exception eee) { }
                        try { raf.close(); } catch (Exception eee) { }

                        return false;
                    }

                    try
                    {
                        Utils.readLong(fc);
                    }
                    catch (EOFException e)
                    {
                        JBChestLog.errorLog("File has <8 bytes. Writing 0x0000000000000000 at the start.");
                        fc.position(0);
                        Utils.writeLong(fc, 0);
                    }

                    if (fc.position() != 8) throw new Error("wtf is this bs");

                    long totalValidDiffsInFile = 0;
                    long filePositionAfterValidDiffsInFile = 8;

                    while (cdl.diffs.size() == 0 || totalValidDiffsInFile < cdl.diffs.get(0).diffIndex)
                    {
                        try
                        {
                            ContainerDiff cd = new ContainerDiff(fc);
                            if (cd.diffIndex != totalValidDiffsInFile)
                            {
                                JBChestLog.errorLog("Found diff with index "+cd.diffIndex+" where index "+totalValidDiffsInFile+" should have been.");
                                break;
                            }
                        }
                        catch (EOFException e)
                        {
                            JBChestLog.errorLog("Failed to read diff index "+totalValidDiffsInFile+": end of file encountered.");
                            break;
                        }
                        catch (Exception e)
                        {
                            JBChestLog.errorLog("Failed to read diff index "+totalValidDiffsInFile+": "+e.getMessage());
                            e.printStackTrace();
                            break;
                        }

                        totalValidDiffsInFile++;
                        filePositionAfterValidDiffsInFile = fc.position();
                    }

                    JBChestLog.errorLog("The file has "+totalValidDiffsInFile+" valid diffs.");

                    if (cdl.diffs.size() != 0)
                    {
                        if (totalValidDiffsInFile < cdl.diffs.get(0).diffIndex)
                        {
                            JBChestLog.errorLog("We don't have diff indices "+totalValidDiffsInFile+" through "+(cdl.diffs.get(0).diffIndex-1)+" in file or in RAM! They have been permanently lost.");
                        }

                        cdl.diffs.get(0).diffIndex = totalValidDiffsInFile;
                        cdl.fixIndexes(0);
                    }

                    JBChestLog.errorLog("Setting file's totalDiffs header to "+totalValidDiffsInFile);
                    cdl.totalDiffs = totalValidDiffsInFile + cdl.diffs.size();
                    totalDiffsSavedCorrectlyToFile = totalValidDiffsInFile;
                    totalDiffsSavedToFile = totalValidDiffsInFile;

                    fc.position(0);
                    Utils.writeLong(fc, totalValidDiffsInFile);

                    JBChestLog.errorLog("Setting file's size to "+filePositionAfterValidDiffsInFile);
                    fc.truncate(filePositionAfterValidDiffsInFile);

                    JBChestLog.errorLog("File fixer succeeded!");

                    this.fileCorruptedShouldBeFixed = false;
                    
                    try { fileLock.release(); } catch (Exception eee) { }
                    try { fc.close(); } catch (Exception eee) { }
                    try { raf.close(); } catch (Exception eee) { }

                    return true;
                }
                catch (Exception e)
                {
                    JBChestLog.errorLog("File fixer failed: "+e.getMessage());
                    e.printStackTrace();
                    try { fileLock.release(); } catch (Exception eee) { }
                    try { fc.close(); } catch (Exception eee) { }
                    try { raf.close(); } catch (Exception eee) { }
                    return false;
                }
            }
        }
    }

	
    private static boolean seekToNextDiff(FileChannel fc) throws IOException
    {
        try
        {
            byte[] buff = new byte[1];
            while (true)
            {
                Utils.readExactly(fc, 1, buff, 0);
                if (buff[0] == ((Constants.MAGIC_DELIMITER >>  0) & 0xFF) ||
                    buff[0] == ((Constants.MAGIC_DELIMITER >> 56) & 0xFF))
                {
                    fc.position(fc.position() - 1);
                    if (Utils.readLong(fc) == Constants.MAGIC_DELIMITER)
                    {
                        fc.position(fc.position() - 8);
                        return true;
                    }
                }
                else
                {
                    continue;
                }
            }
        }
        catch (EOFException e)
        {
            return false;
        }
    }
    
    /*
    private long diffIndexToPositionInFile(final long diffIndex) throws IOException
    {
        if (diffIndex < 0) throw new Error("diffIndexToPositionInFile was called with negative diffIndex "+diffIndex);

        createFileIfNotExists();

        final FileChannel fc = new RandomAccessFile(file, "rw").getChannel();
        final FileLock fileLock = Utils.lockFile(fc);

        if (!seekToDiffIndex(fc, diffIndex))
        {
            fileLock.release();
            fc.close();

            return -1;
        }
        else
        {
            final long ret = fc.position();

            fileLock.release();
            fc.close();

            return ret;
        }
    }
    */

    private boolean seekToDiffIndex(final FileChannel fc, final long diffIndex) throws IOException
    {
        if (diffIndex < 0) throw new Error("seekToDiffIndex was called with negative diffIndex "+diffIndex);

        // If we're looking for diff index 0, it's easy. It always starts at byte 8
        if (diffIndex == 0)
        {
            fc.position(8);
            return true;
        }

        fc.position(0);

        final long amountDiffsCurrentlyStored = Utils.readLong(fc);

        // don't make this >= because diffIndexToPositionInFile may be used to find the position after the last diff
        if (diffIndex > amountDiffsCurrentlyStored)
        {
            return false;
        }

        final long totalFileLength = file.length();

        // First, jump approximately to the diff we seek.
        try
        {
            fc.position(Math.max(0, (diffIndex-2) * totalFileLength / amountDiffsCurrentlyStored));
        }
        catch (Exception e)
        {
            if (Constants.DEBUGGING) e.printStackTrace();
        }
        
        // Make sure we're not at the end of the file
        {
            int timesJumpedBack = 0;
            while (!seekToNextDiff(fc))
            {
                timesJumpedBack++;
                final long currentPosition = fc.position();
                final long skipBack = timesJumpedBack * totalFileLength / amountDiffsCurrentlyStored;
                fc.position(Math.max(0, currentPosition - skipBack));
            }
        }

        // Seek backward until we're before the one we want
        {
            int timesJumpedBack = 0;
            while (true)
            {
                final long magicDelimiter = Utils.readLong(fc);
                if (magicDelimiter != Constants.MAGIC_DELIMITER)
                {
                    JBChestLog.errorLog("Chest log file "+cdl.x+" "+cdl.y+" "+cdl.z+" is corrupted. While seeking backwards, excepted magic delimiter "+Long.toHexString(Constants.MAGIC_DELIMITER)+" but got "+Long.toHexString(magicDelimiter)+"!");
                    this.markThisFileAsCorrupted();
                    return false;
                }
                final long currentDiffIndex = Utils.readLong(fc);

                if (currentDiffIndex == diffIndex)
                {
                    fc.position(fc.position() - 16);
                    return true;
                }
                else if (currentDiffIndex + 1 == amountDiffsCurrentlyStored &&
                            diffIndex == amountDiffsCurrentlyStored)
                {
                    fc.position(fc.position() - 16);
                    break;
                }
                else if (currentDiffIndex < diffIndex)
                {
                    if (!seekToNextDiff(fc))
                    {
                        this.markThisFileAsCorrupted();
                        JBChestLog.errorLog("Chest log file "+cdl.x+" "+cdl.y+" "+cdl.z+" is corrupted. It claims to have more diffs ("+amountDiffsCurrentlyStored+") than it actually has ("+currentDiffIndex+")!");
                        return false;
                    }
                    break;
                }
                else
                {
                    innerLoop:
                    do
                    {
                        timesJumpedBack++;
                        final long currentPosition = fc.position();
                        final long skipBack = timesJumpedBack * (8 + totalFileLength / amountDiffsCurrentlyStored);
                        fc.position(Math.max(0, currentPosition - skipBack));

                        if (!seekToNextDiff(fc) && fc.position() <= 8)
                        {
                            this.markThisFileAsCorrupted();
                            JBChestLog.errorLog("Chest log file "+cdl.x+" "+cdl.y+" "+cdl.z+" is corrupted. It claims to have "+amountDiffsCurrentlyStored+" diffs but it doesn't have any!");
                            return false;
                        }
                        else
                        {
                            break innerLoop;
                        }
                    }
                    while (true);
                }
            }

            JBChestLog.debugLog("seekToDiffIndex("+diffIndex+"): #2 jumped back "+timesJumpedBack+" times");
        }
        
        // Seek forward until we got it :)
        while (seekToNextDiff(fc))
        {
            final long magicDelimiter = Utils.readLong(fc);
            if (magicDelimiter != Constants.MAGIC_DELIMITER)
            {
                JBChestLog.errorLog("Chest log file "+cdl.x+" "+cdl.y+" "+cdl.z+" is corrupted. While seeking forward, excepted magic delimiter "+Long.toHexString(Constants.MAGIC_DELIMITER)+" but got "+Long.toHexString(magicDelimiter)+"!");
                this.markThisFileAsCorrupted();
                return false;
            }

            final long currentDiffIndex = Utils.readLong(fc);

            if (currentDiffIndex == diffIndex)
            {
                fc.position(fc.position() - 16);
                return true;
            }
            if (currentDiffIndex + 1 == diffIndex)
            {
                fc.position(fc.position() - 16);
                new ContainerDiff(fc);
                return true;
            }
            if (currentDiffIndex > diffIndex)
            {
                break;
            }
        }

        this.markThisFileAsCorrupted();

        JBChestLog.errorLog("Chest log file "+cdl.x+" "+cdl.y+" "+cdl.z+" is corrupted. It claims to have "+amountDiffsCurrentlyStored+" diffs, but it doesn't have diff index "+diffIndex);
        return false;
    }


    boolean isSaved()
    {
        if (this.fileCorruptedShouldBeFixed)
        {
            return false;
        }
        else
        {
            synchronized (cdl.diffs)
            {
                return ((cdl.totalDiffs == this.totalDiffsSavedCorrectlyToFile) && (cdl.totalDiffs == this.totalDiffsSavedToFile));
            }
        }
    }

    
    void waitSave() throws Exception
    {
        int waitCounter = 0;
        while (true)
        {
            if ((!this.fileCorruptedShouldBeFixed || this.tryFixFile()) && this.trySave())
            {
                return;
            }
            else
            {
                try { Thread.sleep(10); } catch (Exception e) { }
                waitCounter++;
                if (waitCounter == 100)
                {
                    JBChestLog.errorLog("waitSave "+cdl.x+" "+cdl.y+" "+cdl.z+" waited > 1s");
                    throw new Exception("waitSave "+cdl.x+" "+cdl.y+" "+cdl.z+" waited > 1s");
                }
            }
        }
    }

    boolean trySave()
    {
        try
        {
            getFilePathIfNotHave();
            
            synchronized (file)
            {
                synchronized (cdl.diffs)
                {
                    // If we have nothing to do, we're done
                    if (isSaved())
                    {
                        return true;
                    }
                    
                    this.createFileIfNotExists();
                    
                    // If the file is corrupted, try to fix it
                    if (this.fileCorruptedShouldBeFixed)
                    {
                        if (!this.tryFixFile())
                        {
                            return false;
                        }
                    }
                    
                    cdl.fixIndexes(0);
                                        
                    final FileChannel fc = new RandomAccessFile(file, "rw").getChannel();
                    final FileLock fileLock = Utils.tryLockFile(fc);
                    
                    if (fileLock == null)
                    {
                        fc.close();
                        return false;
                    }
                    
                    if (!seekToDiffIndex(fc, this.totalDiffsSavedCorrectlyToFile))
                    {
                        fileLock.release();
                        fc.close();
                        JBChestLog.errorLog("trySave(): failed, could not find position of diff index "+this.totalDiffsSavedCorrectlyToFile);
                        return false;
                    }
                    
                    for (int i=0; i<cdl.diffs.size(); i++)
                    {
                        if (cdl.diffs.get(i).diffIndex >= this.totalDiffsSavedCorrectlyToFile)
                        {
                            cdl.diffs.get(i).serializeAndWrite(fc);
                        }
                    }

                    fc.truncate(fc.position());

                    fc.position(0);
                    Utils.writeLong(fc, cdl.totalDiffs);

                    this.totalDiffsSavedToFile = cdl.totalDiffs;
                    this.totalDiffsSavedCorrectlyToFile = cdl.totalDiffs;
                    
                    fileLock.release();
                    fc.close();

                    return true;
                }
            }
        }
        catch (Exception e)
        {
            JBChestLog.errorLog("save() on "+cdl.x+" "+cdl.y+" "+cdl.z+" failed catastrophically: "+e.getMessage());
            e.printStackTrace();

            if (cdl.diffs.size() != 0)
            {
                this.totalDiffsSavedCorrectlyToFile = Math.min(this.totalDiffsSavedCorrectlyToFile, cdl.diffs.get(0).diffIndex);
            }

            return false;
        }
    }

    void waitLoad(final long maxAmount) throws IOException
    {
        int waitCounter = 0;
        while (true)
        {
            if ((!this.fileCorruptedShouldBeFixed || this.tryFixFile()) && this.tryLoad(maxAmount))
            {
                return;
            }
            else
            {
                try { Thread.sleep(10); } catch (Exception e) { }
                waitCounter++;
                if (waitCounter == 100)
                {
                    JBChestLog.errorLog("waitLoad "+cdl.x+" "+cdl.y+" "+cdl.z+" waited > 1s");
                }
            }
        }
    }

    boolean tryLoad(final long maxAmount)
    {
        try
        {
            getFilePathIfNotHave();

            synchronized (file)
            {
                synchronized (cdl.diffs)
                {
                    // If there isn't anything more to load because we have everything, quit
                    if (cdl.diffs.size() == cdl.totalDiffs)
                    {
                        return true;
                    }
                    if (cdl.totalDiffs != -1 && cdl.diffs.size() > cdl.totalDiffs)
                    {
                        JBChestLog.errorLog("Assertion failed: cdl.diffs.size() > cdl.totalDiffs");
                    }

                    // If there isn't anything more to load because there is no file, quit
                    if (!file.exists())
                    {
                        if (cdl.totalDiffs == -1) cdl.totalDiffs = 0;
                        this.totalDiffsSavedCorrectlyToFile = 0;
                        this.totalDiffsSavedToFile = 0;
                        return true;
                    }

                    final FileChannel fc = new RandomAccessFile(file, "rw").getChannel();
                    final FileLock fileLock = Utils.tryLockFile(fc);

                    if (fileLock == null)
                    {
                        fc.close();
                        return false;
                    }

                    final long amountOfDiffsInFile = Utils.readLong(fc);
                    
                    if (cdl.totalDiffs == -1 && this.totalDiffsSavedCorrectlyToFile == -1 && this.totalDiffsSavedToFile == -1)
                    {
                        cdl.totalDiffs = amountOfDiffsInFile;
                        this.totalDiffsSavedCorrectlyToFile = amountOfDiffsInFile;
                        this.totalDiffsSavedToFile = amountOfDiffsInFile;
                    }
                    else if (cdl.totalDiffs == -1 || this.totalDiffsSavedCorrectlyToFile == -1 || this.totalDiffsSavedToFile == -1)
                    {
                        JBChestLog.errorLog("This should never happen :( totalDiffs="+cdl.totalDiffs+" totalDiffsSavedCorrectlyToFile="+this.totalDiffsSavedCorrectlyToFile+" totalDiffsSavedToFile"+this.totalDiffsSavedToFile);

                        fileLock.release();
                        fc.close();

                        return false;
                    }

                    final long lastDiffIndexToRead = cdl.totalDiffs - 1 - cdl.diffs.size();
                    final long firstDiffIndexToRead = Math.max(0, lastDiffIndexToRead - maxAmount + 1);
                    final long amountOfDiffsToRead = lastDiffIndexToRead - firstDiffIndexToRead + 1;

                    // If we don't have anything to do, quit
                    if (amountOfDiffsToRead <= 0)
                    {
                        return true;
                    }

                    if (cdl.diffs.size() != 0 && lastDiffIndexToRead != cdl.diffs.get(0).diffIndex - 1) throw new IOException("tryLoad() assertion failed! lastDiffIndexToRead != diffs.get(0).diffIndex - 1");
                    else if (cdl.diffs.size() == 0 && lastDiffIndexToRead != cdl.totalDiffs - 1) throw new IOException("tryLoad() assertion failed! lastDiffIndexToRead != this.totalDiffs - 1");

                    if (lastDiffIndexToRead >= totalDiffsSavedCorrectlyToFile) throw new IOException("tryLoad() assertion failed! lastDiffIndexToRead >= totalDiffsSavedCorrectlyToFile");

                    if (!seekToDiffIndex(fc, firstDiffIndexToRead))
                    {
                        JBChestLog.errorLog("load("+maxAmount+"): failed, could not find position of diff index "+firstDiffIndexToRead);
                        fileLock.release();
                        fc.close();
                        return false;
                    }

                    final List<ContainerDiff> newDiffs = new ArrayList<>();

                    for (int i=0; i<amountOfDiffsToRead; i++)
                    {
                        newDiffs.add(new ContainerDiff(fc));
                    }

                    newDiffs.addAll(cdl.diffs);
                    cdl.diffs.clear();
                    cdl.diffs.addAll(newDiffs);

                    fileLock.release();
                    fc.close();

                    return true;
                }
            }
        }
        catch (Exception e)
        {
            JBChestLog.errorLog("Exception thrown in load("+maxAmount+"): "+e.getMessage());
            e.printStackTrace();

            return false;
        }
    }
}
