package org.yamcs.parameterarchive;

import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.rocksdb.RocksDBException;
import org.yamcs.utils.ByteArrayUtils;
import org.yamcs.utils.DatabaseCorruptionException;
import org.yamcs.utils.IntArray;
import org.yamcs.utils.VarIntUtil;
import org.yamcs.yarch.rocksdb.AscendingRangeIterator;
import org.yamcs.yarch.rocksdb.Tablespace;
import org.yamcs.yarch.rocksdb.YRDB;
import org.yamcs.yarch.rocksdb.protobuf.Tablespace.TablespaceRecord;

import static org.yamcs.yarch.rocksdb.RdbStorageEngine.TBS_INDEX_SIZE;

/**
 * Stores a map between
 * List&lt;parameter_id&gt; and ParameterGroup_id.
 * 
 * Stores data in the main tablespace database
 * key = tbsIndex,ParameterGroup_id
 * value = SortedVarArray of parameter_id
 * 
 * 
 * Backed by RocksDB
 * 
 * @author nm
 *
 */
public class ParameterGroupIdDb {
    final Tablespace tablespace;
    final String yamcsInstance;
    int highestPgId = 0;

    int tbsIndex;
    Map<IntArray, Integer> pg2pgidCache = new HashMap<>();

    ParameterGroupIdDb(String yamcsInstance, Tablespace tablespace) throws RocksDBException {
        this.tablespace = tablespace;
        this.yamcsInstance = yamcsInstance;
        readDb();
    }

    /**
     * Creates (if not already there) a new ParameterGroupId for the given parameter id array
     * 
     * @param s
     * @return the parameterGroupId for the given parameter id array
     * @throws RocksDBException
     */
    public synchronized int createAndGet(IntArray s) throws RocksDBException {
        Integer pgid = pg2pgidCache.get(s);
        if (pgid == null) {
            int x = ++highestPgId;
            pgid = x;
            byte[] key = new byte[TBS_INDEX_SIZE + 4];
            ByteArrayUtils.encodeInt(tbsIndex, key, 0);
            ByteArrayUtils.encodeInt(x, key, TBS_INDEX_SIZE);
            byte[] v = VarIntUtil.encodeDeltaIntArray(s);
            tablespace.putData(key, v);
            pg2pgidCache.put(s, pgid);
        }
        return pgid;
    }

    private void readDb() throws RocksDBException {
        List<TablespaceRecord> trl = tablespace.filter(TablespaceRecord.Type.PARCHIVE_PGID2PG, yamcsInstance,
                trb -> true);
        if (trl.size() > 1) {
            throw new DatabaseCorruptionException("Multiple records of type "
                    + TablespaceRecord.Type.PARCHIVE_PGID2PG.name() + " found for instance " + yamcsInstance);
        }
        TablespaceRecord tr;
        if (trl.isEmpty()) {
            TablespaceRecord.Builder trb = TablespaceRecord.newBuilder()
                    .setType(TablespaceRecord.Type.PARCHIVE_PGID2PG);
            tr = tablespace.createMetadataRecord(yamcsInstance, trb);
        } else {
            tr = trl.get(0);
        }
        this.tbsIndex = tr.getTbsIndex();
        YRDB db = tablespace.getRdb();
        byte[] range = new byte[TBS_INDEX_SIZE];
        ByteArrayUtils.encodeInt(tr.getTbsIndex(), range, 0);

        try (AscendingRangeIterator it = new AscendingRangeIterator(db.newIterator(), range, range)) {
            while (it.isValid()) {
                byte[] key = it.key();
                int pgid = ByteArrayUtils.decodeInt(key, TBS_INDEX_SIZE);

                if (highestPgId < pgid) {
                    highestPgId = pgid;
                }

                IntArray svil = VarIntUtil.decodeDeltaIntArray(it.value());
                pg2pgidCache.put(svil, pgid);
                it.next();
            }
        }
    }

    /**
     * return the members of the pg group.
     * <p>
     * Throws {@link IllegalArgumentException} if the group does not exist
     */
    public IntArray getParameterGroup(int pg) {
        for (Map.Entry<IntArray, Integer> e : pg2pgidCache.entrySet()) {
            if (e.getValue() == pg) {
                return e.getKey();
            }
        }
        throw new IllegalArgumentException("No parameter group with the id " + pg);
    }

    @Override
    public String toString() {
        return pg2pgidCache.toString();
    }

    public void print(PrintStream out) {
        for (Map.Entry<IntArray, Integer> e : pg2pgidCache.entrySet()) {
            out.println(e.getValue() + ": " + e.getKey());
        }
    }

    /**
     * get all parameter group ids for the parameters from which this parameter id is part of
     * 
     * @param pid
     * @return the parameter group ids for the parameters groups that contain the pid
     */
    public synchronized int[] getAllGroups(int pid) {
        IntArray r = new IntArray();
        for (Map.Entry<IntArray, Integer> e : pg2pgidCache.entrySet()) {
            IntArray s = e.getKey();
            if (s.binarySearch(pid) >= 0) {
                r.add(e.getValue());
            }
        }
        return r.toArray();
    }
}
