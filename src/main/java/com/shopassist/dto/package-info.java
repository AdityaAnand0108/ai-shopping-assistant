/**
 * Data carried across boundaries: request bodies, response payloads, and the
 * records passed between the service layer and the model client.
 *
 * <p>Response types are whitelists rather than serialised entities. A column
 * added to an entity later cannot start appearing in a payload by accident.
 */
package com.shopassist.dto;
