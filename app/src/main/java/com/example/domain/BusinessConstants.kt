package com.example.domain

object BusinessConstants {
    // Permanent Super Admin / Owner Firebase Authentication UID & Gmail
    const val SUPER_ADMIN_UID = "yRuPuAgCe2V2Y8avgK9U8n4sTgj1"
    const val OWNER_EMAIL = "ashrafmahmudsany2@gmail.com"

    const val APP_NAME_BN = "Business Manager Pro"

    // 11 Separate Financial Account IDs
    const val ACC_CASH = "cash"
    const val ACC_BKASH_1 = "bkash_1"
    const val ACC_BKASH_2 = "bkash_2"
    const val ACC_BKASH_3 = "bkash_3"
    const val ACC_BKASH_4 = "bkash_4"
    const val ACC_BKASH_5 = "bkash_5"
    const val ACC_NAGAD = "nagad"
    const val ACC_ROCKET = "rocket"
    const val ACC_UPAY = "upay"
    const val ACC_UCB_BANK = "ucb_bank"
    const val ACC_EASY_RECHARGE = "easy_recharge"

    // 3 Partner IDs
    const val PARTNER_SAMAD = "samad_bhai"
    const val PARTNER_JAHIR = "jahir_mama"
    const val PARTNER_ARIFUL = "ariful_islam"

    data class AccountSeed(
        val accountId: String,
        val name: String,
        val group: AccountGroup,
        val sortOrder: Long,
    )

    val ALL_ACCOUNTS: List<AccountSeed> = listOf(
        AccountSeed(ACC_CASH, "Cash", AccountGroup.CASH, 1L),
        AccountSeed(ACC_BKASH_1, "bKash-1 — 0198", AccountGroup.BKASH, 2L),
        AccountSeed(ACC_BKASH_2, "bKash-2 — 4557", AccountGroup.BKASH, 3L),
        AccountSeed(ACC_BKASH_3, "bKash-3 — 9927", AccountGroup.BKASH, 4L),
        AccountSeed(ACC_BKASH_4, "bKash-4 — 3094", AccountGroup.BKASH, 5L),
        AccountSeed(ACC_BKASH_5, "bKash-5 — 2452", AccountGroup.BKASH, 6L),
        AccountSeed(ACC_NAGAD, "Nagad", AccountGroup.MFS, 7L),
        AccountSeed(ACC_ROCKET, "Rocket", AccountGroup.MFS, 8L),
        AccountSeed(ACC_UPAY, "Upay", AccountGroup.MFS, 9L),
        AccountSeed(ACC_UCB_BANK, "UCB Bank", AccountGroup.BANK, 10L),
        AccountSeed(ACC_EASY_RECHARGE, "Easy Recharge", AccountGroup.RECHARGE, 11L),
    )

    val BKASH_ACCOUNT_IDS = setOf(
        ACC_BKASH_1,
        ACC_BKASH_2,
        ACC_BKASH_3,
        ACC_BKASH_4,
        ACC_BKASH_5,
    )

    fun isBkashAccount(accountId: String?): Boolean = accountId in BKASH_ACCOUNT_IDS

    fun accountDisplayName(accountId: String?): String =
        ALL_ACCOUNTS.firstOrNull { it.accountId == accountId }?.name ?: (accountId ?: "অজানা")

    data class PartnerSeed(
        val partnerId: String,
        val name: String,
        val defaultRatePerThousandPaisa: Long,
        val bkashOnlyCommission: Boolean,
    )

    val ALL_PARTNERS: List<PartnerSeed> = listOf(
        PartnerSeed(PARTNER_SAMAD, "Samad Bhai", 0L, true),
        PartnerSeed(PARTNER_JAHIR, "Jahir Mama", 0L, true),
        PartnerSeed(PARTNER_ARIFUL, "Ariful Islam", 0L, true),
    )

    val MOBILE_OPERATORS = listOf(
        "Grameenphone",
        "Robi",
        "Banglalink",
        "Airtel",
        "Teletalk",
    )
}
