package com.kipu.app.feature.categories.presentation.categories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import com.kipu.app.ui.theme.rememberKipuColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = rememberKipuColors()
    val snackbarHostState = remember { SnackbarHostState() }

    // Map to track expanded state of each root category
    val expandedStates = remember { mutableStateMapOf<String, Boolean>() }

    // Interactive search & filter state
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    var currentFilter by remember { mutableStateOf(CategoryFilterOption.ALL) }

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
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = "Buscar categoría...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.inkSecondary,
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = colors.surfaceVariant,
                                unfocusedContainerColor = colors.surfaceVariant,
                                focusedBorderColor = colors.primary,
                                unfocusedBorderColor = Color.Transparent,
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Limpiar búsqueda",
                                            tint = colors.inkSecondary,
                                        )
                                    }
                                }
                            },
                        )
                    } else {
                        Text(
                            text = "Categorías",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = colors.inkPrimary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearchActive) {
                                isSearchActive = false
                                searchQuery = ""
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = colors.inkPrimary,
                        )
                    }
                },
                actions = {
                    if (!isSearchActive) {
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar categorías",
                                tint = colors.inkPrimary,
                            )
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { filterMenuExpanded = true },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Filtrar categorías",
                                tint = if (currentFilter != CategoryFilterOption.ALL) colors.primary else colors.inkPrimary,
                            )
                        }
                        DropdownMenu(
                            expanded = filterMenuExpanded,
                            onDismissRequest = { filterMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Todas las categorías") },
                                onClick = {
                                    currentFilter = CategoryFilterOption.ALL
                                    filterMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Solo personalizadas") },
                                onClick = {
                                    currentFilter = CategoryFilterOption.ONLY_CUSTOM
                                    filterMenuExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Solo predeterminadas") },
                                onClick = {
                                    currentFilter = CategoryFilterOption.ONLY_DEFAULT
                                    filterMenuExpanded = false
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openCreateDialog(parentId = null) },
                containerColor = colors.primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .size(56.dp)
                    .testTag("add_root_category_button"),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Agregar categoría raíz",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = colors.background,
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Segmented Tabs: Gastos vs Ingresos
                val expenseCount = CategoryTab.EXPENSE.filterCategories(state.categories).count { it.category.isRoot }
                val incomeCount = CategoryTab.INCOME.filterCategories(state.categories).count { it.category.isRoot }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = colors.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Gastos Tab
                        val isExpenseSelected = state.selectedTab == CategoryTab.EXPENSE
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isExpenseSelected) colors.primary else Color.Transparent)
                                .clickable { viewModel.onTabSelected(CategoryTab.EXPENSE) }
                                .semantics {
                                    role = Role.Tab
                                    selected = isExpenseSelected
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = if (isExpenseSelected) Color.White else colors.inkSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Gastos",
                                    fontSize = 14.sp,
                                    fontWeight = if (isExpenseSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isExpenseSelected) Color.White else colors.inkSecondary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (isExpenseSelected) Color.White.copy(alpha = 0.25f) else colors.surfaceVariant,
                                ) {
                                    Text(
                                        text = "$expenseCount",
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isExpenseSelected) Color.White else colors.inkSecondary,
                                    )
                                }
                            }
                        }

                        // Ingresos Tab
                        val isIncomeSelected = state.selectedTab == CategoryTab.INCOME
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isIncomeSelected) colors.primary else Color.Transparent)
                                .clickable { viewModel.onTabSelected(CategoryTab.INCOME) }
                                .semantics {
                                    role = Role.Tab
                                    selected = isIncomeSelected
                                }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = if (isIncomeSelected) Color.White else colors.inkSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ingresos",
                                    fontSize = 14.sp,
                                    fontWeight = if (isIncomeSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isIncomeSelected) Color.White else colors.inkSecondary,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = if (isIncomeSelected) Color.White.copy(alpha = 0.25f) else colors.surfaceVariant,
                                ) {
                                    Text(
                                        text = "$incomeCount",
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isIncomeSelected) Color.White else colors.inkSecondary,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quota Card
                if (state.activeCustomRootsCount > 0 || state.isFreeLimitReached) {
                    QuotaBanner(
                        activeCount = state.activeCustomRootsCount,
                        maxCount = state.maxCustomRoots,
                        isLimitReached = state.isFreeLimitReached,
                        onOpenQuotaSelection = if (state.activeCustomRootsCount > state.maxCustomRoots) {
                            viewModel::openQuotaSelection
                        } else null,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Filter categories based on tab, search query, and filter menu
                val displayCategories = remember(
                    state.categories,
                    state.selectedTab,
                    searchQuery,
                    currentFilter,
                ) {
                    val tabFiltered = state.selectedTab.filterCategories(state.categories)
                    val optionFiltered = when (currentFilter) {
                        CategoryFilterOption.ALL -> tabFiltered
                        CategoryFilterOption.ONLY_CUSTOM -> tabFiltered.filter { it.category.isCustom }
                        CategoryFilterOption.ONLY_DEFAULT -> tabFiltered.filter { !it.category.isCustom }
                    }
                    if (searchQuery.isBlank()) {
                        optionFiltered
                    } else {
                        val q = searchQuery.trim().lowercase()
                        optionFiltered.mapNotNull { rootItem ->
                            val matchesRoot = rootItem.displayName.lowercase().contains(q)
                            val matchingSubcategories = rootItem.subcategories.filter { sub ->
                                sub.displayName.lowercase().contains(q)
                            }
                            if (matchesRoot || matchingSubcategories.isNotEmpty()) {
                                rootItem.copy(subcategories = if (matchesRoot && matchingSubcategories.isEmpty()) rootItem.subcategories else matchingSubcategories)
                            } else null
                        }
                    }
                }

                // List of Categories
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        Spacer(modifier = Modifier.height(88.dp))
                    }
                }
            }
        }
    }

    // Modern Form Dialog (Category / Subcategory creation & editing)
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

    // Plan Free Quota Selection Modal
    if (state.isQuotaSelectionOpen) {
        val selectableRoots = state.categories.filter {
            it.category.isRoot && it.category.isCustom && it.category.isActive
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissQuotaSelection,
            title = {
                Text(
                    text = "Elegir categorías para Plan Free",
                    fontWeight = FontWeight.Bold,
                    color = colors.inkPrimary,
                )
            },
            text = {
                Column {
                    Text(
                        text = "Elige hasta 5 categorías personalizadas en total. Las demás conservarán su historial y quedarán bloqueadas por el plan; su estado activo no cambia.",
                        color = colors.inkSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(selectableRoots, key = { it.category.id.value }) { item ->
                            val checked = item.category.id in state.quotaSelectionDraft
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.toggleQuotaSelection(item.category.id) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { viewModel.toggleQuotaSelection(item.category.id) },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(parseHexColor(item.color).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = resolveCategoryIcon(item.icon),
                                        contentDescription = null,
                                        tint = parseHexColor(item.color),
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.displayName,
                                    color = colors.inkPrimary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${state.quotaSelectionDraft.size} / ${state.maxCustomRoots} seleccionadas",
                        fontWeight = FontWeight.Bold,
                        color = colors.primaryText,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::saveQuotaSelection) {
                    Text("Guardar selección", fontWeight = FontWeight.Bold, color = colors.primaryText)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissQuotaSelection) {
                    Text("Cancelar", color = colors.inkSecondary)
                }
            },
            containerColor = colors.surface,
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
                    color = colors.inkPrimary,
                )
            },
            text = {
                Text(
                    text = "Has alcanzado el límite de 5 categorías personalizadas en tu plan gratuito. Conserva el historial y elige cuáles quieres seguir usando o inactiva una para crear otra.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissQuotaDialog) {
                    Text("Aceptar", fontWeight = FontWeight.Bold, color = colors.primaryText)
                }
            },
            containerColor = colors.surface,
        )
    }

    // Diálogo de Confirmación de Inactivación
    state.categoryToDelete?.let { itemToDelete ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDialog,
            title = {
                Text(
                    text = "Inactivar Categoría",
                    fontWeight = FontWeight.Bold,
                    color = colors.inkPrimary,
                )
            },
            text = {
                Text(
                    text = "¿Deseas inactivar la categoría \"${itemToDelete.displayName}\"? Ya no estará disponible para nuevos movimientos, pero se conservará su historial.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteCategory) {
                    Text("Inactivar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteDialog) {
                    Text("Cancelar", color = colors.inkSecondary)
                }
            },
            containerColor = colors.surface,
        )
    }
}

