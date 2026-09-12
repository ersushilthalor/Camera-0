package com.example.camera.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import org.json.JSONArray
import org.json.JSONObject

enum class UiTemplateType(
    val title: String,
    val subtitle: String,
    val accentHex: String
) {
    IPHONE("iPhone Style", "Clean, minimal Apple-inspired layout with yellow accents & top pill indicators", "#FFD54F"),
    SAMSUNG("Samsung Style", "One UI layout with bold toggles, capsule mode selector & solid shutter", "#FFFFFF"),
    VIVO("Vivo Style", "OriginOS/Funtouch camera layout with circular icons, gimbal cues & vivid shutter ring", "#FF7043"),
    CUSTOM("Custom UI", "Completely personalized layout, spacing, typography & controls", "#64B5F6")
}

enum class ShutterStyle(val label: String, val description: String) {
    CLASSIC_WHITE("Classic Ring", "Double ring with solid white center"),
    APPLE_DOT("Apple Minimal", "Thin silver ring with crisp inner circle"),
    SAMSUNG_CAPSULE("Samsung OneUI", "Thick outer border with solid round core"),
    VIVO_GIMBAL("Vivo Origin", "Accent colored outer ring with responsive gimbal core"),
    MINIMAL_ACCENT("Minimalist Accent", "Flat borderless tactile trigger")
}

enum class ModeSelectorPosition(val label: String) {
    ABOVE_SHUTTER("Above Shutter Button"),
    BELOW_SHUTTER("Below Shutter Button")
}

enum class ModeSelectorStyle(val label: String) {
    CLASSIC_DOT("Yellow Indicator Dot"),
    CAPSULE_PILL("OneUI Capsule Pill"),
    UNDERLINE("Vivo Accent Underline"),
    MINIMAL_TEXT("Minimal Typography")
}

enum class FontFamilyOption(val label: String) {
    DEFAULT("Default Sans"),
    MONOSPACE("Monospace / Pro"),
    SERIF("Editorial Serif"),
    ROUNDED("Modern Rounded");

    fun toComposeFontFamily(): FontFamily {
        return when (this) {
            DEFAULT -> FontFamily.SansSerif
            MONOSPACE -> FontFamily.Monospace
            SERIF -> FontFamily.Serif
            ROUNDED -> FontFamily.Default
        }
    }
}

enum class TopBarAlignment(val label: String) {
    SPACE_BETWEEN("Space Between"),
    CENTER("Centered"),
    COMPACT_LEFT("Left Grouped"),
    COMPACT_RIGHT("Right Grouped")
}

enum class TopControlItem(val id: String, val label: String) {
    FLASH("flash", "Flash / Torch"),
    TIMER("timer", "Timer Countdown"),
    GRID("grid", "Grid & Horizon Level"),
    RESOLUTION("resolution", "Photo/Video Resolution"),
    RAW("raw", "RAW Sensor Capture"),
    PRO_EXP("pro_exp", "Pro Manual Mode"),
    SETTINGS("settings", "Settings Gear")
}

