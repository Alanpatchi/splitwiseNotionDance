package com.boobalan.splitwisenotiondance.splitwise

data class User(val user: UserInfo? = null,// is shows up in the credit_card's transactions' page && also includes cash payments
           val paid_share: String? = null,// Note: all cash payments from Amrita should be separately added in splitwise
           val owed_share // is the amount that is effectively paid by other party
           : String? = null,// paid_share + owed_share
           val net_balance: String? = null
)