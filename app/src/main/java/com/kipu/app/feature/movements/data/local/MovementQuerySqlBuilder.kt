package com.kipu.app.feature.movements.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.kipu.app.feature.movements.domain.model.MovementHistoryQuery

object MovementQuerySqlBuilder {
    fun build(userId: String, query: MovementHistoryQuery): SupportSQLiteQuery {
        val sql = StringBuilder("SELECT * FROM transactions WHERE user_id = ?")
        val args = mutableListOf<Any>(userId)

        // Date range
        if (query.fromInclusive != null) {
            sql.append(" AND occurred_at >= ?")
            args.add(query.fromInclusive)
        }
        if (query.toExclusive != null) {
            sql.append(" AND occurred_at < ?")
            args.add(query.toExclusive)
        }

        // Types
        if (query.types.isNotEmpty()) {
            val placeholders = query.types.joinToString(",") { "?" }
            sql.append(" AND type IN ($placeholders)")
            query.types.forEach { args.add(it.name) }
        }

        // Search text across merchant provisional, note, category name, or catalog merchant name
        val text = query.queryText?.trim()
        if (!text.isNullOrBlank()) {
            val pattern = "%$text%"
            sql.append(
                " AND (" +
                    "merchant_provisional_text LIKE ? " +
                    "OR note LIKE ? " +
                    "OR category_id IN (SELECT category_id FROM category_presentations WHERE user_id = ? AND name LIKE ?) " +
                    "OR merchant_id IN (SELECT id FROM merchant_catalog_cache WHERE name LIKE ?)" +
                    ")"
            )
            args.add(pattern)
            args.add(pattern)
            args.add(userId)
            args.add(pattern)
            args.add(pattern)
        }

        // Advanced criteria
        val adv = query.advancedCriteria
        if (adv != null) {
            if (adv.accountIds.isNotEmpty()) {
                val placeholders = adv.accountIds.joinToString(",") { "?" }
                sql.append(" AND (source_account_id IN ($placeholders) OR destination_account_id IN ($placeholders))")
                adv.accountIds.forEach { args.add(it) }
                adv.accountIds.forEach { args.add(it) }
            }
            if (adv.cardIds.isNotEmpty()) {
                val placeholders = adv.cardIds.joinToString(",") { "?" }
                sql.append(" AND card_id IN ($placeholders)")
                adv.cardIds.forEach { args.add(it) }
            }
            if (adv.categoryIds.isNotEmpty()) {
                val placeholders = adv.categoryIds.joinToString(",") { "?" }
                sql.append(" AND category_id IN ($placeholders)")
                adv.categoryIds.forEach { args.add(it) }
            }
            if (adv.merchantIds.isNotEmpty()) {
                val placeholders = adv.merchantIds.joinToString(",") { "?" }
                sql.append(" AND (merchant_id IN ($placeholders) OR merchant_provisional_text IN ($placeholders))")
                adv.merchantIds.forEach { args.add(it) }
                adv.merchantIds.forEach { args.add(it) }
            }
            if (adv.minAmountMinor != null) {
                sql.append(" AND amount_minor >= ?")
                args.add(adv.minAmountMinor)
            }
            if (adv.maxAmountMinor != null) {
                sql.append(" AND amount_minor <= ?")
                args.add(adv.maxAmountMinor)
            }
            if (!adv.currency.isNullOrBlank()) {
                sql.append(" AND currency_code = ?")
                args.add(adv.currency)
            }
            if (adv.financialStates.isNotEmpty()) {
                val placeholders = adv.financialStates.joinToString(",") { "?" }
                sql.append(" AND status IN ($placeholders)")
                adv.financialStates.forEach { args.add(it.name) }
            }
            if (adv.syncStatuses.isNotEmpty()) {
                val placeholders = adv.syncStatuses.joinToString(",") { "?" }
                sql.append(" AND sync_status IN ($placeholders)")
                adv.syncStatuses.forEach { args.add(it.name) }
            }
        }

        // Keyset Pagination Cursor: (occurred_at < cursor.occurredAt) OR (occurred_at == cursor.occurredAt AND id < cursor.transactionId)
        if (query.cursor != null) {
            sql.append(" AND (occurred_at < ? OR (occurred_at = ? AND id < ?))")
            args.add(query.cursor.occurredAt)
            args.add(query.cursor.occurredAt)
            args.add(query.cursor.transactionId)
        }

        // Order and limit (limit + 1 to detect hasMore)
        val fetchLimit = query.limit + 1
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ?")
        args.add(fetchLimit)

        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }
}
