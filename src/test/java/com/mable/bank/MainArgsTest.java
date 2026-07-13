package com.mable.bank;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MainArgsTest {

    @Test
    void defaultsOutputDirToTheCurrentDirectoryWhenOmitted() {
        Main.Args args = Main.parseArgs(new String[] {"balances.csv", "transactions.csv"});

        assertThat(args.balancesPath()).isEqualTo(Path.of("balances.csv"));
        assertThat(args.transactionsPath()).isEqualTo(Path.of("transactions.csv"));
        assertThat(args.outputDir()).isEqualTo(Path.of("."));
    }

    @Test
    void usesTheThirdArgumentAsTheOutputDirWhenGiven() {
        Main.Args args = Main.parseArgs(new String[] {"balances.csv", "transactions.csv", "out"});

        assertThat(args.outputDir()).isEqualTo(Path.of("out"));
    }

    @Test
    void rejectsTooFewArguments() {
        assertThatThrownBy(() -> Main.parseArgs(new String[] {"balances.csv"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Usage:");
    }

    @Test
    void rejectsTooManyArguments() {
        assertThatThrownBy(() -> Main.parseArgs(new String[] {"a", "b", "c", "d"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Usage:");
    }
}
