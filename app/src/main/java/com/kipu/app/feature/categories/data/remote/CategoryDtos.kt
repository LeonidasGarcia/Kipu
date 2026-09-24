package com.kipu.app.feature.categories.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CategoryCatalogItemDto(
    val id: String,
    val name: String,
    @SerialName("icon_key") val iconKey: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    val origin: String,
    @SerialName("category_type") val categoryType: String = "GENERAL",
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("remote_revision") val remoteRevision: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class CategoryPresentationCatalogItemDto(
    @SerialName("category_id") val categoryId: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val icon: String,
    val color: String,
    @SerialName("remote_revision") val remoteRevision: Long,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class CreateCategoryRequestDto(
    @SerialName("operation_id") val operationId: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("category_type") val categoryType: String = "GENERAL",
    @SerialName("name") val name: String,
    @SerialName("icon") val icon: String,
    @SerialName("color") val color: String,
    @SerialName("payload_hash") val payloadHash: String,
)

@Serializable
internal data class CategoryRpcPayload<T>(
    @SerialName("p_payload") val payload: T,
)

@Serializable
data class UpdateCategoryPresentationRequestDto(
    @SerialName("operation_id") val operationId: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("name") val name: String,
    @SerialName("icon") val icon: String,
    @SerialName("color") val color: String,
    @SerialName("expected_revision") val expectedRevision: Long,
    @SerialName("payload_hash") val payloadHash: String,
)

@Serializable
data class SetCategoryActiveRequestDto(
    @SerialName("operation_id") val operationId: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("expected_revision") val expectedRevision: Long? = null,
    @SerialName("payload_hash") val payloadHash: String,
)

@Serializable
data class UpdateMovementClassificationRequestDto(
    @SerialName("operation_id") val operationId: String,
    @SerialName("movement_id") val movementId: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("merchant_id") val merchantId: String? = null,
    @SerialName("merchant_provisional_text") val merchantProvisionalText: String? = null,
    @SerialName("payload_hash") val payloadHash: String,
)

@Serializable
data class ResolveCategoryConflictRequestDto(
    @SerialName("operation_id") val operationId: String,
    @SerialName("conflict_id") val conflictId: String,
    @SerialName("chosen_version") val chosenVersion: String,
    @SerialName("payload_hash") val payloadHash: String,
)

@Serializable
data class MerchantCatalogItemDto(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("normalized_name") val normalizedName: String,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("version") val version: Long = 1L,
)

@Serializable
data class CategoryCommandResponseDto(
    @SerialName("success") val success: Boolean = true,
    @SerialName("status") val status: String? = null,
    @SerialName("error") val error: String? = null,
)
