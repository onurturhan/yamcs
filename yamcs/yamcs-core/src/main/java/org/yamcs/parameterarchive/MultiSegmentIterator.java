package org.yamcs.parameterarchive;

import static org.yamcs.parameterarchive.ParameterArchive.getIntervalEnd;
import static org.yamcs.parameterarchive.ParameterArchive.getIntervalStart;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.yamcs.parameterarchive.ParameterArchive.Partition;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.DecodingException;
import org.yamcs.yarch.rocksdb.AscendingRangeIterator;
import org.yamcs.yarch.rocksdb.DbIterator;
import org.yamcs.yarch.rocksdb.DescendingRangeIterator;

/**
 * Same as {@link SegmentIterator} but provides segments for multiple parameters from the same group in one step
 *
 */
public class MultiSegmentIterator implements ParchiveIterator<MultiParameterValueSegment> {
    private final int parameterGroupId;
    private final ParameterId[] pids;
    final byte[] rangeStart;
    final byte[] rangeStop;

    ParameterArchive parchive;

    SegmentEncoderDecoder segmentEncoder = new SegmentEncoderDecoder();
    List<Partition> partitions;

    // iterates over partitions
    Iterator<Partition> topIt;

    // iterates over segments in one partition
    SubIterator subIt;

    final boolean ascending, retrieveEngValues, retrieveRawValues, retrieveParameterStatus;

    MultiParameterValueSegment curValue;

    // iterates over the segments in the realtime filler
    Iterator<ParameterValueSegment> rtIterator;
    final RealtimeArchiveFiller rtfiller;

    public MultiSegmentIterator(ParameterArchive parchive, ParameterId[] pids, int parameterGroupId,
            ParameterRequest req) {
        this.pids = pids;
        this.parameterGroupId = parameterGroupId;
        this.parchive = parchive;
        this.ascending = req.isAscending();
        this.retrieveEngValues = req.isRetrieveEngineeringValues();
        this.retrieveRawValues = req.isRetrieveRawValues();
        this.retrieveParameterStatus = req.isRetrieveParameterStatus();

        partitions = parchive.getPartitions(getIntervalStart(req.start), getIntervalEnd(req.stop), req.ascending);
        topIt = partitions.iterator();
        int timeParaId = parchive.getParameterIdDb().getTimeParameterId();
        rangeStart = new SegmentKey(timeParaId, parameterGroupId, ParameterArchive.getIntervalStart(req.start),
                SegmentKey.TYPE_ENG_VALUE).encode();
        rangeStop = new SegmentKey(timeParaId, parameterGroupId, ParameterArchive.getIntervalStart(req.stop),
                SegmentKey.TYPE_ENG_VALUE).encode();

        rtfiller = parchive.getRealtimeFiller();
        /*
         * if (rtfiller != null && req.isAscending()) {
         * rtIterator = rtfiller.getSegments(parameterId, parameterGroupId, ascending).iterator();
         * }
         */
        next();
    }

    public boolean isValid() {
        return curValue != null;
    }

    public MultiParameterValueSegment value() {
        return curValue;
    }

    public void next() {
        /*
         * if (ascending && rtIterator != null) {
         * if (rtIterator.hasNext()) {
         * curValue = rtIterator.next();
         * return;
         * } else {
         * rtIterator = null;
         * }
         * }
         */
        subIt = getPartitionIterator();
        if (subIt != null) {
            curValue = subIt.value();
            subIt.next();
            return;
        } else {
            curValue = null;
        }
        /*
         * if (!ascending && rtfiller != null) {
         * if (rtIterator == null) {
         * rtIterator = rtfiller.getSegments(parameterId, parameterGroupId, ascending).iterator();
         * }
         * if (rtIterator.hasNext()) {
         * curValue = rtIterator.next();
         * }
         * }
         */
    }

    private SubIterator getPartitionIterator() {
        while (subIt == null || !subIt.isValid()) {
            if (topIt.hasNext()) {
                Partition p = topIt.next();
                close(subIt);
                subIt = new SubIterator(p);
            } else {
                close(subIt);
                return null;
            }
        }
        return subIt;
    }

    /**
     * Close the underlying rocks iterator if not already closed
     */
    public void close() {
        close(subIt);
    }

