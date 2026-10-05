package com.kipu.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.performScrollToIndex
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RetainedRootTabsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchingRetainsBothCompositionsAndOnlyExposesActivePage() {
        val selected = mutableStateOf(false)
        val mounts = intArrayOf(0, 0)
        val disposals = intArrayOf(0, 0)
        compose.setContent {
            RetainedRootTabs(selected) {
                repeat(2) { index ->
                    DisposableEffect(Unit) {
                        mounts[index]++
                        onDispose { disposals[index]++ }
                    }
                    Box(Modifier.fillMaxSize().testTag("page_$index")) { Text("Page $index") }
                }
            }
        }
        repeat(20) { index ->
            val active = index % 2
            compose.runOnIdle { selected.value = active == 1 }
            compose.onNodeWithTag("page_$active").assertIsDisplayed()
            compose.onNodeWithTag("page_${1 - active}").assertIsNotDisplayed()
        }
        compose.runOnIdle {
            assertEquals(listOf(1, 1), mounts.toList())
            assertEquals(listOf(0, 0), disposals.toList())
        }
    }

    @Test fun eachTabKeepsItsOwnScrollPosition() {
        val selected = mutableStateOf(false)
        compose.setContent {
            RetainedRootTabs(selected) {
                repeat(2) { page ->
                    val scroll = rememberLazyListState()
                    LazyColumn(Modifier.fillMaxSize().testTag("list_$page"), state = scroll) {
                        items(100) { row -> Text("$page:$row", Modifier.testTag("row_${page}_$row")) }
                    }
                }
            }
        }
        compose.onNodeWithTag("list_0").performScrollToIndex(40)
        compose.runOnIdle { selected.value = true }
        compose.onNodeWithTag("list_1").performScrollToIndex(60)
        compose.runOnIdle { selected.value = false }
        compose.onNodeWithTag("row_0_40").assertIsDisplayed()
        compose.runOnIdle { selected.value = true }
        compose.onNodeWithTag("row_1_60").assertIsDisplayed()
    }
}
