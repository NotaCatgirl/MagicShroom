package edu.csumb.magicshroom.ui.search

import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.api.CardPageDto
import edu.csumb.magicshroom.data.api.PageMetaDto
import edu.csumb.magicshroom.data.repository.ApiResult
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun typingDebouncesSearchAndLoadMoreAppendsResults() = runTest(dispatcher) {
        val requests = mutableListOf<Pair<String?, Int>>()
        val viewModel = SearchViewModel { name, page ->
            requests += name to page
            cardPage(
                number = page,
                totalPages = if (name == null) 1 else 2,
                card = card(id = page + 1L, name = if (page == 0) "Lightning Bolt" else "Thunderbolt"),
            )
        }

        runCurrent()
        assertEquals(listOf(null to 0), requests)

        viewModel.onQueryChange("bolt")
        advanceTimeBy(299)
        runCurrent()
        assertEquals(1, requests.size)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(null to 0, "bolt" to 0), requests)
        assertEquals(listOf("Lightning Bolt"), viewModel.uiState.value.cards.map { it.cardName })
        assertTrue(viewModel.uiState.value.hasMore)

        viewModel.loadMore()
        runCurrent()
        assertEquals(listOf(null to 0, "bolt" to 0, "bolt" to 1), requests)
        assertEquals(listOf("Lightning Bolt", "Thunderbolt"), viewModel.uiState.value.cards.map { it.cardName })
        assertFalse(viewModel.uiState.value.hasMore)
    }

    @Test
    fun failedLaterPageKeepsResultsAndCanBeRetried() = runTest(dispatcher) {
        var failPageOne = true
        val viewModel = SearchViewModel { _, page ->
            if (page == 1 && failPageOne) {
                failPageOne = false
                ApiResult.NetworkError(IOException("offline"))
            } else {
                cardPage(
                    number = page,
                    totalPages = 2,
                    card = card(id = page + 1L, name = if (page == 0) "Lightning Bolt" else "Thunderbolt"),
                )
            }
        }

        runCurrent()
        viewModel.loadMore()
        runCurrent()
        assertEquals(listOf("Lightning Bolt"), viewModel.uiState.value.cards.map { it.cardName })
        assertTrue(viewModel.uiState.value.loadMoreErrorMessage != null)

        viewModel.loadMore()
        runCurrent()
        assertEquals(listOf("Lightning Bolt", "Thunderbolt"), viewModel.uiState.value.cards.map { it.cardName })
        assertEquals(null, viewModel.uiState.value.loadMoreErrorMessage)
    }

    private fun card(id: Long, name: String) = CardDto(
        id = id,
        uuid = "00000000-0000-0000-0000-00000000000$id",
        cardName = name,
    )

    private fun cardPage(number: Int, totalPages: Int, card: CardDto) = ApiResult.Success(
        CardPageDto(
            content = listOf(card),
            page = PageMetaDto(size = 20, number = number, totalElements = totalPages.toLong(), totalPages = totalPages),
        ),
    )
}
