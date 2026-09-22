package com.kipu.app.feature.categories.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.model.CategoryConflict
import com.kipu.app.feature.categories.domain.model.CategoryConflictStatus
import com.kipu.app.feature.categories.domain.model.CategoryConflictType
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.ConflictId
import com.kipu.app.feature.categories.presentation.categories.ConflictCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CategoryPresentationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testUserId = UserId.generate()

    @Test
    fun displaysPresentationConflictCardWithChoices() {
        val conflict = CategoryConflict(
            id = ConflictId.generate(),
            categoryId = CategoryId.generate(),
            ownerId = testUserId,
            conflictType = CategoryConflictType.PRESENTATION,
            localVersion = "{\"name\":\"Alimentos Local\"}",
            remoteVersion = "{\"name\":\"Alimentos Remoto\"}",
            status = CategoryConflictStatus.OPEN,
        )

        var choseLocal = false
        var choseRemote = false

        composeTestRule.setContent {
            ConflictCard(
                conflict = conflict,
                onChooseLocal = { choseLocal = true },
                onChooseRemote = { choseRemote = true },
            )
        }

        composeTestRule.onNodeWithText("Conflicto de Aspecto Visual").assertIsDisplayed()
        composeTestRule.onNodeWithTag("choose_local_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("choose_remote_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("choose_local_button").performClick()
        assertTrue(choseLocal)

        composeTestRule.onNodeWithTag("choose_remote_button").performClick()
        assertTrue(choseRemote)
    }

    @Test
    fun displaysLifecycleConflictCard() {
        val conflict = CategoryConflict(
            id = ConflictId.generate(),
            categoryId = CategoryId.generate(),
            ownerId = testUserId,
            conflictType = CategoryConflictType.LIFECYCLE,
            localVersion = "{\"isActive\":true}",
            remoteVersion = "{\"isActive\":false}",
            status = CategoryConflictStatus.OPEN,
        )

        composeTestRule.setContent {
            ConflictCard(
                conflict = conflict,
                onChooseLocal = {},
                onChooseRemote = {},
            )
        }

        composeTestRule.onNodeWithText("Conflicto de Estado (Activa/Inactiva)").assertIsDisplayed()
    }
}
