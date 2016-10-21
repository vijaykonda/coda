package io.shunters.coda.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TODO: Rolling offset index file to offset.
 */
public class OffsetIndex {
    private static Logger log = LoggerFactory.getLogger(OffsetIndex.class);

    private final ReentrantLock lock = new ReentrantLock();

    private long baseOffset;
    private FileChannel fileChannel;
    private long size = 0;
    private long lastOffset = 0;

    public OffsetIndex(File file, long baseOffset)
    {
        this.baseOffset = baseOffset;
        try {
            if(!file.exists()) {
                file.createNewFile();
            }

            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            fileChannel = raf.getChannel();
            size = raf.length();

            log.info("initial size [{}]", size);

            readLastOffset();
            log.info("last offset [{}]", lastOffset);
        }catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public long getLastOffset()
    {
        return this.lastOffset;
    }

    private ByteBuffer getMMap(int position, long length)
    {
        try {
            return fileChannel.map(FileChannel.MapMode.READ_WRITE, position, length).duplicate();
        }catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private int getEntryCount()
    {
        return (int)(size / 8);
    }

    private void readLastOffset()
    {
        if(this.getEntryCount() > 0)
        {
            this.lastOffset = baseOffset + this.getDeltaOffset(getMMap(0, size), this.getEntryCount() -1);
        }
    }

    public void printEntries()
    {
        if(size == 0)
        {
            log.info("no entries to print");
        }

        ByteBuffer buffer = getMMap(0, size).duplicate();
        for(int i = 0; i < this.getEntryCount(); i++)
        {
            long firstOffset = baseOffset + getDeltaOffset(buffer, i);
            int position = getPosition(buffer, i);
            log.info("(firstOffset, position) = ({}, {})", firstOffset, position);
        }
    }

    public void add(long firstOffset, int position)
    {
        lock.lock();
        try {
            if(lastOffset < firstOffset) {
                fileChannel.position((int)size);
                ByteBuffer buffer = ByteBuffer.allocate(8);
                buffer.putInt((int) (firstOffset - baseOffset));
                buffer.putInt(position);
                buffer.rewind();
                fileChannel.write(buffer);

                size += 8;
                lastOffset = firstOffset;
            }
            else
            {
                // search for the entry index whose offset value is greater than the target offset(firstOffset) and difference between them is smallest.
                ByteBuffer buffer = this.getMMap(0, size).duplicate();
                int first = 0;
                int last = (int)this.getEntryCount() -1;
                while(first <= last)
                {
                    int middle = (first + last) / 2;
                    long retOffset = baseOffset + getDeltaOffset(buffer, middle);
                    if(retOffset < firstOffset)
                    {
                        first = middle + 1;
                    }
                    else if(retOffset > firstOffset)
                    {
                        last = middle -1;
                    }
                    else
                    {
                        throw new RuntimeException("Offset [" + firstOffset + "] Already exists.");
                    }
                }

                int entryIndex = first;

                // slice the buffer from the chosen entry index.
                byte[] lastBytes = new byte[(this.getEntryCount() * 8) - (entryIndex * 8)];
                buffer.position(entryIndex * 8);
                buffer.get(lastBytes);

                ByteBuffer lastByteBuffer = ByteBuffer.wrap(lastBytes);
                lastByteBuffer.rewind();

                // put new offset entry.
                fileChannel.position(entryIndex * 8);

                ByteBuffer newBuffer = ByteBuffer.allocate(8);
                newBuffer.putInt((int) (firstOffset - baseOffset));
                newBuffer.putInt(position);
                newBuffer.rewind();
                fileChannel.write(newBuffer);

                // after that, append the sliced buffer.
                fileChannel.write(lastByteBuffer);

                size += 8;
            }
        }catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        finally {
            lock.unlock();
        }
    }


    private int getEntryIndex(ByteBuffer buffer, long offset)
    {
        int first = 0;
        int last = this.getEntryCount() -1;
        while(first <= last)
        {
            int middle = (first + last) / 2;
            long retOffset = baseOffset + getDeltaOffset(buffer, middle);
            if(retOffset < offset)
            {
                first = middle + 1;
            }
            else if(retOffset > offset)
            {
                last = middle -1;
            }
            else
            {
                return middle;
            }
        }

        return last;
    }

    public OffsetPosition getFirstOffsetPosition(long offset)
    {
        if(size == 0)
        {
            return null;
        }
        // offset does not exist in this index file.
        else if(offset > lastOffset)
        {
            return null;
        }

        ByteBuffer buffer = this.getMMap(0, size).duplicate();
        int entryIndex = this.getEntryIndex(buffer, offset);

        long firstOffset = baseOffset + getDeltaOffset(buffer, entryIndex);
        int position = getPosition(buffer, entryIndex);

        return new OffsetPosition(firstOffset, position);
    }

    private int getDeltaOffset(ByteBuffer buffer, int n)
    {
        return buffer.getInt(n * 8);
    }

    private int getPosition(ByteBuffer buffer, int n)
    {
        return buffer.getInt(n * 8 + 4);
    }


    public static class OffsetPosition
    {
        private long offset;
        private int position;

        public OffsetPosition(long offset, int position)
        {
            this.offset = offset;
            this.position = position;
        }

        public long getOffset() {
            return offset;
        }

        public int getPosition() {
            return position;
        }
    }
}