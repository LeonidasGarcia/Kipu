package com.kipu.app.feature.categories.domain.model

import com.kipu.app.core.finance.domain.model.MovementId
import com.kipu.app.core.finance.domain.model.UserId
import java.time.Instant
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
value class MerchantAliasRuleId(val value: String) {
    init {
        require(value.isNotBlank()) { "MerchantAliasRuleId cannot be blank" }
    }

    companion object {
        fun generate(): MerchantAliasRuleId = MerchantAliasRuleId(UUID.randomUUID().toString())
    }
}

/** Exact source text from a bank signal; callers must keep normalization in a separate value. */
@JvmInline
value class SourceMerchantText(val value: String)

data class MerchantAliasRule(
    val id: MerchantAliasRuleId,
    val ownerId: UserId,
    val normalizedPattern: String,
    val merchantId: MerchantId,
    val revision: Long = 1L,
    val deletedAt: Instant? = null,
    val syncState: MerchantRuleSyncState = MerchantRuleSyncState.PENDING,
) {
    init {
        require(normalizedPattern.isNotBlank()) { "Alias pattern cannot be blank" }
        require(revision > 0L) { "Alias revision must be positive" }
    }
}

/** One future-only merchant choice per owner and canonical merchant. */
data class MerchantCategoryPreference(
    val ownerId: UserId,
    val merchantId: MerchantId,
    val categoryId: CategoryId,
    val revision: Long = 1L,
    val deletedAt: Instant? = null,
    val id: String = UUID.randomUUID().toString(),
    val syncState: MerchantRuleSyncState = MerchantRuleSyncState.PENDING,
) {
    init {
        require(revision > 0L) { "Preference revision must be positive" }
    }
}

enum class MerchantRuleSyncState {
    PENDING,
    SYNCED,
    CONFLICT,
    FAILED,
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

enum class CategoryType {
    EXPENSE,
    INCOME,
    GENERAL;

    companion object {
        /** Unknown or pre-typing values remain visible in both category tabs. */
        fun fromStorage(value: String?): CategoryType =
            entries.firstOrNull { it.name == value } ?: GENERAL
    }
}

data class Category(
    val id: CategoryId,
    val ownerId: UserId?,
    val parentId: CategoryId?,
    val origin: CategoryOrigin,
    val isActive: Boolean,
    val revision: Long = 1L,
    val isPlanLocked: Boolean = false,
    val categoryType: CategoryType = CategoryType.GENERAL,
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
    val isActive: Boolean = true,
    val defaultCategoryId: CategoryId? = null,
    val priority: String = "B",
    val logoKey: String? = null,
    val brandColor: String? = null,
)

data class MerchantCategoryFilter(
    val categoryId: CategoryId,
    val name: String,
)

data class MovementClassification(
    val movementId: MovementId,
    val categoryId: CategoryId? = null,
    val merchantId: MerchantId? = null,
    val merchantProvisionalText: String? = null,
    val merchantRawText: String? = null,
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
