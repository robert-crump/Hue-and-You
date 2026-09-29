package com.example.hueandyou.data.profile

/**
 * Whether [name], trimmed, matches one of [existingNames] ignoring case: two profiles that differ
 * only in case would look the same in the list.
 */
fun isProfileNameTaken(name: String, existingNames: Collection<String>): Boolean {
    val trimmed = name.trim()
    return existingNames.any { it.equals(trimmed, ignoreCase = true) }
}

/** [base] if no profile has that name yet, otherwise "[base] 2", "[base] 3", ... */
fun uniqueProfileName(base: String, existingNames: Collection<String>): String {
    if (!isProfileNameTaken(base, existingNames)) return base
    return generateSequence(2) { it + 1 }.map { "$base $it" }.first { !isProfileNameTaken(it, existingNames) }
}
