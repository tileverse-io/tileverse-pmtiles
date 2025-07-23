# Cloud Storage Support in Tileverse PMTiles

This document explains how to use cloud storage support in Tileverse PMTiles, which allows reading PMTiles files from various sources including:

- Local files
- HTTP/HTTPS URLs
- AWS S3 buckets
- Azure Blob Storage
- Google Cloud Storage

## Basic Usage

Tileverse PMTiles uses the [Tileverse Range Reader](https://github.com/tileverse-io/tileverse-rangereader) library to provide efficient access to PMTiles files from multiple sources. Each storage type has its own builder for creating `RangeReader` instances.

```java
import io.tileverse.rangereader.s3.S3RangeReader;
import io.tileverse.rangereader.cache.CachingRangeReader;
import io.tileverse.pmtiles.PMTilesReader;

// Create a RangeReader for S3 with caching
RangeReader s3Reader = S3RangeReader.builder()
    .uri(URI.create("s3://my-bucket/path/to/tiles.pmtiles"))
    .region(Region.US_WEST_2)
    .build();

RangeReader cachedReader = CachingRangeReader.builder(s3Reader)
    .maximumSize(1000)
    .withBlockAlignment()
    .build();

// Use the reader with PMTilesReader
try (PMTilesReader pmtiles = new PMTilesReader(cachedReader)) {
    // Access tiles, metadata, etc.
    Optional<byte[]> tile = pmtiles.getTile(10, 885, 412);
}
```

## Storage Types

### Local Files

```java
import io.tileverse.rangereader.file.FileRangeReader;

// From a Path
RangeReader reader = FileRangeReader.builder()
    .path(Path.of("/path/to/tiles.pmtiles"))
    .build();

// From a file URI
RangeReader reader = FileRangeReader.builder()
    .uri(URI.create("file:///path/to/tiles.pmtiles"))
    .build();
```

### HTTP/HTTPS URLs

```java
import io.tileverse.rangereader.http.HttpRangeReader;

// Basic HTTP reader
RangeReader reader = HttpRangeReader.builder()
    .uri(URI.create("https://example.com/tiles.pmtiles"))
    .build();

// With authentication
RangeReader reader = HttpRangeReader.builder()
    .uri(URI.create("https://example.com/tiles.pmtiles"))
    .bearerToken("your-api-token")
    .build();

// For self-signed certificates
RangeReader reader = HttpRangeReader.builder()
    .uri(URI.create("https://internal-server.example.com/tiles.pmtiles"))
    .trustAllCertificates()
    .build();
```

### AWS S3

```java
import io.tileverse.rangereader.s3.S3RangeReader;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;

// Basic S3 reader (uses default credentials)
RangeReader reader = S3RangeReader.builder()
    .uri(URI.create("s3://my-bucket/path/to/tiles.pmtiles"))
    .build();

// With specific region
RangeReader reader = S3RangeReader.builder()
    .uri(URI.create("s3://my-bucket/path/to/tiles.pmtiles"))
    .region(Region.US_WEST_2)
    .build();

// With specific credentials
RangeReader reader = S3RangeReader.builder()
    .uri(URI.create("s3://my-bucket/path/to/tiles.pmtiles"))
    .credentialsProvider(ProfileCredentialsProvider.builder()
            .profileName("dev")
            .build())
    .build();
```

### Azure Blob Storage

```java
import io.tileverse.rangereader.azure.AzureBlobRangeReader;

// From a URI (using DefaultAzureCredential)
RangeReader reader = AzureBlobRangeReader.builder()
    .uri(URI.create("azure://account.blob.core.windows.net/container/path/to/tiles.pmtiles"))
    .build();

// With explicit account credentials
RangeReader reader = AzureBlobRangeReader.builder()
    .accountName("myaccount")
    .accountKey("accountkey==")
    .containerName("container")
    .blobName("path/to/tiles.pmtiles")
    .build();

// With connection string
RangeReader reader = AzureBlobRangeReader.builder()
    .connectionString("DefaultEndpointsProtocol=https;AccountName=myaccount;AccountKey=key==")
    .containerName("container")
    .blobName("path/to/tiles.pmtiles")
    .build();

// With SAS token (included in URI)
RangeReader reader = AzureBlobRangeReader.builder()
    .uri(URI.create("azure://account.blob.core.windows.net/container/tiles.pmtiles?sv=2020-08-04&..."))
    .build();
```

### Google Cloud Storage

```java
import io.tileverse.rangereader.gcs.GoogleCloudStorageRangeReader;

// Basic GCS reader (uses default credentials)
RangeReader reader = GoogleCloudStorageRangeReader.builder()
    .uri(URI.create("gs://my-bucket/path/to/tiles.pmtiles"))
    .build();

// With specific credentials
RangeReader reader = GoogleCloudStorageRangeReader.builder()
    .uri(URI.create("gs://my-bucket/path/to/tiles.pmtiles"))
    .credentialsProvider(ServiceAccountCredentials.fromStream(
        new FileInputStream("path/to/service-account.json")))
    .build();
```

## Performance Optimizations

When reading from cloud storage or HTTP servers, performance can be improved by using caching and block alignment:

### Caching

Caching helps reduce repeated reads to the same byte ranges, which is common when reading from PMTiles files:

```java
import io.tileverse.rangereader.cache.CachingRangeReader;

// Wrap any reader with caching
RangeReader baseReader = S3RangeReader.builder()
    .uri(URI.create("s3://my-bucket/tiles.pmtiles"))
    .build();

RangeReader cachedReader = CachingRangeReader.builder(baseReader)
    .maximumSize(1000)  // Cache up to 1000 ranges
    .build();
```

### Block Alignment

Block alignment aligns all reads to fixed-size blocks, which can reduce the number of HTTP requests when reading from cloud storage:

```java
import io.tileverse.rangereader.blockaligned.BlockAlignedRangeReader;

// Default block size (64KB)
RangeReader alignedReader = BlockAlignedRangeReader.builder(baseReader)
    .build();

// Custom block size (16KB)
RangeReader alignedReader = BlockAlignedRangeReader.builder(baseReader)
    .blockSize(16384)
    .build();
```

### Combined Optimizations

For best performance, combine caching and block alignment in the optimal order:

```java
// Optimal decorator stack: CachingRangeReader -> BlockAlignedRangeReader -> BaseReader
RangeReader baseReader = S3RangeReader.builder()
    .uri(URI.create("s3://my-bucket/tiles.pmtiles"))
    .build();

RangeReader alignedReader = BlockAlignedRangeReader.builder(baseReader)
    .blockSize(16384)
    .build();

RangeReader optimizedReader = CachingRangeReader.builder(alignedReader)
    .maximumSize(1000)
    .build();
```

## Authentication

### AWS S3

The S3 reader supports various authentication methods:

```java
import software.amazon.awssdk.auth.credentials.*;

// Default credentials chain
RangeReader reader = S3RangeReader.builder()
    .uri(URI.create("s3://my-bucket/tiles.pmtiles"))
    .build();

// Profile credentials
RangeReader reader = S3RangeReader.builder()
    .uri(URI.create("s3://my-bucket/tiles.pmtiles"))
    .credentialsProvider(ProfileCredentialsProvider.builder()
            .profileName("dev")
            .build())
    .build();

// Static credentials
RangeReader reader = S3RangeReader.builder()
    .uri(URI.create("s3://my-bucket/tiles.pmtiles"))
    .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create("access-key", "secret-key")))
    .build();
```

### Azure Blob Storage

Azure support includes:

```java
import com.azure.identity.*;

// Default Azure credential
RangeReader reader = AzureBlobRangeReader.builder()
    .uri(URI.create("azure://account.blob.core.windows.net/container/tiles.pmtiles"))
    .build();

// Account key
RangeReader reader = AzureBlobRangeReader.builder()
    .accountName("myaccount")
    .accountKey("accountkey==")
    .containerName("container")
    .blobName("tiles.pmtiles")
    .build();

// Connection string
RangeReader reader = AzureBlobRangeReader.builder()
    .connectionString("DefaultEndpointsProtocol=https;AccountName=myaccount;AccountKey=key==")
    .containerName("container")
    .blobName("tiles.pmtiles")
    .build();

// Custom token credential (managed identity, service principal, etc.)
RangeReader reader = AzureBlobRangeReader.builder()
    .uri(URI.create("azure://account.blob.core.windows.net/container/tiles.pmtiles"))
    .credential(new DefaultAzureCredentialBuilder()
            .managedIdentityClientId("client-id")
            .build())
    .build();
```

## Integration Testing

The library includes integration tests that demonstrate accessing PMTiles files from different cloud storage providers. To run these tests, you need to provide the necessary credentials and test files in the cloud storage locations.

See the `CloudStorageIntegrationTest` class for examples of reading PMTiles files from S3, Azure, and HTTP sources.