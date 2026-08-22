/**
 * The only way the language model reaches data.
 *
 * <p>Two properties here are structural rather than instructed, and both are
 * covered by tests:
 *
 * <ul>
 *   <li><b>No tool takes a customer argument.</b> The shopper is resolved from
 *       the security context inside the service layer, so there is no parameter
 *       through which the model could name another account.</li>
 *   <li><b>Buying takes two calls that cannot be combined.</b> One prices a
 *       proposal; only a second, carrying the reference the first returned,
 *       creates an order.</li>
 * </ul>
 *
 * <p>Tool descriptions are written for the model rather than for a developer:
 * that text is the only guidance it has when deciding which tool to reach for.
 */
package com.shopassist.services.ai.tools;
