package io.tileverse.cli;

import picocli.CommandLine.IVersionProvider;

/**
 * Provides version information for the CLI.
 */
public class VersionProvider implements IVersionProvider {
    @Override
    public String[] getVersion() {
        // This would ideally be dynamically generated from Maven properties
        return new String[] {
            "Tileverse CLI v0.1.0",
            "Java: " + System.getProperty("java.version"),
            "OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
        };
    }
}
