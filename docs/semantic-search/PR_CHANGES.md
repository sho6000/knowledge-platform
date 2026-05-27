# PR: Semantic Search, Hybrid Search (RRF) and Content Enrich API

## Overview

This PR's primary highlight is **enabling semantic search** — the ability to search content by meaning rather than keywords using kNN vector similarity. Built on top of that foundation, hybrid search (text + semantic fused via RRF) is added as an extension. The Enrich API rounds it out by providing a way to trigger metadata re-indexing without republishing.

13 files changed — 5 new, 8 modified.

---

## Primary Feature: Semantic Search

Semantic search lets users find content by meaning. A search for *"resources about photosynthesis"* can surface content about *"plant biology"* or *"chlorophyll"* — even if those exact words don't appear in the query. This works by embedding the query text into the same vector space as the pre-indexed content embeddings, then running a kNN (k-nearest-neighbour) search to find the most similar vectors.

### How It Works End-to-End

```
User sends: POST /v3/search  { "request": { "query": "photosynthesis", "search_mode": "semantic" } }
                                                    ↓
                              SearchProcessor routes to SemanticQueryStrategy
                                                    ↓
                              Extract query string from properties[propertyName='*']
                                                    ↓
                              Check EmbeddingCache (LRU + TTL, keyed by sha256(service:model:query))
                              Cache miss → call EmbeddingClient.embed(query) → float[1536]
                                                    ↓
                              Quantize float[] → byte[] via Int8QuantizationStrategy
                              (MUST use same strategy as the embedding job — shared byte-space contract)
                                                    ↓
                              Build kNN JSON query manually:
                              { "knn": { "chunks.embedding": { "vector": [...bytes], "k": 50 } } }
                                                    ↓
                              Wrap in nested query on "chunks" path
                              Add schema_version filter (ensures index-time/query-time vector compatibility)
                              Inherit property filters from text path (status, channel, board, etc.)
                                                    ↓
                              OpenSearch runs kNN → returns nearest k documents by cosine similarity
                                                    ↓
                              Results with cosine scores returned to caller
```

### Key Design Decisions

**Vector space contract**: Query-time embedding and index-time embedding MUST use the same model, same quantization, and same vector field. `EmbeddingClientFactory` and `QuantizationStrategyFactory` in the search-api read the same config keys as the content-embedding-job. If they diverge, kNN results are meaningless.

**schema_version filter**: Filters chunks by `schema_version` to protect against reindex-in-progress scenarios. Old-format chunks (pre-quantization or different model) are excluded.

**kNN JSON built manually**: The OpenSearch kNN plugin's `QueryBuilder` is not on the rest-high-level-client classpath. The kNN JSON is built as a raw string and wrapped in `WrapperQueryBuilder`. The shape `{"knn": {field: {vector, k}}}` is stable across OpenSearch 2.x.

**Filter inheritance (with two critical bug fixes)**: Semantic search inherits all the same property filters, channel filters, board/medium/gradeLevel filters etc. from the text path — so existing search constraints still apply. Two bugs were fixed:
- Full-text `propertyName='*'` leaked into the kNN filter clause → fix: strip it before building filter set, restore via try-finally
- `fuzzySearch=true` caused `prepareFilteredSearchQuery()` to return `FunctionScoreQueryBuilder` (not `BoolQueryBuilder`), silently dropping all filters → fix: temporarily disable fuzzy during filter building

**kNN owns ranking**: Default `name`/`lastUpdatedOn` sort is skipped for semantic mode. kNN's cosine similarity ranking is preserved. Explicit `sort_by` from the request is still honored.

**Cosine scores always surfaced**: Semantic results always include the cosine similarity score per hit. In text mode, scores are only surfaced when `fuzzySearch=true`.

**minScore threshold**: Optional `min_score` parameter filters out hits below a cosine confidence threshold, letting callers drop low-quality matches.

---

## Semantic Search Infrastructure Files

These files form the backbone of semantic search at query time. Most were introduced in prior work; this PR wires them together and fixes correctness issues.

---

### `search-api/search-core/src/main/java/org/sunbird/search/strategy/SemanticQueryStrategy.java` (Modified)

**The core of semantic search.** Implements `QueryStrategy` for `search_mode=semantic`.

**What it does**:
1. Extracts query string from `properties` where `propertyName='*'` (same as text search full-text query)
2. Calls `EmbeddingClientFactory.get().embed(query)` — gets float32 vector, checks/updates cache
3. Quantizes float[] → byte[] via `QuantizationStrategyFactory.get("int8")`
4. Builds kNN JSON manually: `{"knn": {"chunks.embedding": {"vector": [...], "k": 50}}}`
5. Wraps in nested query on `chunks` path (vectors live inside nested objects)
6. Filters by `schema_version` to ensure vector compatibility
7. Inherits all property filters from the text query (after stripping full-text leg)
8. Returns a `BoolQueryBuilder` with `must(kNN nested)` + inherited filters

