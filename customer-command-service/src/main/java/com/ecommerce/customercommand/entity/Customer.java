package com.ecommerce.customercommand.entity;

import com.ecommerce.customercommand.enums.CustomerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "customers", indexes = {
        @Index(name = "idx_customers_customer_number", columnList = "customer_number"),
        @Index(name = "idx_customers_email", columnList = "email"),
        @Index(name = "idx_customers_phone", columnList = "phone")
})
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "customer_sequence")
    @SequenceGenerator(name = "customer_sequence", sequenceName = "customer_sequence", allocationSize = 1)
    private Long id;

    @Column(name = "customer_number", nullable = false, unique = true, length = 50, updatable = false)
    private String customerNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    @Setter
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    @Setter
    private String lastName;

    @Column(nullable = false, unique = true, length = 255)
    @Setter
    private String email;

    @Column(nullable = false, length = 20)
    @Setter
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Setter
    private CustomerStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (customerNumber == null && id != null) {
            customerNumber = "CUS-" + (100000 + id);
        }
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }

}
