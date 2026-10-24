package com.binaryunit.aodled.config

enum class Style { RING, DOT, EDGES, CUTOUT }
enum class Animation { OFF, PULSE, MOTION }
enum class ColourMode { APP, FIXED }
enum class LightOff { ON_DISMISS, ON_UNLOCK }

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
)
