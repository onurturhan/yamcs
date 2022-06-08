package org.yamcs.yarch.rocksdb;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.yamcs.yarch.DataType;
import org.yamcs.yarch.TableDefinition;


/**
 * Used in partitioning by value -  translates between value (Object) and byte[]
 * 
 * @author nm
 *
 */
public class ColumnValueSerializer {
    private final DataType valuePartitionDataType;
    public final static byte[] NULL_VALUE = {};

    public ColumnValueSerializer(DataType dt) {
        this.valuePartitionDataType = dt;
    }

    public ColumnValueSerializer(TableDefinition tableDefinition) {
        this(tableDefinition.getPartitioningSpec().getValueColumnType());
    }

    public byte[] objectToByteArray(Object value) {
        if(value==null){
            return NULL_VALUE;
        }

        if(value.getClass()==Integer.class) {
            ByteBuffer bb = ByteBuffer.allocate(4);
            bb.putInt((Integer)value);
            return bb.array();
        } else if(value.getClass()==Short.class) {
            ByteBuffer bb = ByteBuffer.allocate(2);
            bb.putShort((Short)value);
            return bb.array();			
        } else if(value.getClass()==Byte.class) {
            ByteBuffer bb = ByteBuffer.allocate(1);
            bb.put((Byte)value);
            return bb.array();
        } else if(value.getClass()==String.class) {
            return ((String)value).getBytes();
        } else {
            throw new IllegalArgumentException("partition on values of type "+value.getClass()+" not supported");
        }
    }
    /**
     * this is the reverse of the {@link #objectToByteArray(Object value)}
     */
    public Object byteArrayToObject(byte[] b) {
        if(Arrays.equals(b, NULL_VALUE)) {
            return null;
        }

        DataType dt = valuePartitionDataType;
        switch(dt.val) {
        case INT:
            if(b.length!=4) {
                throw new IllegalArgumentException("unexpected buffer of size "+b.length+" for a partition of type "+dt);
            }
            ByteBuffer bb = ByteBuffer.wrap(b);
            return bb.getInt();            
        case SHORT:
        case ENUM: //intentional fall-through
            if(b.length!=2) {
                throw new IllegalArgumentException("unexpected buffer of size "+b.length+" for a partition of type "+dt);
            }
            bb = ByteBuffer.wrap(b);
            return bb.getShort();             
        case BYTE:
            if(b.length!=1) {
                throw new IllegalArgumentException("unexpected buffer of size "+b.length+" for a partition of type "+dt);
            }
            bb = ByteBuffer.wrap(b);
            return bb.get();             
        case STRING:
            return new String(b);
        default:
            throw new IllegalArgumentException("partition on values of type "+dt+" not supported");
        }
    }

}
