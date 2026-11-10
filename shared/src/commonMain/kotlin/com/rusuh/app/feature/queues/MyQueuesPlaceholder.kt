package com.rusuh.app.feature.queues

import com.rusuh.app.data.repository.StubMyQueuesRepository
import com.rusuh.app.ui.components.QueueListItemUi

/**
 * Sample queues for previews and the stateless screen's default. The data itself
 * lives behind [StubMyQueuesRepository]; this just maps it to row UI models.
 */
fun placeholderQueues(): List<QueueListItemUi> = StubMyQueuesRepository.SAMPLE_QUEUES.toUiItems()
