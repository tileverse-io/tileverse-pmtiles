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
