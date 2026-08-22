/**
 * Order reads and purchasing.
 *
 * <p>No method takes a user id; the owner is resolved from the security context
 * on every call. Purchasing is split so that no single call both decides on a
 * purchase and completes it, and confirmation re-validates prices and stock
 * rather than trusting the draft.
 */
package com.shopassist.services.order;
