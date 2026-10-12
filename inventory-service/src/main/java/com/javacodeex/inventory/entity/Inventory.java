package com.javacodeex.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_code", nullable = false, unique = true)
    private String productCode;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    protected Inventory() {
    }

    public Inventory(String productCode, String productName, Integer quantity) {
        this.productCode = productCode;
        this.productName = productName;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public Integer getQuantity() { return quantity; }
}
