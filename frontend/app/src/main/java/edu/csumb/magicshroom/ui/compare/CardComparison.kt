package edu.csumb.magicshroom.ui.compare

import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.api.Color

/** How the base card's value compares with the other card's. */
enum class Verdict { BETTER, WORSE, EQUAL, NOT_COMPARABLE }

/** One table row: a stat's text on each card and the base card's verdict. */
data class StatRow(val label: String, val baseValue: String, val otherValue: String, val verdict: Verdict)

/** Everything the comparison screen shows for a base card against another card. */
data class CardComparison(
    val rows: List<StatRow>,
    val shared: List<String>,
    val uniqueToBase: List<String>,
    val uniqueToOther: List<String>,
) {
    /** Rows where the base card is better. */
    val betterCount: Int get() = rows.count { it.verdict == Verdict.BETTER }

    /** Rows where the base card is worse. */
    val worseCount: Int get() = rows.count { it.verdict == Verdict.WORSE }

    /** Rows where both cards are the same. */
    val equalCount: Int get() = rows.count { it.verdict == Verdict.EQUAL }

    /** Traits that only one of the two cards has. */
    val uniqueCount: Int get() = uniqueToBase.size + uniqueToOther.size
}

/**
 * Compares [other] with [base], always from [base]'s point of view: lower mana value and popularity
 * rank are better, higher power, toughness, and loyalty are better.
 */
fun compareCards(base: CardDto, other: CardDto): CardComparison {
    val sameColors = base.colorIdentity.toSet() == other.colorIdentity.toSet()
    val rows = buildList {
        add(numberRow("Mana value", base.convertedManaCost, other.convertedManaCost, ::formatNumber, higherIsBetter = false))
        textStatRow("Power", base.power, other.power)?.let(::add)
        textStatRow("Toughness", base.toughness, other.toughness)?.let(::add)
        textStatRow("Loyalty", base.loyalty, other.loyalty)?.let(::add)
        if (base.edhrecRank != null || other.edhrecRank != null) {
            add(numberRow("Popularity", base.edhrecRank?.toDouble(), other.edhrecRank?.toDouble(),
                { "#${it.toLong()}" }, higherIsBetter = false, missing = "Unranked"))
        }
        if (sameColors) add(StatRow("Colors", colorCodes(base), colorCodes(other), Verdict.EQUAL))
    }
    val baseTraits = traits(base, includeColors = !sameColors)
    val otherTraits = traits(other, includeColors = !sameColors)
    return CardComparison(
        rows = rows,
        shared = baseTraits.filter(otherTraits::contains),
        uniqueToBase = baseTraits.filterNot(otherTraits::contains),
        uniqueToOther = otherTraits.filterNot(baseTraits::contains),
    )
}

/** Keyword abilities from lines of [text] that list only keywords, e.g. "Flying, vigilance". */
internal fun keywords(text: String?): List<String> = text.orEmpty().lines()
    .map { it.replace(REMINDER_TEXT, "").trim() }
    .filter(String::isNotEmpty)
    .mapNotNull { line ->
        val found = line.split(',').map(String::trim).filter(String::isNotEmpty).map(::keywordOf)
        found.takeIf { it.all { keyword -> keyword != null } }?.filterNotNull()
    }
    .flatten()
    .distinct()

/** The keyword [part] is, or null: "Ward {2}" is Ward and "Protection from red" is Protection. */
private fun keywordOf(part: String): String? = KEYWORDS.firstOrNull { keyword ->
    part.startsWith(keyword, ignoreCase = true) && part.substring(keyword.length).let { rest ->
        rest.isEmpty() ||
            (keyword in FROM_KEYWORDS && rest.startsWith(" from ", ignoreCase = true)) ||
            (keyword == "Ward" && (rest.startsWith(" {") || rest.startsWith("—")))
    }
}

/** A row for text stats such as `4` or `*`; null when neither card has the stat. */
private fun textStatRow(label: String, base: String?, other: String?): StatRow? {
    if (base == null && other == null) return null
    val verdict = verdict(base?.toDoubleOrNull(), other?.toDoubleOrNull(), higherIsBetter = true)
    return StatRow(label, base ?: MISSING, other ?: MISSING, verdict)
}

/** A row for numeric stats, shown with [format], or [missing] when a card lacks it. */
private fun numberRow(
    label: String,
    base: Double?,
    other: Double?,
    format: (Double) -> String,
    higherIsBetter: Boolean,
    missing: String = MISSING,
) = StatRow(label, base?.let(format) ?: missing, other?.let(format) ?: missing, verdict(base, other, higherIsBetter))

/** BETTER or WORSE by [higherIsBetter], EQUAL for equal numbers, NOT_COMPARABLE if either is missing. */
private fun verdict(base: Double?, other: Double?, higherIsBetter: Boolean): Verdict = when {
    base == null || other == null -> Verdict.NOT_COMPARABLE
    base == other -> Verdict.EQUAL
    (base > other) == higherIsBetter -> Verdict.BETTER
    else -> Verdict.WORSE
}

/** Keywords, subtypes, supertypes, main types, and (when asked) color names, in that order. */
private fun traits(card: CardDto, includeColors: Boolean): List<String> =
    (keywords(card.text) + card.subtypes + card.supertypes + card.types +
        if (includeColors) colorNames(card) else emptyList()).distinct()

/** Color identity letters for the table, e.g. `G` or `R W`; `C` when colorless. */
private fun colorCodes(card: CardDto): String =
    card.colorIdentity.takeIf { it.isNotEmpty() }?.joinToString(" ") { it.name } ?: "C"

/** Color identity names for trait lists; "Colorless" when there are none. */
private fun colorNames(card: CardDto): List<String> =
    card.colorIdentity.map(COLOR_NAMES::getValue).ifEmpty { listOf(COLOR_NAMES.getValue(Color.C)) }

/** `5` for whole numbers, `1.5` otherwise. */
private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private const val MISSING = "—"
private val REMINDER_TEXT = Regex("""\s*\([^)]*\)""")
private val FROM_KEYWORDS = setOf("Protection", "Hexproof")
private val KEYWORDS = listOf(
    "Flying", "First strike", "Double strike", "Deathtouch", "Defender", "Flash", "Haste", "Hexproof",
    "Indestructible", "Lifelink", "Menace", "Prowess", "Reach", "Trample", "Vigilance", "Ward", "Protection",
)
private val COLOR_NAMES = mapOf(
    Color.W to "White", Color.U to "Blue", Color.B to "Black", Color.R to "Red", Color.G to "Green", Color.C to "Colorless",
)
