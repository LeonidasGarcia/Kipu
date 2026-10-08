package com.kipu.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kipu.app.feature.accounts.data.local.AccountDao
import com.kipu.app.feature.accounts.data.local.AccountEntity
import com.kipu.app.feature.accounts.data.local.CardDao
import com.kipu.app.feature.accounts.data.local.CardEntity
import com.kipu.app.feature.accounts.data.local.CreditDao
import com.kipu.app.feature.accounts.data.local.CreditInstallmentEntity
import com.kipu.app.feature.accounts.data.local.CreditPaymentAllocationEntity
import com.kipu.app.feature.accounts.data.local.FinancialMovementDao
import com.kipu.app.feature.accounts.data.local.FinancialMovementEntity
import com.kipu.app.feature.accounts.data.local.InstrumentSyncDao
import com.kipu.app.feature.accounts.data.local.InstrumentSyncOutboxEntity
import com.kipu.app.feature.categories.data.local.CategoryConflictEntity
import com.kipu.app.feature.categories.data.local.CategoryDao
import com.kipu.app.feature.categories.data.local.CategoryEntity
import com.kipu.app.feature.categories.data.local.CategoryPresentationEntity
import com.kipu.app.feature.categories.data.local.CategorySyncOutboxEntity
import com.kipu.app.feature.debts.data.local.DebtDao
import com.kipu.app.feature.debts.data.local.DebtEntity
import com.kipu.app.feature.debts.data.local.DebtEventEntity
import com.kipu.app.feature.debts.data.local.DebtInstallmentEntity
import com.kipu.app.feature.debts.data.local.DebtOutboxDao
import com.kipu.app.feature.debts.data.local.DebtOutboxEntity
import com.kipu.app.feature.debts.data.local.DebtSyncCheckpointEntity
import com.kipu.app.feature.categories.data.local.MerchantCatalogDao
import com.kipu.app.feature.categories.data.local.MerchantCatalogEntity
import com.kipu.app.feature.categories.data.local.MerchantAliasRuleEntity
import com.kipu.app.feature.categories.data.local.MerchantCategoryPreferenceEntity
import com.kipu.app.feature.categories.data.local.MerchantRulesDao
import com.kipu.app.feature.movements.data.local.BalanceProjectionEntity
import com.kipu.app.feature.movements.data.local.LedgerEntryEntity
import com.kipu.app.feature.movements.data.local.LocalCommandReceiptEntity
import com.kipu.app.feature.movements.data.local.MovementConflictProposalEntity
import com.kipu.app.feature.movements.data.local.MovementDao
import com.kipu.app.feature.movements.data.local.MovementLedgerAliasEntity
import com.kipu.app.feature.movements.data.local.MovementLedgerEffectEntity
import com.kipu.app.feature.movements.data.local.MovementOfficialRevisionEntity
import com.kipu.app.feature.movements.data.local.MovementOutboxEntity
import com.kipu.app.feature.movements.data.local.MovementSyncCheckpointEntity
import com.kipu.app.feature.movements.data.local.TransactionRevisionEntity
import com.kipu.app.feature.movements.data.local.TransactionEntity
import com.kipu.app.feature.notifications.data.local.AppNotificationDao
import com.kipu.app.feature.notifications.data.local.AppNotificationEntity
import com.kipu.app.feature.notifications.data.local.NotificationSyncOutboxDao
import com.kipu.app.feature.notifications.data.local.NotificationSyncOutboxEntity
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheEntity
import com.kipu.app.feature.plans.data.local.FeatureAccessCacheDao
import com.kipu.app.feature.plans.data.local.PlanPreferencesDao
import com.kipu.app.feature.plans.data.local.PlanPreferencesEntity
import com.kipu.app.feature.plans.data.local.PlanQuotaSelectionDao
import com.kipu.app.feature.plans.data.local.PlanQuotaSelectionEntity
import com.kipu.app.feature.plans.data.local.PlanQuotaSelectionItemEntity
import com.kipu.app.feature.plans.data.local.PlanSelectionSyncStateEntity
import com.kipu.app.feature.plans.data.local.SyncOutboxEntity
import com.kipu.app.feature.settings.data.local.AccountSourceConsentEntity
import com.kipu.app.feature.settings.data.local.DeviceAccountSettingsEntity
import com.kipu.app.feature.settings.data.local.InstallationPermissionStateEntity
import com.kipu.app.feature.settings.data.local.ProfilePreferenceOutboxEntity
import com.kipu.app.feature.settings.data.local.UserProfileCacheEntity

@Database(
    entities = [
        PlanPreferencesEntity::class,
        PlanSelectionSyncStateEntity::class,
        PlanQuotaSelectionEntity::class,
        PlanQuotaSelectionItemEntity::class,
        SyncOutboxEntity::class,
        FeatureAccessCacheEntity::class,
        UserProfileCacheEntity::class,
        ProfilePreferenceOutboxEntity::class,
        DeviceAccountSettingsEntity::class,
        InstallationPermissionStateEntity::class,
        AccountSourceConsentEntity::class,
        AccountEntity::class,
        CardEntity::class,
        FinancialMovementEntity::class,
        InstrumentSyncOutboxEntity::class,
        CategoryEntity::class,
        CategoryPresentationEntity::class,
        MerchantCatalogEntity::class,
        MerchantAliasRuleEntity::class,
        MerchantCategoryPreferenceEntity::class,
        CategoryConflictEntity::class,
        CategorySyncOutboxEntity::class,
        TransactionEntity::class,
        DebtEntity::class,
        DebtInstallmentEntity::class,
        DebtEventEntity::class,
        DebtOutboxEntity::class,
        DebtSyncCheckpointEntity::class,
        TransactionRevisionEntity::class,
        MovementOfficialRevisionEntity::class,
        MovementLedgerEffectEntity::class,
        MovementLedgerAliasEntity::class,
        MovementConflictProposalEntity::class,

        LedgerEntryEntity::class,
        LocalCommandReceiptEntity::class,
        MovementOutboxEntity::class,
        BalanceProjectionEntity::class,
        MovementSyncCheckpointEntity::class,
        CreditInstallmentEntity::class,
        CreditPaymentAllocationEntity::class,
        AppNotificationEntity::class,
        NotificationSyncOutboxEntity::class,
    ],
    version = 20,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class KipuDatabase : RoomDatabase() {
    abstract fun planPreferencesDao(): PlanPreferencesDao
    abstract fun featureAccessCacheDao(): FeatureAccessCacheDao
    abstract fun planQuotaSelectionDao(): PlanQuotaSelectionDao
    abstract fun profilePreferencesDao(): com.kipu.app.feature.settings.data.local.ProfilePreferencesDao
    abstract fun permissionConsentDao(): com.kipu.app.feature.settings.data.local.PermissionConsentDao
    abstract fun deviceAccountSettingsDao(): com.kipu.app.feature.settings.data.local.DeviceAccountSettingsDao
    abstract fun accountDao(): AccountDao
    abstract fun cardDao(): CardDao
    abstract fun financialMovementDao(): FinancialMovementDao
    abstract fun instrumentSyncDao(): InstrumentSyncDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantCatalogDao(): MerchantCatalogDao
    abstract fun merchantRulesDao(): MerchantRulesDao
    abstract fun movementDao(): MovementDao
    abstract fun debtDao(): DebtDao
    abstract fun debtOutboxDao(): DebtOutboxDao
    abstract fun creditDao(): CreditDao
    abstract fun appNotificationDao(): AppNotificationDao
    abstract fun notificationSyncOutboxDao(): NotificationSyncOutboxDao
}
