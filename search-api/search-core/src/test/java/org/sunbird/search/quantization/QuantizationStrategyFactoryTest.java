package org.sunbird.search.quantization;

import org.junit.Assert;
import org.junit.Test;
import org.sunbird.common.exception.ClientException;

public class QuantizationStrategyFactoryTest {

    @Test
    public void testGetInt8_returnsInt8Strategy() {
        QuantizationStrategy strategy = QuantizationStrategyFactory.get("int8");
        Assert.assertNotNull(strategy);
        Assert.assertEquals("int8", strategy.getName());
    }

    @Test
    public void testGetNull_defaultsToInt8() {
        QuantizationStrategy strategy = QuantizationStrategyFactory.get(null);
        Assert.assertNotNull(strategy);
        Assert.assertEquals("int8", strategy.getName());
    }

    @Test
    public void testGetEmpty_defaultsToInt8() {
        QuantizationStrategy strategy = QuantizationStrategyFactory.get("");
        Assert.assertNotNull(strategy);
        Assert.assertEquals("int8", strategy.getName());
    }

    @Test(expected = ClientException.class)
    public void testGetUnknown_throwsClientException() {
        QuantizationStrategyFactory.get("float32");
    }

    @Test
    public void testInt8IsSingleton() {
        QuantizationStrategy a = QuantizationStrategyFactory.get("int8");
        QuantizationStrategy b = QuantizationStrategyFactory.get("int8");
        Assert.assertSame("int8 strategy should be singleton", a, b);
    }
}
