package com.coachchicken.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlankPoseTest {
    /** 33 invisible landmarks, with the given left-side points filled in. */
    private fun pose(
        s: Pair<Float, Float>, e: Pair<Float, Float>, h: Pair<Float, Float>, a: Pair<Float, Float>,
        vis: Float = 1f, left: Boolean = true,
    ): List<Landmark> {
        val l = MutableList(PoseIdx.COUNT) { Landmark(0f, 0f, 0f) }
        val (si, ei, hi, ai) = if (left) listOf(11, 13, 23, 27) else listOf(12, 14, 24, 28)
        l[si] = Landmark(s.first, s.second, vis); l[ei] = Landmark(e.first, e.second, vis)
        l[hi] = Landmark(h.first, h.second, vis); l[ai] = Landmark(a.first, a.second, vis)
        return l
    }

    private val plankRight = pose(0.25f to 0.50f, 0.25f to 0.62f, 0.50f to 0.54f, 0.80f to 0.58f)

    @Test fun plankFacingRight() = assertEquals(PoseReading.PLANK, readPlank(plankRight))

    @Test fun plankFacingLeftAndOnTheRightSideOfTheBody() {
        val mirrored = pose(0.75f to 0.50f, 0.75f to 0.62f, 0.50f to 0.54f, 0.20f to 0.58f, left = false)
        assertEquals(PoseReading.PLANK, readPlank(mirrored))
    }

    @Test fun standingIsNotAPlank() {
        val standing = pose(0.5f to 0.20f, 0.5f to 0.35f, 0.5f to 0.50f, 0.5f to 0.90f)
        assertEquals(PoseReading.NOT_PLANK, readPlank(standing))
    }

    @Test fun lyingFlatWithoutPropUpIsNotAPlank() {
        val lying = pose(0.25f to 0.60f, 0.25f to 0.60f, 0.50f to 0.60f, 0.80f to 0.60f)
        assertEquals(PoseReading.NOT_PLANK, readPlank(lying))
    }

    @Test fun pikedHipsAreNotAPlank() {
        val piked = pose(0.25f to 0.55f, 0.25f to 0.65f, 0.50f to 0.35f, 0.80f to 0.58f)
        assertEquals(PoseReading.NOT_PLANK, readPlank(piked))
    }

    @Test fun saggingHipsAreNotAPlank() {
        val sagging = pose(0.25f to 0.50f, 0.25f to 0.62f, 0.50f to 0.75f, 0.80f to 0.58f)
        assertEquals(PoseReading.NOT_PLANK, readPlank(sagging))
    }

    @Test fun lowVisibilityMeansNoBody() =
        assertEquals(PoseReading.NO_BODY, readPlank(pose(0.25f to 0.50f, 0.25f to 0.62f, 0.50f to 0.54f, 0.80f to 0.58f, vis = 0.2f)))

    @Test fun tooFewLandmarksMeansNoBody() = assertEquals(PoseReading.NO_BODY, readPlank(emptyList()))

    @Test fun aspectRatioIsAccountedFor() {
        // In a portrait image (aspect 0.5) a shallow-looking line in normalised coords is steeper in reality.
        val steep = pose(0.40f to 0.30f, 0.40f to 0.42f, 0.50f to 0.45f, 0.60f to 0.60f)
        assertEquals(PoseReading.NOT_PLANK, readPlank(steep, aspect = 0.5f))
    }
}

class PlankMetricsTest {
    @Test fun metricsForAStraightPlank() {
        val l = MutableList(PoseIdx.COUNT) { Landmark(0f, 0f, 0f) }
        l[11] = Landmark(0.25f, 0.50f); l[13] = Landmark(0.25f, 0.62f)
        l[23] = Landmark(0.50f, 0.54f); l[27] = Landmark(0.80f, 0.58f)
        val m = plankMetrics(l)!!
        assertEquals('L', m.side)
        assertTrue(m.bodyAngleDeg < 10)
        assertTrue(m.hipAngleDeg > 170)
        assertTrue(m.elbowDropRatio > 0.4)
        assertEquals(0.0, m.elbowOffsetRatio, 1e-6)
    }

    @Test fun nullWithoutEnoughLandmarks() = assertEquals(null, plankMetrics(emptyList()))
}

class MissingLegsTest {
    private fun pose(ankleVis: Float, kneeVis: Float): MutableList<Landmark> {
        val l = MutableList(PoseIdx.COUNT) { Landmark(0f, 0f, 0f) }
        l[11] = Landmark(0.25f, 0.50f); l[13] = Landmark(0.25f, 0.62f); l[23] = Landmark(0.50f, 0.54f)
        l[25] = Landmark(0.65f, 0.56f, kneeVis); l[27] = Landmark(0.80f, 0.58f, ankleVis)
        return l
    }

    @Test fun usesTheAnkleWhenVisible() {
        assertEquals('A', plankMetrics(pose(1f, 1f))!!.lower)
        assertEquals(PoseReading.PLANK, readPlank(pose(1f, 1f)))
    }

    @Test fun fallsBackToTheKneeWhenTheAnkleIsMissing() {
        assertEquals('K', plankMetrics(pose(0.1f, 0.9f))!!.lower)
        assertEquals(PoseReading.PLANK, readPlank(pose(0.1f, 0.9f)))
    }

    @Test fun fallsBackToTheTorsoWhenBothLegPointsAreMissing() {
        assertEquals('H', plankMetrics(pose(0.1f, 0.1f))!!.lower)
        assertEquals(PoseReading.PLANK, readPlank(pose(0.1f, 0.1f)))
    }

    @Test fun torsoOnlyStillRejectsAStandingBody() {
        val standing = pose(0f, 0f).also { it[11] = Landmark(0.5f, 0.2f); it[13] = Landmark(0.5f, 0.35f); it[23] = Landmark(0.5f, 0.5f) }
        assertEquals(PoseReading.NOT_PLANK, readPlank(standing))
    }

    @Test fun torsoOnlyStillNeedsTheArmProp() {
        val lying = pose(0f, 0f).also { it[13] = Landmark(0.25f, 0.50f) } // elbow level with shoulder
        assertEquals(PoseReading.NOT_PLANK, readPlank(lying))
    }

    @Test fun kneeFallbackStillRejectsPikedHips() {
        val piked = pose(0f, 1f).also { it[23] = Landmark(0.50f, 0.30f) }
        assertEquals(PoseReading.NOT_PLANK, readPlank(piked))
    }
}
