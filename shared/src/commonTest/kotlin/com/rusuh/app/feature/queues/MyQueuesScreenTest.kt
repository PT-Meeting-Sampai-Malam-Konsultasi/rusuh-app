package com.rusuh.app.feature.queues

import com.rusuh.app.ui.components.QueueListPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MyQueuesScreenTest {
    @Test
    fun placeholderIsNonEmptyAndHasStableKeys() {
        val queues = placeholderQueues()
        assertTrue(queues.isNotEmpty())
        val ids = queues.map { it.eventId }
        assertEquals(ids.size, ids.toSet().size, "placeholder event ids must be unique (used as list keys)")
    }

    @Test
    fun placeholderCoversEveryPhaseVariant() {
        val phases = placeholderQueues().map { it.phase }.toSet()
        assertEquals(QueueListPhase.entries.toSet(), phases)
    }

    @Test
    fun contentStateCarriesThePlaceholderQueues() {
        val state = MyQueuesUiState.Content(placeholderQueues())
        assertEquals(placeholderQueues().size, state.queues.size)
    }

    @Test
    fun allFourUiStatesAreDistinct() {
        val states: List<MyQueuesUiState> =
            listOf(
                MyQueuesUiState.Empty,
                MyQueuesUiState.Loading,
                MyQueuesUiState.Content(placeholderQueues()),
                MyQueuesUiState.Error("x"),
            )
        assertEquals(states.size, states.map { it::class }.toSet().size)
    }

    @Test
    fun actionsDefaultToNoOps() {
        val actions = MyQueuesActions()
        actions.onOpenWaitingRoom("evt")
        actions.onBrowseEvents()
        actions.onRetry()
    }

    @Test
    fun languageOptionsAreIdThenEn() {
        assertEquals(listOf("id", "en"), AppLanguageOption.entries.map { it.code })
    }
}
