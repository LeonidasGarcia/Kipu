package com.kipu.app.feature.categories.presentation.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kipu.app.feature.categories.domain.model.CategoryId
import com.kipu.app.feature.categories.domain.model.CategoryType
import com.kipu.app.feature.categories.domain.usecase.CategoryItem
import com.kipu.app.feature.categories.presentation.parseHexColor
import com.kipu.app.feature.categories.presentation.resolveCategoryIcon
import com.kipu.app.feature.movements.presentation.CategoryOption
import com.kipu.app.feature.movements.presentation.CustomPastelColorDialog
import com.kipu.app.feature.movements.presentation.IconCatalogDialog
import com.kipu.app.feature.movements.presentation.ParentCategoryChooserDialog
import com.kipu.app.feature.movements.presentation.PastelColorUtils
import com.kipu.app.feature.movements.presentation.PastelDesignTokens
import com.kipu.app.feature.movements.presentation.PastelIconBadge
import com.kipu.app.ui.theme.rememberKipuColors

// Exact 6 quick icons + 7th item is the "+" button, matching Image 1
private val QUICK_ICONS = listOf(
    "tv", "music_note", "directions_car", "content_cut", "receipt", "shopping_cart"
)

// Exact 6 quick colors + 7th item is the palette button, matching Image 1
private val QUICK_COLORS = listOf(
    "#06B6D4", "#3B82F6", "#8B5CF6", "#F97316", "#F43F5E", "#10B981"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFormDialog(
    isEditing: Boolean,
    categoryType: CategoryType = CategoryType.GENERAL,
    name: String,
    icon: String,
    color: String,
    parentId: CategoryId?,
    availableRoots: List<CategoryItem>,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onParentIdChange: (CategoryId?) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberKipuColors()
    val categoryOptions = remember(availableRoots) {
        availableRoots.map { item ->
            CategoryOption(
                id = item.category.id.value,
                name = item.displayName,
                icon = item.icon,
                color = item.color,
                parentCategoryId = item.category.parentId?.value,
                categoryType = item.category.categoryType,
            )
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = colors.surface,
        modifier = modifier,
    ) {
        CategoryFormContent(
            isEditing = isEditing,
            categoryType = categoryType,
            name = name,
            icon = icon,
            color = color,
            parentId = parentId,
            availableRoots = categoryOptions,
            onNameChange = onNameChange,
            onIconChange = onIconChange,
            onColorChange = onColorChange,
            onParentIdChange = onParentIdChange,
            onBack = onDismiss,
            onDismiss = onDismiss,
            onConfirm = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
        )
    }
}

/**
 * Canonical unified category & subcategory form content shared across all screens.
 * Uses exact 44.dp items with SpaceBetween arrangement to guarantee all 7 elements
 * fit on any screen size without horizontal scrolling or right-edge clipping.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryFormContent(
    isEditing: Boolean = false,
    categoryType: CategoryType = CategoryType.GENERAL,
    name: String,
    icon: String,
    color: String,
    parentId: CategoryId?,
    availableRoots: List<CategoryOption>,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onParentIdChange: (CategoryId?) -> Unit,
    rememberFrequentMerchant: Boolean = false,
    onRememberFrequentMerchantChange: ((Boolean) -> Unit)? = null,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    isCreating: Boolean = false,
    showBack: Boolean = false,
    selectOnSave: Boolean = false,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = rememberKipuColors()
    val isSubcategory = parentId != null
    var showParentChooserDialog by remember { mutableStateOf(false) }
    var showIconCatalogDialog by remember { mutableStateOf(false) }
    var showColorModalDialog by remember { mutableStateOf(false) }

    val selectedRoot = remember(parentId, availableRoots) {
        availableRoots.find { it.id == parentId?.value }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        // Header with Back, Title, and Close
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (showBack) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("category_form_back_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = colors.inkPrimary,
                )
            }
            }

            Text(
                text = when {
                    isEditing && isSubcategory -> "Editar subcategoría"
                    isEditing -> "Editar categoría"
                    isSubcategory -> "Nueva subcategoría"
                    else -> "Nueva categoría"
                },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.inkPrimary,
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("category_form_close_button"),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = colors.inkSecondary,
                )
            }
        }

        // Subtitle
        Text(
            text = if (isSubcategory) {
                "Crea y personaliza una subcategoría para clasificar mejor tus gastos"
            } else {
                "Crea y personaliza una categoría para organizar tus finanzas"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkSecondary,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        // Scrollable Content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. TIPO DE CATEGORÍA
            if (isSubcategory) {
                Column {
                    Text(
                        text = "CATEGORÍA PRINCIPAL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkSecondary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                            .background(colors.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val parentColorHex = selectedRoot?.color ?: "#8B5CF6"
                        val parentColor = parseHexColor(parentColorHex)

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(parentColor.copy(alpha = if (colors.isDark) 0.35f else 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = resolveCategoryIcon(selectedRoot?.icon ?: "tv"),
                                contentDescription = null,
                                tint = parentColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedRoot?.name ?: "Sin categoría",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = colors.inkPrimary,
                            )
                            Text(
                                text = "Categoría padre vinculada",
                                fontSize = 12.sp,
                                color = colors.inkSecondary,
                            )
                        }

                        if (!isEditing && availableRoots.isNotEmpty()) {
                            TextButton(
                                onClick = { showParentChooserDialog = true },
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .testTag("category_form_change_parent_button"),
                            ) {
                                Text(
                                    text = "Cambiar >",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            } else {
                // CATEGORÍA RAÍZ
                Column {
                    Text(
                        text = "TIPO DE CATEGORÍA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkSecondary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                            .background(colors.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = colors.primaryText,
                                modifier = Modifier.size(24.dp),
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (categoryType) {
                                    CategoryType.EXPENSE -> "Gastos"
                                    CategoryType.INCOME -> "Ingresos"
                                    CategoryType.GENERAL -> "General"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = colors.inkPrimary,
                            )
                            Text(
                                text = "Categoría Principal",
                                fontSize = 12.sp,
                                color = colors.inkSecondary,
                            )
                        }

                        if (!isEditing && availableRoots.isNotEmpty()) {
                            TextButton(
                                onClick = { showParentChooserDialog = true },
                                modifier = Modifier
                                    .heightIn(min = 44.dp)
                                    .testTag("category_form_change_parent_button"),
                            ) {
                                Text(
                                    text = "Vincular a padre >",
                                    fontWeight = FontWeight.Bold,
                                    color = colors.primaryText,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }

            // 2. NOMBRE DE LA SUBCATEGORÍA / CATEGORÍA
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (isSubcategory) "NOMBRE DE LA SUBCATEGORÍA" else "NOMBRE DE LA CATEGORÍA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkSecondary,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = "(${name.length}/30)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkSecondary,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) onNameChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("category_name_input"),
                    placeholder = {
                        Text(
                            text = if (isSubcategory) "Ej. Disney+, Spotify, Farmacia..." else "Ej. Entretenimiento, Salud...",
                            color = colors.inkSecondary.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                        )
                    },
                    trailingIcon = {
                        if (name.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Válido",
                                tint = colors.positive,
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surface,
                        unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.5f),
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.border,
                    ),
                )
            }

            // 3. ELIGE UN ICONO (Exactamente 6 iconos + 7mo botón "+ Más" distribuidos uniformemente)
            Column {
                Text(
                    text = "ELIGE UN ICONO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QUICK_ICONS.forEach { iconKey ->
                        val isSelected = icon.equals(iconKey, ignoreCase = true)
                        val iconVector = resolveCategoryIcon(iconKey)

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) colors.primary else colors.border,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .background(if (isSelected) colors.primary.copy(alpha = 0.14f) else colors.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { onIconChange(iconKey) }
                                .semantics { selected = isSelected }
                                .testTag("quick_icon_$iconKey"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = iconKey,
                                tint = if (isSelected) colors.primary else colors.inkPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    // 7mo elemento: botón "+" de Más iconos
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = colors.border,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .background(colors.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { showIconCatalogDialog = true }
                            .testTag("more_icons_button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Más iconos",
                            modifier = Modifier.size(22.dp),
                            tint = colors.primaryText,
                        )
                    }
                }
            }

            // 4. COLOR DE CATEGORÍA (Exactamente 6 colores + 7mo botón Paleta distribuidos uniformemente)
            Column {
                Text(
                    text = "COLOR DE CATEGORÍA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkSecondary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QUICK_COLORS.forEach { hex ->
                        val isSelected = color.equals(hex, ignoreCase = true)
                        val chipColor = parseHexColor(hex)

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(chipColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) colors.inkPrimary else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable { onColorChange(hex) }
                                .semantics { selected = isSelected }
                                .testTag("quick_color_$hex"),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Surface(
                                    shape = CircleShape,
                                    color = colors.surface.copy(alpha = 0.85f),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Seleccionado",
                                        tint = colors.inkPrimary,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .padding(2.dp),
                                    )
                                }
                            }
                        }
                    }

                    // 7mo elemento: botón de Paleta HSV avanzada
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .border(1.dp, colors.border, CircleShape)
                            .background(colors.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { showColorModalDialog = true }
                            .testTag("custom_color_picker_button"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Selector de color personalizado",
                            tint = colors.primaryText,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // 5. SWITCH: RECORDAR COMERCIO FRECUENTE (Solo si es subcategoría Y el callback existe)
            if (isSubcategory && onRememberFrequentMerchantChange != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                        .background(colors.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Recordar comercio frecuente",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colors.inkPrimary,
                        )
                        Text(
                            text = "Auto-completa la subcategoría en futuros gastos con este nombre",
                            fontSize = 12.sp,
                            color = colors.inkSecondary,
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Switch(
                        checked = rememberFrequentMerchant,
                        onCheckedChange = onRememberFrequentMerchantChange,
                        modifier = Modifier.testTag("new_subcategory_frequent_merchant_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colors.primary,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = colors.dragHandle,
                        ),
                    )
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Sticky CTA Button
        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
            color = colors.surface,
            tonalElevation = 6.dp,
        ) {
            Button(
                onClick = onConfirm,
                enabled = name.isNotBlank() && name.length <= 30 && !isCreating,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .padding(vertical = 10.dp)
                    .testTag("save_category_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    disabledContainerColor = colors.border,
                    disabledContentColor = colors.inkSecondary,
                ),
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = if (selectOnSave) "Guardar y seleccionar" else if (isSubcategory) "Guardar subcategoría" else "Guardar categoría",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }

    // Modal: Parent Chooser Dialog
    if (showParentChooserDialog && availableRoots.isNotEmpty()) {
        ParentCategoryChooserDialog(
            categories = availableRoots,
            currentParentId = parentId?.value ?: "",
            onSelectParent = { selectedId ->
                onParentIdChange(if (selectedId.isBlank()) null else CategoryId(selectedId))
                showParentChooserDialog = false
            },
            onDismiss = { showParentChooserDialog = false },
        )
    }

    // Modal: Icon Catalog Pop-up
    if (showIconCatalogDialog) {
        IconCatalogDialog(
            initialIconId = icon,
            onConfirmIcon = { selectedIcon ->
                onIconChange(selectedIcon)
                showIconCatalogDialog = false
            },
            onDismiss = { showIconCatalogDialog = false },
        )
    }

    // Modal: Custom Pastel HSV Color Dialog
    if (showColorModalDialog) {
        CustomPastelColorDialog(
            initialColorHex = color,
            parentName = name.ifBlank { "Categoría" },
            onApplyColor = { selectedColor ->
                onColorChange(selectedColor)
                showColorModalDialog = false
            },
            onDismiss = { showColorModalDialog = false },
        )
    }
}
