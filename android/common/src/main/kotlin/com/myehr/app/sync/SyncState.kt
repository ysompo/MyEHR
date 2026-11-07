package com.myehr.app.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Sync progress, shared by the service, the sync screen and the app. */
object SyncState {
    data class Status(val running: Boolean = false, val text: String = "", val finishedAt: Long = 0, val needsLogin: Boolean = false)
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status
    fun set(s: Status) { _status.value = s }

    /** "5 תוצאות חדשות (2 מחוץ לטווח): אבא 3, נועה 2", or "" when none. */
    fun newResultsText(byPerson: Map<String, Pair<Int, Int>>): String {
        if (byPerson.isEmpty()) return ""
        val total = byPerson.values.sumOf { it.first }; val flagged = byPerson.values.sumOf { it.second }
        return "$total תוצאות חדשות" + (if (flagged > 0) " ($flagged מחוץ לטווח)" else "") +
            ": " + byPerson.entries.joinToString(", ") { "${it.key} ${it.value.first}" }
    }

    /** "\nלא נטענו הפעם: תרופות (אבא) ..." or "" when everything was fetched. */
    fun missing(failures: Collection<String>) =
        if (failures.isEmpty()) "" else "\nלא נטענו הפעם: ${failures.joinToString(", ")}. אפשר לנסות שוב מאוחר יותר."
}

