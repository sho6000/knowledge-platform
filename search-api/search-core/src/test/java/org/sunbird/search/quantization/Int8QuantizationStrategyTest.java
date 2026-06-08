package org.sunbird.search.quantization;

import org.junit.Assert;
import org.junit.Test;

public class Int8QuantizationStrategyTest {

    private final Int8QuantizationStrategy strategy = new Int8QuantizationStrategy();

    @Test
    public void testNullVector_returnsEmpty() {
        Assert.assertArrayEquals(new byte[0], strategy.quantize(null));
    }

    @Test
    public void testEmptyVector_returnsEmpty() {
        Assert.assertArrayEquals(new byte[0], strategy.quantize(new float[0]));
    }

    @Test
    public void testNormalizedVector_l2PathUsed() {
        // Build L2-normalized vector: all 1/sqrt(N)
        int dims = 4;
        float val = (float) (1.0 / Math.sqrt(dims));
        float[] vec = new float[dims];
        for (int i = 0; i < dims; i++) vec[i] = val;

        byte[] result = strategy.quantize(vec);
        Assert.assertEquals(dims, result.length);
        // Each value ~ val * 127, rounded
        int expected = (int) Math.round(val * 127.0);
        for (byte b : result) {
            Assert.assertEquals(expected, (int) b);
        }
    }

    @Test
    public void testNormalizedVector_maxValue_clampsTo127() {
        float[] vec = new float[]{1.0f}; // L2 norm = 1.0, val = 1.0 → 127
        byte[] result = strategy.quantize(vec);
        Assert.assertEquals((byte) 127, result[0]);
    }

    @Test
    public void testNormalizedVector_minValue_clampsToMinus127() {
        float[] vec = new float[]{-1.0f}; // L2 norm = 1.0, val = -1.0 → -127
        byte[] result = strategy.quantize(vec);
        Assert.assertEquals((byte) -127, result[0]);
    }

    @Test
    public void testUnnormalizedVector_minMaxPath() {
        // L2 norm = sqrt(0^2 + 100^2) = 100 → not normalized
        float[] vec = new float[]{0f, 100f};
        byte[] result = strategy.quantize(vec);
        Assert.assertEquals(2, result.length);
        // min=0, max=100, range=100
        // vec[0]=0 → n=0 → s=0*255-128=-128 → -128
        // vec[1]=100 → n=1 → s=255-128=127 → 127
        Assert.assertEquals((byte) -128, result[0]);
        Assert.assertEquals((byte) 127, result[1]);
    }

    @Test
    public void testAllSameValues_returnsZeroVector() {
        float[] vec = new float[]{5f, 5f, 5f};
        byte[] result = strategy.quantize(vec);
        for (byte b : result) {
            Assert.assertEquals(0, b);
        }
    }

    @Test
    public void testOutputLength_matchesInput() {
        float[] vec = new float[1536];
        for (int i = 0; i < 1536; i++) vec[i] = (float) (i * 0.001);
        byte[] result = strategy.quantize(vec);
        Assert.assertEquals(1536, result.length);
    }

    @Test
    public void testGetName() {
        Assert.assertEquals("int8", strategy.getName());
    }

    @Test
    public void testGetVersion() {
        Assert.assertEquals("1.0", strategy.getVersion());
    }
}
