package org.yamcs.tctm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.YConfiguration;
import org.yamcs.events.EventProducerFactory;

public class TcpTcUplinkerTest {

    @BeforeAll
    public static void beforeClass() {
        EventProducerFactory.setMockup(false);
    }

    @Test
    public void testConfig1() {
        Map<String, Object> config = new HashMap<>();
        config.put("host", "localhost");
        config.put("port", 10025);
        TcpTcDataLink tcuplink = new TcpTcDataLink();
        config.put("commandPostprocessorClassName", IssCommandPostprocessor.class.getName());
        tcuplink.init("testinst", "name0", YConfiguration.wrap(config));
        IssCommandPostprocessor icpp = (IssCommandPostprocessor) tcuplink.cmdPostProcessor;
        assertEquals(-1, icpp.getMiniminimumTcPacketLength());
    }

    @Test
    public void testConfig2() {
        Map<String, Object> config = new HashMap<>();
        config.put("host", "localhost");
        config.put("port", 10025);

        Map<String, Object> postProcessorArgs = new HashMap<>();
        postProcessorArgs.put("minimumTcPacketLength", 48);
        config.put("commandPostprocessorClassName", IssCommandPostprocessor.class.getName());
        config.put("commandPostprocessorArgs", postProcessorArgs);

        TcpTcDataLink tcuplink = new TcpTcDataLink();
        tcuplink.init("testinst", "test1", YConfiguration.wrap(config));
        IssCommandPostprocessor icpp = (IssCommandPostprocessor) tcuplink.cmdPostProcessor;
        assertEquals(48, icpp.getMiniminimumTcPacketLength());
    }
}
