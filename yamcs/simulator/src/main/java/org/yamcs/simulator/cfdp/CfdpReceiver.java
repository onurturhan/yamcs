package org.yamcs.simulator.cfdp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.cfdp.DataFile;
import org.yamcs.cfdp.DataFileSegment;
import org.yamcs.cfdp.FileDirective;
import org.yamcs.cfdp.pdu.AckPacket;
import org.yamcs.cfdp.pdu.CfdpHeader;
import org.yamcs.cfdp.pdu.CfdpPacket;
import org.yamcs.cfdp.pdu.ConditionCode;
import org.yamcs.cfdp.pdu.EofPacket;
import org.yamcs.cfdp.pdu.FileDataPacket;
import org.yamcs.cfdp.pdu.FileDirectiveCode;
import org.yamcs.cfdp.pdu.FinishedPacket;
import org.yamcs.cfdp.pdu.MetadataPacket;
import org.yamcs.cfdp.pdu.NakPacket;
import org.yamcs.cfdp.pdu.SegmentRequest;
import org.yamcs.simulator.AbstractSimulator;
import org.yamcs.cfdp.pdu.AckPacket.FileDirectiveSubtypeCode;
import org.yamcs.cfdp.pdu.AckPacket.TransactionStatus;
import org.yamcs.cfdp.pdu.FinishedPacket.FileStatus;

/**
 * Receives CFDP files.
 * <p>
 * It doesn't store them but just print a message at the end of the reception.
 * 
 * @author nm
 *
 */
public class CfdpReceiver {
    private static final Logger log = LoggerFactory.getLogger(CfdpReceiver.class);
    final AbstractSimulator simulator;
    final File dataDir;
    private DataFile cfdpDataFile = null;
    List<SegmentRequest> missingSegments;
    MetadataPacket metadata;

    public CfdpReceiver(AbstractSimulator simulator, File dataDir) {
        this.simulator = simulator;
        this.dataDir = dataDir;
    }

    public void processCfdp(ByteBuffer buffer) {
        CfdpPacket packet = CfdpPacket.getCFDPPacket(buffer);
        if (packet.getHeader().isFileDirective()) {
            processFileDirective(packet);
        } else {
            processFileData((FileDataPacket) packet);
        }
    }

    private void processFileDirective(CfdpPacket packet) {
        switch (((FileDirective) packet).getFileDirectiveCode()) {
        case EOF:
            // 1 in 2 chance that we did not receive the EOF packet
            if (Math.random() > 0.5) {
                log.warn("EOF CFDP packet received and dropped (data loss simulation)");
                break;
            }
            processEofPacket((EofPacket) packet);
            break;
        case FINISHED:
            log.info("Finished CFDP packet received");
            break;
        case ACK:
            log.info("ACK CFDP packet received");
            break;
        case METADATA:
            log.info("Metadata CFDP packet received");
            metadata = (MetadataPacket) packet;
            long packetLength = metadata.getFileLength();
            cfdpDataFile = new DataFile(packetLength);
            missingSegments = null;
            break;
        case NAK:
            log.info("NAK CFDP packet received");
            break;
        case PROMPT:
            log.info("Prompt CFDP packet received");
            break;
        case KEEP_ALIVE:
            log.info("KeepAlive CFDP packet received");
            break;
        default:
            log.error("CFDP packet of unknown type received");
            break;
        }
    }

