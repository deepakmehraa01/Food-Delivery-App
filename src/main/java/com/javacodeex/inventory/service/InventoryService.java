package com.javacodeex.inventory.service;

import com.javacodeex.inventory.dto.InventoryResponse;
import com.javacodeex.inventory.entity.Inventory;
import com.javacodeex.inventory.exception.ProductNotFoundException;
import com.javacodeex.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public InventoryResponse checkAvailability(String productCode, Integer requestedQuantity) {
        if (requestedQuantity == null || requestedQuantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }

        Inventory inventory = inventoryRepository.findByProductCode(productCode)
                .orElseThrow(() -> new ProductNotFoundException("Product not found: " + productCode));

        return new InventoryResponse(
                inventory.getProductCode(),
                inventory.getProductName(),
                inventory.getQuantity(),
                inventory.getQuantity() >= requestedQuantity
        );
    }
}
