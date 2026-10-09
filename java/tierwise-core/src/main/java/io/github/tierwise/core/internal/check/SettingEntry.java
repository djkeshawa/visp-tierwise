package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.internal.model.SettingDef;
import java.util.Optional;

/**
 * A setting that a policy declares. A setting with a fault stays declared, so that a reference to it is
 * not also reported as unknown, but it has no definition, so the checks that need its kind or its fact
 * are skipped (FIND-35).
 *
 * @param name the NFC-normalised setting name
 * @param definition the definition, when the declaration has no fault
 */
record SettingEntry(String name, Optional<SettingDef> definition) {
}
