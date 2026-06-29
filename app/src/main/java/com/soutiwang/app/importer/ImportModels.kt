package com.soutiwang.app.importer

data class ImportQuestion(
    val stem: String,
    val answer: String,
    val options: Map<Char, String>,
    val questionType: String
)

data class ImportIssue(
    val rowNumber: Int,
    val reason: String
)

data class ImportPreview(
    val bankName: String,
    val sourceFileName: String,
    val totalRows: Int,
    val importableQuestions: List<ImportQuestion>,
    val issues: List<ImportIssue>
)
