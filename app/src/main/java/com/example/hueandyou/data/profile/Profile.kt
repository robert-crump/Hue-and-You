package com.example.hueandyou.data.profile

/**
 * How many colors each of a profile's lists (Best / Avoid) may hold, so the Profile editor's two
 * 5x5 grids fit on screen without scrolling. Enforced on add only: lists restored from a backup or
 * created before the cap are kept as-is.
 */
const val MAX_COLORS_PER_KIND = 25

data class PaletteColor(
    val id: Long,
    val kind: ColorKind,
    val argb: Int
)

data class Profile(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val bestColors: List<PaletteColor>,
    val avoidColors: List<PaletteColor>
)

internal fun ProfileWithColors.toDomain(): Profile {
    val sorted = colors.sortedBy { it.position }
    return Profile(
        id = profile.id,
        name = profile.name,
        createdAt = profile.createdAt,
        updatedAt = profile.updatedAt,
        bestColors = sorted.filter { it.kind == ColorKind.BEST }
            .map { PaletteColor(it.id, it.kind, it.argb) },
        avoidColors = sorted.filter { it.kind == ColorKind.AVOID }
            .map { PaletteColor(it.id, it.kind, it.argb) }
    )
}
