/**
 * An AI shopping assistant: a Spring Boot monolith where a language model
 * answers questions about a real catalog and real orders.
 *
 * <p>The organising idea of the codebase is that <b>the model is never trusted
 * with a fact or an action</b>. It cannot read the database, only call tools;
 * those tools cannot be pointed at another shopper, because they take no
 * identity argument; and they cannot complete a purchase in one step. Prompts
 * ask the model to behave well, but nothing important depends on it doing so.
 *
 * <p>Packages are organised by feature rather than by layer, so everything about
 * orders lives together. The {@code ai} subtree is the only place that knows a
 * language model exists; every other package would compile and work unchanged if
 * the assistant were removed.
 */
package com.shopassist;
