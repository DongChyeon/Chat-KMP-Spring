package com.dongchyeon.chat.shared.room

object RoomRules {
    const val NAME_MAX_LENGTH = 50
    const val PAGE_SIZE_DEFAULT = 20
    const val PAGE_SIZE_MAX = 50

    fun isValidName(name: String): Boolean = name.isNotBlank() && name.length <= NAME_MAX_LENGTH
}