    private void close(SubIterator pit) {
        if (pit != null) {
            pit.close();
        }
    }

    public int getParameterGroupId() {
        return parameterGroupId;
    }

    class SubIterator {
        final Partition partition;
        private SegmentKey currentKey;
        SegmentEncoderDecoder segmentEncoder = new SegmentEncoderDecoder();
        SortedTimeSegment currentTimeSegment;

        DbIterator dbIterator;
        boolean valid;

        public SubIterator(Partition partition) {
            this.partition = partition;
            RocksIterator iterator;
            try {
                iterator = parchive.getIterator(partition);
            } catch (RocksDBException | IOException e) {
                throw new ParameterArchiveException("Failed to create iterator", e);
            }
            if (ascending) {
                dbIterator = new AscendingRangeIterator(iterator, rangeStart, rangeStop);
            } else {
                dbIterator = new DescendingRangeIterator(iterator, rangeStart, rangeStop);
            }
            next();
        }

        public void next() {
            if (!dbIterator.isValid()) {
                valid = false;
                return;
            }
            valid = true;
            currentKey = SegmentKey.decode(dbIterator.key());
            try {
                currentTimeSegment = (SortedTimeSegment) segmentEncoder.decode(dbIterator.value(),
                        currentKey.segmentStart);
            } catch (DecodingException e) {
                throw new DatabaseCorruptionException("Cannot decode time segment", e);
            }

            if (ascending) {
                dbIterator.next();
            } else {
                dbIterator.prev();
            }
        }

        SegmentKey key() {
            return currentKey;
        }

        MultiParameterValueSegment value() {
            if (!valid) {
                throw new NoSuchElementException();
            }

            MultiParameterValueSegment pvs = new MultiParameterValueSegment(currentTimeSegment);
            pvs.engValueSegments = new ValueSegment[pids.length];
            if (retrieveRawValues) {
                pvs.rawValueSegments = new ValueSegment[pids.length];
            }
            if (retrieveParameterStatus) {
                pvs.parameterStatusSegments = new ParameterStatusSegment[pids.length];
            }

            long segStart = currentKey.segmentStart;
            try (RocksIterator it = parchive.getIterator(partition)) {
                for (int i = 0; i < pids.length; i++) {
                    int pid = pids[i].getPid();
                    SegmentKey key = new SegmentKey(pid, parameterGroupId, segStart, (byte) 0);
                    it.seek(key.encode());
                    if (!it.isValid()) {
                        throw new DatabaseCorruptionException(
                                "Cannot find any record for parameter id " + pid + " at start " + segStart);
                    }
                    while (it.isValid()) {
                        key = SegmentKey.decode(it.key());
                        if (key.parameterId != pid || key.parameterGroupId != parameterGroupId) {
                            break;
                        }
                        byte type = key.type;
                        if (key.segmentStart != segStart) {
                            break;
                        }
                        if ((type == SegmentKey.TYPE_ENG_VALUE) && (retrieveEngValues || retrieveRawValues)) {
                            pvs.engValueSegments[i] = (ValueSegment) segmentEncoder.decode(it.value(), segStart);
                        }
                        if ((type == SegmentKey.TYPE_RAW_VALUE) && retrieveRawValues) {
                            pvs.rawValueSegments[i] = (ValueSegment) segmentEncoder.decode(it.value(), segStart);
                        }
                        if ((type == SegmentKey.TYPE_PARAMETER_STATUS) && retrieveParameterStatus) {
                            pvs.parameterStatusSegments[i] = (ParameterStatusSegment) segmentEncoder.decode(it.value(),
                                    segStart);
                        }
                        it.next();
                    }
                    if (retrieveRawValues && pvs.rawValueSegments[i] == null) {
                        pvs.rawValueSegments[i] = pvs.engValueSegments[i];
                    }
                }
            } catch (DecodingException e) {
                throw new DatabaseCorruptionException(e);
            } catch (RocksDBException | IOException e) {
                throw new ParameterArchiveException("Failded extracting data from the parameter archive", e);
            }

            return pvs;
        }

        boolean isValid() {
            return valid;
        }

        void close() {
            if (dbIterator != null) {
                dbIterator.close();
            }
        }
    }

}
