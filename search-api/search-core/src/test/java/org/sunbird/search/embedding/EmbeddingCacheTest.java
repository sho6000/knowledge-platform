package org.sunbird.search.embedding;

import org.junit.Assert;
import org.junit.Test;

public class EmbeddingCacheTest {

    @Test
    public void testCacheMiss_returnsNull() {
        EmbeddingCache cache = new EmbeddingCache(true, 10, 60);
        Assert.assertNull(cache.get("openai", "v1", "photosynthesis"));
    }

    @Test
    public void testPutAndGet_returnsVector() {
        EmbeddingCache cache = new EmbeddingCache(true, 10, 60);
        float[] vec = new float[]{0.1f, 0.2f, 0.3f};
        cache.put("openai", "v1", "photosynthesis", vec);
        float[] result = cache.get("openai", "v1", "photosynthesis");
        Assert.assertArrayEquals(vec, result, 0.0001f);
    }

    @Test
    public void testDifferentModels_dontCollide() {
        EmbeddingCache cache = new EmbeddingCache(true, 10, 60);
        float[] vecV1 = new float[]{0.1f};
        float[] vecV2 = new float[]{0.9f};
        cache.put("openai", "v1", "query", vecV1);
        cache.put("openai", "v2", "query", vecV2);

        Assert.assertArrayEquals(vecV1, cache.get("openai", "v1", "query"), 0.0001f);
        Assert.assertArrayEquals(vecV2, cache.get("openai", "v2", "query"), 0.0001f);
    }

    @Test
    public void testDisabledCache_alwaysReturnsNull() {
        EmbeddingCache cache = new EmbeddingCache(false, 10, 60);
        cache.put("openai", "v1", "query", new float[]{0.1f});
        Assert.assertNull(cache.get("openai", "v1", "query"));
    }

    @Test
    public void testTTLExpiry_returnsNullAfterExpiry() throws InterruptedException {
        EmbeddingCache cache = new EmbeddingCache(true, 10, 1); // 1 second TTL
        cache.put("openai", "v1", "query", new float[]{0.1f});
        Assert.assertNotNull(cache.get("openai", "v1", "query"));
        Thread.sleep(1100);
        Assert.assertNull("entry should have expired", cache.get("openai", "v1", "query"));
    }

    @Test
    public void testLRUEviction_eldestEvictedWhenFull() {
        EmbeddingCache cache = new EmbeddingCache(true, 2, 60); // max 2 entries
        cache.put("openai", "v1", "q1", new float[]{0.1f});
        cache.put("openai", "v1", "q2", new float[]{0.2f});
        cache.put("openai", "v1", "q3", new float[]{0.3f}); // evicts q1

        Assert.assertNull("q1 should be evicted", cache.get("openai", "v1", "q1"));
        Assert.assertNotNull("q2 still present", cache.get("openai", "v1", "q2"));
        Assert.assertNotNull("q3 still present", cache.get("openai", "v1", "q3"));
    }

    @Test
    public void testClear_emptiesCache() {
        EmbeddingCache cache = new EmbeddingCache(true, 10, 60);
        cache.put("openai", "v1", "query", new float[]{0.1f});
        cache.clear();
        Assert.assertNull(cache.get("openai", "v1", "query"));
    }
}
