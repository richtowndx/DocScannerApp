package com.richtowndx.docscannerapp.data.models

import java.util.Date

/**
 * Document data model
 */
data class Document(
    val id: String,
    val name: String,
    val imagePath: String,
    val ocrText: String = "",
    val createdAt: Date = Date(),
    val ocrEngine: String = "ML Kit"
)

/**
 * Scan result data model
 */
data class ScanResult(
    val imagePath: String,
    val ocrText: String,
    val engineUsed: String
)
