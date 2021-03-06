package io.shunters.coda.store;

import io.shunters.coda.deser.AvroDeSer;
import io.shunters.coda.protocol.ClientServerSpec;
import org.apache.avro.generic.GenericRecord;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by mykidong on 2017-08-30.
 */
public class PartitionLog {

    private static Logger log = LoggerFactory.getLogger(PartitionLog.class);

    /**
     * avro de-/serialization.
     */
    private static AvroDeSer avroDeSer = AvroDeSer.getAvroDeSerSingleton();


    private long baseOffset;
    private File file;
    private FileChannel fileChannel;
    private OffsetIndex offsetIndex;
    private long size = 0;

    private final ReentrantLock lock = new ReentrantLock();

    public PartitionLog(File file, long baseOffset, OffsetIndex offsetIndex) {
        this.file = file;
        this.baseOffset = baseOffset;
        this.offsetIndex = offsetIndex;

        try {
            if (!file.exists()) {
                FileUtils.forceMkdir(file.getParentFile());
                file.createNewFile();
            }

            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            fileChannel = raf.getChannel();

            size = raf.length();
            log.info("initial size [{}]", size);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getFilePath() {
        return file.getAbsolutePath();
    }

    public OffsetIndex getOffsetIndex() {
        return offsetIndex;
    }

    public long getBaseOffset() {
        return baseOffset;
    }

    public long getSize() {
        return this.size;
    }

    private ByteBuffer getMMap(int position, long length) {
        try {
            return fileChannel.map(FileChannel.MapMode.READ_WRITE, position, length).duplicate();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public int add(long firstOffset, GenericRecord records, int recordSize) {
        int errorCode = 0;

        lock.lock();
        try {
            int currentPosition = (int) size;

            byte[] avroBytes = avroDeSer.serialize(records);

            int dataSize = avroBytes.length;

            // add offset position to offset index file.
            offsetIndex.add(firstOffset, currentPosition, dataSize, recordSize);

            fileChannel.position(currentPosition);

            ByteBuffer buffer = ByteBuffer.wrap(avroBytes);
            buffer.rewind();

            // add avro records to segment file.
            while (buffer.hasRemaining()) {
                fileChannel.write(buffer);
            }

            size += avroBytes.length;
        } catch (IOException e) {
            e.printStackTrace();

            // TODO: set errorCode if exception occurs.
        } finally {
            lock.unlock();
        }

        return errorCode;
    }

    public LogHandler.FetchRecord fetch(long fetchOffset, int maxBytes) {
        int errorCode = 0;
        long highwaterMarkOffset = 0; // TODO: set highwaterMarkOffset!

        if (size == 0) {
            return null;
        }

        int lengthSum = 0;

        long currentOffset = fetchOffset;

        List<GenericRecord> recordsList = new ArrayList<>();

        while (lengthSum < maxBytes) {
            OffsetIndex.OffsetPosition offsetPosition = offsetIndex.getFirstOffsetPosition(currentOffset);

            if (offsetPosition == null) {
                break;
            }

            int position = offsetPosition.getPosition();
            int dataSize = offsetPosition.getDataSize();

            lengthSum += dataSize;
            if (maxBytes < lengthSum) {
                break;
            }

            byte[] avroBytes = new byte[dataSize];

            // TODO: it may cause to be memory-exhausting???
            ByteBuffer buffer = getMMap(position, dataSize);
            buffer.rewind();
            buffer.get(avroBytes);

            GenericRecord records = avroDeSer.deserialize(ClientServerSpec.AVRO_SCHEMA_NAME_RECORDS, avroBytes);
            int recordSize = ((Collection<GenericRecord>) records.get("records")).size();

            currentOffset += recordSize;

            recordsList.add(records);
        }


        return new LogHandler.FetchRecord(errorCode, highwaterMarkOffset, recordsList);
    }

    @Override
    public String toString()
    {
        return "log file: " + this.getFilePath() + ", index file: " + this.offsetIndex.getFilePath();
    }
}

