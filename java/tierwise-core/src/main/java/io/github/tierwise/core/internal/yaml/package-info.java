/**
 * Parsed source documents with positions.
 *
 * <p>{@link io.github.tierwise.core.internal.yaml.PNode} and its three kinds carry the file, the path and
 * the line of every node, so that each finding has a location (FIND-17 to FIND-23). The loader that
 * produces them (YAML 1.2 core schema, no aliases, anchors, tags or merge keys) lives in this package
 * too.
 */
package io.github.tierwise.core.internal.yaml;
