/**
 * Codegen: typed handles for the keys and features of a project, written as Java or TypeScript.
 *
 * <p>{@link io.github.tierwise.cli.codegen.Codegen} compiles a project in memory with
 * {@code Tierwise.compile}, reads the keys and features from the bundle bytes, and writes the code. The
 * generated code lives in the user's project, so the declared names appear in it only as string literals
 * and in identifiers that the naming rules derive from them.
 *
 * <p>Naming. A key or feature constant is the declared name in upper case, with a separator in front of
 * each upper-case letter that follows a lower-case letter or digit, and a separator for each other
 * character that is not an ASCII letter or digit. An effect constant is named the same way. A feature
 * with an enum form gets the enum {@code <Segments>Effect}, where {@code <Segments>} joins the ASCII letter
 * and digit runs of the feature name with an upper-case first letter each. Two declared names that map to
 * one identifier are an error, and so is a name with no ASCII letter or digit.
 *
 * <p>Java output: the handle interfaces {@code Key} and {@code Feature}, the constant classes
 * {@code Keys} and {@code Features}, and the typed facade {@code TypedEvaluator}. TypeScript output: the
 * module {@code tierwise.ts}, with name unions, value and effect maps, and a typed wrapper.
 */
package io.github.tierwise.cli.codegen;
