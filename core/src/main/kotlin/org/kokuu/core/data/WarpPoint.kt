package org.kokuu.core.data

/** 公共传送点。 */
class WarpPoint(
    var name: String = "",
    var location: StoredLocation = StoredLocation(),
    var createdBy: String = "",
    var createdAt: Long = 0L
)
