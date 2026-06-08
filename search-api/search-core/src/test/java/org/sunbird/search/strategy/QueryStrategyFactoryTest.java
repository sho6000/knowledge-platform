package org.sunbird.search.strategy;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.sunbird.common.exception.ClientException;
import org.sunbird.search.util.SearchConstants;

public class QueryStrategyFactoryTest {

    @Before
    public void setup() {
        // Reset to clean state (text-only) before each test
        QueryStrategyFactory.resetForTest();
    }

    @After
    public void teardown() {
        QueryStrategyFactory.resetForTest();
    }

    @Test
    public void testGetText_returnsTextStrategy() {
        QueryStrategy strategy = QueryStrategyFactory.get(SearchConstants.SEARCH_MODE_TEXT);
        Assert.assertNotNull(strategy);
        Assert.assertEquals(SearchConstants.SEARCH_MODE_TEXT, strategy.getMode());
    }

    @Test
    public void testGetNull_defaultsToText() {
        QueryStrategy strategy = QueryStrategyFactory.get(null);
        Assert.assertNotNull(strategy);
        Assert.assertEquals(SearchConstants.SEARCH_MODE_TEXT, strategy.getMode());
    }

    @Test
    public void testGetEmpty_defaultsToText() {
        QueryStrategy strategy = QueryStrategyFactory.get("");
        Assert.assertNotNull(strategy);
        Assert.assertEquals(SearchConstants.SEARCH_MODE_TEXT, strategy.getMode());
    }

    @Test
    public void testGetSemantic_whenDisabled_throwsClientException() {
        // resetForTest() leaves only text strategy — semantic is disabled
        try {
            QueryStrategyFactory.get(SearchConstants.SEARCH_MODE_SEMANTIC);
            Assert.fail("Expected ClientException for disabled semantic mode");
        } catch (ClientException e) {
            Assert.assertEquals(SearchConstants.ERR_SEMANTIC_DISABLED, e.getErrCode());
        }
    }

    @Test
    public void testGetHybrid_whenDisabled_throwsClientException() {
        try {
            QueryStrategyFactory.get(SearchConstants.SEARCH_MODE_HYBRID);
            Assert.fail("Expected ClientException for disabled hybrid mode");
        } catch (ClientException e) {
            Assert.assertEquals(SearchConstants.ERR_SEMANTIC_DISABLED, e.getErrCode());
        }
    }

    @Test
    public void testGetUnknownMode_throwsClientException() {
        try {
            QueryStrategyFactory.get("vector_only");
            Assert.fail("Expected ClientException for unknown mode");
        } catch (ClientException e) {
            Assert.assertEquals(SearchConstants.ERR_INVALID_SEARCH_MODE, e.getErrCode());
        }
    }

    @Test
    public void testRegisterAndGet_customStrategy() {
        QueryStrategyFactory.register(new QueryStrategy() {
            @Override public String getMode() { return "custom"; }
            @Override public org.opensearch.index.query.QueryBuilder build(
                    org.sunbird.search.dto.SearchDTO dto,
                    org.sunbird.search.processor.SearchProcessor processor) { return null; }
        });
        QueryStrategy result = QueryStrategyFactory.get("custom");
        Assert.assertEquals("custom", result.getMode());
    }

    @Test
    public void testGetSemantic_whenEnabled_returnsStrategy() {
        // Manually register semantic strategy
        QueryStrategyFactory.register(new SemanticQueryStrategy(
                new org.sunbird.search.embedding.EmbeddingCache(false, 0, 0)));
        QueryStrategy strategy = QueryStrategyFactory.get(SearchConstants.SEARCH_MODE_SEMANTIC);
        Assert.assertEquals(SearchConstants.SEARCH_MODE_SEMANTIC, strategy.getMode());
    }

    @Test
    public void testGetHybrid_whenEnabled_returnsStrategy() {
        QueryStrategyFactory.register(new HybridQueryStrategy());
        QueryStrategy strategy = QueryStrategyFactory.get(SearchConstants.SEARCH_MODE_HYBRID);
        Assert.assertEquals(SearchConstants.SEARCH_MODE_HYBRID, strategy.getMode());
    }
}
