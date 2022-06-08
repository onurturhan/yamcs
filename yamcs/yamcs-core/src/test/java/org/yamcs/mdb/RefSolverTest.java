package org.yamcs.mdb;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.XtceDb;

public class RefSolverTest {

    @Test
    public void test1() {
        XtceDb db = XtceDbFactory.createInstanceByConfig("xtce-refsolver");
        Parameter para = db.getParameter("/refsolver1/bool1");
        BooleanParameterType ptype = (BooleanParameterType) para.getParameterType();
        IntegerDataEncoding encoding = (IntegerDataEncoding) ptype.getEncoding();
        assertEquals(12, encoding.getSizeInBits());
    }
}
