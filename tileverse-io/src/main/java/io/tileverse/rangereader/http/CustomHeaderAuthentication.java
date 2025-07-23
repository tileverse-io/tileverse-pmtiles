package io.tileverse.rangereader.http;

import java.net.http.HttpRequest.Builder;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Custom HTTP Header Authentication implementation for HttpRangeReader.
 * <p>
 * This authenticator allows arbitrary headers to be added to requests,
 * which is useful for custom authentication schemes or when multiple
 * headers are required.
 */
public class CustomHeaderAuthentication implements HttpAuthentication {

    private final Map<String, String> headers;

    /**
     * Creates a new Custom Header Authentication instance.
     *
     * @param headers A map of header names to values
     */
    public CustomHeaderAuthentication(Map<String, String> headers) {
        this.headers = Collections.unmodifiableMap(Objects.requireNonNull(headers, "Headers map cannot be null"));

        if (headers.isEmpty()) {
            throw new IllegalArgumentException("Headers map cannot be empty");
        }
    }

    @Override
    public Builder authenticate(Builder requestBuilder) {
        // Add all headers to the request builder
        headers.forEach(requestBuilder::header);
        return requestBuilder;
    }
}
