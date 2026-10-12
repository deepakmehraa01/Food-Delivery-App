package com.javacodeex.inventory.controller;

import com.javacodeex.inventory.dto.InventoryResponse;
import com.javacodeex.inventory.service.InventoryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productCode}")
    public InventoryResponse checkStock(
            @PathVariable @NotBlank String productCode,
            @RequestParam @Positive Integer quantity) {
        return inventoryService.checkAvailability(productCode, quantity);
    }
}
