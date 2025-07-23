package io.tileverse.rangereader.http;

import java.net.http.HttpRequest.Builder;
import java.util.Objects;

/**
 * HTTP Bearer Token Authentication implementation for HttpRangeReader.
 * <p>
 * This authenticator adds the standard Bearer token Authorization header
 * to requests, which is commonly used for OAuth and JWT tokens.
 */
public class BearerTokenAuthentication implements HttpAuthentication {

    private final String token;

    /**
     * Creates a new Bearer Token Authentication instance.
     *
     * @param token The bearer token
     */
    public BearerTokenAuthentication(String token) {
        this.token = Objects.requireNonNull(token, "Token cannot be null");
    }

    @Override
    public Builder authenticate(Builder requestBuilder) {
        return requestBuilder.header("Authorization", "Bearer " + token);
    }
}
