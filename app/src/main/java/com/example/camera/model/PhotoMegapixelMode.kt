package com.example.camera.model

/**
 * Capture resolution mode:
 * - M12: Standard 12MP (Instant snapshot)
 * - M50: 50MP Computational Multi-Frame (Accurate 4-frame alignment, fusion & ghost rejection)
 */
enum class PhotoMegapixelMode(
    val label: String,
    val megapixels: Int,
    val description: String
) {
    M12("12M", 12, "Standard 12MP (Instant Capture)"),
    M50("50M", 50, "50MP Computational Multi-Frame (4-Frame Fusion)");

    val is50M: Boolean
        get() = this == M50

    val isSuperRes: Boolean
        get() = this == M50
}

