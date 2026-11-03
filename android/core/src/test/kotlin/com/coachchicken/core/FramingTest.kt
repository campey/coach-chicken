package com.coachchicken.core

import kotlin.test.Test
import kotlin.test.assertEquals

class FramingTest {
    private fun withNose(x: Float, y: Float) = MutableList(PoseIdx.COUNT) { Landmark(0.5f, 0.5f) }.also { it[PoseIdx.NOSE] = Landmark(x, y) }

    @Test fun aHeadInTheMiddleIsFine() = assertEquals(false, headOutOfFrame(withNose(0.5f, 0.4f)))

    @Test fun aHeadBeyondTheEdgeIsOut() {
        assertEquals(true, headOutOfFrame(withNose(1.057f, 0.539f))) // from the real phone log: nose x above 1
        assertEquals(true, headOutOfFrame(withNose(-0.1f, 0.5f)))
        assertEquals(true, headOutOfFrame(withNose(0.5f, 1.2f)))
    }

    @Test fun aHeadPressedAgainstTheEdgeCountsAsOut() {
        assertEquals(true, headOutOfFrame(withNose(0.99f, 0.5f)))
        assertEquals(true, headOutOfFrame(withNose(0.5f, 0.01f)))
    }

    @Test fun aHeadJustInsideTheMarginIsFine() = assertEquals(false, headOutOfFrame(withNose(0.93f, 0.5f)))

    @Test fun noLandmarksNoHint() = assertEquals(false, headOutOfFrame(emptyList()))
}
