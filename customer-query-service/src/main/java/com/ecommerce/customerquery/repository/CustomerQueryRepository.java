package com.ecommerce.customerquery.repository;

import com.ecommerce.customerquery.entity.CustomerReadModel;
import com.ecommerce.customerquery.enums.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface CustomerQueryRepository extends JpaRepository<CustomerReadModel, Long>,
        JpaSpecificationExecutor<CustomerReadModel> {

    Optional<CustomerReadModel> findByIdAndStatusNot(Long id, CustomerStatus status);

    Optional<CustomerReadModel> findByCustomerNumberAndStatusNot(String customerNumber, CustomerStatus status);

    Optional<CustomerReadModel> findByEmailIgnoreCaseAndStatusNot(String email, CustomerStatus status);

    Page<CustomerReadModel> findByStatus(CustomerStatus status, Pageable pageable);

    Page<CustomerReadModel> findByStatusNot(CustomerStatus status, Pageable pageable);
}
