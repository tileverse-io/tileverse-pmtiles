# Contributing to Tileverse PMTiles

We welcome contributions to the Tileverse PMTiles project! This document outlines how to contribute effectively.

## Getting Started

### Prerequisites

- **Java 21+** for development (Java 17+ for runtime)
- **Maven 3.9.0+** (use the included Maven wrapper `./mvnw`)
- **Git** for version control

### Development Setup

1. Fork the repository on GitHub
2. Clone your fork locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/tileverse-pmtiles.git
   cd tileverse-pmtiles
   ```

3. Set up the development environment:
   ```bash
   make dev-setup  # or ./mvnw clean compile
   ```

4. Run tests to ensure everything works:
   ```bash
   make test      # or ./mvnw verify
   ```

## Development Workflow

### Building and Testing

Use the provided Makefile for common development tasks:

```bash
# Format code and check style
make format
make lint

# Run tests
make test           # All tests
make test-unit      # Unit tests only  
make test-it        # Integration tests only

# Full verification
make verify         # Lint + test

# Build artifacts
make compile        # Compile only
make package        # Build JARs
make install        # Install to local repo
```

### Code Style

- **Formatting**: Use Spotless for code formatting (`./mvnw spotless:apply`)
- **POM files**: Use SortPOM for consistent XML structure (`./mvnw sortpom:sort`)
- **License headers**: All Java files must include the project license header
- **Documentation**: Add JavaDoc for public APIs
- **Tests**: Write comprehensive tests for new functionality

### Module Structure

The project uses a multi-module Maven structure:

- **`dependencies/`**: BOM managing third-party dependency versions
- **`bom/`**: BOM managing Range Reader module versions
- **`src/core`**: Core interfaces and base implementations
- **`src/s3`**: AWS S3 range reader implementation
- **`src/azure`**: Azure Blob Storage implementation
- **`src/gcs`**: Google Cloud Storage implementation
- **`src/tileverse-vectortiles`**: Vector tiles (MVT) library
- **`benchmarks`**: JMH performance benchmarks

## Contributing Guidelines

### Submitting Issues

Before creating an issue:

1. **Search existing issues** to avoid duplicates
2. **Use the issue templates** when available
3. **Provide clear reproduction steps** for bugs
4. **Include relevant system information** (Java version, OS, etc.)

### Submitting Pull Requests

1. **Create a feature branch** from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes** following the code style guidelines

3. **Write or update tests** for your changes

4. **Update documentation** if needed (README, JavaDoc, etc.)

5. **Ensure all tests pass**:
   ```bash
   make verify
   ```

6. **Commit your changes** with descriptive commit messages:
   ```bash
   git commit -m "Add support for custom authentication methods
   
   - Implement CustomAuth interface
   - Add tests for authentication scenarios
   - Update documentation with usage examples"
   ```

7. **Push to your fork** and create a pull request

### Pull Request Requirements

- **Clear description**: Explain what your PR does and why
- **Tests**: Include tests that verify your changes work correctly
- **Documentation**: Update relevant documentation
- **No breaking changes** unless discussed in an issue first
- **Code quality**: All checks must pass (formatting, tests, etc.)

### Commit Message Format

Use clear, descriptive commit messages:

```
Short summary (50 chars or less)

More detailed explanatory text if needed. Wrap lines at 72 characters.
Explain what and why, not how.

- Use bullet points for multiple changes
- Reference issues: "Fixes #123" or "Related to #456"
```

## Code Review Process

1. **Automated checks** must pass (CI pipeline)
2. **Code review** by project maintainers
3. **Address feedback** promptly and professionally  
4. **Squash commits** if requested before merging

## Testing

### Test Categories

- **Unit tests** (`*Test.java`): Fast, isolated component tests
- **Integration tests** (`*IT.java`): End-to-end tests with TestContainers
- **Performance tests**: JMH benchmarks for performance-critical code

### TestContainers Usage

Integration tests use TestContainers for:
- **LocalStack**: S3-compatible testing
- **Azurite**: Azure Blob Storage emulation
- **MinIO**: S3-compatible storage testing

### Writing Tests

- Use **descriptive test names**: `testS3RangeReaderWithCustomCredentials()`
- **Test edge cases** and error conditions
- **Use AssertJ** for fluent assertions
- **Mock external dependencies** in unit tests
- **Use TestContainers** for integration tests

## Performance Considerations

- **Range Reader**: Optimize for minimal network requests
- **Vector Tiles**: Consider memory usage for large tiles
- **Caching**: Implement appropriate caching strategies
- **Thread Safety**: All public APIs must be thread-safe

## Documentation

- **JavaDoc**: Document all public APIs
- **README**: Keep module READMEs up to date
- **Examples**: Include practical usage examples
- **Architecture**: Document design decisions and patterns

## License

By contributing, you agree that your contributions will be licensed under the Apache License, Version 2.0.

## Getting Help

- **GitHub Issues**: For bugs and feature requests
- **GitHub Discussions**: For questions and general discussion
- **Code Review**: Ask questions in PR comments

## Recognition

Contributors are recognized in:
- Git commit history
- Release notes for significant contributions
- GitHub contributors list

Thank you for contributing to Tileverse PMTiles!

