package com.boobalan.splitwisenotiondance.splitwise

data class Expense(val users: List<User>? = null, val id: String? = null,
                   val description: String? = null, val repeats: Boolean = false,
                   val repeat_interval: String? = null, val next_repeat: String? = null,
                   val date: String? = null, val created_at: String? = null,
                   val updated_at: String? = null
)