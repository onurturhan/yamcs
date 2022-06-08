package org.yamcs.cfdp;

import org.yamcs.yarch.Bucket;

/**
 * A Put.request is a primitive that requests data delivery from a source to a destination
 * 
 * @author ddw
 *
 */

public class PutRequest extends CfdpRequest {
    private long sourceId;
    private long destinationId;
    private String targetPath;
    private byte[] fileData;
    private String objectName;
    private Bucket bucket;
    private boolean overwrite;
    private boolean createpath;
    private long checksum;
    private boolean acknowledged;
    private boolean closureRequested = false;

    public PutRequest(long sourceId, long destinationId, String objectName, String targetPath, boolean overwrite,
            boolean acknowledged, boolean closureRequested, boolean createpath, Bucket b, byte[] data) {
        super(CfdpRequestType.PUT);
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.objectName = objectName;
        this.targetPath = targetPath;
        this.overwrite = overwrite;
        this.createpath = createpath;
        this.bucket = b;
        this.fileData = data;
        this.acknowledged = acknowledged;
        this.closureRequested = closureRequested;
        this.checksum = ChecksumCalculator.calculateChecksum(data);
    }

    public long getSourceId() {
        return this.sourceId;
    }

    public long getDestinationId() {
        return destinationId;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getTargetPath() {
        return targetPath;
    }
	
	public void setTargetPath(String path) {
		targetPath = path;
	}

    public int getFileLength() {
        return fileData.length;
    }

    public long getChecksum() {
        return this.checksum;
    }

    public byte[] getFileData() {
        return fileData;
    }

    public Bucket getBucket() {
        return bucket;
    }

    public boolean getOverwrite() {
        return overwrite;
    }

    public boolean isAcknowledged() {
        return this.acknowledged;
    }

    public boolean getCreatePath() {
        return createpath;
    }

    public boolean isClosureRequested() {
        return closureRequested;
    }

    public void setClosureRequested(boolean closureRequested) {
        this.closureRequested = closureRequested;
    }
}
