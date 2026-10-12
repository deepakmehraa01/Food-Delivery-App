package com.ecommerce.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CustomerDirectory {
    private final RestClient client;
    public CustomerDirectory(RestClient.Builder builder, @Value("${services.customer-query.url}") String url) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); factory.setReadTimeout(5000);
        client = builder.baseUrl(url).requestFactory(factory).build();
    }
    public String emailFor(long customerId) {
        Customer customer = client.get().uri("/api/customers/{id}", customerId).retrieve().body(Customer.class);
        if (customer == null || customer.id() == null || customer.id() != customerId
                || customer.email() == null || customer.email().isBlank())
            throw new IllegalStateException("Customer email unavailable");
        return customer.email();
    }
    public record Customer(Long id, String email) {}
}
