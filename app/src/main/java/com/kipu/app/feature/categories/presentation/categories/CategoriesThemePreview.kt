package com.kipu.app.feature.categories.presentation.categories

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kipu.app.core.finance.domain.model.UserId
import com.kipu.app.feature.categories.domain.model.Category
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryOrigin
import com.kipu.app.feature.categories.domain.model.CategoryPresentation
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.usecase.CategoryItem
import com.kipu.app.ui.theme.KipuTheme
import com.kipu.app.ui.theme.rememberKipuColors

@Composable
private fun CategoriesThemePreviewContent() {
    val colors = rememberKipuColors()
    val testUserId = UserId.generate()

    val subItem1 = CategoryItem(
        category = Category(
            id = CategoryId("streaming"),
            ownerId = testUserId,
            parentId = CategoryId("subs"),
            origin = CategoryOrigin.CUSTOM,
            isActive = true,
        ),
        presentation = CategoryPresentation(
            categoryId = CategoryId("streaming"),
            ownerId = testUserId,
            name = "Streaming",
            icon = "tv",
            color = "#8B5CF6",
        ),
    )

    val subItem2 = CategoryItem(
        category = Category(
            id = CategoryId("musica"),
            ownerId = testUserId,
            parentId = CategoryId("subs"),
            origin = CategoryOrigin.CUSTOM,
            isActive = true,
        ),
        presentation = CategoryPresentation(
            categoryId = CategoryId("musica"),
            ownerId = testUserId,
            name = "Música",
            icon = "music_note",
            color = "#EC4899",
        ),
    )

    val subItem3 = CategoryItem(
        category = Category(
            id = CategoryId("software"),
            ownerId = testUserId,
            parentId = CategoryId("subs"),
            origin = CategoryOrigin.CUSTOM,
            isActive = true,
        ),
        presentation = CategoryPresentation(
            categoryId = CategoryId("software"),
            ownerId = testUserId,
            name = "Software",
            icon = "devices",
            color = "#3B82F6",
        ),
    )

    val rootItemExpanded = CategoryItem(
        category = Category(
            id = CategoryId("subs"),
            ownerId = testUserId,
            parentId = null,
            origin = CategoryOrigin.CUSTOM,
            isActive = true,
            categoryType = CategoryType.EXPENSE,
        ),
        presentation = CategoryPresentation(
            categoryId = CategoryId("subs"),
            ownerId = testUserId,
            name = "Suscripciones",
            icon = "star",
            color = "#10B981",
        ),
        subcategories = listOf(subItem1, subItem2, subItem3),
    )

    val rootItemCollapsed = CategoryItem(
        category = Category(
            id = CategoryId("food"),
            ownerId = testUserId,
            parentId = null,
            origin = CategoryOrigin.SYSTEM,
            isActive = true,
            categoryType = CategoryType.EXPENSE,
        ),
        presentation = CategoryPresentation(
            categoryId = CategoryId("food"),
            ownerId = testUserId,
            name = "Alimentación",
            icon = "restaurant",
            color = "#F97316",
        ),
        subcategories = emptyList(),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Quota Card Preview
        QuotaBanner(
            activeCount = 2,
            maxCount = 5,
            isLimitReached = false,
        )

        // Expanded Card Preview
        CategoryRootCard(
            item = rootItemExpanded,
            isExpanded = true,
            onToggleExpand = {},
            onAddSubcategory = {},
        )

        // Collapsed Card Preview
        CategoryRootCard(
            item = rootItemCollapsed,
            isExpanded = false,
            onToggleExpand = {},
        )
    }
}

@Preview(name = "Categorías · Claro", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun CategoriesLightPreview() {
    KipuTheme(darkTheme = false) {
        CategoriesThemePreviewContent()
    }
}

@Preview(name = "Categorías · Oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun CategoriesDarkPreview() {
    KipuTheme(darkTheme = true) {
        CategoriesThemePreviewContent()
    }
}
