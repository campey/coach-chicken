package com.coachchicken.core

import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseTest {
    private fun landmarks(mirror: Boolean): List<Landmark> {
        val l = MutableList(PoseIdx.COUNT) { Landmark(0f, 0f, 0f) }
        Exercises.PLANK.template.forEach { (i, xy) -> l[i] = Landmark(if (mirror) 1f - xy.first else xy.first, xy.second) }
        return l
    }

    // The ghost the player is asked to match has to be a plank our own detector accepts, facing either way.
    @Test fun theTemplateIsAPerfectPlank() {
        assertEquals(PlankGrade.PERFECT, gradePlank(landmarks(false), 4f / 3f))
        assertEquals(PlankGrade.PERFECT, gradePlank(landmarks(true), 4f / 3f))
    }

    @Test fun unknownExercisesFallBackToThePlank() = assertEquals(Exercises.PLANK, Exercises.byId("somewhere-newer"))
}
