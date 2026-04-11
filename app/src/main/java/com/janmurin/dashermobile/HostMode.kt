package com.janmurin.dashermobile

enum class HostMode(
    val isCompact: Boolean,
    val allowsTiltControls: Boolean
) {
    APP(isCompact = false, allowsTiltControls = true),
    IME(isCompact = true, allowsTiltControls = false)
}