private enum class CategoryFilterOption {
    ALL,
    ONLY_CUSTOM,
    ONLY_DEFAULT
}

@Composable
fun QuotaBanner(
    activeCount: Int,
    maxCount: Int,
    isLimitReached: Boolean,
    modifier: Modifier = Modifier,
    onOpenQuotaSelection: (() -> Unit)? = null,
) {
    val colors = rememberKipuColors()

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, if (isLimitReached) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Squircle Gear Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.primary.copy(alpha = if (colors.isDark) 0.25f else 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = colors.primaryText,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Categorías personalizadas (Plan Free)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkPrimary,
                    )
                    Text(
                        text = "Activas en tu plan: $activeCount de $maxCount",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.inkSecondary,
                    )
                }

                if (onOpenQuotaSelection != null) {
                    IconButton(
                        onClick = onOpenQuotaSelection,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Elegir categorías del plan Free",
                            tint = colors.inkSecondary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5-Segment Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val totalBars = maxOf(maxCount, 5)
                for (i in 0 until totalBars) {
                    val isFilled = i < activeCount
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isFilled) colors.primary
                                else if (colors.isDark) colors.surfaceVariant
                                else Color(0xFFE2E8F0)
                            ),
                    )
                }
            }

            if (isLimitReached) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Límite del plan Gratuito alcanzado. Desactiva una categoría para activar otra.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
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
    val colors = rememberKipuColors()
    val isRootActive = item.category.isActive
    val cardAlpha = if (isRootActive) 1f else 0.55f
    val reducedMotion = rememberReducedMotionEnabled()
    val rotationAngle = animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else KipuMotionTokens.SegmentMillis),
        label = "expand_rotation",
    )
    var showRootMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.border.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Category Squircle Icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(parseHexColor(item.color).copy(alpha = if (colors.isDark) 0.30f else 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = resolveCategoryIcon(item.icon),
                        contentDescription = null,
                        tint = parseHexColor(item.color),
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Title, Subtitle, and Badge Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleExpand() },
                ) {
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (item.subcategories.size) {
                                0 -> "Sin subcategorías"
                                1 -> "1 subcategoría"
                                else -> "${item.subcategories.size} subcategorías"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.inkSecondary,
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // The quota label makes the SYSTEM/CUSTOM distinction explicit in the list.
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (item.category.isCustom) colors.positiveContainer else colors.surfaceVariant,
                        ) {
                            Text(
                                text = if (item.category.isCustom) {
                                    "Personalizada · consume cupo Free"
                                } else {
                                    "Predeterminada · no consume cupo Free"
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (item.category.isCustom) colors.onPositiveContainer else colors.inkSecondary,
                            )
                        }
                    }

                    // Status line if inactive or locked
                    if (item.category.isPlanLocked) {
                        Text(
                            text = "Bloqueada por el plan Free",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )
                    } else if (!isRootActive) {
                        Text(
                            text = "Inactiva · Bloquea nuevas asignaciones",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                        )
                    } else {
                        // Preserves semantic text for accessibility and automated tests
                        Text(
                            text = "Activa",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.positive,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // Trailing 3-dots Options Menu
                Box {
                    IconButton(
                        onClick = { showRootMenu = true },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Editar ${item.displayName}",
                            tint = colors.inkSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    DropdownMenu(
                        expanded = showRootMenu,
                        onDismissRequest = { showRootMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Editar") },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = colors.inkPrimary)
                            },
                            onClick = {
                                showRootMenu = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Inactivar", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                showRootMenu = false
                                onDelete()
                            },
                        )
                    }
                }

                // Chevron Expand/Collapse
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Colapsar ${item.displayName}" else "Expandir ${item.displayName}",
                        tint = colors.inkSecondary,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = rotationAngle.value },
                    )
                }
            }

            // Subcategories Collapsible List
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(if (reducedMotion) 0 else KipuMotionTokens.SubtreeEnterMillis)) +
                    expandVertically(tween(if (reducedMotion) 0 else KipuMotionTokens.SubtreeEnterMillis)),
                exit = fadeOut(tween(if (reducedMotion) 0 else KipuMotionTokens.QuickMillis)) +
                    shrinkVertically(tween(if (reducedMotion) 0 else KipuMotionTokens.QuickMillis)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item.subcategories.forEach { subItem ->
                        var showSubMenu by remember { mutableStateOf(false) }

                        Surface(
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                // Subcategory Icon
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(parseHexColor(subItem.color).copy(alpha = if (colors.isDark) 0.30f else 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = resolveCategoryIcon(subItem.icon),
                                        contentDescription = null,
                                        tint = parseHexColor(subItem.color),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = subItem.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.inkPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )

                                        // Subcategory 3-dots Menu aligned with label
                                        Box {
                                            IconButton(
                                                onClick = { showSubMenu = true },
                                                modifier = Modifier.size(36.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "Editar ${subItem.displayName}",
                                                    tint = colors.inkSecondary,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showSubMenu,
                                                onDismissRequest = { showSubMenu = false },
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Editar") },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.Edit, contentDescription = null, tint = colors.inkPrimary)
                                                    },
                                                    onClick = {
                                                        showSubMenu = false
                                                        onEditSubcategory(subItem)
                                                    },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Inactivar", color = MaterialTheme.colorScheme.error) },
                                                    leadingIcon = {
                                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                                    },
                                                    onClick = {
                                                        showSubMenu = false
                                                        onDeleteSubcategory(subItem)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                    if (!isRootActive) {
                                        Text(
                                            text = "Inactiva por categoría padre",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontSize = 11.sp,
                                        )
                                    } else {
                                        Text(
                                            text = "Subcategoría",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.inkSecondary,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Button: Agregar subcategoría
                    if (onAddSubcategory != null) {
                        TextButton(
                            onClick = onAddSubcategory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Agregar subcategoría",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = colors.primaryText,
                            )
                        }
                    }
                }
            }
        }
    }
}
