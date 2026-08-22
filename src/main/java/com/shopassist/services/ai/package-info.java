/**
 * The seam between the application and the language model.
 *
 * <p>{@code AssistantModel} and {@code CatalogRetriever} are interfaces with a
 * single implementation each, and those implementations are the only classes
 * that import Spring AI. That boundary is what lets the chat surface and the
 * hybrid search path be tested with no model server running.
 */
package com.shopassist.services.ai;
