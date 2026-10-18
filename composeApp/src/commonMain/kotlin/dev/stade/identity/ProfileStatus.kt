package dev.stade.identity

enum class ProfileStatus(val code: String) {
    NONE(""),
    ONLINE("online"),
    IDLE("idle"),
    DND("dnd"),
    OFFLINE("offline");

    companion object {
        fun fromCode(code: String): ProfileStatus =
            entries.firstOrNull { it.code.isNotEmpty() && it.code == code } ?: NONE
    }
}

fun contactPresence(statusCode: String, connected: Boolean, everConnected: Boolean): ProfileStatus {
    if (!connected) return if (everConnected) ProfileStatus.OFFLINE else ProfileStatus.NONE
    val chosen = ProfileStatus.fromCode(statusCode)
    return if (chosen != ProfileStatus.NONE) chosen else ProfileStatus.ONLINE
}
