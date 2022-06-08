package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.mdb.ContainerProcessingResult;
import org.yamcs.mdb.XtceDbFactory;
import org.yamcs.mdb.XtceTmExtractor;
import org.yamcs.parameter.ParameterValue;
import org.yamcs.protobuf.Pvalue.AcquisitionStatus;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.XtceDb;

public class ParameterValidityRangesTest {
    static XtceDb db;
    long now = TimeEncoding.getWallclockTime();

    @BeforeAll
    public static void beforeClass() throws ConfigurationException {
        YConfiguration.setupTest(null);
        db = XtceDbFactory.createInstanceByConfig("ranges-test");
    }

    @Test
    public void test1() {
        XtceTmExtractor extractor = new XtceTmExtractor(db);
        extractor.provideAll();
        byte[] buf = new byte[8];
        ByteBuffer.wrap(buf).putDouble(90);
        ContainerProcessingResult cpr = extractor.processPacket(buf, now, now);
        ParameterValue pv = cpr.getParameterResult()
                .getFirstInserted(db.getParameter("/Example/latitude"));
        assertEquals(AcquisitionStatus.ACQUIRED, pv.getAcquisitionStatus());

        ByteBuffer.wrap(buf).putDouble(90.01);
        cpr = extractor.processPacket(buf, now, now);
        ParameterValue pv1 = cpr.getParameterResult()
                .getFirstInserted(db.getParameter("/Example/latitude"));
        assertEquals(AcquisitionStatus.INVALID, pv1.getAcquisitionStatus());

        ByteBuffer.wrap(buf).putDouble(-90.01);
        cpr = extractor.processPacket(buf, now, now);
        ParameterValue pv2 = cpr.getParameterResult()
                .getFirstInserted(db.getParameter("/Example/latitude"));
        assertEquals(AcquisitionStatus.INVALID, pv2.getAcquisitionStatus());
    }
}
