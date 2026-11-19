package com.coachchicken.core

/**
 * An exercise the session can track. For now it is what the get-into-position screen shows as the target: a ghost
 * skeleton [template] drawn where the player is expected to be. Judging and timing stay with the plank for now and move
 * onto the exercise when a second one arrives.
 */
class Exercise(
    val id: String,
    val name: String,
    /**
     * Joint positions (MediaPipe pose indices) in the picture as the player sees it, 0..1 across and down the frame,
     * with the head on the left. The screen mirrors it when the player faces the other way.
     */
    val template: Map<Int, Pair<Float, Float>>,
)

object Exercises {
    /** A forearm plank, side-on: forearms forward under the shoulders, body in one straight line down to the feet. */
    val PLANK = Exercise(
        id = "plank",
        name = "Plank",
        template = mapOf(
            PoseIdx.NOSE to (0.20f to 0.50f),
            PoseIdx.L_SHOULDER to (0.29f to 0.51f), PoseIdx.R_SHOULDER to (0.30f to 0.505f),
            PoseIdx.L_ELBOW to (0.29f to 0.63f), PoseIdx.R_ELBOW to (0.30f to 0.625f),
            PoseIdx.L_WRIST to (0.20f to 0.64f), PoseIdx.R_WRIST to (0.21f to 0.635f),
            PoseIdx.L_HIP to (0.55f to 0.54f), PoseIdx.R_HIP to (0.56f to 0.535f),
            PoseIdx.L_KNEE to (0.70f to 0.555f), PoseIdx.R_KNEE to (0.71f to 0.55f),
            PoseIdx.L_ANKLE to (0.85f to 0.575f), PoseIdx.R_ANKLE to (0.86f to 0.57f),
        ),
    )

    val all: List<Exercise> = listOf(PLANK)

    fun byId(id: String?): Exercise = all.firstOrNull { it.id == id } ?: PLANK
}
