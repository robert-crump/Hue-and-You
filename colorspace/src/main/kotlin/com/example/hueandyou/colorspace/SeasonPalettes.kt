package com.example.hueandyou.colorspace

/** The 12 seasons of the seasonal color analysis system. */
enum class Season {
    LIGHT_SPRING,
    TRUE_SPRING,
    BRIGHT_SPRING,
    LIGHT_SUMMER,
    TRUE_SUMMER,
    SOFT_SUMMER,
    SOFT_AUTUMN,
    TRUE_AUTUMN,
    DEEP_AUTUMN,
    DEEP_WINTER,
    TRUE_WINTER,
    BRIGHT_WINTER;

    /** The season with every trait flipped (warm/cool, light/deep, bright/soft). */
    val opposite: Season
        get() = when (this) {
            LIGHT_SPRING -> DEEP_WINTER
            DEEP_WINTER -> LIGHT_SPRING
            LIGHT_SUMMER -> DEEP_AUTUMN
            DEEP_AUTUMN -> LIGHT_SUMMER
            TRUE_SPRING -> TRUE_SUMMER
            TRUE_SUMMER -> TRUE_SPRING
            TRUE_AUTUMN -> TRUE_WINTER
            TRUE_WINTER -> TRUE_AUTUMN
            BRIGHT_SPRING -> SOFT_SUMMER
            SOFT_SUMMER -> BRIGHT_SPRING
            BRIGHT_WINTER -> SOFT_AUTUMN
            SOFT_AUTUMN -> BRIGHT_WINTER
        }
}

/** The groups a season's Best colors come in, in stored order. */
enum class PaletteCategory { NEUTRALS, LIGHTS, MID_TONES, ACCENTS, DARKS }

/**
 * The fixed Best palette of each season, and the Avoid palette derived from it.
 *
 * Each Best palette holds [BEST_PER_CATEGORY] colors per [PaletteCategory], in category order.
 * Within a category the first colors are the season's most characteristic ones: they're what the
 * opposite season's Avoid list takes.
 */
object SeasonPalettes {
    const val BEST_PER_CATEGORY = 4
    const val AVOID_PER_CATEGORY = 2

    /** The season's 20 Best colors as opaque ARGB ints, in [PaletteCategory] order. */
    fun best(season: Season): List<Int> = bestColors.getValue(season)

    /** The season's Best colors in one category. */
    fun best(season: Season, category: PaletteCategory): List<Int> =
        best(season).chunked(BEST_PER_CATEGORY)[category.ordinal]

    /**
     * The season's 10 Avoid colors: the first [AVOID_PER_CATEGORY] of each category of the
     * opposite season's Best, in [PaletteCategory] order.
     */
    fun avoid(season: Season): List<Int> = PaletteCategory.entries.flatMap { category ->
        best(season.opposite, category).take(AVOID_PER_CATEGORY)
    }

