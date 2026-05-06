package com.janmurin.dashermobile

/**
 * Describes the visual and behavioural mode in which the Dasher UI is hosted.
 *
 * @property isCompact        `true` when running inside the IME panel where vertical space is
 *                            limited; controls text sizes and layout compactness.
 * @property allowsTiltControls `true` when tilt-mode controls (toggle switch, calibrate button)
 *                            should be shown.
 */
enum class HostMode(
    val isCompact: Boolean,
    val allowsTiltControls: Boolean
) {
    /** Full-screen standalone activity ([MainActivity]). */
    APP(isCompact = false, allowsTiltControls = true),
    /** Compact keyboard panel ([DasherImeService]). */
    IME(isCompact = true, allowsTiltControls = true)
}
