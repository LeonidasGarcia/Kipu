package com.kipu.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import com.kipu.app.ui.theme.KipuTheme

class RetainedRootTabsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun switchingRetainsAllRootCompositionsAndOnlyExposesActivePage() {
        val selected = mutableIntStateOf(0)
        val mounts = intArrayOf(0, 0, 0)
        val disposals = intArrayOf(0, 0, 0)
        compose.setContent {
            RetainedRootTabs(selectedTabIndex = selected) {
                repeat(3) { index ->
                    val isActive = selected.intValue == index
                    DisposableEffect(Unit) {
                        mounts[index]++
                        onDispose { disposals[index]++ }
                    }
                    Box(Modifier.fillMaxSize().then(if (isActive) Modifier else Modifier.clearAndSetSemantics { })) {
                        Box(Modifier.fillMaxSize().testTag("page_$index")) { Text("Page $index") }
                    }
                }
            }
        }
        repeat(30) { index ->
            val active = index % 3
            compose.runOnIdle { selected.intValue = active }
            compose.onNodeWithTag("page_$active").assertIsDisplayed()
            (0..2).filter { it != active }.forEach { inactive ->
                compose.onAllNodesWithTag("page_$inactive").assertCountEquals(0)
            }
        }
        compose.runOnIdle {
            assertEquals(listOf(1, 1, 1), mounts.toList())
            assertEquals(listOf(0, 0, 0), disposals.toList())
        }
    }

    @Test fun eachTabKeepsItsOwnScrollPosition() {
        val selected = mutableIntStateOf(0)
        compose.setContent {
            RetainedRootTabs(selectedTabIndex = selected) {
                repeat(3) { page ->
                    val scroll = rememberLazyListState()
                    LazyColumn(Modifier.fillMaxSize().testTag("list_$page"), state = scroll) {
                        items(100) { row -> Text("$page:$row", Modifier.testTag("row_${page}_$row")) }
                    }
                }
            }
        }
        val targetRows = listOf(40, 60, 80)
        targetRows.forEachIndexed { page, row ->
            compose.runOnIdle { selected.intValue = page }
            compose.waitForIdle()
            compose.onNodeWithTag("list_$page").performScrollToIndex(row)
        }
        targetRows.forEachIndexed { page, row ->
            compose.runOnIdle { selected.intValue = page }
            compose.waitForIdle()
            compose.onNodeWithTag("row_${page}_$row").assertIsDisplayed()
        }
    }

    @Test fun rootTabsMoveSelectionAndKeepTheRegisterActionClickable() {
        val route = mutableStateOf(ACCOUNTS_DASHBOARD_ROUTE)
        var registerClicks = 0
        compose.setContent {
            KipuTheme {
                KipuNavigationBar(
                    currentRoute = route.value,
                    onNavigateToDinero = { route.value = ACCOUNTS_DASHBOARD_ROUTE },
                    onNavigateToMovimientos = { route.value = MOVEMENTS_HISTORY_ROUTE },
                    onNavigateToDeudas = { route.value = DEBT_LIST_ROUTE },
                    onRegisterClick = { registerClicks++ },
                )
            }
        }

        compose.onNodeWithTag("nav_item_dinero").assertIsSelected()
        compose.onNodeWithTag("nav_item_movimientos").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("nav_item_movimientos").assertIsSelected()
        compose.onNodeWithTag("nav_selected_indicator").assertIsDisplayed()

        compose.onNodeWithTag("nav_item_deudas").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("nav_item_deudas").assertIsSelected()
        compose.onNodeWithTag("btn_root_register").performClick()
        compose.runOnIdle { assertEquals(1, registerClicks) }
    }

    @Test fun movementsLabelFitsInTheSelectedTabAtCompactWidthAndLargerText() {
        compose.setContent {
            KipuTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                    KipuNavigationBar(
                        currentRoute = MOVEMENTS_HISTORY_ROUTE,
                        onNavigateToDinero = {},
                        onNavigateToMovimientos = {},
                        onNavigateToDeudas = {},
                        onRegisterClick = {},
                        modifier = Modifier.width(360.dp),
                    )
                }
            }
        }

        compose.onNodeWithTag("nav_label_movimientos", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