data class ModeLayoutConfig(
    val visibleModes: List<CameraMode> = listOf(
        CameraMode.PHOTO,
        CameraMode.PORTRAIT,
        CameraMode.VIDEO,
        CameraMode.MORE
    ),
    val modeSelectorPosition: ModeSelectorPosition = ModeSelectorPosition.BELOW_SHUTTER,
    val modeSelectorStyle: ModeSelectorStyle = ModeSelectorStyle.CLASSIC_DOT,
    val modeTextSizeSp: Float = 13.5f,
    val modeFontFamily: FontFamilyOption = FontFamilyOption.DEFAULT,
    val shutterStyle: ShutterStyle = ShutterStyle.CLASSIC_WHITE,
    val shutterSizeDp: Int = 80,
    val shutterHorizontalOffsetDp: Int = 0,
    val flipButtonSizeDp: Int = 54,
    val galleryThumbSizeDp: Int = 54,
    val showGalleryButton: Boolean = true,
    val showFlipButton: Boolean = true,
    val topControlsOrder: List<TopControlItem> = listOf(
        TopControlItem.FLASH,
        TopControlItem.TIMER,
        TopControlItem.RESOLUTION,
        TopControlItem.SETTINGS
    ),
    val hiddenTopControls: Set<TopControlItem> = emptySet(),
    val topControlsIconSizeDp: Int = 24,
    val topControlsSpacingDp: Int = 16,
    val topBarAlignment: TopBarAlignment = TopBarAlignment.SPACE_BETWEEN,
    val topPaddingDp: Int = 12,
    val bottomPaddingDp: Int = 14,
    val showZoomCapsule: Boolean = true,
    val zoomCapsuleScale: Float = 1.0f,
    val zoomCapsuleVerticalOffsetDp: Int = 0,
    val accentColorHex: String = "#FFD54F"
) {
    fun getComposeAccentColor(): Color {
        return try {
            Color(android.graphics.Color.parseColor(accentColorHex))
        } catch (e: Exception) {
            Color(0xFFFFD54F)
        }
    }

    fun toJson(): JSONObject {
        val json = JSONObject()
        val modesArray = JSONArray()
        visibleModes.forEach { modesArray.put(it.name) }
        json.put("visibleModes", modesArray)
        json.put("modeSelectorPosition", modeSelectorPosition.name)
        json.put("modeSelectorStyle", modeSelectorStyle.name)
        json.put("modeTextSizeSp", modeTextSizeSp.toDouble())
        json.put("modeFontFamily", modeFontFamily.name)
        json.put("shutterStyle", shutterStyle.name)
        json.put("shutterSizeDp", shutterSizeDp)
        json.put("shutterHorizontalOffsetDp", shutterHorizontalOffsetDp)
        json.put("flipButtonSizeDp", flipButtonSizeDp)
        json.put("galleryThumbSizeDp", galleryThumbSizeDp)
        json.put("showGalleryButton", showGalleryButton)
        json.put("showFlipButton", showFlipButton)

        val topOrderArray = JSONArray()
        topControlsOrder.forEach { topOrderArray.put(it.name) }
        json.put("topControlsOrder", topOrderArray)

        val hiddenTopArray = JSONArray()
        hiddenTopControls.forEach { hiddenTopArray.put(it.name) }
        json.put("hiddenTopControls", hiddenTopArray)

        json.put("topControlsIconSizeDp", topControlsIconSizeDp)
        json.put("topControlsSpacingDp", topControlsSpacingDp)
        json.put("topBarAlignment", topBarAlignment.name)
        json.put("topPaddingDp", topPaddingDp)
        json.put("bottomPaddingDp", bottomPaddingDp)
        json.put("showZoomCapsule", showZoomCapsule)
        json.put("zoomCapsuleScale", zoomCapsuleScale.toDouble())
        json.put("zoomCapsuleVerticalOffsetDp", zoomCapsuleVerticalOffsetDp)
        json.put("accentColorHex", accentColorHex)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): ModeLayoutConfig {
            val visibleModesList = mutableListOf<CameraMode>()
            val modesArray = json.optJSONArray("visibleModes")
            if (modesArray != null) {
                for (i in 0 until modesArray.length()) {
                    val mName = modesArray.optString(i)
                    try { visibleModesList.add(CameraMode.valueOf(mName)) } catch (ignored: Exception) {}
                }
            }
            if (visibleModesList.isEmpty()) {
                visibleModesList.addAll(listOf(
                    CameraMode.PHOTO, CameraMode.PORTRAIT, CameraMode.VIDEO, CameraMode.MORE
                ))
            }

            val topOrderList = mutableListOf<TopControlItem>()
            val topOrderArray = json.optJSONArray("topControlsOrder")
            if (topOrderArray != null) {
                for (i in 0 until topOrderArray.length()) {
                    val item = topOrderArray.optString(i)
                    try { topOrderList.add(TopControlItem.valueOf(item)) } catch (ignored: Exception) {}
                }
            }
            if (topOrderList.isEmpty()) {
                topOrderList.addAll(listOf(
                    TopControlItem.FLASH, TopControlItem.TIMER,
                    TopControlItem.RESOLUTION, TopControlItem.SETTINGS
                ))
            }

            val hiddenTopSet = mutableSetOf<TopControlItem>()
            val hiddenTopArray = json.optJSONArray("hiddenTopControls")
            if (hiddenTopArray != null) {
                for (i in 0 until hiddenTopArray.length()) {
                    val item = hiddenTopArray.optString(i)
                    try { hiddenTopSet.add(TopControlItem.valueOf(item)) } catch (ignored: Exception) {}
                }
            }

            return ModeLayoutConfig(
                visibleModes = visibleModesList,
                modeSelectorPosition = try {
                    ModeSelectorPosition.valueOf(json.optString("modeSelectorPosition", ModeSelectorPosition.BELOW_SHUTTER.name))
                } catch (e: Exception) { ModeSelectorPosition.BELOW_SHUTTER },
                modeSelectorStyle = try {
                    ModeSelectorStyle.valueOf(json.optString("modeSelectorStyle", ModeSelectorStyle.CLASSIC_DOT.name))
                } catch (e: Exception) { ModeSelectorStyle.CLASSIC_DOT },
                modeTextSizeSp = json.optDouble("modeTextSizeSp", 13.5).toFloat(),
                modeFontFamily = try {
                    FontFamilyOption.valueOf(json.optString("modeFontFamily", FontFamilyOption.DEFAULT.name))
                } catch (e: Exception) { FontFamilyOption.DEFAULT },
                shutterStyle = try {
                    ShutterStyle.valueOf(json.optString("shutterStyle", ShutterStyle.CLASSIC_WHITE.name))
                } catch (e: Exception) { ShutterStyle.CLASSIC_WHITE },
                shutterSizeDp = json.optInt("shutterSizeDp", 80),
                shutterHorizontalOffsetDp = json.optInt("shutterHorizontalOffsetDp", 0),
                flipButtonSizeDp = json.optInt("flipButtonSizeDp", 54),
                galleryThumbSizeDp = json.optInt("galleryThumbSizeDp", 54),
                showGalleryButton = json.optBoolean("showGalleryButton", true),
                showFlipButton = json.optBoolean("showFlipButton", true),
                topControlsOrder = topOrderList,
                hiddenTopControls = hiddenTopSet,
                topControlsIconSizeDp = json.optInt("topControlsIconSizeDp", 24),
                topControlsSpacingDp = json.optInt("topControlsSpacingDp", 16),
                topBarAlignment = try {
                    TopBarAlignment.valueOf(json.optString("topBarAlignment", TopBarAlignment.SPACE_BETWEEN.name))
                } catch (e: Exception) { TopBarAlignment.SPACE_BETWEEN },
                topPaddingDp = json.optInt("topPaddingDp", 12),
                bottomPaddingDp = json.optInt("bottomPaddingDp", 14),
                showZoomCapsule = json.optBoolean("showZoomCapsule", true),
                zoomCapsuleScale = json.optDouble("zoomCapsuleScale", 1.0).toFloat(),
                zoomCapsuleVerticalOffsetDp = json.optInt("zoomCapsuleVerticalOffsetDp", 0),
                accentColorHex = json.optString("accentColorHex", "#FFD54F")
            )
        }
    }
}

