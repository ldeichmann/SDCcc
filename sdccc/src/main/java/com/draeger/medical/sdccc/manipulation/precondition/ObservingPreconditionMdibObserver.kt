/*
 * This Source Code Form is subject to the terms of the MIT License.
 * Copyright (c) 2024 Draegerwerk AG & Co. KGaA.
 *
 * SPDX-License-Identifier: MIT
 */

package com.draeger.medical.sdccc.manipulation.precondition

import com.draeger.medical.sdccc.manipulation.precondition.PreconditionChange.Companion.from
import com.google.common.eventbus.Subscribe
import org.somda.sdc.biceps.common.access.MdibAccessObserver
import org.somda.sdc.biceps.common.event.AlertStateModificationMessage
import org.somda.sdc.biceps.common.event.ComponentStateModificationMessage
import org.somda.sdc.biceps.common.event.ContextStateModificationMessage
import org.somda.sdc.biceps.common.event.DescriptionModificationMessage
import org.somda.sdc.biceps.common.event.MetricStateModificationMessage
import org.somda.sdc.biceps.common.event.OperationStateModificationMessage
import org.somda.sdc.biceps.common.event.WaveformStateModificationMessage

/**
 * Observes changes in the MDIB and notifies the precondition about them.
 */
@Suppress("MethodOverloading") // the names are irrelevant, the dispatching is based on types
class ObservingPreconditionMdibObserver(
    private val precondition: Observing
) : MdibAccessObserver {

    /**
     * Observes changes in the MDIB and notifies the precondition about them.
     */
    @Subscribe
    fun onChange(report: DescriptionModificationMessage) {
        precondition.observeChange(from(report))
    }

    /**
     * Observes changes in the MDIB and notifies the precondition about them.
     */
    @Subscribe
    fun onChange(report: AlertStateModificationMessage) {
        precondition.observeChange(from(report))
    }

    /**
     * Observes changes in the MDIB and notifies the precondition about them.
     */
    @Subscribe
    fun onChange(report: ComponentStateModificationMessage) {
        precondition.observeChange(from(report))
    }

    /**
     * Observes changes in the MDIB and notifies the precondition about them.
     */
    @Subscribe
    fun onChange(report: ContextStateModificationMessage) {
        precondition.observeChange(from(report))
    }

    /**
     * Observes changes in the MDIB and notifies the precondition about them.
     */
    @Subscribe
    fun onChange(report: MetricStateModificationMessage) {
        precondition.observeChange(from(report))
    }

    /**
     * Observes changes in the MDIB and notifies the precondition about them.
     */
    @Subscribe
    fun onChange(report: OperationStateModificationMessage) {
        precondition.observeChange(from(report))
    }

    /**
     * Observes changes in the MDIB and notifies the precondition about them.
     */
    @Subscribe
    fun onChange(report: WaveformStateModificationMessage) {
        precondition.observeChange(from(report))
    }
}
