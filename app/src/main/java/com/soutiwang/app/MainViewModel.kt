package com.soutiwang.app

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soutiwang.app.data.Question
import com.soutiwang.app.data.QuestionBank
import com.soutiwang.app.importer.ImportPreview
import com.soutiwang.app.importer.XlsxQuestionImporter
import com.soutiwang.app.util.displayName
import com.soutiwang.app.util.normalizeKeyword
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SoutiWangApp
    private val repository = app.repository
    private val importer = XlsxQuestionImporter(application.contentResolver)

    val banks = repository.observeBanks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val searchKeyword = MutableStateFlow("")
    val selectedBankId = MutableStateFlow<Long?>(null)
    val detailBankId = MutableStateFlow<Long?>(null)
    val isLoading = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val currentPreview = MutableStateFlow<ImportPreview?>(null)

    val searchResults = selectedBankId.flatMapLatest { bankId ->
        searchKeyword.flatMapLatest { keyword ->
            repository.observeQuestions(bankId, normalizeKeyword(keyword))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val detailQuestions = detailBankId.flatMapLatest { bankId ->
        repository.observeQuestions(bankId, "")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun parseImport(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                isLoading.value = true
                val name = getApplication<Application>().contentResolver.displayName(uri)
                importer.parse(uri, name)
            }.onSuccess {
                currentPreview.value = it
            }.onFailure {
                Log.e(TAG, "Failed to import xlsx", it)
                message.value = "导入失败：无法解析该 Excel 文件，请确认文件为 .xlsx 格式，且包含题干和答案列。"
            }
            isLoading.value = false
        }
    }

    fun updatePreviewBankName(name: String) {
        currentPreview.value = currentPreview.value?.copy(bankName = name)
    }

    fun clearPreview() {
        currentPreview.value = null
    }

    fun confirmImport(onDone: () -> Unit) {
        val preview = currentPreview.value ?: return
        val bankName = preview.bankName.trim()
        if (bankName.isBlank()) {
            message.value = "题库名称不能为空"
            return
        }
        if (preview.importableQuestions.isEmpty()) {
            message.value = "没有可导入的题目"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val bank = QuestionBank(
                name = bankName,
                sourceFileName = preview.sourceFileName,
                createdAt = now,
                updatedAt = now
            )
            val questions = preview.importableQuestions.map {
                Question(
                    bankId = 0,
                    stem = it.stem,
                    answer = it.answer,
                    optionA = it.options['A'],
                    optionB = it.options['B'],
                    optionC = it.options['C'],
                    optionD = it.options['D'],
                    optionE = it.options['E'],
                    optionF = it.options['F'],
                    questionType = it.questionType,
                    normalizedStem = normalizeKeyword(it.stem),
                    normalizedAnswer = normalizeKeyword(it.answer),
                    createdAt = now
                )
            }
            repository.importBank(bank, questions)
            currentPreview.value = null
            message.value = "已导入 ${questions.size} 道题"
            launch(Dispatchers.Main) { onDone() }
        }
    }

    fun deleteBank(bankId: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBank(bankId)
            message.value = "题库已删除"
            launch(Dispatchers.Main) { onDone() }
        }
    }

    fun consumeMessage() {
        message.value = null
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