**What changed in this PR**: Two critical bug fixes to filter inheritance (detailed above in design decisions). The old approach of inspecting `QueryBuilder.getName()` to strip the text leg failed silently — replaced with DTO clone + strip `propertyName='*'` + try-finally restore.

---

### `search-api/search-core/src/main/java/org/sunbird/search/embedding/EmbeddingClient.java`

**Interface defining the query-time embedding contract.**

Methods:
- `getName()` — service identifier ("openai" or "e5") — must match what the embedding job used at index time
- `getVersion()` — model version — used as part of cache key
- `getDimensions()` — vector dimension — must match OpenSearch `knn_vector` field mapping
- `embed(text)` — single text → float[]
- `embedBatch(texts)` — batch embedding (used by hybrid when multiple queries needed)
- `close()` — release HTTP client

**Why it exists**: Decouples `SemanticQueryStrategy` from the specific embedding provider. Same interface regardless of whether OpenAI or E5 is configured.

---

### `search-api/search-core/src/main/java/org/sunbird/search/embedding/EmbeddingClientFactory.java`

**Lazy singleton factory that builds the configured embedding client from application config.**

Reads `semantic_search.embedding_service` (default: "openai") and builds either:
- `OpenAIEmbeddingClient` — for OpenAI or Azure OpenAI (`azure_endpoint` config triggers Azure path)
- `E5EmbeddingClient` — for HuggingFace TEI server (multilingual-e5-large)

Both clients are thread-safe, singleton per service instance — HTTP client construction is amortized.

Config keys used:
```hocon
semantic_search {
  embedding_service = "openai"         # or "e5"
  openai {
    api_key          = ${?OPENAI_API_KEY}
    model            = "text-embedding-3-small"
    dimensions       = 1536
    timeout          = 5
    azure_endpoint   = ""              # non-empty activates Azure path
    azure_deployment = ""
    azure_api_version = "2024-12-01-preview"
  }
  e5 {
    host       = "localhost"
    port       = 80
    dimensions = 768
    timeout    = 5
  }
}
```

---

### `search-api/search-core/src/main/java/org/sunbird/search/embedding/EmbeddingCache.java`

**LRU + TTL cache for query embedding API calls.**

Every semantic search query requires an embedding API call — which is a network round-trip (5-30ms to OpenAI, sub-ms for local E5). The cache avoids repeated API calls for the same query text.

**Implementation**:
- Key: `sha256(service:model:text)` — different models or providers cannot collide
- Storage: `LinkedHashMap` with access-order (LRU eviction) + size cap
- TTL checked on read — stale entries evicted lazily
- Thread-safe via coarse synchronization (expected QPS is low relative to OpenSearch path)
- Configurable: `enabled`, `size` (default 1024 entries), `ttl_seconds` (default 300s)

**Why SHA-256 key**: Prevents different embedding services/models from sharing cached vectors. A cached vector from model v1 must not be served for model v2.

---

### `search-api/search-core/src/main/java/org/sunbird/search/quantization/QuantizationStrategy.java`

**Interface for float → byte vector quantization at query time.**

```java
byte[] quantize(float[] vector);
```

**Why it exists**: The content-embedding-job stores int8-quantized byte vectors in OpenSearch (4x smaller than float32). At query time the search-api must quantize the query vector the same way — same byte-space — so cosine similarity is computed correctly. This interface mirrors the embedding job's quantization contract.

---

### `search-api/search-core/src/main/java/org/sunbird/search/quantization/QuantizationStrategyFactory.java`

**Returns the configured quantization strategy. Only int8 supported in v1.**

```java
QuantizationStrategyFactory.get("int8") // → Int8QuantizationStrategy
```

Same strategy name used by the content-embedding-job ensures byte-space alignment between index-time and query-time vectors.

---

### `search-api/search-core/src/main/java/org/sunbird/search/strategy/QueryStrategyFactory.java` (Modified)

**Gate-keeper for all search modes. Registers strategies at startup.**

- `TextQueryStrategy` always registered
- `SemanticQueryStrategy` + `HybridQueryStrategy` registered ONLY when `semantic_search.enabled = true`
- If semantic/hybrid mode requested but disabled → throws `ERR_SEMANTIC_DISABLED` (clear error, not silent degradation)
- If unknown mode → throws `ERR_INVALID_SEARCH_MODE`

**What changed in this PR**: One line added — `register(new HybridQueryStrategy())` alongside semantic registration.

