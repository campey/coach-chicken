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

/** Rows lifted from a real phone log (front camera, portrait, loose jersey, legs out of frame). Aspect 0.75. */
class RealPhoneRowsTest {
    private val aspect = 0.75f

    /** The log's right-side joints; the rest of the body is left invisible. */
    private fun row(
        sho: Pair<Float, Float>, elb: Pair<Float, Float>, hip: Pair<Float, Float>,
        kne: Pair<Float, Float>, ank: Pair<Float, Float>, vis: Float,
    ): List<Landmark> {
        val l = MutableList(PoseIdx.COUNT) { Landmark(0f, 0f, 0f) }
        l[PoseIdx.R_SHOULDER] = Landmark(sho.first, sho.second, vis)
        l[PoseIdx.R_ELBOW] = Landmark(elb.first, elb.second, vis)
        l[PoseIdx.R_HIP] = Landmark(hip.first, hip.second, vis)
        l[PoseIdx.R_KNEE] = Landmark(kne.first, kne.second, vis)
        l[PoseIdx.R_ANKLE] = Landmark(ank.first, ank.second, vis)
        return l
    }

    // t=47705: a good plank mid-set. The legs are off the left edge (negative x) but MediaPipe is 64% sure where
    // they are, and the extrapolation looked right on screen. The hip angle reads 141 degrees: it used to fail the
    // 150 limit, and passes the tolerant 130.
    private val goodPlankLegsOffFrame = row(0.752f to 0.523f, 0.712f to 0.732f, 0.207f to 0.605f, -0.092f to 0.685f, -0.115f to 0.481f, 0.64f)

    // t=6861: the loose jersey drags the elbow point up (elbow only 0.13 torso-lengths below the shoulder).
    private val sleeveDraggedElbow = row(0.834f to 0.491f, 0.683f to 0.541f, 0.327f to 0.593f, -0.026f to 0.680f, -0.263f to 0.674f, 0.50f)

    // t=30266: hip angle read 148.5 against a 150 limit.
    private val hipDipped = row(0.840f to 0.564f, 0.668f to 0.691f, 0.298f to 0.613f, -0.048f to 0.719f, -0.067f to 0.489f, 0.55f)

    // t=66330: starting to get up; the hips are lifting (hip angle ~117 degrees with the off-frame ankle).
    private val gettingUp = row(0.778f to 0.531f, 0.747f to 0.751f, 0.220f to 0.438f, 0.224f to 0.804f, -0.061f to 0.698f, 0.74f)

    // t=67707: standing facing the camera.
    private val standing = row(0.311f to 0.106f, 0.166f to 0.234f, 0.153f to 0.489f, 0.266f to 0.741f, 0.261f to 0.809f, 0.83f)

    @Test fun aConfidentOffFrameAnkleIsUsed() {
        val m = plankMetrics(goodPlankLegsOffFrame, aspect)!!
        assertEquals('A', m.lower)
        assertTrue(m.hipAngleDeg in 138.0..145.0, "hip ${m.hipAngleDeg}")
        assertEquals(PlankGrade.OK, gradePlank(goodPlankLegsOffFrame, aspect))
    }

    @Test fun aLessConfidentOffFrameAnkleFallsBackToTheTorso() {
        val unsure = goodPlankLegsOffFrame.map { it.copy(visibility = if (it.visibility > 0f) 0.4f else 0f) }
        assertEquals('H', plankMetrics(unsure, aspect)!!.lower)
        assertEquals(PoseReading.PLANK, readPlank(unsure, aspect))
    }

    @Test fun anAnkleFarOutsideTheFrameIsNotTrusted() {
        val far = goodPlankLegsOffFrame.toMutableList().also {
            it[PoseIdx.R_ANKLE] = Landmark(-0.9f, 0.48f, 0.9f)
            it[PoseIdx.R_KNEE] = Landmark(-0.7f, 0.68f, 0.9f)
        }
        assertEquals('H', plankMetrics(far, aspect)!!.lower)
    }

    @Test fun anElbowDraggedByALooseSleeveStillCountsButIsNotPerfect() {
        assertEquals(PlankGrade.OK, gradePlank(sleeveDraggedElbow, aspect))
    }

    @Test fun aSlightHipDipStillCounts() = assertEquals(PoseReading.PLANK, readPlank(hipDipped, aspect))

    @Test fun startingToGetUpIsNotAPlank() = assertEquals(PoseReading.NOT_PLANK, readPlank(gettingUp, aspect))

