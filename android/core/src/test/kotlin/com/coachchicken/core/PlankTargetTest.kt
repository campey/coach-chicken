package com.coachchicken.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlankTargetTest {
    private fun pose(hipY: Float = 0.54f, ankleVis: Float = 1f, kneeVis: Float = 1f): MutableList<Landmark> {
        val l = MutableList(PoseIdx.COUNT) { Landmark(0f, 0f, 0f) }
        l[11] = Landmark(0.25f, 0.50f); l[13] = Landmark(0.25f, 0.62f)
        l[23] = Landmark(0.50f, hipY)
        l[25] = Landmark(0.65f, 0.56f, kneeVis); l[27] = Landmark(0.80f, 0.58f, ankleVis)
        return l
    }

    @Test fun targetRunsFromTheShoulderToTheAnkle() {
        val t = lockTarget(pose())!!
        assertEquals(0.25f, t.ax); assertEquals(0.50f, t.ay)
        assertEquals(0.80f, t.bx); assertEquals(0.58f, t.by)
        assertTrue(t.halfWidth > 0.03f)
    }

    @Test fun aStraightPlanksHipIsInsideTheZone() {
        val p = pose()
        assertTrue(lockTarget(p)!!.contains(plankHip(p)!!, 1f))
    }

    @Test fun theZoneIsLockedFromTheIdealLineSoALaterSagLeavesIt() {
        val target = lockTarget(pose())!!
        val sagged = pose(hipY = 0.75f)
        assertTrue(!target.contains(plankHip(sagged)!!, 1f), "offset ${target.offset(plankHip(sagged)!!, 1f)}")
        val piked = pose(hipY = 0.30f)
        assertTrue(!target.contains(plankHip(piked)!!, 1f))
    }

    @Test fun lockingWhileSaggingStillAimsAtTheStraightLine() {
        // the goal is the ideal line, not wherever the hip happens to be
        val sagging = pose(hipY = 0.70f)
        val target = lockTarget(sagging)!!
        assertTrue(!target.contains(plankHip(sagging)!!, 1f))
        assertEquals(0.58f, target.by)
    }

    @Test fun usesTheKneeWhenTheAnkleIsMissing() {
        val t = lockTarget(pose(ankleVis = 0f))!!
        assertEquals(0.65f, t.bx)
    }

    @Test fun runsOnThroughTheHipWhenTheLegsAreOutOfFrame() {
        val p = pose(ankleVis = 0f, kneeVis = 0f)
        val t = lockTarget(p)!!
        assertTrue(t.bx > 0.5f, "runs past the hip towards the legs: ${t.bx}")
        assertEquals(0f, t.offset(plankHip(p)!!, 1f), 1e-4f) // the hip is, by construction, on it
    }

    @Test fun offsetUsesRealProportionsNotNormalisedOnes() {
        val t = PlankTarget(0.2f, 0.5f, 0.8f, 0.5f, halfWidth = 0.1f)
        val p = Landmark(0.5f, 0.55f) // 0.05 image-heights off the line
        assertEquals(0.5f, t.offset(p, 1.78f), 1e-4f)
        assertEquals(0.5f, t.offset(p, 0.75f), 1e-4f) // a horizontal line: aspect doesn't change the vertical gap
        val steep = PlankTarget(0.5f, 0.2f, 0.5f, 0.8f, halfWidth = 0.1f)
        assertEquals(0.05f * 1.78f / 0.1f, steep.offset(Landmark(0.55f, 0.5f), 1.78f), 1e-4f)
    }

    @Test fun noBodyNoTarget() {
        assertNull(lockTarget(emptyList()))
        assertNotNull(lockTarget(pose()))
    }
}
