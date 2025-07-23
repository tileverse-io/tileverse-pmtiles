package io.tileverse.rangereader.http;

import java.net.http.HttpRequest.Builder;

/**
 * Interface for HTTP authentication strategies used by HttpRangeReader.
 * <p>
 * Implementations of this interface provide authentication for HTTP requests
 * by adding appropriate headers or other authentication mechanisms to the
 * request builder.
 */
public interface HttpAuthentication {

    /**
     * Apply authentication to an HTTP request.
     *
     * @param requestBuilder The HTTP request builder to authenticate
     * @return The same request builder with authentication applied
     */
    Builder authenticate(Builder requestBuilder);
}
