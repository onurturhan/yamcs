package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.ContainerProcessingResult;
import org.yamcs.mdb.XtceDbFactory;
import org.yamcs.mdb.XtceTmExtractor;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;

public class XtceStringDecodingTest {
    static XtceDb xtcedb;
    long now = TimeEncoding.getWallclockTime();
    XtceTmExtractor extractor;

    @BeforeAll
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        xtcedb = XtceDbFactory.createInstanceByConfig("xtce-strings-tm");
    }

    @BeforeEach
    public void before() {
        extractor = new XtceTmExtractor(xtcedb);
        extractor.provideAll();
    }

    @Test
    // null terminated string in fixed size buffer
    public void testFixedSizeString1() {
        byte[] buf = new byte[] { 'a', 'b', 0, 0, 0, 0, 0x01, 0x02 };
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now,
                xtcedb.getSequenceContainer("/StringsTm/packet1"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(2, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("string1"));
        assertEquals("ab", pv.getEngValue().getStringValue());
        pv = pvl.getFirstInserted(param("uint16_param1"));
        assertEquals(0x0102, pv.getEngValue().getUint32Value());
    }

    @Test
    // null terminated string in fixed size buffer but the string is as long as the buffer so there is no terminator
    public void testFixedSizeString1_noterminator() {
        byte[] buf = new byte[] { 'a', 'b', 'c', 'd', 'e', 'f', 0x01, 0x02 };
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now,
                xtcedb.getSequenceContainer("/StringsTm/packet1"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(2, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("string1"));
        assertEquals("abcdef", pv.getEngValue().getStringValue());
        pv = pvl.getFirstInserted(param("uint16_param1"));
        assertEquals(0x0102, pv.getEngValue().getUint32Value());

    }

    @Test
    // fixed size string in fixed size buffer
    public void testFixedSizeString2() {
        byte[] buf = new byte[] { 'a', 'b', 'c', 'd', 'e', 'f', 0x01, 0x02 };
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now,
                xtcedb.getSequenceContainer("/StringsTm/packet2"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(2, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("string2"));
        assertEquals("abcdef", pv.getEngValue().getStringValue());
        pv = pvl.getFirstInserted(param("uint16_param1"));
        assertEquals(0x0102, pv.getEngValue().getUint32Value());
    }

    @Test
    // null terminated string in undefined buffer
    public void testFixedSizeString3() {
        byte[] buf = new byte[] { 'a', 'b', 0, 0x01, 0x02 };
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now,
                xtcedb.getSequenceContainer("/StringsTm/packet3"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(2, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("string3"));
        assertEquals("ab", pv.getEngValue().getStringValue());
        pv = pvl.getFirstInserted(param("uint16_param1"));
        assertEquals(0x0102, pv.getEngValue().getUint32Value());
    }

    @Test
    // non terminated string in undefined buffer -> error
    public void testFixedSizeString3_no_terminator() {
        byte[] buf = new byte[] { 'a', 'b', 'c', 'd', 'e', 'f', 0x01, 0x02 };
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now,
                xtcedb.getSequenceContainer("/StringsTm/packet3"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(0, pvl.size());
        assertNotNull(cpr.exception);
    }

    @Test
    // prefixed size string in buffer whose size is given by another parameter
    public void testFixedSizeString4() {
        byte[] buf = new byte[] {
                0x00, 0x06, // buffer size
                0x03, // string size
                'a', 'b', 'c', // string
                'x', 'x', // filler at the end of the buffer
                0x01, 0x02 // uint16_param1 coming after the string
        };
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now,
                xtcedb.getSequenceContainer("/StringsTm/packet4"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(3, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("string4"));
        assertEquals("abc", pv.getEngValue().getStringValue());
        pv = pvl.getFirstInserted(param("uint16_param1"));
        assertEquals(0x0102, pv.getEngValue().getUint32Value());
    }

    @Test
    // prefixed size string in undefined buffer
    public void testFixedSizeString5() {
        byte[] buf = new byte[] { 0x00, 0x02, 'a', 'b', 0x01, 0x02 };
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now,
                xtcedb.getSequenceContainer("/StringsTm/packet5"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(2, pvl.size());
        ParameterValue pv = pvl.getFirstInserted(param("string5"));
        assertEquals("ab", pv.getEngValue().getStringValue());
        pv = pvl.getFirstInserted(param("uint16_param1"));
        assertEquals(0x0102, pv.getEngValue().getUint32Value());
    }

    @Test
    // prefixed size string in undefined buffer, exceeding the max size
    public void testFixedSizeString5_too_long() {
        byte[] buf = new byte[] { 0x00, 0x05, 'a', 'b', 'c', 'd', 'e', 0x01, 0x02 };
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now,
                xtcedb.getSequenceContainer("/StringsTm/packet5"));

        ParameterValueList pvl = cpr.getParameterResult();
        assertEquals(0, pvl.size());
        assertNotNull(cpr.exception);
    }

    private Parameter param(String name) {
        return xtcedb.getParameter("/StringsTm/" + name);
    }
}
