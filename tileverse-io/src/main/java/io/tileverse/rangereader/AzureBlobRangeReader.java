package io.tileverse.rangereader;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.rest.Response;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.models.BlobRange;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.models.DownloadRetryOptions;
import com.azure.storage.common.StorageSharedKeyCredential;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A RangeReader implementation that reads from an Azure Blob Storage container.
 * <p>
 * This class enables reading data stored in Azure Blob Storage using the
 * Azure Storage Blob client library for Java.
 */
public class AzureBlobRangeReader extends AbstractRangeReader implements RangeReader {

    private final BlobClient blobClient;
    private long contentLength = -1;

    /**
     * Creates a new AzureBlobRangeReader for the specified blob.
     *
     * @param blobClient The Azure Blob client to read from
     * @throws IOException If an I/O error occurs
     */
    public AzureBlobRangeReader(BlobClient blobClient) throws IOException {
        this.blobClient = Objects.requireNonNull(blobClient, "BlobClient cannot be null");

        // Check if the blob exists and get its content length
        try {
            if (!blobClient.exists()) {
                throw new IOException("Blob does not exist: " + blobClient.getBlobUrl());
            }

            this.contentLength = blobClient.getProperties().getBlobSize();
        } catch (Exception e) {
            throw new IOException("Failed to access blob: " + blobClient.getBlobUrl(), e);
        }
    }

