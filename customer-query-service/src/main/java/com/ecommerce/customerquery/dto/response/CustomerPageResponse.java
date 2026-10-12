package com.ecommerce.customerquery.dto.response;

import java.util.List;

public record CustomerPageResponse(
        List<CustomerResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
