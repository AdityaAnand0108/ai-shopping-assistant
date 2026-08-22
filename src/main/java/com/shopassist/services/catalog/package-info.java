/**
 * Catalog reads and hybrid search.
 *
 * <p>Retrieval decides which products a phrase is about; SQL decides what may
 * actually be shown. Retrieval never has the last word — if it is unavailable
 * or returns nothing useful, search falls back to keywords.
 */
package com.shopassist.services.catalog;
