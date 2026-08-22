/**
 * The seam between the application and whatever language model backs it.
 *
 * <p>{@link com.shopassist.ai.client.AssistantModel} is the interface the rest
 * of the application talks to;
 * {@link com.shopassist.ai.client.SpringAiAssistantModel} is the only class in
 * the project that imports Spring AI. That boundary is what lets the chat
 * surface be tested with no model server running, and what gives tool
 * registration a single home.
 */
package com.shopassist.ai.client;