---

## Secondary Feature: Hybrid Search (RRF)

Built directly on top of semantic search. Runs text and semantic searches in parallel and fuses results with Reciprocal Rank Fusion. A document that appears high in both rankings gets a naturally higher fused score — no score normalization needed.

---

### `search-api/search-core/src/main/java/org/sunbird/search/fusion/RrfFusion.java` (New)

**Pure RRF algorithm. No IO, no dependencies.**

```
score(document) = Σ  1 / (k + rank_i(document))
```

- Rank is 1-based. Missing from a list → contributes nothing
- Default `k=60` (Cormack et al.)
- Generic `<T>` — identity extracted via caller-supplied function
- Returns `List<FusedHit<T>>` sorted by score desc, with `ranks[]` per source preserved

---

### `search-api/search-core/src/main/java/org/sunbird/search/processor/HybridSearchExecutor.java` (New)

**Orchestrates hybrid search — parallel dispatch + RRF fusion.**

1. Clone `SearchDTO` twice — flip mode to `text` and `semantic`
2. Create **fresh `SearchProcessor` per leg** (avoids `relevanceSort` field race)
3. Dispatch both `processSearch()` — returns `Future`s, `.zip()` to wait for both
4. `RrfFusion.fuse()` on results lists keyed by `identifier`
5. Apply pagination post-fusion
6. Attach `score_components` (text_rank, semantic_rank) per hit
7. Borrow facets from text leg

---

### `search-api/search-core/src/main/java/org/sunbird/search/strategy/HybridQueryStrategy.java` (New)

**Fallback strategy for leaf code paths (processCount, collection fetch).** Returns text query. Real hybrid execution is intercepted in `SearchProcessor` before reaching this `build()` method.

---

### `search-api/search-core/src/main/java/org/sunbird/search/processor/SearchProcessor.java` (Modified)

Three additions:
1. **Hybrid intercept** at top of `processSearch()` → routes to `HybridSearchExecutor`
2. **Semantic scores always surfaced** — `getDocumentsFromSearchResultWithScore()` called for semantic mode (previously only for fuzzy)
3. **kNN ranking preserved** — `relevanceSort=true` for semantic mode skips default sort; `minScore` applied when provided

---

## Tertiary Feature: Content Enrich API

---

### `content-api/content-actors/src/main/scala/org/sunbird/content/util/EnrichManager.scala` (New)

Accepts list of content IDs, validates existence, emits `BE_JOB_REQUEST` Kafka events with `action=enrich`. Consumed by `EnrichOnlyFunction` in knowlg-publish which re-emits enriched metadata without republishing. Enables bulk backfilling of existing content into the semantic index.

---

### Other Modified Files (Plumbing)

| File | Change |
|------|--------|
| `ContentActor.scala` | Added `case "triggerEnrich"` → `EnrichManager.triggerEnrich()` |
| `ContentController.scala` | New `triggerEnrich()` Play action |
| `ApiId.scala` | Added `TRIGGER_ENRICH = "api.content.enrich"` |
| `content-service/conf/routes` | `POST /content/v3/enrich` route |
| `knowlg-service/conf/routes` | `POST /content/v3/enrich` route (mirrored) |

---

### `search-api/docs/semantic-search/openapi.yaml` (New)

OpenAPI 3.0 spec for `/v3/search`. Documents all three modes, semantic params, score_components, degraded fallback, error codes, 7 request examples and 4 response examples.

---

## How Everything Connects

```
POST /v3/search (search_mode=semantic)
  → SearchProcessor → QueryStrategyFactory.get("semantic")
  → SemanticQueryStrategy.build()
      → EmbeddingClientFactory.get().embed(query)   [OpenAI / E5]
      → EmbeddingCache.get/put()                    [LRU+TTL cache]
      → QuantizationStrategyFactory.get("int8").quantize()
      → kNN JSON → WrapperQueryBuilder → nested query
      → Inherit property filters (with bug fixes)
  → OpenSearch kNN → cosine scores → results

POST /v3/search (search_mode=hybrid)
  → SearchProcessor → HybridSearchExecutor.execute()
      ├── textProcessor.processSearch(mode=text)    [TextQueryStrategy]
      └── semProcessor.processSearch(mode=semantic) [SemanticQueryStrategy above]
  → RrfFusion.fuse([textHits, semHits], k=60)
  → Paginated results with score_components

POST /content/v3/enrich
  → ContentController → ContentActor → EnrichManager
  → Kafka: BE_JOB_REQUEST (action=enrich)
  → knowlg-publish: EnrichOnlyFunction
  → Enriched metadata event → content-embedding-job → vectors in OpenSearch
```
