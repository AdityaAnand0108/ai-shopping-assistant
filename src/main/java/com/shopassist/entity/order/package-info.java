/**
 * Orders, their line items, their tracking timeline, and proposed purchases.
 *
 * <p>{@code OrderEvent} records what actually happened to an order, which is
 * why a delivery question can be answered from recorded facts rather than
 * estimated. {@code OrderDraft} is a purchase nobody has agreed to yet — it is
 * the reason the model cannot spend a shopper's money in a single call.
 */
package com.shopassist.entity.order;
