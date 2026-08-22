/**
 * Orders, their tracking timelines, and buying.
 *
 * <p>Two invariants hold across this package:
 *
 * <ul>
 *   <li>Every order lookup takes the owner as well as the order number. There is
 *       deliberately no {@code findByOrderNumber(String)}, so the ownership check
 *       cannot be forgotten at a call site.</li>
 *   <li>An order that belongs to somebody else is indistinguishable from one
 *       that never existed, so the order-number space cannot be walked.</li>
 * </ul>
 *
 * <p>{@code order_events} records what actually happened to an order. It is why
 * a delivery question can be answered from recorded facts rather than estimated.
 */
package com.shopassist.order;
