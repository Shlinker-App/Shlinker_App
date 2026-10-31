package at.awit.shlinker.feature.shorturls

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.collectAsLazyPagingItems
import at.awit.shlinker.core.model.ShortUrl
import at.awit.shlinker.core.model.ShortUrlSort
import at.awit.shlinker.core.network.ConnectionError
import at.awit.shlinker.core.network.ShortUrlRequestException
import at.awit.shlinker.core.ui.theme.ShlinkerTheme
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "de-w411dp-h891dp-xxhdpi", shadows = [ShadowPagingUiDispatcher::class])
class ShortUrlsScreenTest {
    @get:Rule val compose = createComposeRule()

    private var copied: String? = null
    private var query: String? = null
    private var wentBack = false
    private var creating = false
    private var selectedSort: ShortUrlSort? = null
    private val loadedEmpty = PagingData.empty<ShortUrl>(
        sourceLoadStates = LoadStates(
            refresh = LoadState.NotLoading(true),
            prepend = LoadState.NotLoading(true),
            append = LoadState.NotLoading(true),
        ),
    )

    private fun screen(flow: Flow<PagingData<ShortUrl>>, search: String = "", sortError: Boolean = false) {
        compose.setContent {
            var sort by remember { mutableStateOf(ShortUrlSort.NEWEST) }
            var error by remember { mutableStateOf(sortError) }
            ShlinkerTheme(dynamicColor = false) {
                ShortUrlsScreen(
                    urls = flow.collectAsLazyPagingItems(),
                    search = search,
                    serverName = "Testserver",
                    onSearchChange = { query = it },
                    onBack = { wentBack = true },
                    onCreate = { creating = true },
                    onCopy = { copied = it },
                    sort = sort,
                    onSortChange = {
                        selectedSort = it
                        sort = it
                    },
                    sortError = error,
                    onDismissSortError = { error = false },
                )
            }
        }
    }

    private fun assertText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText(text).assertIsDisplayed()
    }

    @Test fun createActionWorksWithAnEmptyOrFilteredList() {
        screen(flowOf(loadedEmpty), search = "missing")
        compose.onNodeWithText("Create shlink", useUnmergedTree = true).performClick()
        assertTrue(creating)
        assertText("No matching shlinks found.")
        assertText("Newest first")
    }

    @Test fun listShowsUrlsAndTagsAndCopiesShortUrl() {
        screen(
            flowOf(
                PagingData.from(
                    listOf(
                        ShortUrl(
                            "abc",
                            "https://s.example/abc",
                            "https://example.org/article",
                            "Article",
                            listOf("work"),
                        ),
                    ),
                ),
            ),
        )
        assertText("Article")
        assertText("Shlinks")
        assertText("Testserver")
        assertText("https://example.org/article")
        assertText("work")
        compose.onNodeWithContentDescription("Copy https://s.example/abc").performClick()
        assertEquals("https://s.example/abc", copied)
        compose.onNodeWithContentDescription("Back to server overview").performClick()
        assertTrue(wentBack)
    }

    @Test fun sortMenuSupportsAllFourOrdersAndShowsCurrentChoice() {
        screen(flowOf(loadedEmpty))
        assertText("Newest first")
        for ((label, sort) in listOf(
            "Oldest first" to ShortUrlSort.OLDEST,
            "Most visited first" to ShortUrlSort.MOST_VISITED,
            "Least visited first" to ShortUrlSort.LEAST_VISITED,
            "Newest first" to ShortUrlSort.NEWEST,
        )) {
            compose.onNodeWithContentDescription("Sort shlinks").performClick()
            compose.onNodeWithText(label).performClick()
            assertText(label)
            assertEquals(sort, selectedSort)
        }
    }

    @Test fun sortSaveErrorIsVisibleAndDismissible() {
        screen(flowOf(loadedEmpty), sortError = true)
        val message = "Could not save the sort order. Your previous order is still selected. Please try again."
        assertText(message)
        compose.onNodeWithText("Dismiss").performClick()
        compose.onNodeWithText(message).assertDoesNotExist()
        assertText("Newest first")
    }

    @Test fun emptyListAndSearchAreVisible() {
        screen(flowOf(loadedEmpty))
        assertText("No shlinks on this server yet.")
        compose.onNodeWithText("Search shlinks").performTextInput("example")
        assertEquals("example", query)
    }

    @Test fun noSearchResultsHasDifferentMessage() {
        screen(flowOf(loadedEmpty), search = "missing")
        assertText("No matching shlinks found.")
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun englishTitleAndServerNameAreVisible() {
        screen(flowOf(loadedEmpty))
        assertText("Shlinks")
        assertText("Testserver")
        assertText("No shlinks on this server yet.")
        compose.onNodeWithContentDescription("Back to server overview").assertIsDisplayed()
    }

    @Test fun buttonAndPullGestureLoadFreshData() {
        var generation = 0
        val flow = Pager(PagingConfig(30)) {
            val title = "Version ${++generation}"
            object : PagingSource<Int, ShortUrl>() {
                override fun getRefreshKey(state: PagingState<Int, ShortUrl>): Int? = null

                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ShortUrl> = LoadResult.Page(
                    listOf(ShortUrl("abc", "https://s.example/abc", "https://example.org", title, emptyList())),
                    null,
                    null,
                )
            }
        }.flow
        screen(flow)
        assertText("Version 1")
        compose.onNodeWithContentDescription("Refresh").performClick()
        assertText("Version 2")
        compose.onNode(hasScrollAction() and hasAnyDescendant(hasText("Version 2")))
            .performTouchInput { swipeDown() }
        assertText("Version 3")
        assertEquals(3, generation)
    }

    @Test fun loadErrorOffersWorkingRetry() {
        var attempts = 0
        val flow = Pager(PagingConfig(30)) {
            object : PagingSource<Int, ShortUrl>() {
                override fun getRefreshKey(state: PagingState<Int, ShortUrl>): Int? = null

                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ShortUrl> {
                    attempts++
                    return if (attempts == 1) {
                        LoadResult.Error(ShortUrlRequestException(ConnectionError.InvalidApiKey))
                    } else {
                        LoadResult.Page(emptyList(), null, null)
                    }
                }
            }
        }.flow
        screen(flow)
        assertText("The API key is invalid or does not have the required permissions.")
        compose.onNodeWithText("Try again").performClick()
        assertText("No shlinks on this server yet.")
        assertEquals(2, attempts)
    }
}

// Paging caches a Choreographer dispatcher across Robolectric's per-test looper resets.
@Implements(className = "androidx.paging.compose.UiDispatcher_androidKt", isInAndroidSdk = false)
class ShadowPagingUiDispatcher {
    companion object {
        @JvmStatic
        @Implementation
        fun getUiDispatcher(): CoroutineContext = Dispatchers.Main.immediate
    }
}
