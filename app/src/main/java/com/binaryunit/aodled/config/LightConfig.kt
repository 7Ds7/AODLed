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

// The pulse popup's slider steps; 4 s is the original pulse
val PULSE_PERIODS = listOf(100L, 200L, 300L, 500L, 1000L, 2000L, 3000L, 4000L, 5000L, 6000L, 7000L, 8000L)
val PULSE_PAUSES = listOf(0L, 3_000L, 5_000L, 10_000L, 15_000L, 30_000L, 60_000L, 120_000L, 300_000L, 600_000L, 900_000L)

/** One blink lasts [periodMs]; with a pause, [blinks] of them make a burst and the light stays dark for [pauseMs]. */
data class Pulse(val periodMs: Long = 4000L, val blinks: Int = 3, val pauseMs: Long = 0L)

/** More than 3 flashes a second, where flashing can trigger photosensitive seizures (WCAG 2.3.1). */
fun Pulse.flashesFast(): Boolean = periodMs * 3 < 1000

/** A null placement means "use the default derived from the camera cutout". */
data class LightConfig(
    // Off keeps the cover down; notifications are still tracked, so switching on shows the ones pending
    val enabled: Boolean = true,
    val style: Style = Style.RING,
    val animation: Animation = Animation.OFF,
    val pulse: Pulse = Pulse(),
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
