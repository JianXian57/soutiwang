package com.soutiwang.app.data

class QuestionRepository(private val dao: QuestionDao) {
    fun observeBanks() = dao.observeBanks()
    fun observeQuestions(bankId: Long?, keyword: String) = dao.observeQuestions(bankId, keyword)
    suspend fun getBank(bankId: Long) = dao.getBank(bankId)
    suspend fun importBank(bank: QuestionBank, questions: List<Question>) = dao.insertBankWithQuestions(bank, questions)
    suspend fun deleteBank(bankId: Long) = dao.deleteBank(bankId)
}
