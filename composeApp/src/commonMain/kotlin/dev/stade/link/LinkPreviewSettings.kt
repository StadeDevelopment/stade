package dev.stade.link

import dev.stade.db.StadeDb

private const val LINK_PREVIEWS_KV_KEY = "linkPreviewsEnabled"

fun getLinkPreviewsEnabled(db: StadeDb): Boolean =
    runCatching { db.stadeDbQueries.getKv(LINK_PREVIEWS_KV_KEY).executeAsOneOrNull() }
        .getOrNull()?.decodeToString() == "1"

fun setLinkPreviewsEnabled(db: StadeDb, enabled: Boolean) {
    db.stadeDbQueries.putKv(LINK_PREVIEWS_KV_KEY, (if (enabled) "1" else "0").encodeToByteArray())
}
