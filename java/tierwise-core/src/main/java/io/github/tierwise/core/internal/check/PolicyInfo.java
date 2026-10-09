package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.internal.model.PolicyDef;
import java.util.Map;
import java.util.Optional;

/**
 * A policy that a rules file declares. A policy with a fault stays declared, so that a binding that
 * names it is not reported as unknown.
 *
 * @param name the policy name
 * @param settings the settings of the policy, when the {@code settings} entry could be read
 * @param definition the policy, when it has no fault
 */
record PolicyInfo(String name, Optional<Map<String, SettingEntry>> settings, Optional<PolicyDef> definition) {
}
