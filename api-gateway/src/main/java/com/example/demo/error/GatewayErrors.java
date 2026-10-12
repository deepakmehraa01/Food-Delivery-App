package com.example.demo.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Component
public class GatewayErrors {
    public static final String REQUEST_ID = "gateway.requestId";
    private final ObjectMapper mapper;

    public GatewayErrors(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ErrorBody body(HttpServletRequest request, HttpStatus status, String code, String message) {
        Object path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        if (path == null) path = request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
        if (path == null) path = request.getRequestURI();
        String requestId = (String) request.getAttribute(REQUEST_ID);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
            request.setAttribute(REQUEST_ID, requestId);
        }
        return new ErrorBody(Instant.now(), status.value(), status.getReasonPhrase(), code, message,
                path.toString(), requestId);
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String code, String message) throws IOException {
        ErrorBody body = body(request, status, code, message);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-Request-ID", body.requestId());
        mapper.writeValue(response.getOutputStream(), body);
    }

    public record ErrorBody(Instant timestamp, int status, String error, String code, String message,
                            String path, String requestId) { }
}
