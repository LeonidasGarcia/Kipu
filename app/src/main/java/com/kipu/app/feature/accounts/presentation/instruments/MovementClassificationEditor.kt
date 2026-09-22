package com.kipu.app.feature.accounts.presentation.instruments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.feature.categories.domain.usecase.CategoryItem
import com.kipu.app.feature.categories.presentation.components.MerchantPicker

private fun parseCategoryColor(hex: String?): Color {
    if (hex == null) return Color(0xFF3F51B5)
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFF3F51B5)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovementClassificationEditor(
    viewModel: MovementClassificationEditorViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var isCategoryPickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.isSavedSuccessfully) {
        if (state.isSavedSuccessfully) {
            onNavigateBack()
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Clasificación de Movimiento", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Section 1: Category Selection
                Text(
                    text = "CATEGORÍA",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                if (state.selectedCategoryItem != null) {
                    SelectedCategoryCard(
                        categoryItem = state.selectedCategoryItem!!,
                        onChangeCategory = { isCategoryPickerOpen = true },
                        onClearCategory = viewModel::clearCategory,
                    )
                } else {
                    OutlinedButton(
                        onClick = { isCategoryPickerOpen = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("select_category_button"),
                    ) {
                        Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Asignar categoría")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Section 2: Merchant Selection
                Text(
                    text = "COMERCIO O SERVICIO",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                MerchantPicker(
                    state = state.merchantPickerState,
                    onQueryChange = viewModel::onMerchantQueryChange,
                    onSelectMerchant = viewModel::selectMerchant,
                    onSetProvisionalText = viewModel::setProvisionalText,
                    onClearSelection = viewModel::clearMerchantSelection,
                )

                Spacer(modifier = Modifier.weight(1f))

                // Save button
                Button(
                    onClick = viewModel::saveClassification,
                    enabled = !state.isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_classification_button"),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Guardar Clasificación", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (isCategoryPickerOpen) {
        CategoryPickerDialog(
            categories = state.availableCategories,
            selectedCategoryId = state.selectedCategoryId,
            onSelectCategory = {
                viewModel.selectCategory(it)
                isCategoryPickerOpen = false
            },
            onDismiss = { isCategoryPickerOpen = false },
        )
    }
}

@Composable
fun SelectedCategoryCard(
    categoryItem: CategoryItem,
    onChangeCategory: () -> Unit,
    onClearCategory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(parseCategoryColor(categoryItem.color)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (categoryItem.category.isRoot) Icons.Default.Folder else Icons.Default.Label,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = categoryItem.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (categoryItem.category.isRoot) "Categoría principal" else "Subcategoría",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            TextButton(
                onClick = onChangeCategory,
                modifier = Modifier.height(48.dp),
            ) {
                Text("Cambiar")
            }
            IconButton(
                onClick = onClearCategory,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("clear_category_button"),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Quitar categoría")
            }
        }
    }
}

@Composable
fun CategoryPickerDialog(
    categories: List<CategoryItem>,
    selectedCategoryId: com.kipu.app.feature.categories.domain.model.CategoryId?,
    onSelectCategory: (com.kipu.app.feature.categories.domain.model.CategoryId) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar Categoría") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.filter { it.category.isActive }.forEach { root ->
                    item(key = root.category.id.value) {
                        CategoryPickerRow(
                            item = root,
                            isSelected = root.category.id == selectedCategoryId,
                            isSubcategory = false,
                            onClick = { onSelectCategory(root.category.id) },
                        )
                    }

                    items(root.subcategories.filter { it.category.isActive }, key = { it.category.id.value }) { sub ->
                        CategoryPickerRow(
                            item = sub,
                            isSelected = sub.category.id == selectedCategoryId,
                            isSubcategory = true,
                            onClick = { onSelectCategory(sub.category.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Cerrar")
            }
        },
    )
}

@Composable
fun CategoryPickerRow(
    item: CategoryItem,
    isSelected: Boolean,
    isSubcategory: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = if (isSubcategory) 20.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(parseCategoryColor(item.color)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isSubcategory) Icons.Default.Label else Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Seleccionada",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
