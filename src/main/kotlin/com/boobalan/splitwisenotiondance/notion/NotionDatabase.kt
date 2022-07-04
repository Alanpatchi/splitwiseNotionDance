package com.boobalan.splitwisenotiondance.notion

data class NotionDatabase(val id: String, val title: List<TitleValue>, val properties: Map<String, *>)