package at.awit.shlinker.feature.shorturls

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
    private val loadedEmpty = PagingData.empty<ShortUrl>(
        sourceLoadStates = LoadStates(
            refresh = LoadState.NotLoading(true),
            prepend = LoadState.NotLoading(true),
            append = LoadState.NotLoading(true),
        ),
    )

    private fun screen(flow: Flow<PagingData<ShortUrl>>, search: String = "") {
        compose.setContent {
            ShlinkerTheme(dynamicColor = false) {
                ShortUrlsScreen(
                    urls = flow.collectAsLazyPagingItems(),
                    search = search,
                    serverName = "Testserver",
                    onSearchChange = { query = it },
                    onBack = { wentBack = true },
                    onCopy = { copied = it },
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
        compose.onNodeWithContentDescription("https://s.example/abc kopieren").performClick()
        assertEquals("https://s.example/abc", copied)
        compose.onNodeWithContentDescription("Zurück zur Serverübersicht").performClick()
        assertTrue(wentBack)
    }

    @Test fun emptyListAndSearchAreVisible() {
        screen(flowOf(loadedEmpty))
        assertText("Noch keine Kurz-URLs auf diesem Server.")
        compose.onNodeWithText("Kurz-URLs suchen").performTextInput("example")
        assertEquals("example", query)
    }

    @Test fun noSearchResultsHasDifferentMessage() {
        screen(flowOf(loadedEmpty), search = "missing")
        assertText("Keine passenden Kurz-URLs gefunden.")
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp-xxhdpi")
    fun englishTitleAndServerNameAreVisible() {
        screen(flowOf(loadedEmpty))
        assertText("Shlinks")
        assertText("Testserver")
        assertText("No short URLs on this server yet.")
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
        compose.onNodeWithContentDescription("Aktualisieren").performClick()
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
        assertText("Der API-Key ist ungültig oder hat nicht die nötigen Berechtigungen.")
        compose.onNodeWithText("Erneut versuchen").performClick()
        assertText("Noch keine Kurz-URLs auf diesem Server.")
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
