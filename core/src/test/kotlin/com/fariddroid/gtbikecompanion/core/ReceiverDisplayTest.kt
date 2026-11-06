package com.fariddroid.gtbikecompanion.core

import com.fariddroid.gtbikecompanion.core.model.ReceiverState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ride hides a receiver the PC does not have while the other one works (no ANT+ stick: ANT in ERROR, BLE ready). */
class ReceiverDisplayTest {
    @Test fun missingAntStickIsHiddenWhileBluetoothWorks() {
        assertFalse(ReceiverState.shown(ReceiverState.ERROR, ReceiverState.READY))
        assertFalse("the mod retries ANT in a loop", ReceiverState.shown(ReceiverState.STARTED, ReceiverState.READY))
        assertTrue(ReceiverState.shown(ReceiverState.READY, ReceiverState.READY))
    }

    @Test fun bothAreShownWhenNeitherWorks() {
        assertTrue(ReceiverState.shown(ReceiverState.ERROR, ReceiverState.ERROR))
        assertTrue(ReceiverState.shown(ReceiverState.UNINITIALIZED, ReceiverState.UNINITIALIZED))
        assertTrue(ReceiverState.shown(ReceiverState.ERROR, ReceiverState.STARTED))
    }
}
