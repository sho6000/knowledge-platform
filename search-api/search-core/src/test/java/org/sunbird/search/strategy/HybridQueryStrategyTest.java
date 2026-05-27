package org.sunbird.search.strategy;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.index.query.QueryBuilder;
import org.sunbird.search.dto.SearchDTO;
import org.sunbird.search.processor.SearchProcessor;
import org.sunbird.search.util.SearchConstants;

import java.util.Arrays;
import java.util.HashMap;

public class HybridQueryStrategyTest {

    private final HybridQueryStrategy strategy = new HybridQueryStrategy();

    @Test
    public void testGetMode_returnsHybrid() {
        Assert.assertEquals(SearchConstants.SEARCH_MODE_HYBRID, strategy.getMode());
    }

    @Test
    public void testBuild_fallsBackToTextQuery_returnsNonNull() {
        SearchProcessor processor = new SearchProcessor();
        SearchDTO dto = new SearchDTO();
        HashMap<String, Object> prop = new HashMap<>();
        prop.put("propertyName", "*");
        prop.put("values", Arrays.asList("test"));
        prop.put("operation", "EQ");
        dto.setProperties(Arrays.asList(prop));

        QueryBuilder result = strategy.build(dto, processor);
        Assert.assertNotNull("hybrid fallback should return a non-null query", result);
    }
}
