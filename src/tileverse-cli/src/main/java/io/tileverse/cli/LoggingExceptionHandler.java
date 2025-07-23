/*
 * (c) Copyright 2025 Multiversio LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
