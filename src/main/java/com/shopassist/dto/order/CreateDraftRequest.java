package com.shopassist.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A basket the shopper wants priced.
 *
 * <p>Constraints here bound the shape of the request only. How many lines an
 * order may hold, how many units of one product, and whether any of it is in
 * stock are policy, and policy lives in {@code PurchaseService} so that the
 * assistant and this API cannot drift apart on the answer.
 *
 * <p>Notably absent: price. The client sends what it wants and how many, never
 * what it expects to pay — the server prices the basket itself. A checkout that
 * accepted a price from the browser would be one edited request away from a
 * free order.
 */
public record CreateDraftRequest(

        @NotEmpty(message = "A purchase needs at least one item")
        @Size(max = 50, message = "Too many lines in one request")
        @Valid
        List<Line> items
) {

    /** One requested product and how many of it. */
    public record Line(

            @NotBlank(message = "A SKU is required")
            @Size(max = 40)
            String sku,

            @Positive(message = "Quantity must be at least 1")
            int quantity
    ) {
    }
}
