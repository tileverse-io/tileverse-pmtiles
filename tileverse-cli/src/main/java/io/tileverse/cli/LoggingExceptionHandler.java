package io.tileverse.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.ParseResult;

/**
 * Exception handler that logs exceptions and shows user-friendly error messages.
 */
public class LoggingExceptionHandler implements IExecutionExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(LoggingExceptionHandler.class);

    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine, ParseResult parseResult) {
        // Log the full exception for debugging
        logger.error("Command execution failed", ex);

        // Show user-friendly message
        CommandLine.Help.ColorScheme colorScheme = commandLine.getColorScheme();
        commandLine.getErr().println(colorScheme.errorText("Error: " + extractUserFriendlyMessage(ex)));

        return CommandLine.ExitCode.SOFTWARE;
    }

    /**
     * Extracts a user-friendly message from the exception.
     */
    private String extractUserFriendlyMessage(Throwable ex) {
        if (ex instanceof ExecutionException && ex.getCause() != null) {
            return extractUserFriendlyMessage(ex.getCause());
        }

        // Add specific exception handling here as needed

        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
