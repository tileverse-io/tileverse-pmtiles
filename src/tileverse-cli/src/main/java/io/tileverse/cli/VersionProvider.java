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
