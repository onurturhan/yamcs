package org.yamcs.cfdp.pdu;

import java.nio.ByteBuffer;

import org.yamcs.cfdp.CfdpUtils;
import org.yamcs.cfdp.ChecksumType;
import org.yamcs.cfdp.FileDirective;
import org.yamcs.logging.Log;

public class MetadataPacket extends CfdpPacket implements FileDirective {
    static final Log log = new Log(MetadataPacket.class);

    private long fileSize;
    private LV sourceFileName;
    private LV destinationFileName;
    private boolean closureRequested;
    private ChecksumType checksumType;

    public MetadataPacket(boolean closureRequested, ChecksumType checksumType, int fileSize,
            String source, String destination, CfdpHeader header) {
        super(header);
        if (fileSize == 0) {
            throw new java.lang.UnsupportedOperationException("Unbound data size not yet implemented");
        }
        this.fileSize = fileSize;
        this.sourceFileName = new LV(source);
        this.destinationFileName = new LV(destination);
        this.closureRequested = closureRequested;
        this.checksumType = checksumType;
    }

    /**
     * decodes a metadata PDU from the buffer at the current position. The limit has to be set at the end of the PDU.
     */
    MetadataPacket(ByteBuffer buffer, CfdpHeader header) throws PduDecodingException {
        super(header);

        byte temp = buffer.get();
        closureRequested = (temp & 0x40) == 0x40;
        checksumType = ChecksumType.fromId(temp & 0x0F);
        if (checksumType == null) {
            throw new PduDecodingException("Invalid checksum type " + (temp & 0x0f), null);
        }

        this.fileSize = CfdpUtils.getUnsignedInt(buffer);
        if (this.fileSize == 0) {
            throw new java.lang.UnsupportedOperationException("Unbound data size not yet implemented");
        }
        this.sourceFileName = LV.readLV(buffer);
        this.destinationFileName = LV.readLV(buffer);

        if (buffer.hasRemaining()) {
            log.warn("Ignoring " + buffer.remaining() + " TLV bytes");
        }
        buffer.position(buffer.limit());
    }

    public long getFileLength() {
        return this.fileSize;
    }

    @Override
    public int getDataFieldLength() {
        int toReturn = 5 // first byte + File size
                + this.sourceFileName.getValue().length
                + this.destinationFileName.getValue().length;

        toReturn += 3;

        return toReturn;
    }

    public boolean closureRequested() {
        return closureRequested;
    }

    @Override
    protected void writeCFDPPacket(ByteBuffer buffer) {
        buffer.put(getFileDirectiveCode().getCode());
        int tmp = ((closureRequested ? 1 : 0) << 6) + checksumType.id();
        buffer.put((byte) tmp);

        CfdpUtils.writeUnsignedInt(buffer, fileSize);
        sourceFileName.writeToBuffer(buffer);
        destinationFileName.writeToBuffer(buffer);
    }

    @Override
    public FileDirectiveCode getFileDirectiveCode() {
        return FileDirectiveCode.METADATA;
    }

    public String getSourceFilename() {
        return sourceFileName.toString();
    }

    public String getDestinationFilename() {
        return new String(destinationFileName.getValue());
    }

    public ChecksumType getChecksumType() {
        return checksumType;
    }

    @Override
    public String toString() {
        return "MetadataPacket [closureRequested=" + closureRequested + ", fileSize=" + fileSize
                + ", checksumType=" + checksumType
                + ", sourceFileName=" + sourceFileName + ", destinationFileName=" + destinationFileName + "]";
    }

    public String toJson() {
        return " {\n"
                + "    header: " + header.toJson() + ", \n"
                + "    closureRequested:" + closureRequested + ",\n"
                + "    fileSize=" + fileSize + ",\n"
                + "    checksumType=" + checksumType + ",\n"
                + "    sourceFileName=" + sourceFileName + ",\n"
                + "    destinationFileName=" + destinationFileName + ",\n"
                + "}";
    }
}
