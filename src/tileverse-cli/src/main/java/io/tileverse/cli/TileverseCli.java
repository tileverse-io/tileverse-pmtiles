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

import java.io.PrintWriter;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Ansi;

/**
 * Main CLI entry point for Tileverse.
 */
@Command(
        name = "tileverse",
        description = "Tileverse: A Java tool for creating PMTiles from GeoJSON and other spatial data formats",
        versionProvider = VersionProvider.class,
        mixinStandardHelpOptions = true,
        subcommands = {GenerateCommand.class, TileJoinCommand.class, TileviewCommand.class, OverzoomCommand.class})
public class TileverseCli implements Callable<Integer> {

    @Override
    public Integer call() {
        // By default, show help when no subcommand is specified
        CommandLine cmd = new CommandLine(this);
        cmd.usage(System.out);
        return 0;
    }

    /**
     * The main method.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // Custom error output with ANSI colors
        PrintWriter errWriter = new PrintWriter(System.err, true);

        int exitCode = new CommandLine(new TileverseCli())
                .setColorScheme(CommandLine.Help.defaultColorScheme(Ansi.AUTO))
                .setErr(errWriter)
                .setExecutionExceptionHandler(new LoggingExceptionHandler())
                .execute(args);

        System.exit(exitCode);
    }
}
