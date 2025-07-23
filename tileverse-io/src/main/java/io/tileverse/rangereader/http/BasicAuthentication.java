package io.tileverse.rangereader.http;

import java.net.http.HttpRequest.Builder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * HTTP Basic Authentication implementation for HttpRangeReader.
 * <p>
 * This authenticator adds the standard HTTP Basic Authentication header
 * to requests, which encodes username and password in Base64.
 */
public class BasicAuthentication implements HttpAuthentication {

    private final String username;

    @SuppressWarnings("unused")
    private final String password;

    private final String encodedCredentials;

    /**
     * Creates a new Basic Authentication instance.
     *
     * @param username The username
     * @param password The password
     */
    public BasicAuthentication(String username, String password) {
        this.username = Objects.requireNonNull(username, "Username cannot be null");
        this.password = Objects.requireNonNull(password, "Password cannot be null");

        // Pre-compute the encoded credentials
        String credentials = username + ":" + password;
        this.encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Builder authenticate(Builder requestBuilder) {
        return requestBuilder.header("Authorization", "Basic " + encodedCredentials);
    }

    /**
     * Gets the username.
     *
     * @return The username
     */
    public String getUsername() {
        return username;
    }
}
