package org.yamcs.tests;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.yamcs.ConfigurationException;
import org.yamcs.Processor;
import org.yamcs.TmPacket;
import org.yamcs.TmPacketProvider;
import org.yamcs.TmProcessor;
import org.yamcs.YConfiguration;
import org.yamcs.tctm.TmSink;
import org.yamcs.utils.GpsCcsdsTime;
import org.yamcs.utils.StringConverter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.SequenceContainer;

import com.google.common.util.concurrent.AbstractService;

/**
 * Generates packets according to the refmdb database.
 * 
 * Works either as a ProcessorService part of a processor or as TmPacketProvider in the integration tests
 * 
 * @author nm
 *
 */
public class RefMdbPacketGenerator extends AbstractService implements TmPacketProvider {
    TmProcessor tmProcessor;
    public final int headerLength = 16;
    public final int pkt1Length = headerLength + 3;
    public final int pkt1_1Length = pkt1Length + 56;
    public final int pkt1_2Length = pkt1Length + 16;
    public final int pkt1_3Length = pkt1Length + 100;
    public final int pkt1_4Length = pkt1Length + 300;
    public final int pkt1_5Length = pkt1Length + 50;
    public final int pkt1_6Length = pkt1Length + 4;
    public final int pkt1_7Length = pkt1Length + 6;
    public final int pkt1_8Length = pkt1Length + 6;
    public final int pkt1_9Length = pkt1Length + 1;
    public final int pkt1_10Length = pkt1Length + 8;
    public final int pkt1_11Length = pkt1Length + 4;

    public final int pkt4Length = headerLength + 4;
    public final int pkt5Length = headerLength + pFixedBinary1.length + pPrependedSizeBinary1.length;
    public final int pkt6Length = headerLength + 5;

    public final int pkt2Length = 8;
    public final int pkt1_ListLength = pkt1Length;
    public final int pkt1_AndLength = pkt1Length;
    public final int pkt1_OrLength = pkt1Length;
    public final int pkt1_And_OrLength = pkt1Length;

    public final int contVerifCmdAck_Length = headerLength + 7;
    public final int algVerifCmdAck_Length = headerLength + 9;

    // raw values of parameters
    public volatile short pIntegerPara1_1 = 5;

    public volatile byte pIntegerPara1_1_1 = 20;
    public volatile short pFloatPara1_1_2 = 1000;
    public volatile float pFloatPara1_1_3 = 2;
    public volatile byte pEnumerationPara1_1_4 = 0;
    public volatile String pStringPara1_1_5 = "cucu";
    public volatile int pIntegerPara1_1_6 = 236;
    public volatile byte pIntegerPara1_1_7 = 34;
    public volatile long pIntegerPara1_1_8 = 5084265585L;
    public volatile int pIntegerPara1_11_1 = 0xAFFFFFFE; // a uint32 stored in signed java int
    public volatile long pIntegerPara1_11_1_unsigned_value = 2952790014L; // the equivalent unsigned value
    public volatile float pFloatPara1_20_1 = (float) (Math.PI / 2);
    public volatile int pTimePara_sec6_1 = 1;
    public volatile byte pTimePara_sec6_2 = (byte) 128;

    public volatile byte pLEIntegerPara1_2_1 = 13;
    public volatile short pLEIntegerPara1_2_2 = 1300;
    public volatile int pLEIntegerPara1_2_3 = 130000;
    public volatile short pLEFloatPara1_2_1 = 300;
    public volatile float pLEFloatPara1_2_2 = 2.7182f;

    static public final String pFixedStringPara1_3_1 = "Ab"; // 16 bits
    static public final String pFixedStringPara1_3_2 = "A"; // 8 bits
    static public final String pTerminatedStringPara1_3_3 = "Abcdef"; // Null terminated
    static public final String pTerminatedStringPara1_3_4 = "Abcdef"; // Comma terminated
    static public final String pPrependedSizeStringPara1_3_5 = "Abcdefghijklmnopqrstuvwxyz"; // First 16 bits (2 bytes)
                                                                                             // set size in bits of size
                                                                                             // tag
    static public final String pPrependedSizeStringPara1_3_6 = "Abcdef"; // First 8 bits (1 byte) set size in bits of
                                                                         // size tag
    static public final String pFixedStringPara1_3_7 = "Abcdefghijklmnop"; // 128 bits

