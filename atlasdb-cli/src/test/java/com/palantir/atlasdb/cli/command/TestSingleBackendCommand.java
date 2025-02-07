/**
 * Copyright 2015 Palantir Technologies
 *
 * Licensed under the BSD-3 License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.atlasdb.cli.command;

import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.base.Preconditions;
import com.palantir.atlasdb.AtlasDbConstants;
import com.palantir.atlasdb.cli.runner.AbstractTestRunner;
import com.palantir.atlasdb.cli.runner.RocksDbTestRunner;
import com.palantir.atlasdb.cli.services.AtlasDbServices;
import com.palantir.atlasdb.keyvalue.api.TableReference;

import io.airlift.airline.Cli;
import io.airlift.airline.Command;
import io.airlift.airline.Help;
import io.airlift.airline.Option;

public class TestSingleBackendCommand {

    private static String SIMPLE_CONFIG_FILE;
    private static String NESTED_CONFIG_FILE;

    @Command(name = "test", description = "test functionality")
    public static class TestCommand extends SingleBackendCommand {

        @Option(name = {"-f1", "--flag1"}, description = "flag 1")
        Boolean flag1;

        @Option(name = {"-f2", "--flag2"}, description = "flag 2")
        String flag2;

        @Override
        public int execute(AtlasDbServices services) {
            // test a method on each of the services
            if (flag1 != null) {
                services.getKeyValueService().getAllTableNames();
                services.getTimestampService().getFreshTimestamp();
                services.getLockSerivce().getMinLockedInVersionId("test-client");
                services.getTransactionManager().getImmutableTimestamp();
            }

            // test kvs create table
            if (flag2 != null) {
                TableReference table = TableReference.createUnsafe(flag2);
                services.getKeyValueService().createTable(table, AtlasDbConstants.GENERIC_TABLE_METADATA);
                Preconditions.checkArgument(services.getKeyValueService().getAllTableNames().contains(table),
                        "kvs contains tables %s, but not table %s", services.getKeyValueService().getAllTableNames(), table.getQualifiedName());
                services.getKeyValueService().dropTable(table);
            }
            return 0;
        }

    }

    @BeforeClass
    public static void setup() throws URISyntaxException {
        SIMPLE_CONFIG_FILE = AbstractTestRunner.getResourcePath(RocksDbTestRunner.SIMPLE_ROCKSDB_CONFIG_FILENAME);
        NESTED_CONFIG_FILE = Paths.get(TestSingleBackendCommand.class.getClassLoader().getResource("nested_rocksdb_config.yml").toURI()).toString();
    }

    @Test
    public void testFailure() {
        assertFailure(runTest(new String[] { "test", "--noopt" }));
    }

    @Test
    public void testRunHelp() {
        assertSuccessful(runTest(new String[] { "help" }));
        assertSuccessful(runTest(new String[] { "help", "test" }));
    }

    @Test
    public void testRun() {
        assertSuccessful(runTest(new String[] { "test", "--config", SIMPLE_CONFIG_FILE}));
    }

    @Test
    public void testFlag1Run() {
        assertSuccessful(runTest(new String[] { "test", "--config", SIMPLE_CONFIG_FILE, "--flag1"}));
    }

    @Test
    public void testFlag2Run() {
        assertSuccessful(runTest(new String[] { "test", "-c", SIMPLE_CONFIG_FILE, "--flag2", "test.new_table"}));
    }

    @Test
    public void testRunNestedConfig() {
        assertSuccessful(runTest(new String[] { "test", "-c", NESTED_CONFIG_FILE, "-f1", "--config-root", "atlasdb", "-f2", "test.new_table"}));
    }

    @Test
    public void testMustSpecifyConfig() {
        assertFailure(runTest(new String[] { "test" }));
    }

    private int runTest(String[] args) {
        Cli.CliBuilder<Callable> builder = Cli.<Callable>builder("test-cli")
                .withDescription("test the cli framework")
                .withDefaultCommand(Help.class)
                .withCommands(Help.class, TestCommand.class);
        Cli<Callable> parser = builder.build();
        try {
            parser.parse(args).call();
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
    }

    private void assertSuccessful(int returnVal) {
        Preconditions.checkArgument(returnVal == 0, "CLI exited with non-zero exit code.");
    }

    private void assertFailure(int returnVal) {
        Preconditions.checkArgument(returnVal == 1, "CLI exited with exit code zero.");
    }

}
