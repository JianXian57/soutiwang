package com.soutiwang.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Query(
        """
        SELECT b.id, b.name, b.sourceFileName, b.createdAt, b.updatedAt, COUNT(q.id) AS questionCount
        FROM question_banks b
        LEFT JOIN questions q ON q.bankId = b.id
        GROUP BY b.id
        ORDER BY b.createdAt DESC
        """
    )
    fun observeBanks(): Flow<List<BankWithCount>>

    @Query("SELECT * FROM question_banks WHERE id = :bankId")
    suspend fun getBank(bankId: Long): QuestionBank?

    @Insert
    suspend fun insertBank(bank: QuestionBank): Long

    @Insert
    suspend fun insertQuestions(questions: List<Question>)

    @Transaction
    suspend fun insertBankWithQuestions(bank: QuestionBank, questions: List<Question>) {
        val bankId = insertBank(bank)
        val now = System.currentTimeMillis()
        insertQuestions(
            questions.map {
                it.copy(bankId = bankId, createdAt = if (it.createdAt == 0L) now else it.createdAt)
            }
        )
    }

    @Query("DELETE FROM question_banks WHERE id = :bankId")
    suspend fun deleteBank(bankId: Long)

    @Query(
        """
        SELECT q.id, q.bankId, q.stem, q.answer, q.optionA, q.optionB, q.optionC, q.optionD, q.optionE, q.optionF,
               q.questionType, b.name AS bankName
        FROM questions q
        INNER JOIN question_banks b ON b.id = q.bankId
        WHERE (:bankId IS NULL OR q.bankId = :bankId)
          AND (:keyword = '' OR q.normalizedStem LIKE '%' || :keyword || '%' OR q.normalizedAnswer LIKE '%' || :keyword || '%')
        ORDER BY q.id DESC
        """
    )
    fun observeQuestions(bankId: Long?, keyword: String): Flow<List<QuestionWithBank>>
}