data class UiCustomizationState(
    val selectedTemplate: UiTemplateType = UiTemplateType.IPHONE,
    val globalConfig: ModeLayoutConfig = CameraUiTemplates.getTemplateConfig(UiTemplateType.IPHONE),
    val modeSpecificConfigs: Map<CameraMode, ModeLayoutConfig> = emptyMap(),
    val customPresets: List<CustomUiPreset> = emptyList()
) {
    /**
     * Resolves layout config for a given camera mode (mode-specific override if present, else global config).
     */
    fun getConfigForMode(mode: CameraMode): ModeLayoutConfig {
        return modeSpecificConfigs[mode] ?: globalConfig
    }

    fun toJson(): String {
        val root = JSONObject()
        root.put("selectedTemplate", selectedTemplate.name)
        root.put("globalConfig", globalConfig.toJson())

        val modeObj = JSONObject()
        modeSpecificConfigs.forEach { (mode, config) ->
            modeObj.put(mode.name, config.toJson())
        }
        root.put("modeSpecificConfigs", modeObj)

        val presetsArr = JSONArray()
        customPresets.forEach { preset ->
            val pObj = JSONObject()
            pObj.put("id", preset.id)
            pObj.put("name", preset.name)
            pObj.put("templateType", preset.templateType.name)
            pObj.put("config", preset.config.toJson())
            presetsArr.put(pObj)
        }
        root.put("customPresets", presetsArr)

        return root.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): UiCustomizationState {
            if (jsonStr.isBlank()) return UiCustomizationState()
            return try {
                val root = JSONObject(jsonStr)
                val template = try {
                    UiTemplateType.valueOf(root.optString("selectedTemplate", UiTemplateType.IPHONE.name))
                } catch (e: Exception) { UiTemplateType.IPHONE }

                val gConfig = root.optJSONObject("globalConfig")?.let { ModeLayoutConfig.fromJson(it) }
                    ?: CameraUiTemplates.getTemplateConfig(template)

                val modeMap = mutableMapOf<CameraMode, ModeLayoutConfig>()
                val modeObj = root.optJSONObject("modeSpecificConfigs")
                if (modeObj != null) {
                    val keys = modeObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        try {
                            val mode = CameraMode.valueOf(k)
                            val cfgJson = modeObj.getJSONObject(k)
                            modeMap[mode] = ModeLayoutConfig.fromJson(cfgJson)
                        } catch (ignored: Exception) {}
                    }
                }

                val presetsList = mutableListOf<CustomUiPreset>()
                val pArr = root.optJSONArray("customPresets")
                if (pArr != null) {
                    for (i in 0 until pArr.length()) {
                        val pObj = pArr.getJSONObject(i)
                        val id = pObj.optString("id", System.currentTimeMillis().toString())
                        val name = pObj.optString("name", "Custom Preset")
                        val tType = try {
                            UiTemplateType.valueOf(pObj.optString("templateType", UiTemplateType.CUSTOM.name))
                        } catch (e: Exception) { UiTemplateType.CUSTOM }
                        val cfg = pObj.optJSONObject("config")?.let { ModeLayoutConfig.fromJson(it) }
                            ?: ModeLayoutConfig()
                        presetsList.add(CustomUiPreset(id, name, tType, cfg))
                    }
                }

                UiCustomizationState(
                    selectedTemplate = template,
                    globalConfig = gConfig,
                    modeSpecificConfigs = modeMap,
                    customPresets = presetsList
                )
            } catch (e: Exception) {
                UiCustomizationState()
            }
        }
    }
}