    // Get floats from strings
    public String pStringFloatFSPara1_4_1 = "1.34"; // Fixed size 32 bit
    public String pStringFloatTSCPara1_4_2 = "0.0000001"; // Comma terminated, leading zeros and calibrated
    static public final String pStringFloatTSSCPara1_4_3 = "0.12"; // Semi-colon terminated, leading zero
    static public final String pStringFloatFSBPara1_4_4 = "1.34567890123456"; // 128 bit string
    static public final String pStringFloatPSPara1_4_5 = "1.345678"; // Prepended size string, first 8 bits (1 byte) set
                                                                     // size in bits of size tag

    // Get integers from strings
    static public final String pStringIntFixedPara1_5_1 = "120"; // Fixed size, 24 bits
    public String pStringIntTermPara1_5_2 = "12"; // Comma terminated
    static public final String pStringIntTermPara1_5_3 = "12045"; // Semi-colon terminated
    static public final String pStringIntPrePara1_5_4 = "1204507"; // Prepended size (16 bits)
    static public final String pStringIntStrPara1_5_5 = "123406789"; // string

    static public final byte[] pFixedBinary1 = StringConverter.hexStringToArray("0102030004050607");
    static public final byte[] pPrependedSizeBinary1 = StringConverter.hexStringToArray("0220AC");

    // Get enumerations from strings
    public String pStringEnumPara1_12_1 = "1";

    // Get boolean from strings
    public String pStringBooleanPara10_1 = "True";

    static public final int pIntegerPara2_1 = 123;
    static public final int pIntegerPara2_2 = 25;

    Map<Integer, AtomicInteger> seqCount = new HashMap<Integer, AtomicInteger>();

    private long generationTime = TimeEncoding.INVALID_INSTANT;
    TmSink tmSink;

    SequenceContainer rootSc;

    public RefMdbPacketGenerator(String instance, String name, String spec) {

    }

    public RefMdbPacketGenerator() {
    }

    @Override
    public void init(Processor proc, YConfiguration config, Object spec) {
        this.tmProcessor = proc.getTmProcessor();
        proc.setPacketProvider(this);
        rootSc = proc.getXtceDb().getRootSequenceContainer();
        if (rootSc == null) {
            throw new ConfigurationException("Cannot find the root sequence container");
        }
    }

    public void setTmSink(TmSink tmSink) {
        this.tmSink = tmSink;
    }

