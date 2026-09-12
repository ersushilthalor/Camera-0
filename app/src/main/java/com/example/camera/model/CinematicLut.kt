package com.example.camera.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * Cinematic 3D/1D Look-Up Table (LUT) Presets for Log video monitoring & grading.
 * Designed to preview filmic contrast & color separation on top of Log transfer curves
 * without modifying the pristine recorded Log master stream.
 */
enum class CinematicLut(
    val id: String,
    val label: String,
    val description: String,
    val category: String,
    val accentColor: Color,
    val contrast: Float = 1.0f,
    val saturation: Float = 1.0f,
    val warmCoolOffset: Float = 0.0f,
    private val matrixValues: FloatArray? = null
) {
    NONE(
        id = "clean_log",
        label = "None (Clean Log)",
        description = "Unmodified wide-latitude Log sensor curve for pure uncompressed grading",
        category = "Master",
        accentColor = Color(0xFFFFD54F),
        contrast = 1.0f,
        saturation = 1.0f,
        warmCoolOffset = 0.0f
    ),

    FILMIC_GOLD(
        id = "filmic_gold",
        label = "Filmic Gold",
        description = "Kodak 2383 print stock warm highlight rolloff, golden skin tones & deep rich shadows",
        category = "Cinema Film",
        accentColor = Color(0xFFF6C343),
        contrast = 1.22f,
        saturation = 1.15f,
        warmCoolOffset = 0.18f,
        matrixValues = floatArrayOf(
            1.18f, 0.02f, -0.05f, 0f, 15f,
            0.02f, 1.06f, -0.02f, 0f, 6f,
            -0.08f, 0.01f, 0.88f, 0f, -12f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    TEAL_ORANGE(
        id = "teal_orange",
        label = "Teal & Orange",
        description = "Modern Hollywood blockbuster color separation: amber skin tones against cyan shadows",
        category = "Hollywood",
        accentColor = Color(0xFF00E5FF),
        contrast = 1.28f,
        saturation = 1.25f,
        warmCoolOffset = 0.08f,
        matrixValues = floatArrayOf(
            1.22f, -0.06f, -0.08f, 0f, 18f,
            -0.04f, 1.08f, 0.06f, 0f, 2f,
            -0.12f, 0.08f, 1.25f, 0f, 14f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    MUTED_CINE(
        id = "muted_cine",
        label = "Muted Arthouse",
        description = "Subtle desaturation with lifted charcoal shadows & soft, organic highlight roll",
        category = "Modern Cine",
        accentColor = Color(0xFFB0BEC5),
        contrast = 1.08f,
        saturation = 0.78f,
        warmCoolOffset = -0.05f,
        matrixValues = floatArrayOf(
            0.92f, 0.04f, 0.04f, 0f, 12f,
            0.04f, 0.94f, 0.04f, 0f, 12f,
            0.04f, 0.04f, 0.96f, 0f, 16f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    HIGH_CONTRAST(
        id = "high_contrast",
        label = "High-Contrast Noir",
        description = "Punchy dynamic range with crushed ink blacks, crisp speculars & sharp definition",
        category = "Dramatic",
        accentColor = Color(0xFFECEFF1),
        contrast = 1.45f,
        saturation = 1.10f,
        warmCoolOffset = 0.0f,
        matrixValues = floatArrayOf(
            1.35f, -0.10f, -0.10f, 0f, -18f,
            -0.10f, 1.35f, -0.10f, 0f, -18f,
            -0.10f, -0.10f, 1.35f, 0f, -18f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    VINTAGE_CHROME(
        id = "vintage_chrome",
        label = "Vintage Chrome",
        description = "1970s reversal slide film with warm faded blacks, yellowed mids & rich crimson",
        category = "Vintage",
        accentColor = Color(0xFFFF8A65),
        contrast = 1.18f,
        saturation = 1.12f,
        warmCoolOffset = 0.22f,
        matrixValues = floatArrayOf(
            1.15f, 0.08f, -0.06f, 0f, 16f,
            0.02f, 1.05f, -0.04f, 0f, 8f,
            -0.06f, -0.02f, 0.82f, 0f, 4f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    MOODY_NOIR(
        id = "moody_noir",
        label = "Moody Atmosphere",
        description = "Atmospheric thriller tone with emerald-tinted shadows, cold highlights & brooding depth",
        category = "Atmospheric",
        accentColor = Color(0xFF26A69A),
        contrast = 1.25f,
        saturation = 0.85f,
        warmCoolOffset = -0.15f,
        matrixValues = floatArrayOf(
            0.88f, 0.02f, -0.02f, 0f, -8f,
            -0.02f, 1.12f, 0.04f, 0f, 8f,
            0.02f, 0.06f, 1.18f, 0f, 16f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    WARM_SUNSET(
        id = "warm_sunset",
        label = "Warm Golden Sunset",
        description = "Luminous amber golden hour lighting, radiant skin warmth & gentle compressed contrast",
        category = "Warm",
        accentColor = Color(0xFFFFB300),
        contrast = 1.14f,
        saturation = 1.20f,
        warmCoolOffset = 0.28f,
        matrixValues = floatArrayOf(
            1.24f, 0.04f, -0.10f, 0f, 22f,
            0.02f, 1.10f, -0.06f, 0f, 10f,
            -0.12f, -0.04f, 0.76f, 0f, -14f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    COOL_STEEL(
        id = "cool_steel",
        label = "Cool Steel & Ice",
        description = "Nordic sci-fi cold steel aesthetic with cobalt shadows and clean neutral skin tones",
        category = "Cool",
        accentColor = Color(0xFF64B5F6),
        contrast = 1.20f,
        saturation = 0.95f,
        warmCoolOffset = -0.24f,
        matrixValues = floatArrayOf(
            0.86f, -0.02f, 0.02f, 0f, -10f,
            -0.02f, 0.96f, 0.06f, 0f, -2f,
            0.04f, 0.08f, 1.32f, 0f, 24f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    BLEACH_BYPASS(
        id = "bleach_bypass",
        label = "Bleach Bypass",
        description = "Silver retention chemical process with desaturated hues, harsh grain feel & striking grit",
        category = "Dramatic",
        accentColor = Color(0xFFCFD8DC),
        contrast = 1.38f,
        saturation = 0.50f,
        warmCoolOffset = -0.04f,
        matrixValues = floatArrayOf(
            1.20f, 0.12f, 0.12f, 0f, -8f,
            0.12f, 1.20f, 0.12f, 0f, -8f,
            0.12f, 0.12f, 1.20f, 0f, -8f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    EKTACHROME(
        id = "ektachrome",
        label = "Ektachrome 100",
        description = "Vibrant saturated primaries, ultra-clean cyan skies and dynamic reversal clarity",
        category = "Cinema Film",
        accentColor = Color(0xFF29B6F6),
        contrast = 1.24f,
        saturation = 1.32f,
        warmCoolOffset = -0.08f,
        matrixValues = floatArrayOf(
            1.12f, -0.04f, -0.02f, 0f, 6f,
            -0.02f, 1.18f, -0.04f, 0f, 8f,
            -0.04f, 0.02f, 1.28f, 0f, 16f,
            0f, 0f, 0f, 1f, 0f
        )
    ),

    MONO_CINE(
        id = "mono_cine",
        label = "Cine Monochrome",
        description = "Panatomic silver emulsion black & white grading with smooth midtone tonality",
        category = "Monochrome",
        accentColor = Color(0xFFFAFAFA),
        contrast = 1.30f,
        saturation = 0.0f,
        warmCoolOffset = 0.0f,
        matrixValues = floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    );

    /**
     * Generates a Compose [ColorFilter] for real-time live viewfinder monitoring.
     */
    fun toColorFilter(): ColorFilter? {
        val vals = matrixValues ?: return null
        return ColorFilter.colorMatrix(ColorMatrix(vals))
    }

    /**
     * Generates an Android [android.graphics.ColorMatrix] for image post-processing.
     */
    fun toAndroidColorMatrix(): android.graphics.ColorMatrix? {
        val vals = matrixValues ?: return null
        return android.graphics.ColorMatrix(vals)
    }
}
