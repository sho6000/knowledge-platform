package org.sunbird.search.processor;

import org.sunbird.common.Platform;
import scala.concurrent.ExecutionContext;

import java.util.concurrent.ForkJoinPool;

/**
 * Dedicated thread pool for blocking embedding HTTP calls.
 * Keeps embed latency off the Pekko actor dispatcher — see DESIGN.md §Threading.
 */
public final class SemanticEmbeddingPool {

    private static volatile ForkJoinPool pool;
    private static volatile ExecutionContext context;

    private SemanticEmbeddingPool() { }

    public static ExecutionContext context() {
        if (context == null) {
            synchronized (SemanticEmbeddingPool.class) {
                if (context == null) {
                    int parallelism = Platform.config.hasPath("semantic_search.embedding_pool_size")
                            ? Platform.config.getInt("semantic_search.embedding_pool_size")
                            : Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
                    pool    = new ForkJoinPool(parallelism);
                    context = ExecutionContext.fromExecutorService(pool);
                }
            }
        }
        return context;
    }
}
