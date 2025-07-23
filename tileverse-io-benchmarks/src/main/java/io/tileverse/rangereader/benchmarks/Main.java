package io.tileverse.rangereader.benchmarks;

import java.io.IOException;
import org.openjdk.jmh.runner.RunnerException;

/**
 * Main entry point for running RangeReader benchmarks.
 * <p>
 * This class delegates to the BenchmarkRunner. It exists as a simple
 * entry point that can be specified in the JAR manifest.
 */
public class Main {

    /**
     * Main method for running benchmarks from command line.
     *
     * @param args command line arguments to pass to the BenchmarkRunner
     * @throws RunnerException if an error occurs during benchmark execution
     * @throws IOException if an I/O error occurs
     */
    public static void main(String[] args) throws RunnerException, IOException {
        System.out.println("Starting RangeReader Benchmarks...");
        BenchmarkRunner.main(args);
    }
}
