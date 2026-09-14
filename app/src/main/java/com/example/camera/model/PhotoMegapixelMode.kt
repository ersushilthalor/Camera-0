package com.example.camera.model

/**
 * Megapixel capture mode for photo mode:
 * - M12: Standard 12MP Quad-Bayer pixel binned (fast snapshot)
 * - M24: 24MP AI Super Resolution (Real-ESRGAN enhanced)
 * - M50: 50MP AI Super Resolution (Real-ESRGAN enhanced)
 * - M100: 100MP AI Super Resolution (Real-ESRGAN enhanced)
 * - M200: 200MP AI Super Resolution (Real-ESRGAN enhanced)
 */
enum class PhotoMegapixelMode(
    val label: String,
    val megapixels: Int,
    val description: String
) {
    M12("12M", 12, "Standard 12MP (4-in-1 Binned)"),
    M24("24M", 24, "24MP AI Super Resolution"),
    M50("50M", 50, "50MP AI Super Resolution"),
    M100("100M", 100, "100MP AI Super Resolution"),
    M200("200M", 200, "200MP AI Super Resolution");

    val isSuperRes: Boolean
        get() = this != M12
}
