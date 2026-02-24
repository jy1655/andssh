package com.opencode.sshterminal.ui.connection

import com.opencode.sshterminal.data.ConnectionProfile

internal enum class ConnectionSortOption {
    NAME,
    RECENT,
}

internal const val UNGROUPED_FILTER_KEY = "__ungrouped__"

internal fun filterAndSortProfiles(
    profiles: List<ConnectionProfile>,
    searchQuery: String,
    selectedGroupFilter: String?,
    sortOption: ConnectionSortOption,
): List<ConnectionProfile> {
    val query = searchQuery.trim()
    val byQuery =
        if (query.isBlank()) {
            profiles
        } else {
            profiles.filter { profile ->
                profile.name.contains(query, ignoreCase = true) ||
                    profile.group.orEmpty().contains(query, ignoreCase = true) ||
                    profile.host.contains(query, ignoreCase = true) ||
                    profile.tags.any { tag -> tag.contains(query, ignoreCase = true) } ||
                    profile.username.contains(query, ignoreCase = true)
            }
        }

    val byGroup =
        when (selectedGroupFilter) {
            null -> byQuery
            UNGROUPED_FILTER_KEY -> byQuery.filter { profile -> profile.group.isNullOrBlank() }
            else -> byQuery.filter { profile -> profile.group == selectedGroupFilter }
        }

    return when (sortOption) {
        ConnectionSortOption.NAME ->
            byGroup.sortedWith(
                compareBy(
                    { profile -> profile.group.orEmpty().lowercase() },
                    { profile -> profile.name.lowercase() },
                ),
            )
        ConnectionSortOption.RECENT ->
            byGroup.sortedWith(
                compareBy<ConnectionProfile> { profile -> profile.group.orEmpty().lowercase() }
                    .thenByDescending { profile -> profile.lastUsedEpochMillis }
                    .thenBy { profile -> profile.name.lowercase() },
            )
    }
}
