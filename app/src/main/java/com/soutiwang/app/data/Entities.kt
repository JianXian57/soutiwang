package com.soutiwang.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "question_banks")
data class QuestionBank(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourceFileName: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = QuestionBank::class,
            parentColumns = ["id"],
            childColumns = ["bankId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bankId"), Index("normalizedStem"), Index("normalizedAnswer")]
)
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankId: Long,
    val stem: String,
    val answer: String,
    val optionA: String? = null,
    val optionB: String? = null,
    val optionC: String? = null,
    val optionD: String? = null,
    val optionE: String? = null,
    val optionF: String? = null,
    val questionType: String,
    val normalizedStem: String,
    val normalizedAnswer: String,
    val createdAt: Long
)

data class BankWithCount(
    val id: Long,
    val name: String,
    val sourceFileName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val questionCount: Int
)

data class QuestionWithBank(
    val id: Long,
    val bankId: Long,
    val stem: String,
    val answer: String,
    val optionA: String?,
    val optionB: String?,
    val optionC: String?,
    val optionD: String?,
    val optionE: String?,
    val optionF: String?,
    val questionType: String,
    val bankName: String
)
