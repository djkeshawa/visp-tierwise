/**
 * The {@code tierwise} command line.
 *
 * <p>{@link io.github.tierwise.cli.TierwiseCli} is the root command. Its subcommands are {@code init},
 * {@code validate}, {@code lint}, {@code test}, {@code compile} and {@code explain}; {@code codegen} is
 * filled in by its own work item. Every command is a thin layer over
 * {@link io.github.tierwise.core.Tierwise}: the CLI reports the findings of the core, it does not
 * decide any of them.
 *
 * <p>Exit codes: {@code 0} on success, {@code 1} when a validation fails (a finding with severity
 * {@code error}, a failing example, an unknown code for {@code explain}), {@code 2} on a usage error or
 * an I/O failure.
 */
package io.github.tierwise.cli;
