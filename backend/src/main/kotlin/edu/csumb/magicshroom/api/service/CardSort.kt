package edu.csumb.magicshroom.api.service

import edu.csumb.magicshroom.api.error.BadRequestException
import edu.csumb.magicshroom.api.model.Card

/** The `sort` values GET /api/v1/cards accepts. Ties are broken by id so paging is stable. */
enum class CardSort(val param: String, val comparator: Comparator<Card>) {
	NAME_ASC("cardName,asc", compareBy(String.CASE_INSENSITIVE_ORDER, Card::cardName).thenBy(Card::id)),
	NAME_DESC("cardName,desc", compareByDescending(String.CASE_INSENSITIVE_ORDER, Card::cardName).thenBy(Card::id)),
	MANA_VALUE_ASC("convertedManaCost,asc", compareBy(nullsLast(), Card::convertedManaCost).thenBy(Card::id)),
	MANA_VALUE_DESC("convertedManaCost,desc", compareByDescending(nullsFirst(), Card::convertedManaCost).thenBy(Card::id));

	companion object {
		/** The sort for a `sort` query value; 400 if it is not one the contract lists. */
		fun fromParam(value: String): CardSort = entries.firstOrNull { it.param == value }
			?: throw BadRequestException("sort must be one of ${entries.joinToString { it.param }}.")
	}
}