    private void processEofPacket(EofPacket packet) {
        ConditionCode code = packet.getConditionCode();
        log.info("EOF CFDP packet received code={}, sending back ACK (EOF) packet", code);

        CfdpHeader header = new CfdpHeader(
                true,
                true,
                false,
                false,
                packet.getHeader().getEntityIdLength(),
                packet.getHeader().getSequenceNumberLength(),
                packet.getHeader().getSourceId(),
                packet.getHeader().getDestinationId(),
                packet.getHeader().getSequenceNumber());
        AckPacket EofAck = new AckPacket(
                FileDirectiveCode.EOF,
                FileDirectiveSubtypeCode.FINISHED_BY_WAYPOINT_OR_OTHER,
                code,
                TransactionStatus.ACTIVE,
                header);
        transmitCfdp(EofAck);
        if (code != ConditionCode.NO_ERROR) {
            return;
        }
        log.info("ACK (EOF) sent, delaying a bit and sending Finished packet");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // checking the file completeness;
        missingSegments = cfdpDataFile.getMissingChunks();
        if (missingSegments.isEmpty()) {
            saveFile();

            log.info("Sending back finished PDU");
            header = new CfdpHeader(
                    true, // file directive
                    true, // towards sender
                    false, // not acknowledged
                    false, // no CRC
                    packet.getHeader().getEntityIdLength(),
                    packet.getHeader().getSequenceNumberLength(),
                    packet.getHeader().getSourceId(),
                    packet.getHeader().getDestinationId(),
                    packet.getHeader().getSequenceNumber());

            FinishedPacket finished = new FinishedPacket(ConditionCode.NO_ERROR,
                    true, // data complete
                    FileStatus.SUCCESSFUL_RETENTION,
                    null,
                    header);

            transmitCfdp(finished);
        } else {
            header = new CfdpHeader(
                    true, // file directive
                    true, // towards sender
                    false, // not acknowledged
                    false, // no CRC
                    packet.getHeader().getEntityIdLength(),
                    packet.getHeader().getSequenceNumberLength(),
                    packet.getHeader().getSourceId(),
                    packet.getHeader().getDestinationId(),
                    packet.getHeader().getSequenceNumber());

            NakPacket nak = new NakPacket(
                    missingSegments.get(0).getSegmentStart(),
                    missingSegments.get(missingSegments.size() - 1).getSegmentEnd(),
                    missingSegments,
                    header);

            log.info("File not complete ({} segments missing), sending NAK", missingSegments.size());
            transmitCfdp(nak);
        }
    }

    private void saveFile() {
        try {
            File f = new File(dataDir, sanitize(metadata.getDestinationFilename()));
            try (FileOutputStream fw = new FileOutputStream(f)) {
                fw.write(cfdpDataFile.getData());
                log.info("CFDP file saved in {}", f.getAbsolutePath());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    String sanitize(String filename) {
        return filename.replace("/", "_");
    }

    private void processFileData(FileDataPacket packet) {
        if (missingSegments == null || missingSegments.isEmpty()) {
            // we're not in "resending mode"
            // 1 in 5 chance to 'lose' the packet
            if (Math.random() > 0.8) {
                log.warn("Received and dropped (data loss simulation) {}", packet);
            } else {
                log.info("Received {}", packet);
                cfdpDataFile.addSegment(new DataFileSegment(packet.getOffset(), packet.getData()));
            }
        } else {
            // we're in resending mode, no more data loss
            cfdpDataFile.addSegment(new DataFileSegment(packet.getOffset(), packet.getData()));
            missingSegments = cfdpDataFile.getMissingChunks();
            log.info("Received missing data: {} still missing: {}", packet, missingSegments.size());
            if (missingSegments.isEmpty()) {
                saveFile();

                CfdpHeader header = new CfdpHeader(
                        true, // file directive
                        true, // towards sender
                        false, // not acknowledged
                        false, // no CRC
                        packet.getHeader().getEntityIdLength(),
                        packet.getHeader().getSequenceNumberLength(),
                        packet.getHeader().getSourceId(),
                        packet.getHeader().getDestinationId(),
                        packet.getHeader().getSequenceNumber());

                FinishedPacket finished = new FinishedPacket(
                        ConditionCode.NO_ERROR,
                        true, // data complete
                        FileStatus.SUCCESSFUL_RETENTION,
                        null,
                        header);

                transmitCfdp(finished);
            }
        }

    }

    protected void transmitCfdp(CfdpPacket packet) {
        simulator.transmitCfdp(packet);
    }
}
