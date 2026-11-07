package com.myehr.app.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Sync progress, shared by the service, the sync screen and the app. */
object SyncState {
    data class Status(val running: Boolean = false, val text: String = "", val finishedAt: Long = 0, val needsLogin: Boolean = false)
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status
    fun set(s: Status) { _status.value = s }

    /** "\nלא נטענו הפעם: תרופות (אבא) ..." or "" when everything was fetched. */
    fun missing(failures: Collection<String>) =
        if (failures.isEmpty()) "" else "\nלא נטענו הפעם: ${failures.joinToString(", ")}. אפשר לנסות שוב מאוחר יותר."
}

