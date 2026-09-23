package com.kipu.app.feature.categories.domain.model

import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.core.finance.domain.model.UserId
import java.util.UUID

@JvmInline
value class CategoryId(val value: String) {
    init {
        require(value.isNotBlank()) { "CategoryId cannot be blank" }
    }
    companion object {
        fun generate(): CategoryId = CategoryId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class MerchantId(val value: String) {
    init {
        require(value.isNotBlank()) { "MerchantId cannot be blank" }
    }
    companion object {
        fun generate(): MerchantId = MerchantId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class ConflictId(val value: String) {
    init {
        require(value.isNotBlank()) { "ConflictId cannot be blank" }
    }
    companion object {
        fun generate(): ConflictId = ConflictId(UUID.randomUUID().toString())
    }
}

enum class CategoryOrigin {
    SYSTEM,
    CUSTOM
}

data class Category(
    val id: CategoryId,
    val ownerId: UserId?,
    val parentId: CategoryId?,
    val origin: CategoryOrigin,
    val isActive: Boolean,
    val revision: Long = 1L,
    val isPlanLocked: Boolean = false,
) {
    val isRoot: Boolean get() = parentId == null
    val isSubcategory: Boolean get() = parentId != null
    val isSystem: Boolean get() = origin == CategoryOrigin.SYSTEM
    val isCustom: Boolean get() = origin == CategoryOrigin.CUSTOM
}

data class CategoryPresentation(
    val categoryId: CategoryId,
    val ownerId: UserId,
    val name: String,
    val icon: String,
    val color: String,
    val revision: Long = 1L
)

data class MerchantCatalogEntry(
    val id: MerchantId,
    val name: String,
    val normalizedName: String,
    val isActive: Boolean = true
)

data class MovementClassification(
    val movementId: MovementId,
    val categoryId: CategoryId? = null,
    val merchantId: MerchantId? = null,
    val merchantProvisionalText: String? = null
) {
    init {
        if (merchantId != null) {
            require(merchantProvisionalText == null) {
                "Provisional text cannot coexist with a catalog merchant"
            }
        }
    }
}

enum class CategoryConflictType {
    PRESENTATION,
    LIFECYCLE
}

enum class CategoryConflictStatus {
    OPEN,
    RESOLVED
}

data class CategoryConflict(
    val id: ConflictId,
    val categoryId: CategoryId,
    val ownerId: UserId,
    val conflictType: CategoryConflictType,
    val localVersion: String,
    val remoteVersion: String,
    val status: CategoryConflictStatus = CategoryConflictStatus.OPEN,
    val resolutionOperationId: String? = null
)