    @Test fun standingIsNotAPlank() = assertEquals(PoseReading.NOT_PLANK, readPlank(standing, aspect))
}

class GradingTest {
    private fun pose(hipY: Float = 0.54f, elbowY: Float = 0.62f, ankleY: Float = 0.58f): List<Landmark> {
        val l = MutableList(PoseIdx.COUNT) { Landmark(0f, 0f, 0f) }
        l[11] = Landmark(0.25f, 0.50f); l[13] = Landmark(0.25f, elbowY)
        l[23] = Landmark(0.50f, hipY); l[27] = Landmark(0.80f, ankleY)
        return l
    }

    @Test fun aCleanStraightPlankIsPerfect() = assertEquals(PlankGrade.PERFECT, gradePlank(pose()))

    @Test fun aSaggingButTolerableHipIsOkNotPerfect() {
        // hip drops 0.12 below the shoulder-ankle line: roughly a 148 degree hip angle
        assertEquals(PlankGrade.OK, gradePlank(pose(hipY = 0.66f)))
    }

    @Test fun aShallowElbowDropIsOk() = assertEquals(PlankGrade.OK, gradePlank(pose(elbowY = 0.54f)))

    @Test fun veryBentIsNotAPlank() = assertEquals(PlankGrade.NOT_PLANK, gradePlank(pose(hipY = 0.82f)))

    @Test fun gradeMapsToReading() {
        assertEquals(PoseReading.PLANK, PlankGrade.PERFECT.reading)
        assertEquals(PoseReading.PLANK, PlankGrade.OK.reading)
        assertEquals(PoseReading.NOT_PLANK, PlankGrade.NOT_PLANK.reading)
        assertEquals(PoseReading.NO_BODY, PlankGrade.NO_BODY.reading)
    }
}

/** Rows from a second real phone run (landscape, phone propped at a slight tilt: a straight plank read 12-17 degrees). */
class TiltedCameraTest {
    private fun row(
        sho: Pair<Float, Float>, elb: Pair<Float, Float>, hip: Pair<Float, Float>,
        kne: Pair<Float, Float>, ank: Pair<Float, Float>, vis: Float,
    ): List<Landmark> {
        val l = MutableList(PoseIdx.COUNT) { Landmark(0f, 0f, 0f) }
        l[PoseIdx.R_SHOULDER] = Landmark(sho.first, sho.second, vis); l[PoseIdx.R_ELBOW] = Landmark(elb.first, elb.second, vis)
        l[PoseIdx.R_HIP] = Landmark(hip.first, hip.second, vis); l[PoseIdx.R_KNEE] = Landmark(kne.first, kne.second, vis)
        l[PoseIdx.R_ANKLE] = Landmark(ank.first, ank.second, vis)
        return l
    }

    private val aspect = 4f / 3f

    // t=36082: body angle 12.6, hip 173.5, elbow drop 0.58. Used to be OK only because of the 12 degree body limit.
    private val tiltedAnkle = row(0.624f to 0.530f, 0.546f to 0.816f, 0.270f to 0.666f, 0.019f to 0.746f, -0.168f to 0.766f, 0.52f)


    @Test fun aStraightPlankOnATiltedPhoneStillGradesPerfect() {
        val m = plankMetrics(tiltedAnkle, aspect)!!
        assertTrue(m.bodyAngleDeg > 12.0, "body ${m.bodyAngleDeg}")
        assertEquals(PlankGrade.PERFECT, gradePlank(tiltedAnkle, aspect))
    }

    @Test fun theGradeDoesNotFlipWhenTheLegPointFlipsBetweenAnkleAndKnee() {
        val withKnee = tiltedAnkle.toMutableList().also { it[PoseIdx.R_ANKLE] = Landmark(0f, 0f, 0f) }
        assertEquals('K', plankMetrics(withKnee, aspect)!!.lower)
        assertEquals(gradePlank(tiltedAnkle, aspect), gradePlank(withKnee, aspect))
    }

    @Test fun aPerfectPlankMustStayInTheGreenZone() {
        val target = lockTarget(tiltedAnkle, aspect)!!
        assertEquals(PlankGrade.PERFECT, gradePlank(tiltedAnkle, aspect, target = target))
        // the hips lift well out of the zone, though the arms and the hip angle still look fine
        val lifted = tiltedAnkle.toMutableList().also { it[PoseIdx.R_HIP] = Landmark(0.270f, 0.50f, 0.52f) }
        val grade = gradePlank(lifted, aspect, target = target)
        assertTrue(grade != PlankGrade.PERFECT, "grade $grade")
    }
}
