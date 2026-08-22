/**
 * Availability bands and the permitted sort orders.
 *
 * <p>Both exist to avoid leaking. Availability is a band rather than a count,
 * so inventory cannot be reconstructed; sorting is an allowlist rather than a
 * free-text property, so results cannot be ordered by a hidden column and read
 * back out of the ordering.
 */
package com.shopassist.enums.catalog;