data class CustomUiPreset(
    val id: String,
    val name: String,
    val templateType: UiTemplateType,
    val config: ModeLayoutConfig
)

object CameraUiTemplates {
    fun getTemplateConfig(type: UiTemplateType): ModeLayoutConfig {
        return when (type) {
            UiTemplateType.IPHONE -> ModeLayoutConfig(
                modeSelectorPosition = ModeSelectorPosition.ABOVE_SHUTTER,
                modeSelectorStyle = ModeSelectorStyle.CLASSIC_DOT,
                shutterStyle = ShutterStyle.APPLE_DOT,
                shutterSizeDp = 78,
                shutterHorizontalOffsetDp = 0,
                flipButtonSizeDp = 52,
                galleryThumbSizeDp = 52,
                topControlsIconSizeDp = 22,
                topControlsSpacingDp = 14,
                topBarAlignment = TopBarAlignment.SPACE_BETWEEN,
                accentColorHex = "#FFD54F",
                modeTextSizeSp = 13.0f,
                modeFontFamily = FontFamilyOption.DEFAULT,
                topPaddingDp = 10,
                bottomPaddingDp = 12
            )
            UiTemplateType.SAMSUNG -> ModeLayoutConfig(
                modeSelectorPosition = ModeSelectorPosition.ABOVE_SHUTTER,
                modeSelectorStyle = ModeSelectorStyle.CAPSULE_PILL,
                shutterStyle = ShutterStyle.SAMSUNG_CAPSULE,
                shutterSizeDp = 82,
                shutterHorizontalOffsetDp = 0,
                flipButtonSizeDp = 56,
                galleryThumbSizeDp = 56,
                topControlsIconSizeDp = 24,
                topControlsSpacingDp = 18,
                topBarAlignment = TopBarAlignment.SPACE_BETWEEN,
                accentColorHex = "#FFFFFF",
                modeTextSizeSp = 13.5f,
                modeFontFamily = FontFamilyOption.ROUNDED,
                topPaddingDp = 12,
                bottomPaddingDp = 16
            )
            UiTemplateType.VIVO -> ModeLayoutConfig(
                modeSelectorPosition = ModeSelectorPosition.BELOW_SHUTTER,
                modeSelectorStyle = ModeSelectorStyle.UNDERLINE,
                shutterStyle = ShutterStyle.VIVO_GIMBAL,
                shutterSizeDp = 84,
                shutterHorizontalOffsetDp = 0,
                flipButtonSizeDp = 54,
                galleryThumbSizeDp = 54,
                topControlsIconSizeDp = 24,
                topControlsSpacingDp = 16,
                topBarAlignment = TopBarAlignment.SPACE_BETWEEN,
                accentColorHex = "#FF7043",
                modeTextSizeSp = 14.0f,
                modeFontFamily = FontFamilyOption.DEFAULT,
                topPaddingDp = 12,
                bottomPaddingDp = 14
            )
            UiTemplateType.CUSTOM -> ModeLayoutConfig(
                modeSelectorPosition = ModeSelectorPosition.ABOVE_SHUTTER,
                modeSelectorStyle = ModeSelectorStyle.CAPSULE_PILL,
                shutterStyle = ShutterStyle.CLASSIC_WHITE,
                shutterSizeDp = 80,
                shutterHorizontalOffsetDp = 0,
                flipButtonSizeDp = 54,
                galleryThumbSizeDp = 54,
                topControlsIconSizeDp = 24,
                topControlsSpacingDp = 16,
                topBarAlignment = TopBarAlignment.SPACE_BETWEEN,
                accentColorHex = "#64B5F6",
                modeTextSizeSp = 13.5f,
                modeFontFamily = FontFamilyOption.DEFAULT,
                topPaddingDp = 12,
                bottomPaddingDp = 14
            )
        }
    }
}
