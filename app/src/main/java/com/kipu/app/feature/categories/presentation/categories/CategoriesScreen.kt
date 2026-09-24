package com.kipu.app.feature.categories.presentation.categories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.usecase.CategoryItem
import com.kipu.app.feature.categories.presentation.parseHexColor
import com.kipu.app.feature.categories.presentation.resolveCategoryIcon
import com.kipu.app.ui.theme.KipuMotionTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Map to track expanded state of each root category
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Categorías",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                    )
                },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openCreateDialog(parentId = null) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(56.dp)
                    .testTag("add_root_category_button"),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar categoría raíz")
            }
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
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Pestañas / Filter Chips: Gastos e Ingresos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val expenseCount = CategoryTab.EXPENSE.filterCategories(state.categories).count { it.category.isRoot }
                    val incomeCount = CategoryTab.INCOME.filterCategories(state.categories).count { it.category.isRoot }

                    FilterChip(
                        selected = state.selectedTab == CategoryTab.EXPENSE,
                        onClick = { viewModel.onTabSelected(CategoryTab.EXPENSE) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gastos")
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (state.selectedTab == CategoryTab.EXPENSE) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(
                                        text = "$expenseCount",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                        ),
                    )

                    FilterChip(
                        selected = state.selectedTab == CategoryTab.INCOME,
                        onClick = { viewModel.onTabSelected(CategoryTab.INCOME) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ingresos")
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (state.selectedTab == CategoryTab.INCOME) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Text(
                                        text = "$incomeCount",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(42.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quota banner if needed
                if (state.activeCustomRootsCount > 0) {
                    QuotaBanner(
                        activeCount = state.activeCustomRootsCount,
                        maxCount = state.maxCustomRoots,
                        isLimitReached = state.isFreeLimitReached,
                    )
                    if (state.activeCustomRootsCount > state.maxCustomRoots) {
                        TextButton(onClick = viewModel::openQuotaSelection) {
                            Text("Elegir categorías del plan Free")
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                val displayCategories = remember(state.categories, state.selectedTab) {
                    state.selectedTab.filterCategories(state.categories)
                }

                // Lista de Categorías
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(displayCategories, key = { it.category.id.value }) { rootItem ->
                        val isExpanded = expandedStates[rootItem.category.id.value] ?: true

                        CategoryRootCard(
                            item = rootItem,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedStates[rootItem.category.id.value] = !isExpanded
                            },
                            onEdit = { viewModel.openEditDialog(rootItem) },
                            onDelete = { viewModel.requestDeleteCategory(rootItem) },
                            onEditSubcategory = { subItem -> viewModel.openEditDialog(subItem) },
                            onDeleteSubcategory = { subItem -> viewModel.requestDeleteCategory(subItem) },
                            onAddSubcategory = { viewModel.openCreateDialog(rootItem.category.id) },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // Formulario Dialog (Screen 8)
    if (state.isCreateDialogOpen) {
        CategoryFormDialog(
            isEditing = state.isEditing,
            categoryType = state.createCategoryType,
            name = state.createCategoryName,
            icon = state.createCategoryIcon,
            color = state.createCategoryColor,
            parentId = state.createParentId,
            availableRoots = state.categories.filter {
                it.category.isRoot &&
                    (it.category.categoryType == state.createCategoryType ||
                        (state.createCategoryType != com.kipu.app.feature.categories.domain.model.CategoryType.GENERAL &&
                            it.category.categoryType == com.kipu.app.feature.categories.domain.model.CategoryType.GENERAL))
            },
            onNameChange = viewModel::onNameChanged,
            onIconChange = viewModel::onIconChanged,
            onColorChange = viewModel::onColorChanged,
            onParentIdChange = viewModel::onParentIdChanged,
            onDismiss = viewModel::closeCreateDialog,
            onConfirm = viewModel::submitCreateCategory,
        )
    }

    if (state.isQuotaSelectionOpen) {
        val selectableRoots = state.categories.filter {
            it.category.isRoot && it.category.isCustom && it.category.isActive
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissQuotaSelection,
            title = { Text("Elegir categorías para Plan Free") },
            text = {
                Column {
                    Text("Elige hasta ${state.maxCustomRoots}. Las demás conservarán su historial y quedarán bloqueadas por el plan; su estado activo no cambia.")
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(selectableRoots, key = { it.category.id.value }) { item ->
                            val checked = item.category.id in state.quotaSelectionDraft
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleQuotaSelection(item.category.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = checked, onCheckedChange = { viewModel.toggleQuotaSelection(item.category.id) })
                                Text(item.displayName)
                            }
                        }
                    }
                    Text("${state.quotaSelectionDraft.size} / ${state.maxCustomRoots} seleccionadas")
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::saveQuotaSelection) {
                    Text("Guardar selección")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissQuotaSelection) { Text("Cancelar") }
            },
        )
    }

    // Diálogo de Límite Superado
    if (state.showQuotaExceededDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissQuotaDialog,
            title = {
                Text(
                    text = "Límite de Categorías Superado",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Has alcanzado el límite de 5 categorías personalizadas en tu plan gratuito. Conserva el historial y elige cuáles quieres seguir usando o inactiva una para crear otra.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissQuotaDialog) {
                    Text("Aceptar", fontWeight = FontWeight.SemiBold)
                }
            },
        )
    }

    // Diálogo de Confirmación de Inactivación / Eliminación
    state.categoryToDelete?.let { itemToDelete ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDialog,
            title = {
                Text(
                    text = "Inactivar Categoría",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "¿Deseas inactivar la categoría \"${itemToDelete.displayName}\"? Ya no estará disponible para nuevos movimientos, pero se conservará su historial.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteCategory) {
                    Text("Inactivar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteDialog) {
                    Text("Cancelar")
                }
            },
        )
    }
}

@Composable
fun QuotaBanner(
    activeCount: Int,
    maxCount: Int,
    isLimitReached: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isLimitReached) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = if (isLimitReached) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Categorías personalizadas activas: $activeCount / $maxCount",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isLimitReached) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isLimitReached) {
                    Text(
                        text = "Límite del plan Gratuito alcanzado. Desactiva una categoría para activar otra.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryRootCard(
    item: CategoryItem,
    isExpanded: Boolean = true,
    onToggleExpand: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEditSubcategory: (CategoryItem) -> Unit = {},
    onDeleteSubcategory: (CategoryItem) -> Unit = {},
    onToggleActive: (() -> Unit)? = null,
    onAddSubcategory: (() -> Unit)? = null,
    onToggleSubcategoryActive: ((CategoryId, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isRootActive = item.category.isActive
    val cardAlpha = if (isRootActive) 1f else 0.5f
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(KipuMotionTokens.EnterMillis),
        label = "expand_rotation",
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(parseHexColor(item.color).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = resolveCategoryIcon(item.icon),
                        contentDescription = null,
                        tint = parseHexColor(item.color),
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f).clickable { onToggleExpand() }) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when (item.subcategories.size) {
                            0 -> "Sin subcategorías"
                            1 -> "1 subcategoría"
                            else -> "${item.subcategories.size} subcategorías"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar ${item.displayName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Inactivar ${item.displayName}",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Colapsar ${item.displayName}" else "Expandir ${item.displayName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(rotationAngle),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp, top = 4.dp),
            ) {
                Text(
                    text = if (item.category.isCustom) "Personalizada" else "Predeterminada",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.category.isCustom) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        item.category.isPlanLocked -> "Bloqueada por el plan Free"
                        isRootActive -> "Activa"
                        else -> "Inactiva · Bloquea nuevas asignaciones"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.category.isPlanLocked || !isRootActive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Subcategorías colapsables
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(KipuMotionTokens.EnterMillis)) + expandVertically(tween(KipuMotionTokens.EnterMillis)),
                exit = fadeOut(tween(KipuMotionTokens.ExitMillis)) + shrinkVertically(tween(KipuMotionTokens.ExitMillis)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.subcategories.forEach { subItem ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(item.color)),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = subItem.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (!isRootActive) {
                                    Text(
                                        text = "Inactiva por categoría padre",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onEditSubcategory(subItem) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Editar ${subItem.displayName}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = { onDeleteSubcategory(subItem) },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "Inactivar ${subItem.displayName}",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }

                    if (onAddSubcategory != null) {
                        TextButton(
                            onClick = onAddSubcategory,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Agregar subcategoría", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