    public byte[] generate_PKT1_1() {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_1Length);
        fill_PKT1_1(bb);
        sendToTmProcessor(bb);
        return bb.array();
    }

    public byte[] generate_PKT1_2() {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_2Length);
        fill_PKT1_2(bb);
        sendToTmProcessor(bb);
        return bb.array();
    }

    public byte[] generate_PKT1_3() {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_3Length);
        fill_PKT1_3(bb);
        sendToTmProcessor(bb);
        return bb.array();
    }

    public byte[] generate_PKT14() {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_4Length);
        fill_PKT1_4(bb);
        sendToTmProcessor(bb);
        return bb.array();
    }

    public byte[] generate_PKT1_5() {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_5Length);
        fill_PKT1_5(bb);
        sendToTmProcessor(bb);
        return bb.array();
    }

    public byte[] generate_TM2_PKT1() {
        ByteBuffer bb = ByteBuffer.allocate(12);
        bb.putInt(1);// pkt id
        bb.putInt(20); // para1
        bb.putFloat((float) 3.15); // para2
        sendToTmProcessor(bb);
        return bb.array();
    }

    /**
     * Generate a packet with configurable content
     */
    public byte[] generate_PKT1_6(int pIntegerPara16_1, int pIntegerPara16_2) {
        return generate_PKT1_6(pIntegerPara16_1, pIntegerPara16_2, TimeEncoding.getWallclockTime(),
                TimeEncoding.getWallclockTime());
    }

    /**
     * Generate a packet with configurable content
     */
    public byte[] generate_PKT1_6(int pIntegerPara16_1, int pIntegerPara16_2, long rectime, long gentime) {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_6Length);
        fill_PKT1_6(bb, pIntegerPara16_1, pIntegerPara16_2);
        sendToTmProcessor(bb, rectime, gentime);
        return bb.array();
    }

    public byte[] generate_PKT1_7() {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_7Length);
        fill_PKT1_7(bb);
        sendToTmProcessor(bb);
        return bb.array();
    }

    public byte[] generate_PKT1_8(int pIntegerPara18_1, int pIntegerPara18_2) {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_8Length);
        fill_PKT1_8(bb, pIntegerPara18_1, pIntegerPara18_2);
        sendToTmProcessor(bb);
        return bb.array();
    }

    public byte[] generate_PKT1_9() {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_9Length);
        fill_PKT1_9(bb);
        sendToTmProcessor(bb);
        return bb.array();
    }

    public byte[] generate_PKT1_10(int pIntegerPara1_10_1, int pEnumerationPara1_10_2, float pFloatPara1_10_3) {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_10Length);
        fill_PKT1_10(bb, pIntegerPara1_10_1, pEnumerationPara1_10_2, pFloatPara1_10_3);
        sendToTmProcessor(bb);
        return bb.array();
    }

    public byte[] generate_PKT1_11() {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_11Length);
        fill_PKT1(bb, 11);
        bb.position(pkt1Length);
        bb.putInt(pIntegerPara1_11_1);
        sendToTmProcessor(bb);
        return bb.array();
    }

    public byte[] generate_PKT1_12() {
        ByteBuffer bb = ByteBuffer.allocate(pkt1Length + pStringEnumPara1_12_1.length() + 1);
        fill_PKT1_12(bb);
        sendToTmProcessor(bb);
        return bb.array();
    }

    public byte[] generate_PKT4() {
        ByteBuffer bb = ByteBuffer.allocate(pkt4Length);
        fill_PKT4(bb);
        sendToTmProcessor(bb);
        return bb.array();
    }

    public byte[] generate_PKT5() {
        ByteBuffer bb = ByteBuffer.allocate(pkt5Length);
        fill_PKT5(bb);
        sendToTmProcessor(bb);
        return bb.array();
    }

    private void fill_PKT5(ByteBuffer bb) {
        fill_CcsdsHeader(bb, 995, 5);
        bb.position(headerLength);
        bb.put(pFixedBinary1);
        bb.put(pPrependedSizeBinary1);
    }

    public byte[] generate_PKT6() {
        ByteBuffer bb = ByteBuffer.allocate(pkt6Length);
        fill_PKT6(bb);
        sendToTmProcessor(bb);
        return bb.array();
    }

    public byte[] generate_PKT2() {
        ByteBuffer bb = ByteBuffer.allocate(pkt2Length);
        fill_PKT2(bb);
        sendToTmProcessor(bb);
        return bb.array();
    }

    // Packets to test the boolean inheritance condition
    public ByteBuffer generate_PKT1_List() {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_ListLength);
        fill_PKT1(bb, 1, 13, (short) 2);
        sendToTmProcessor(bb);
        return bb;
    }

    public ByteBuffer generate_PKT1_AND() {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_ListLength);
        fill_PKT1(bb, 2, 13, (short) 3);
        sendToTmProcessor(bb);
        return bb;
    }

    public ByteBuffer generate_PKT1_OR_1() {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_ListLength);
        fill_PKT1(bb, 1, 14, (short) 2);
        sendToTmProcessor(bb);
        return bb;
    }

    public ByteBuffer generate_PKT1_AND_OR_1() {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_ListLength);
        fill_PKT1(bb, 1, 15, (short) 1);
        sendToTmProcessor(bb);
        return bb;
    }

    public ByteBuffer generate_PKT1_AND_OR_2() {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_ListLength);
        fill_PKT1(bb, 14, 0, (short) 15);
        sendToTmProcessor(bb);
        return bb;
    }

    public byte[] generate_PKT1(int integerPara1_1, int packetType, short integerPara1_2) {
        ByteBuffer bb = ByteBuffer.allocate(pkt1_ListLength);
        fill_PKT1(bb, integerPara1_1, packetType, integerPara1_2);
        sendToTmProcessor(bb);
        return bb.array();
    }

    public ByteBuffer generateContVerifCmdAck(short cmdId, byte stage, int result) {
        ByteBuffer bb = ByteBuffer.allocate(contVerifCmdAck_Length);
        fill_CcsdsHeader(bb, 101, 1000);
        bb.position(headerLength);
        bb.putShort(cmdId);
        bb.put(stage);
        bb.putInt(result);
        sendToTmProcessor(bb);
        return bb;
    }

    public ByteBuffer generateAlgVerifCmdAck(short cmdId, short packetSeq, byte stage, int result) {
        ByteBuffer bb = ByteBuffer.allocate(algVerifCmdAck_Length);
        fill_CcsdsHeader(bb, 101, 2000);
        bb.position(headerLength);
        bb.putShort(cmdId);
        bb.putShort(packetSeq);
        bb.put(stage);
        bb.putInt(result);
        sendToTmProcessor(bb);
        return bb;
    }

    /*
     * Dynamic sized packet.
     * Test packet contains:
     * ccsds_header (headerLength bits)
     * IntegerPara1_1 = 2 (4 bits)
     * IntegerPara1_2 = 3 (16 bits)
     * IntegerPara1_2 = 4 (16 bits)
     * block_para1 = 5 (8 bits)
     * block_para2 = 6 (8 bits)
     * block_para1 = 7 (8 bits)
     * block_para2 = 8 (8 bits)
     * block_para1 = 9 (8 bits)
     * block_para2 = 10 (8 bits)
     * block_para3 = 11 (8 bits)
     * block_para4 = 12 (8 bits)
     * block_para3 = 13 (8 bits)
     * block_para4 = 14 (8 bits)
     */
    public byte[] generate_PKT3() {
        int pktLength = headerLength + 1 + 2 * 2 + 11;
        ByteBuffer bb = ByteBuffer.allocate(pktLength);
        fill_PKT3(bb);
        sendToTmProcessor(bb);
        return bb.array();
    }

    /**
     * set the generation time used to send the packets.
     * If TimeEncoding.INVALID_INSTANT is used, the current time will be sent
     * 
     * @param genTime
     */
    public void setGenerationTime(long genTime) {
        this.generationTime = genTime;
    }

    private void fill_CcsdsHeader(ByteBuffer bb, int apid, int packetId) {
        short xs;
        // Primary header:
        // version(3bits) type(1bit) secondary header flag(1bit) apid(11 bits)
        xs = (short) ((3 << 11) | apid);
        bb.putShort(0, xs);

        AtomicInteger a = seqCount.computeIfAbsent(apid, r -> new AtomicInteger(0));

        // Seq Flags (2 bits) Seq Count(14 bits)
        xs = (short) ((3 << 14) | a.getAndIncrement());

        bb.putShort(2, xs);
        // packet length (16 bits).
        bb.putShort(4, (short) (bb.capacity() - 7));

        // Secondary header:
        // coarse time(32 bits)
        GpsCcsdsTime t = TimeEncoding.toGpsTime(generationTime);
        bb.putInt(6, t.coarseTime);
        // fine time(8 bits) timeID(2bits) checkword(1 bit) spare(1 bit) pktType(4 bits)
        // xs=(short)((shTimeId<<6)|(shChecksumIndicator<<5)|shPacketType);

        bb.put(10, t.fineTime);
        // packetId(32 bits)
        bb.putInt(12, packetId);
        bb.position(headerLength);
    }

    private void fill_PKT1(ByteBuffer bb, int packetType) {
        fill_CcsdsHeader(bb, 995, 318813007);
        bb.put(headerLength, (byte) ((pIntegerPara1_1 << 4) + packetType));
    }

    private void fill_PKT1(ByteBuffer bb, int integerPara1_1, int packetType, short integerPara1_2) {
        fill_CcsdsHeader(bb, 995, 318813007);
        bb.put(headerLength, (byte) ((integerPara1_1 << 4) + packetType));
        bb.putShort(headerLength + 1, integerPara1_2);
    }

    private void fill_PKT1_1(ByteBuffer bb) {
        fill_PKT1(bb, 1);
        int offset = pkt1Length;
        bb.position(offset);
        bb.put(pIntegerPara1_1_1);
        bb.putShort(pFloatPara1_1_2);
        bb.putFloat(pFloatPara1_1_3);
        bb.put(pEnumerationPara1_1_4);

        bb.put((byte) (pIntegerPara1_1_6 >> 16));
        bb.putShort((short) (pIntegerPara1_1_6 & 0xFFFF));
        bb.put(pIntegerPara1_1_7);

        bb.putShort((short) (pIntegerPara1_1_8 >> 32));
        bb.putInt((int) pIntegerPara1_1_8 & 0xFFFFFFFF);

        byte[] b = new byte[10];
        System.arraycopy(pStringPara1_1_5.getBytes(), 0, b, 0, pStringPara1_1_5.getBytes().length);
        bb.put(b);
    }

    private void fill_PKT1_2(ByteBuffer bb) {
        fill_PKT1(bb, 2);
        bb.position(pkt1Length);

        bb.order(ByteOrder.LITTLE_ENDIAN);
        bb.put(pLEIntegerPara1_2_1);
        bb.putShort(pLEIntegerPara1_2_2);
        bb.putInt(pLEIntegerPara1_2_3);
        bb.putShort(pLEFloatPara1_2_1);
        bb.putFloat(pLEFloatPara1_2_2);
    }

    private void fill_PKT1_3(ByteBuffer bb) {
        fill_PKT1(bb, 3);
        int offset = pkt1Length;
        bb.position(offset);

        putFixedStringParam(bb, pFixedStringPara1_3_1, 16);
        putFixedStringParam(bb, pFixedStringPara1_3_2, 8);

        putTerminatedStringParam(bb, pTerminatedStringPara1_3_3, (byte) 0);
        putTerminatedStringParam(bb, pTerminatedStringPara1_3_4, (byte) ',');

        putPrependedSizeStringParam(bb, pPrependedSizeStringPara1_3_5, 16);
        putPrependedSizeStringParam(bb, pPrependedSizeStringPara1_3_6, 8);

        putFixedStringParam(bb, pFixedStringPara1_3_7, 128);
    }

    private void fill_PKT1_4(ByteBuffer bb) {
        fill_PKT1(bb, 4);
        int offset = pkt1Length;
        bb.position(offset);

        // Floats in strings
        putFixedStringParam(bb, pStringFloatFSPara1_4_1, 32);
        putTerminatedStringParam(bb, pStringFloatTSCPara1_4_2, (byte) ',');
        putTerminatedStringParam(bb, pStringFloatTSSCPara1_4_3, (byte) ';');
        putTerminatedStringParam(bb, pStringFloatTSSCPara1_4_3, (byte) ';');
        putPrependedSizeStringParam(bb, pStringFloatPSPara1_4_5, 8);
        putFixedStringParam(bb, pStringFloatFSBPara1_4_4, 128);
    }

    private void fill_PKT1_5(ByteBuffer bb) {
        fill_PKT1(bb, 5);
        int offset = pkt1Length;
        bb.position(offset);

        // Integers in strings
        putFixedStringParam(bb, pStringIntFixedPara1_5_1, 24);
        putTerminatedStringParam(bb, pStringIntTermPara1_5_2, (byte) ',');
        putTerminatedStringParam(bb, pStringIntTermPara1_5_3, (byte) ';');
        putPrependedSizeStringParam(bb, pStringIntPrePara1_5_4, 16);
        // Straight string is null terminated
        putTerminatedStringParam(bb, pStringIntStrPara1_5_5, (byte) 0);
    }

    private void fill_PKT4(ByteBuffer bb) {
        fill_CcsdsHeader(bb, 995, 4);
        bb.position(headerLength);
        bb.putFloat(pFloatPara1_20_1);
    }

    private void fill_PKT6(ByteBuffer bb) {
        fill_CcsdsHeader(bb, 995, 6);
        bb.position(headerLength);
        bb.putInt(pTimePara_sec6_1);
        bb.put(pTimePara_sec6_2);
    }

    private void fill_PKT2(ByteBuffer bb) {
        bb.position(4);
        bb.putShort((short) (pIntegerPara2_1 & 0xFFFF));
        bb.putShort((short) (pIntegerPara2_2 & 0xFFFF));
    }

    private void fill_PKT1_6(ByteBuffer bb, int pIntegerPara16_1, int pIntegerPara16_2) {
        fill_PKT1(bb, 6);
        int offset = pkt1Length;
        bb.position(offset);
        bb.putShort((short) (pIntegerPara16_1 & 0xFFFF));
        bb.putShort((short) (pIntegerPara16_2 & 0xFFFF));
    }

    private void fill_PKT1_7(ByteBuffer bb) {
        fill_PKT1(bb, 7);
        int offset = pkt1Length;
        bb.position(offset);

        // 16-bit signed integer (in sign-magnitude)
        bb.put(StringConverter.hexStringToArray("BA50"));
        // 6 (000110), filler (000), -6 (1000110) (sign-magnitude)
        bb.put(StringConverter.hexStringToArray("1846"));
        // 6 (000110), filler (000), -6 (1111001) (1's complement)
        bb.put(StringConverter.hexStringToArray("1879"));
    }

    private void fill_PKT1_8(ByteBuffer bb, int pIntegerPara18_1, int pIntegerPara18_2) {
        fill_PKT1(bb, 8);
        int offset = pkt1Length;
        bb.position(offset);

        bb.putShort((short) (pIntegerPara18_1 & 0xFFFF));
        bb.putInt(pIntegerPara18_2);
    }

    private void fill_PKT1_9(ByteBuffer bb) {
        fill_PKT1(bb, 9);
        int offset = pkt1Length;
        bb.position(offset);
        bb.put((byte) 0xA1);
    }

    private void fill_PKT1_10(ByteBuffer bb, int pIntegerPara1_10_1, int pEnumerationPara1_10_2,
            float pFloatPara1_10_3) {
        fill_PKT1(bb, 10);
        int offset = pkt1Length;
        bb.position(offset);
        bb.putShort((short) pIntegerPara1_10_1);
        bb.put((byte) pEnumerationPara1_10_2);
        bb.put((byte) 0);
        bb.putFloat(pFloatPara1_10_3);
    }

    private void fill_PKT1_12(ByteBuffer bb) {
        fill_PKT1(bb, 12);
        int offset = pkt1Length;
        bb.position(offset);

        putTerminatedStringParam(bb, pStringEnumPara1_12_1, (byte) ';');
    }

    private void fill_PKT3(ByteBuffer bb) {
        fill_CcsdsHeader(bb, 995, 318813009);
        bb.position(headerLength);
        bb.put((byte) (2 << 4)); // IntegerPara1_1 = 2 (4 bits)
        bb.put((byte) 0); // IntegerPara1_2 = 3
        bb.put((byte) 3); //
        bb.put((byte) 0); // IntegerPara1_2 = 4
        bb.put((byte) 4); //
        bb.put((byte) 5); // block_para1 = 5
        bb.put((byte) 6); // block_para2 = 6
        bb.put((byte) 61); // block_para2_1 = 61
        bb.put((byte) 7); // block_para1 = 7
        bb.put((byte) 8); // block_para2 = 8
        bb.put((byte) 9); // block_para1 = 9
        bb.put((byte) 10); // block_para2 = 10
        bb.put((byte) 11); // block_para3 = 11
        bb.put((byte) 12); // block_para4 = 12
        bb.put((byte) 13); // block_para3 = 13
        bb.put((byte) 14); // block_para4 = 14
    }

    // ######################### PKT7
    public final int pkt7Length = headerLength + 7;
    public byte paggr1_member1 = 2;
    public short paggr1_member2 = 30;
    public float paggr1_member3 = 2.72f;

    public byte[] generate_PKT7() {
        ByteBuffer bb = ByteBuffer.allocate(pkt7Length);
        fill_CcsdsHeader(bb, 995, 7);
        bb.put(paggr1_member1);
        bb.putShort(paggr1_member2);
        bb.putFloat(paggr1_member3);

        sendToTmProcessor(bb);
        return bb.array();
    }

    // ########################### PKT8
    public final int para_pkt8_count = 150;
    public final int pkt8Length = headerLength + 1 + 7 * para_pkt8_count + 9 * 4;

    public byte[] generate_PKT8() {
        ByteBuffer bb = ByteBuffer.allocate(pkt8Length);
        fill_CcsdsHeader(bb, 995, 8);
        bb.position(headerLength);
        bb.put((byte) para_pkt8_count);
        for (int i = 0; i < para_pkt8_count; i++) {
            bb.put((byte) i);
            bb.putShort((short) (i * 2));
            bb.putFloat(i / 2.0f);
        }

        // 3x3 matrix
        for (int i = 0; i < 9; i++) {
            bb.putFloat(i);
        }
        sendToTmProcessor(bb);
        return bb.array();
    }

    // ########################### PKT9
    public final int pkt9Length = headerLength + 6;

    public byte[] generate_PKT9(short obId, int v) {
        ByteBuffer bb = ByteBuffer.allocate(pkt8Length);
        fill_CcsdsHeader(bb, 995, 9);
        bb.putShort(obId);
        bb.putInt(v);

        sendToTmProcessor(bb);
        return bb.array();
    }

    public byte[] generate_PKT10() {
        int pkt10Length = headerLength + pStringBooleanPara10_1.length() + 1;
        ByteBuffer bb = ByteBuffer.allocate(pkt10Length);

        fill_CcsdsHeader(bb, 995, 10);
        putTerminatedStringParam(bb, pStringBooleanPara10_1, (byte) 0);

        sendToTmProcessor(bb);
        return bb.array();
    }

    public byte[] generate_PKT11(String s, byte v) {
        int strLength = 20;
        int pkt11Length = headerLength + strLength + 1;
        ByteBuffer bb = ByteBuffer.allocate(pkt11Length);
        fill_CcsdsHeader(bb, 995, 11);
        byte[] sb = s.getBytes();
        bb.put(sb, 0, Math.min(20, sb.length));
        bb.put(headerLength + strLength, v);
        sendToTmProcessor(bb);
        return bb.array();
    }

    public byte[] generate_PKT12() {
        int pkt12Length = headerLength + 100;
        ByteBuffer bb = ByteBuffer.allocate(pkt12Length);
        fill_CcsdsHeader(bb, 995, 12);

        // sint32
        bb.putInt(-1);

        // uint32
        bb.putInt(0xF0F1F2F3);

        // sint64
        bb.putLong(-2);

        // uint64
        bb.putLong(0xF0F1F2F3F4F5F6F7l);

        // double
        bb.putDouble(3.14);

        // float
        bb.putFloat(2.72f);

        // boolean
        bb.put((byte) 1);

        // enum
        bb.put((byte) 1);

        // string
        bb.put("bla".getBytes());
        bb.put((byte) 0);

        // binary
        bb.put((byte) 5);
        bb.put(StringConverter.hexStringToArray("0102030405"));

        sendToTmProcessor(bb);
        return bb.array();
    }

    private void putFixedStringParam(ByteBuffer bb, String value, int bits) {
        int baSize = bits / 8;
        if (bits == -1) {
            baSize = value.getBytes().length;
        }
        byte[] ba = new byte[baSize];
        System.arraycopy(value.getBytes(), 0, ba, 0, value.getBytes().length);
        bb.put(ba);
    }

    private void putTerminatedStringParam(ByteBuffer bb, String value, byte terminator) {
        byte[] ba = new byte[value.getBytes().length + 1];
        System.arraycopy(value.getBytes(), 0, ba, 0, value.getBytes().length);
        ba[ba.length - 1] = terminator;
        bb.put(ba);
        /*
         * if( terminator == 0 ) {
         * System.out.println( String.format(
         * "- put TerminatedString '%s' length %d bits (%d bytes) with terminator null", value,
         * value.getBytes().length*8, value.getBytes().length ) );
         * } else {
         * System.out.println( String.format(
         * "- put TerminatedString '%s' length %d bits (%d bytes) with terminator '%c'", value,
         * value.getBytes().length*8, value.getBytes().length, terminator ) );
         * }
         */
    }

    private void putPrependedSizeStringParam(ByteBuffer bb, String value, int tagSizeInBits) {
        if (tagSizeInBits <= 8) {
            bb.put(((byte) (value.getBytes().length)));
        } else {
            bb.putShort(((short) (value.getBytes().length)));
        }
        byte[] ba = new byte[value.getBytes().length];
        System.arraycopy(value.getBytes(), 0, ba, 0, value.getBytes().length);
        bb.put(ba);
        // System.out.println( String.format("- put PrependedSizeString '%s' with leading %d bits filled with number %d
        // to specify the number of bytes the string uses.",value,tagSizeInBits,value.getBytes().length) );
    }

    private void sendToTmProcessor(ByteBuffer bb) {

        long gentime = generationTime;
        if (gentime == TimeEncoding.INVALID_INSTANT) {
            gentime = TimeEncoding.getWallclockTime();
        }
        sendToTmProcessor(bb, TimeEncoding.getWallclockTime(), gentime);
    }

    private void sendToTmProcessor(ByteBuffer bb, long rectime, long gentime) {
        if (tmProcessor != null) {
            tmProcessor.processPacket(new TmPacket(rectime, gentime, bb.getInt(0), bb.array()), rootSc);
        }
        if (tmSink != null) {
            tmSink.processPacket(new TmPacket(rectime, gentime, bb.getInt(0), bb.array()));
        }
    }

    public long getGenerationTime() {
        return generationTime;
    }

    public void simulateGap(int apid) {
        AtomicInteger a = seqCount.computeIfAbsent(apid, r -> new AtomicInteger(0));
        a.incrementAndGet();
    }

    @Override
    public boolean isArchiveReplay() {
        return false;
    }

    @Override
    protected void doStart() {
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }

}
