/**
 * Whole-world content-completeness validation (issue #530).
 *
 * <p>Generalises the area-consistency pattern (issue #529): every machine-checkable item in
 * {@code docs/content-dod.md} becomes a cross-reference rule so that incomplete game content fails
 * {@code --validate-data} (and therefore CI) instead of relying on the feature matrix or reviewer
 * memory. The checker performs no mutation and no networking, so it stays unit-testable without a
 * running server (AGENTS.md §10). It is constructed only by the composition root and depends purely
 * on domain repository ports.
 */
@NullMarked
package io.taanielo.jmud.core.content;

import org.jspecify.annotations.NullMarked;
