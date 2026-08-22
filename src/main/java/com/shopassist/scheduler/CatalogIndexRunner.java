package com.shopassist.scheduler;

import com.shopassist.services.ai.VectorCatalogRetriever;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Builds the semantic index once the catalog exists.
 *
 * <p>Ordered after {@code DataSeeder}: indexing an empty table would produce an
 * empty index and then never rebuild, because the stored count would match.
 *
 * <p>Carries the same property condition as the retriever it drives. Retrieval
 * is switched off in tests, where there is no embedding model to call, and a
 * runner that hard-depended on a bean which had conditioned itself away would
 * fail the whole context. The condition is on the property rather than on the
 * bean because {@code @ConditionalOnBean} outside auto-configuration depends on
 * definition ordering, and is documented as unreliable there.
 */
@Component
@ConditionalOnProperty(prefix = "shopassist.rag", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@Order(20)
public class CatalogIndexRunner implements ApplicationRunner {

    private final VectorCatalogRetriever retriever;

    public CatalogIndexRunner(VectorCatalogRetriever retriever) {
        this.retriever = retriever;
    }

    @Override
    public void run(ApplicationArguments args) {
        retriever.buildIndex();
    }
}
