# Code Coverage

This project uses JaCoCo for code coverage analysis. Coverage reports are generated both per-module and as an aggregate across all modules.

## Per-Module Coverage Reports

Each module generates its own coverage report when tests are run:

```bash
# Generate coverage for a specific module
./mvnw clean test -pl src/tileverse-pmtiles-reader

# Generate coverage for all modules
./mvnw clean test -pl src/tileverse-pmtiles-reader,src/tileverse-vectortiles
```

Individual module reports are available at:
- PMTiles Reader: `src/tileverse-pmtiles-reader/target/site/jacoco/index.html`
- Vector Tiles: `src/tileverse-vectortiles/target/site/jacoco/index.html`

## Aggregate Coverage Report

To generate a combined coverage report across all modules:

```bash
# First run tests to generate coverage data
./mvnw clean test -pl src/tileverse-pmtiles-reader,src/tileverse-vectortiles

# Then generate the aggregate report
./mvnw verify -pl coverage-report
```

The aggregate report is available at: `coverage-report/target/site/jacoco-aggregate/index.html`

## Coverage with Integration Tests

To include integration test coverage:

```bash
# Run all tests including integration tests
./mvnw clean verify -pl src/tileverse-pmtiles-reader,src/tileverse-vectortiles

# Generate aggregate report
./mvnw verify -pl coverage-report
```

## Coverage Profile

For advanced coverage analysis, you can use the coverage profile:

```bash
# Generate merged coverage data
./mvnw clean verify -Pcoverage
```

This will merge all coverage execution files and provide additional analysis options.

## Report Formats

JaCoCo generates reports in multiple formats:
- **HTML**: Interactive browsable reports (`index.html`)
- **XML**: Machine-readable format (`jacoco.xml`)
- **CSV**: Tabular data format (`jacoco.csv`)

The XML format is particularly useful for CI/CD integration and coverage tracking tools.