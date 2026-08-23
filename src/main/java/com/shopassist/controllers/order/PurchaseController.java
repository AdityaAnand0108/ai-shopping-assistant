package com.shopassist.controllers.order;

import com.shopassist.dto.order.CreateDraftRequest;
import com.shopassist.dto.order.DraftResponse;
import com.shopassist.dto.order.OrderDetailResponse;
import com.shopassist.services.order.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Checkout from the storefront.
 *
 * <p>Two calls, deliberately. {@code POST /draft} prices a basket and creates
 * nothing; {@code POST /{reference}/confirm} is the only one that places an
 * order. That is the same split the assistant's tools observe, and for the same
 * reason: no single request should be able to both decide on a purchase and
 * complete it, whether it came from a model or from a button.
 *
 * <p>Like the order endpoints, no path here carries a user identifier. Whose
 * basket and whose draft comes from the token.
 */
@RestController
@RequestMapping("/api/purchases")
@Tag(name = "Purchases", description = "Price a basket, then place the order")
public class PurchaseController {

    private final CheckoutService checkoutService;

    public PurchaseController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping("/draft")
    @Operation(summary = "Price a basket without ordering anything")
    public ResponseEntity<DraftResponse> draft(@Valid @RequestBody CreateDraftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(checkoutService.draft(request));
    }

    @PostMapping("/{reference}/confirm")
    @Operation(summary = "Place the order for a basket you have already had priced")
    public ResponseEntity<OrderDetailResponse> confirm(@PathVariable String reference) {
        return ResponseEntity.status(HttpStatus.CREATED).body(checkoutService.confirm(reference));
    }

    @DeleteMapping("/{reference}")
    @Operation(summary = "Abandon a priced basket you decided against")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String reference) {
        checkoutService.cancel(reference);
    }
}
