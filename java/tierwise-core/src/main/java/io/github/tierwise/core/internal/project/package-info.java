/**
 * The project loader: manifest, presets, source mapping and namespaces (specification section 1).
 *
 * <p>{@link io.github.tierwise.core.internal.project.ProjectLoader} reads {@code tierwise.yaml}, resolves
 * and composes presets, maps the source files to roles, scopes and namespaces, and returns an immutable
 * {@link io.github.tierwise.core.internal.project.SourceSet} together with every MANIFEST, SRC and NS
 * finding. It knows no domain names and checks no declaration that another section owns.
 */
package io.github.tierwise.core.internal.project;