    @Override
    protected int readRangeNoFlip(long offset, int actualLength, ByteBuffer target) throws IOException {

        try {
            // Download the specified range
            BlobRange range = new BlobRange(offset, (long) actualLength);
            DownloadRetryOptions options = new DownloadRetryOptions().setMaxRetryRequests(3);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(actualLength);

            // API requires Duration and Context parameters
            Response<Void> response = blobClient.downloadStreamWithResponse(
                    outputStream,
                    range,
                    options,
                    new BlobRequestConditions(),
                    false,
                    java.time.Duration.ofSeconds(60), // Timeout
                    com.azure.core.util.Context.NONE); // Context

            // Verify the response is successful
            if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
                throw new IOException("Failed to download blob range, status code: " + response.getStatusCode());
            }

            // Copy the bytes directly into the target buffer
            byte[] data = outputStream.toByteArray();
            target.put(data);
            // Return the number of bytes read
            return data.length;
        } catch (Exception e) {
            throw new IOException("Failed to read range from blob: " + e.getMessage(), e);
        }
    }

    @Override
    public long size() throws IOException {
        if (contentLength < 0) {
            try {
                contentLength = blobClient.getProperties().getBlobSize();
            } catch (Exception e) {
                throw new IOException("Failed to get blob size: " + e.getMessage(), e);
            }
        }
        return contentLength;
    }

    @Override
    public void close() {
        // Azure BlobClient doesn't require explicit closing
    }

    /**
     * Creates a new builder for AzureBlobRangeReader.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for AzureBlobRangeReader.
     */
    public static class Builder {
        private BlobClient blobClient;
        private TokenCredential tokenCredential;
        private String accountName;
        private String accountKey;
        private String connectionString;
        private String containerName;
        private String blobPath;
        private String sasToken;

        private Builder() {}

        /**
         * Sets the Azure Blob client to use.
         *
         * @param blobClient the blob client
         * @return this builder
         */
        public Builder blobClient(BlobClient blobClient) {
            this.blobClient = Objects.requireNonNull(blobClient, "BlobClient cannot be null");
            return this;
        }

        /**
         * Sets the Azure token credential.
         *
         * @param tokenCredential the token credential
         * @return this builder
         */
        public Builder tokenCredential(TokenCredential tokenCredential) {
            this.tokenCredential = Objects.requireNonNull(tokenCredential, "Token credential cannot be null");
            return this;
        }

        /**
         * Sets the Azure Storage account name.
         *
         * @param accountName the account name
         * @return this builder
         */
        public Builder accountName(String accountName) {
            this.accountName = Objects.requireNonNull(accountName, "Account name cannot be null");
            return this;
        }

        /**
         * Sets the Azure Storage account name and key.
         *
         * @param accountName the account name
         * @param accountKey the account key
         * @return this builder
         */
        public Builder accountCredentials(String accountName, String accountKey) {
            this.accountName = Objects.requireNonNull(accountName, "Account name cannot be null");
            this.accountKey = Objects.requireNonNull(accountKey, "Account key cannot be null");
            return this;
        }

        /**
         * Sets the Azure Storage connection string.
         *
         * @param connectionString the connection string
         * @return this builder
         */
        public Builder connectionString(String connectionString) {
            this.connectionString = Objects.requireNonNull(connectionString, "Connection string cannot be null");
            return this;
        }

        /**
         * Sets the container name.
         *
         * @param containerName the container name
         * @return this builder
         */
        public Builder containerName(String containerName) {
            this.containerName = Objects.requireNonNull(containerName, "Container name cannot be null");
            return this;
        }

        /**
         * Sets the blob path.
         *
         * @param blobPath the blob path
         * @return this builder
         */
        public Builder blobPath(String blobPath) {
            this.blobPath = Objects.requireNonNull(blobPath, "Blob path cannot be null");
            return this;
        }

        /**
         * Sets the SAS token.
         *
         * @param sasToken the SAS token
         * @return this builder
         */
        public Builder sasToken(String sasToken) {
            this.sasToken = Objects.requireNonNull(sasToken, "SAS token cannot be null");
            return this;
        }

        /**
         * Sets the blob information from an Azure URI.
         *
         * @param uri the Azure URI (azure://account.blob.core.windows.net/container/blob or https://...)
         * @return this builder
         */
        public Builder uri(URI uri) {
            Objects.requireNonNull(uri, "URI cannot be null");

            String scheme = uri.getScheme().toLowerCase();
            if (!"azure".equals(scheme) && !"https".equals(scheme) && !"blob".equals(scheme)) {
                throw new IllegalArgumentException("URI must have azure, https, or blob scheme: " + uri);
            }

            String blobUrl = uri.toString();
            if (scheme.equals("azure")) {
                // Convert azure://account.blob.core.windows.net/container/blob
                // to https://account.blob.core.windows.net/container/blob
                blobUrl = blobUrl.replace("azure://", "https://");
            }

            // Parse container and blob from path
            String path = uri.getPath();
            if (path != null && path.startsWith("/")) {
                String[] pathParts = path.substring(1).split("/", 2);
                if (pathParts.length >= 1) {
                    this.containerName = pathParts[0];
                }
                if (pathParts.length >= 2) {
                    this.blobPath = pathParts[1];
                }
            }

            // Check for SAS token in query string
            String query = uri.getQuery();
            if (query != null && !query.isEmpty()) {
                this.sasToken = query;
            }

            return this;
        }

        /**
         * Builds the AzureBlobRangeReader.
         *
         * @return a new AzureBlobRangeReader instance
         * @throws IOException if an error occurs during construction
         */
        public AzureBlobRangeReader build() throws IOException {
            BlobClient client = blobClient;
            if (client == null) {
                if (connectionString != null) {
                    if (containerName == null || blobPath == null) {
                        throw new IllegalStateException(
                                "Container name and blob path are required with connection string");
                    }
                    client = new BlobClientBuilder()
                            .connectionString(connectionString)
                            .containerName(containerName)
                            .blobName(blobPath)
                            .buildClient();
                } else if (accountName != null && accountKey != null) {
                    if (containerName == null || blobPath == null) {
                        throw new IllegalStateException(
                                "Container name and blob path are required with account credentials");
                    }
                    StorageSharedKeyCredential credential = new StorageSharedKeyCredential(accountName, accountKey);
                    String endpoint = String.format("https://%s.blob.core.windows.net", accountName);
                    client = new BlobClientBuilder()
                            .endpoint(endpoint)
                            .credential(credential)
                            .containerName(containerName)
                            .blobName(blobPath)
                            .buildClient();
                } else if (accountName != null && sasToken != null) {
                    if (containerName == null || blobPath == null) {
                        throw new IllegalStateException("Container name and blob path are required with SAS token");
                    }
                    String sasTokenWithQuestion = sasToken.startsWith("?") ? sasToken : "?" + sasToken;
                    String endpoint = String.format("https://%s.blob.core.windows.net", accountName);
                    client = new BlobClientBuilder()
                            .endpoint(endpoint)
                            .containerName(containerName)
                            .blobName(blobPath)
                            .sasToken(sasTokenWithQuestion)
                            .buildClient();
                } else if (tokenCredential != null) {
                    if (containerName == null || blobPath == null || accountName == null) {
                        throw new IllegalStateException(
                                "Account name, container name and blob path are required with token credential");
                    }
                    String endpoint = String.format("https://%s.blob.core.windows.net", accountName);
                    client = new BlobClientBuilder()
                            .endpoint(endpoint)
                            .credential(tokenCredential)
                            .containerName(containerName)
                            .blobName(blobPath)
                            .buildClient();
                } else {
                    // Use default Azure credential
                    if (containerName == null || blobPath == null || accountName == null) {
                        throw new IllegalStateException("Account name, container name and blob path are required");
                    }
                    String endpoint = String.format("https://%s.blob.core.windows.net", accountName);
                    client = new BlobClientBuilder()
                            .endpoint(endpoint)
                            .credential(new DefaultAzureCredentialBuilder().build())
                            .containerName(containerName)
                            .blobName(blobPath)
                            .buildClient();
                }
            }

            return new AzureBlobRangeReader(client);
        }
    }
}