    private val bestColors: Map<Season, List<Int>> = mapOf(
        Season.LIGHT_SPRING to palette(
            neutrals = listOf("#D8B58C", "#FBF1D9", "#E3CFAE", "#C8BBA6"),
            lights = listOf("#FFCBA4", "#FBE6A0", "#A8E4D8", "#F9B8A8"),
            midTones = listOf("#F29A83", "#6FCFC0", "#F6B27A", "#A9D48A"),
            accents = listOf("#FA7E62", "#33BFB0", "#F6CF45", "#8CC63F"),
            darks = listOf("#8A6448", "#2F7D78", "#946A3C", "#6B7B3C"),
        ),
        Season.TRUE_SPRING to palette(
            neutrals = listOf("#C19A6B", "#FFF0CC", "#D9BA8C", "#A38E73"),
            lights = listOf("#FFE08A", "#FFC097", "#9DE3D1", "#C9E59A"),
            midTones = listOf("#F48A6A", "#2FB5A6", "#F7A35C", "#A6CF4C"),
            accents = listOf("#F0512E", "#F7C318", "#F7812A", "#4DAF4A"),
            darks = listOf("#8A5A2B", "#1F7A74", "#7B4424", "#6F7A2A"),
        ),
        Season.BRIGHT_SPRING to palette(
            neutrals = listOf("#C8965E", "#FFF8E1", "#4A3F35", "#D2C6B4"),
            lights = listOf("#FFEF7A", "#FFB09A", "#8EEAD9", "#B8F08A"),
            midTones = listOf("#FF6B4E", "#00BFA5", "#FF9A2E", "#9BD62B"),
            accents = listOf("#FF3D1F", "#FFCC00", "#1FB54A", "#00B3A1"),
            darks = listOf("#4A2B18", "#00665E", "#7A2E12", "#1E5A2A"),
        ),
        Season.LIGHT_SUMMER to palette(
            neutrals = listOf("#AEB8C6", "#F3F2F5", "#9EA3AE", "#D7C6C4"),
            lights = listOf("#B8D4EE", "#F5C9D6", "#D6CCEB", "#C3E6DC"),
            midTones = listOf("#8DB3DF", "#E59CB3", "#9FA6DE", "#86C5C3"),
            accents = listOf("#D46A92", "#6F8FD6", "#B67FC6", "#5FAF9C"),
            darks = listOf("#5E6E8C", "#7E6682", "#4C5F86", "#4F7B7A"),
        ),
        Season.TRUE_SUMMER to palette(
            neutrals = listOf("#8795A8", "#EEEEF2", "#8E8488", "#B5B7BD"),
            lights = listOf("#A9C6E8", "#EAB6C6", "#C5BEE0", "#A9D8D6"),
            midTones = listOf("#6E93C8", "#D48AA5", "#A98BB5", "#5AA89A"),
            accents = listOf("#C2466F", "#3F6FB5", "#E0607E", "#2A8C7E"),
            darks = listOf("#2E3F66", "#6B2D45", "#3F4957", "#1F5C60"),
        ),
        Season.SOFT_SUMMER to palette(
            neutrals = listOf("#9A9291", "#8C98A4", "#E9E6E6", "#555A60"),
            lights = listOf("#D9B8C0", "#B7C7D5", "#C1CEC4", "#C8BFD3"),
            midTones = listOf("#B98C99", "#7A8FAA", "#7FA4A3", "#A08AA6"),
            accents = listOf("#9E5A74", "#5B7598", "#5E8F87", "#B2687F"),
            darks = listOf("#3F4A63", "#5C4658", "#3E5A5E", "#43464D"),
        ),
        Season.SOFT_AUTUMN to palette(
            neutrals = listOf("#B39B7F", "#E4D8C3", "#9C8C7A", "#7E6A55"),
            lights = listOf("#EBC7A8", "#CFCDA5", "#BFCBB0", "#EEDFB6"),
            midTones = listOf("#D39A80", "#9FA880", "#7FA59A", "#C9A968"),
            accents = listOf("#B8674A", "#8A8A4E", "#5E9A8E", "#BF9A45"),
            darks = listOf("#5B4535", "#535335", "#3E5E58", "#7A4A33"),
        ),
        Season.TRUE_AUTUMN to palette(
            neutrals = listOf("#B08352", "#F3E3C3", "#A6966A", "#7A5A3A"),
            lights = listOf("#E8CC8E", "#F2BE94", "#C8C98C", "#EDB27F"),
            midTones = listOf("#D9772F", "#7E8A3A", "#D1A12E", "#3A8C84"),
            accents = listOf("#B5471E", "#D35F1F", "#C8412B", "#5F7D2A"),
            darks = listOf("#4A2E1C", "#2F4A2A", "#1C4F4C", "#5E2419"),
        ),
        Season.DEEP_AUTUMN to palette(
            neutrals = listOf("#3E2A1E", "#6B4A32", "#EFE0C0", "#7A6E48"),
            lights = listOf("#E3C79A", "#F0B889", "#C9C38C", "#E0A58A"),
            midTones = listOf("#A8502E", "#6B6B2A", "#B8861E", "#2A6E68"),
            accents = listOf("#B32E1E", "#C0561A", "#A67C12", "#1F6B4A"),
            darks = listOf("#2B1B12", "#1D2E1D", "#3E1A12", "#133A38"),
        ),
        Season.DEEP_WINTER to palette(
            neutrals = listOf("#111114", "#36373D", "#F4F5F7", "#8A8D95"),
            lights = listOf("#D6E6F5", "#F3D5E2", "#DCD5EE", "#D0EDE3"),
            midTones = listOf("#1E5E4A", "#6A2A5E", "#2A4590", "#8C1D3A"),
            accents = listOf("#C8102E", "#008A5E", "#1F3FA0", "#C0187A"),
            darks = listOf("#141E3C", "#4A0E24", "#0B3B2E", "#2A1330"),
        ),
        Season.TRUE_WINTER to palette(
            neutrals = listOf("#0E0E10", "#FAFAFC", "#3B3D44", "#9A9DA6"),
            lights = listOf("#D2E5F7", "#F6D3E3", "#DDD3F1", "#CFEFEF"),
            midTones = listOf("#2F5BD1", "#D1207F", "#009B6B", "#D0103A"),
            accents = listOf("#E8288F", "#0047AB", "#C8102E", "#6A2FB0"),
            darks = listOf("#1B2A5A", "#6A0F2E", "#0F4D3C", "#3B1A5A"),
        ),
        Season.BRIGHT_WINTER to palette(
            neutrals = listOf("#0A0A0A", "#FFFFFF", "#333740", "#B8BCC4"),
            lights = listOf("#FAD6E8", "#D5EBFF", "#CFF6E8", "#E3D7FA"),
            midTones = listOf("#FF3E9A", "#1E5BFF", "#00A86B", "#8A2BE2"),
            accents = listOf("#E4002B", "#0077FF", "#E0149A", "#00B8C8"),
            darks = listOf("#0F1F5C", "#7A0E4F", "#005C43", "#3A1470"),
        ),
    )

    private fun palette(
        neutrals: List<String>,
        lights: List<String>,
        midTones: List<String>,
        accents: List<String>,
        darks: List<String>,
    ): List<Int> = listOf(neutrals, lights, midTones, accents, darks).flatMap { category ->
        require(category.size == BEST_PER_CATEGORY)
        category.map { requireNotNull(parseHexColor(it)) { "Invalid hex $it" } }
    }
}
