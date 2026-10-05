package dev.stade.ui

import dev.stade.ui.components.GroupPosition
import dev.stade.ui.components.SettingsGroupScope
import dev.stade.ui.components.groupPositions
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsGroupTest {

    @Test
    fun aLoneRowKeepsBothEndsRounded() {
        assertEquals(listOf(GroupPosition.Single), groupPositions(1))
    }

    @Test
    fun twoRowsAreATopAndABottomWithNoMiddle() {
        assertEquals(listOf(GroupPosition.Top, GroupPosition.Bottom), groupPositions(2))
    }

    @Test
    fun everythingBetweenTheEndsIsAMiddle() {
        assertEquals(
            listOf(
                GroupPosition.Top,
                GroupPosition.Middle,
                GroupPosition.Middle,
                GroupPosition.Bottom
            ),
            groupPositions(4)
        )
    }

    @Test
    fun anEmptyGroupProducesNothing() {
        assertEquals(emptyList(), groupPositions(0))
    }

    @Test
    fun aConditionalRowShiftsWhichRowIsLast() {
        val withoutOptional = SettingsGroupScope().apply {
            row { }
            row { }
        }
        val withOptional = SettingsGroupScope().apply {
            row { }
            row { }
            row { }
        }

        assertEquals(
            listOf(GroupPosition.Top, GroupPosition.Bottom),
            groupPositions(withoutOptional.size)
        )
        assertEquals(
            listOf(GroupPosition.Top, GroupPosition.Middle, GroupPosition.Bottom),
            groupPositions(withOptional.size)
        )
    }
}
