package org.sunbird.search.fusion;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RrfFusionTest {

    private static Map<String, Object> doc(String id) {
        Map<String, Object> m = new HashMap<>();
        m.put("identifier", id);
        return m;
    }

    private static java.util.function.Function<Map<String, Object>, String> idOf() {
        return m -> (String) m.get("identifier");
    }

    @Test
    public void testDocInBothLists_ranksFirst() {
        List<Map<String, Object>> list1 = Arrays.asList(doc("A"), doc("B"), doc("C"));
        List<Map<String, Object>> list2 = Arrays.asList(doc("A"), doc("D"), doc("E"));

        List<RrfFusion.FusedHit<Map<String, Object>>> result =
                RrfFusion.fuse(Arrays.asList(list1, list2), idOf(), 60);

        Assert.assertEquals("A should rank first — appears in both lists", "A", result.get(0).id);
    }

    @Test
    public void testDocInBothLists_higherScoreThanDocInOne() {
        List<Map<String, Object>> list1 = Arrays.asList(doc("shared"), doc("textOnly"));
        List<Map<String, Object>> list2 = Arrays.asList(doc("shared"), doc("semOnly"));

        List<RrfFusion.FusedHit<Map<String, Object>>> result =
                RrfFusion.fuse(Arrays.asList(list1, list2), idOf(), 60);

        double sharedScore = result.stream()
                .filter(h -> "shared".equals(h.id)).findFirst().get().score;
        double textOnlyScore = result.stream()
                .filter(h -> "textOnly".equals(h.id)).findFirst().get().score;

        Assert.assertTrue("shared doc score > single-list doc score", sharedScore > textOnlyScore);
    }

    @Test
    public void testRanksAttached_correctValues() {
        List<Map<String, Object>> list1 = Arrays.asList(doc("A"), doc("B"));
        List<Map<String, Object>> list2 = Arrays.asList(doc("B"), doc("A"));

        List<RrfFusion.FusedHit<Map<String, Object>>> result =
                RrfFusion.fuse(Arrays.asList(list1, list2), idOf(), 60);

        RrfFusion.FusedHit<Map<String, Object>> hitA = result.stream()
                .filter(h -> "A".equals(h.id)).findFirst().get();

        Assert.assertEquals("A is rank 1 in list1", 1, hitA.ranks[0]);
        Assert.assertEquals("A is rank 2 in list2", 2, hitA.ranks[1]);
    }

    @Test
    public void testDocAbsentFromList_rankIsZero() {
        List<Map<String, Object>> list1 = Arrays.asList(doc("A"), doc("B"));
        List<Map<String, Object>> list2 = Arrays.asList(doc("C"));

        List<RrfFusion.FusedHit<Map<String, Object>>> result =
                RrfFusion.fuse(Arrays.asList(list1, list2), idOf(), 60);

        RrfFusion.FusedHit<Map<String, Object>> hitA = result.stream()
                .filter(h -> "A".equals(h.id)).findFirst().get();

        Assert.assertEquals("A absent from list2 — rank should be 0", 0, hitA.ranks[1]);
    }

    @Test
    public void testEmptyLists_returnsEmpty() {
        List<RrfFusion.FusedHit<Map<String, Object>>> result =
                RrfFusion.fuse(Collections.emptyList(), idOf(), 60);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testNullLists_returnsEmpty() {
        List<RrfFusion.FusedHit<Map<String, Object>>> result =
                RrfFusion.fuse(null, idOf(), 60);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testKZero_usesDefault() {
        List<Map<String, Object>> list1 = Arrays.asList(doc("A"));
        List<RrfFusion.FusedHit<Map<String, Object>>> result =
                RrfFusion.fuse(Arrays.asList(list1), idOf(), 0);
        Assert.assertFalse("should fall back to DEFAULT_K and return results", result.isEmpty());
    }

    @Test
    public void testSortedDescending() {
        // rank 1 in both should outscore rank 2 in both
        List<Map<String, Object>> list1 = Arrays.asList(doc("top"), doc("bottom"));
        List<Map<String, Object>> list2 = Arrays.asList(doc("top"), doc("bottom"));

        List<RrfFusion.FusedHit<Map<String, Object>>> result =
                RrfFusion.fuse(Arrays.asList(list1, list2), idOf(), 60);

        Assert.assertEquals("top", result.get(0).id);
        Assert.assertEquals("bottom", result.get(1).id);
        Assert.assertTrue(result.get(0).score > result.get(1).score);
    }

    @Test
    public void testScoreFormula_correctValue() {
        // doc at rank 1 in one list, k=60: score = 1/(60+1) ≈ 0.01639
        List<Map<String, Object>> list1 = Arrays.asList(doc("A"));
        List<RrfFusion.FusedHit<Map<String, Object>>> result =
                RrfFusion.fuse(Arrays.asList(list1), idOf(), 60);

        double expected = 1.0 / (60 + 1);
        Assert.assertEquals(expected, result.get(0).score, 0.00001);
    }

    @Test
    public void testUnionOfBothLists() {
        List<Map<String, Object>> list1 = Arrays.asList(doc("A"), doc("B"));
        List<Map<String, Object>> list2 = Arrays.asList(doc("C"), doc("D"));

        List<RrfFusion.FusedHit<Map<String, Object>>> result =
                RrfFusion.fuse(Arrays.asList(list1, list2), idOf(), 60);

        Assert.assertEquals("union of both lists", 4, result.size());
    }
}
