package com.coachchicken.core

/**
 * True when the head is off the edge of the image (or hard against it): the phone is too close, or the person
 * is too big for the shot. Judged from the nose; call it only when a body has actually been found.
 */
fun headOutOfFrame(landmarks: List<Landmark>, margin: Float = 0.03f): Boolean {
    if (landmarks.size < PoseIdx.COUNT) return false
    val nose = landmarks[PoseIdx.NOSE]
    return nose.x < margin || nose.x > 1f - margin || nose.y < margin || nose.y > 1f - margin
}
