package io.tileverse.rangereader.http;

import java.net.http.HttpRequest.Builder;
import java.util.Objects;

/**
 * API Key Authentication implementation for HttpRangeReader.
 * <p>
 * This authenticator adds an API key header to requests, which is commonly used
 * for API authentication. It supports different header names and value formats.
 */
public class ApiKeyAuthentication implements HttpAuthentication {

    private final String headerName;
    private final String apiKey;
    private final String valuePrefix;

    /**
     * Creates a new API Key Authentication instance with a custom header name.
     *
     * @param headerName The name of the header to use (e.g., "X-API-Key")
     * @param apiKey The API key value
     */
    public ApiKeyAuthentication(String headerName, String apiKey) {
        this(headerName, apiKey, "");
    }

    /**
     * Creates a new API Key Authentication instance with custom header name and value prefix.
     * <p>
     * This is useful for APIs that require a specific format for the API key value,
     * such as "ApiKey " or "Key " followed by the actual key.
     *
     * @param headerName The name of the header to use (e.g., "X-API-Key")
     * @param apiKey The API key value
     * @param valuePrefix An optional prefix for the API key value (e.g., "ApiKey ")
     */
    public ApiKeyAuthentication(String headerName, String apiKey, String valuePrefix) {
        this.headerName = Objects.requireNonNull(headerName, "Header name cannot be null");
        this.apiKey = Objects.requireNonNull(apiKey, "API key cannot be null");
        this.valuePrefix = valuePrefix != null ? valuePrefix : "";
    }

    @Override
    public Builder authenticate(Builder requestBuilder) {
        return requestBuilder.header(headerName, valuePrefix + apiKey);
    }
}
