package com.boobalan.splitwisenotiondance.notion

import com.fasterxml.jackson.databind.JsonNode
import lombok.AllArgsConstructor
import lombok.Builder
import lombok.Data
import lombok.NoArgsConstructor

data class NotionPage(val properties: JsonNode? = null, val parent: ParentDatabase? = null) {
}