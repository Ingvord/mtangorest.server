package hzg.wpn.mtango;

import org.junit.Test;

/**
 * @author Igor Khokhriakov <igor.khokhriakov@hzg.de>
 * @since 23.05.14
 */
public class DatabaseDsTest {
    @Test
    public void testGetDeviceAddress() throws Exception {
        //TODO environment independent
        DatabaseDs instance = new DatabaseDs("hzgharwi3:10000");

        String result = instance.getDeviceAddress("sys/tg_test/1");

        //TODO assert
    }
}
