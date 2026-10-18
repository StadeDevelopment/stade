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
    if (!connected && !everConnected) return ProfileStatus.NONE
    val chosen = ProfileStatus.fromCode(statusCode)
    if (chosen != ProfileStatus.NONE) return chosen
    return if (connected) ProfileStatus.ONLINE else ProfileStatus.OFFLINE
}
