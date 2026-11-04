package com.binaryunit.aodled.config

enum class Style { RING, DOT, EDGES, CUTOUT }
enum class Animation { OFF, PULSE, MOTION }
enum class ColourMode { APP, FIXED }
enum class LightOff { ON_DISMISS, ON_UNLOCK }
enum class FilterMode { ALL, ALLOW, IGNORE }

data class RingPlacement(val cx: Float, val cy: Float, val radius: Float)
data class DotPlacement(val x: Float, val y: Float, val size: Float)

/** The cutout outline scaled by [scale] around its bounds' centre, with that centre moved to ([cx], [cy]). */
data class CutoutPlacement(val cx: Float, val cy: Float, val scale: Float)

/** A null placement means "use the default derived from the camera cutout". */
data class LightConfig(
    val style: Style = Style.RING,
    val animation: Animation = Animation.OFF,
    val colourMode: ColourMode = ColourMode.APP,
    val fixedColour: Int = 0xFFFFFFFF.toInt(),
    val lightOff: LightOff = LightOff.ON_DISMISS,
    val ring: RingPlacement? = null,
    val dot: DotPlacement? = null,
    val cutout: CutoutPlacement? = null,
    val edgeThickness: Float = EDGE_DEFAULT,
    val ringLineScale: Float = 1f,
    val cutoutLineScale: Float = 1f,
    val respectDnd: Boolean = true,
    val filterMode: FilterMode = FilterMode.ALL,
    // Package names; each list is kept while the other mode is in use
    val allowList: Set<String> = emptySet(),
    val ignoreList: Set<String> = emptySet(),
)

/**
 * The config after Done on [mode]'s list with [picked] checked: saving switches to [mode]. An emptied list is
 * deleted, and if it was the one in use the filter goes back to All. Null when there was no list to delete.
 */
fun LightConfig.withFilterList(mode: FilterMode, picked: Set<String>): LightConfig? {
    require(mode != FilterMode.ALL)
    val old = if (mode == FilterMode.ALLOW) allowList else ignoreList
    if (picked.isEmpty() && old.isEmpty()) return null
    val next = if (picked.isNotEmpty()) mode else if (filterMode == mode) FilterMode.ALL else filterMode
    return if (mode == FilterMode.ALLOW) copy(filterMode = next, allowList = picked)
        else copy(filterMode = next, ignoreList = picked)
}
