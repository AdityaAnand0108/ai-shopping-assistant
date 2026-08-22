/**
 * Failures reaching the language model. Distinct from a generic error so the
 * caller receives a 503 with a clear retry, not a 500 that looks like a bug in
 * the shop.
 */
package com.shopassist.exception.ai;
