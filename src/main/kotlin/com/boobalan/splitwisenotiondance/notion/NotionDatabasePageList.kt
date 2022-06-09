package com.boobalan.splitwisenotiondance.notion

import lombok.AllArgsConstructor
import lombok.Builder
import lombok.Data
import lombok.NoArgsConstructor

data class NotionDatabasePageList(val results: List<NotionPageDetail>? = null) {
}