package org.yamcs.parameterarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.utils.TimeEncoding;

public class TimeSegmentTest {
    long t0 = ParameterArchive.getIntervalStart(10 * 365 * 25 * 60 * 1000L);

    @BeforeAll
    public static void beforeClass() {
        TimeEncoding.setUp();
    }

    @Test
    public void testIllegalAdd() {
        SortedTimeSegment ts = new SortedTimeSegment(t0);
        try {
            ts.add(1000);
            fail("Should not allow to add timestamp because it does not fit into the segment");
        } catch (IllegalArgumentException e) {

        }
    }

    public void test1() {
        SortedTimeSegment ts = new SortedTimeSegment(t0);
        ts.add(1000);
        assertEquals(0, ts.search(t0 + 1000));
        assertEquals(-1, ts.search(t0 + 10));
        assertEquals(-2, ts.search(t0 + 2000));
    }
}
