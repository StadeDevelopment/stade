package dev.stade.contact

import dev.stade.AppContainer

suspend fun AppContainer.deleteContact(ownerId: String, contactId: String) {
    chatWallpapers.set(contactId, null)
    val stillNeeded = stadiums.allStadiums(ownerId).any { it.creatorStadeId == contactId } ||
        stadiums.stadiumsForContact(contactId).isNotEmpty() ||
        groups.groupsForContact(contactId).isNotEmpty()
    if (stillNeeded) {
        contacts.removeFromView(contactId)
    } else {
        sync.forgetContact(contactId)
        contacts.purge(contactId)
    }
}
