package io.tileverse.rangereader.benchmarks;

import io.tileverse.rangereader.FileRangeReader;
import io.tileverse.rangereader.RangeReader;
import java.io.IOException;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.RunnerException;

/**
 * JMH benchmark for FileRangeReader with various configurations.
 * <p>
 * This benchmark measures the performance of FileRangeReader with different
 * combinations of caching and block alignment.
 */
@State(Scope.Benchmark)
public class FileRangeReaderBenchmark extends AbstractRangeReaderBenchmark {

    @Override
    protected RangeReader createBaseReader() throws IOException {
        return new FileRangeReader(testFilePath);
    }

    @Override
    protected String getSourceIndentifier() {
        return testFilePath.toString();
    }
    /**
     * Main method to run this benchmark.
     */
    public static void main(String[] args) throws RunnerException {
        runBenchmark(FileRangeReaderBenchmark.class);
    }
}
