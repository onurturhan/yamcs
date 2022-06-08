package org.yamcs;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class YConfigurationTest {
    @Test
    public void testBinaryDefault() {
        YConfiguration config = YConfiguration.getConfiguration("test-config");
        assertArrayEquals(new byte[] { 0x01, 0x0A }, config.getBinary("binary-nonexistent", new byte[] { 0x01, 0x0A }));
    }

    @Test
    public void testEmptyBinary() {
        YConfiguration config = YConfiguration.getConfiguration("test-config");
        assertArrayEquals(new byte[] {}, config.getBinary("emptyBinary"));
    }

    @Test
    public void testBinary1() {
        YConfiguration config = YConfiguration.getConfiguration("test-config");
        assertArrayEquals(new byte[] { 0x01, (byte) 0xAB }, config.getBinary("binary1"));
    }

    @Test
    public void testBinary2() {
        YConfiguration config = YConfiguration.getConfiguration("test-config");
        assertArrayEquals(new byte[] { 0x02, (byte) 0xCD, 0x03 }, config.getBinary("binary2"));
    }

    @Test
    public void testInvalidBinary() {
        assertThrows(ConfigurationException.class, () -> {
            YConfiguration config = YConfiguration.getConfiguration("test-config");
            assertArrayEquals(new byte[] { 0x01, (byte) 0xAB }, config.getBinary("invalid-binary1"));
        });
    }
}
