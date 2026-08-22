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
 * <p>Packages are organised <b>layer-first, then by business domain</b>: the
 * top level names a technical responsibility ({@code controllers},
 * {@code services}, {@code repository}, {@code entity}, {@code dto}), and the
 * domain appears beneath it ({@code services.order}, {@code dto.catalog}). The
 * six domains are ai, auth, catalog, chat, order and user.
 */
package com.shopassist;
