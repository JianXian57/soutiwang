package com.soutiwang.app.importer

import android.content.ContentResolver
import android.net.Uri
import android.util.Xml
import java.io.InputStream
import java.text.Normalizer
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

class XlsxQuestionImporter(private val resolver: ContentResolver) {
    fun parse(uri: Uri, sourceFileName: String): ImportPreview {
        val rows = resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "无法打开文件" }
            readRows(input)
        }

        if (rows.isEmpty()) {
            return ImportPreview(defaultBankName(sourceFileName), sourceFileName, 0, emptyList(), listOf(ImportIssue(1, "文件没有可读取的内容")))
        }

        val mapping = detectColumns(rows.first())
        val dataRows = if (mapping.fromHeader) rows.drop(1) else rows
        val issues = mutableListOf<ImportIssue>()
        val questions = mutableListOf<ImportQuestion>()
        val seenStems = mutableSetOf<String>()

        if (mapping.stemIndex == null || mapping.answerIndex == null) {
            issues += ImportIssue(1, "无法识别列")
        }

        dataRows.forEachIndexed { index, row ->
            val rowNumber = index + if (mapping.fromHeader) 2 else 1
            if (row.all { it.isBlank() }) return@forEachIndexed

            val stem = mapping.stemIndex?.let { row.cell(it) }.orEmpty()
            val answer = mapping.answerIndex?.let { row.cell(it) }.orEmpty()
            when {
                stem.isBlank() -> issues += ImportIssue(rowNumber, "缺少题干")
                answer.isBlank() -> issues += ImportIssue(rowNumber, "缺少答案")
                !seenStems.add(normalizeForCompare(stem)) -> issues += ImportIssue(rowNumber, "重复题目")
                mapping.stemIndex == null || mapping.answerIndex == null -> Unit
                else -> {
                    val options = mapping.optionIndices.mapNotNull { (key, col) ->
                        val value = row.cell(col)
                        if (value.isBlank()) null else key to value
                    }.toMap()
                    questions += ImportQuestion(stem, answer, options, inferQuestionType(answer, options))
                }
            }
        }

        return ImportPreview(
            bankName = defaultBankName(sourceFileName),
            sourceFileName = sourceFileName,
            totalRows = dataRows.size,
            importableQuestions = questions,
            issues = issues
        )
    }

    private fun readRows(input: InputStream): List<List<String>> {
        val entries = readXlsxEntries(input)
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val sheetEntry = entries["xl/worksheets/sheet1.xml"]
            ?: entries.entries.firstOrNull { it.key.startsWith("xl/worksheets/sheet") && it.key.endsWith(".xml") }?.value
            ?: throw IllegalArgumentException("xlsx 文件中没有找到工作表")
        return parseWorksheet(sheetEntry, sharedStrings)
    }

    private fun readXlsxEntries(input: InputStream): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.trimStart('/')
                if (!entry.isDirectory && (name == "xl/sharedStrings.xml" || name.startsWith("xl/worksheets/"))) {
                    entries[name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        if (entries.isEmpty()) throw IllegalArgumentException("不是有效的 xlsx 文件")
        return entries
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val parser = newParser(xml)
        val values = mutableListOf<String>()
        var current: StringBuilder? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> current = StringBuilder()
                    "t" -> current?.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (parser.name == "si") {
                    values += normalizeCell(current?.toString().orEmpty())
                    current = null
                }
            }
        }
        return values
    }

    private fun parseWorksheet(xml: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val parser = newParser(xml)
        val rows = mutableListOf<List<String>>()
        var rowCells: MutableMap<Int, String>? = null
        var fallbackColumn = 0

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        rowCells = linkedMapOf()
                        fallbackColumn = 0
                    }
                    "c" -> {
                        val cellRef = parser.getAttributeValue(null, "r")
                        val column = cellRef?.let(::columnIndexFromCellRef) ?: fallbackColumn
                        val type = parser.getAttributeValue(null, "t")
                        val value = parseCell(parser, type, sharedStrings)
                        rowCells?.put(column, normalizeCell(value))
                        fallbackColumn = column + 1
                    }
                }
                XmlPullParser.END_TAG -> if (parser.name == "row") {
                    rows += rowCells.toRow()
                    rowCells = null
                }
            }
        }
        return rows
    }

    private fun parseCell(parser: XmlPullParser, type: String?, sharedStrings: List<String>): String {
        var rawValue = ""
        val inlineText = StringBuilder()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "v" -> rawValue = parser.nextText()
                    "t" -> inlineText.append(parser.nextText())
                }
                XmlPullParser.END_TAG -> if (parser.name == "c") {
                    return when (type) {
                        "s" -> rawValue.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                        "inlineStr" -> inlineText.toString()
                        else -> rawValue.ifBlank { inlineText.toString() }
                    }
                }
            }
        }
        return ""
    }

    private fun MutableMap<Int, String>?.toRow(): List<String> {
        if (this == null || isEmpty()) return emptyList()
        val width = (keys.maxOrNull() ?: -1) + 1
        return (0 until width).map { get(it).orEmpty() }.dropLastWhile { it.isBlank() }
    }

    private fun newParser(xml: ByteArray): XmlPullParser =
        Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(xml.inputStream(), "UTF-8")
        }

    private fun columnIndexFromCellRef(ref: String): Int {
        var index = 0
        var seenColumn = false
        for (ch in ref) {
            if (!ch.isLetter()) break
            seenColumn = true
            index = index * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return if (seenColumn) index - 1 else 0
    }

    private fun detectColumns(header: List<String>): ColumnMapping {
        val normalized = header.map { normalizeHeader(it) }
        val stem = normalized.indexOfFirstOrNull { it in stemHeaders }
        val answer = normalized.indexOfFirstOrNull { it in answerHeaders }
        val options = mutableMapOf<Char, Int>()

        ('A'..'F').forEach { letter ->
            val names = optionHeaders(letter)
            val index = normalized.indexOfFirstOrNull { it in names }
            if (index != null) options[letter] = index
        }

        val hasHeader = stem != null || answer != null || options.isNotEmpty()
        if (hasHeader) {
            return ColumnMapping(true, stem, answer, options)
        }

        return ColumnMapping(
            fromHeader = false,
            stemIndex = 0,
            answerIndex = 1,
            optionIndices = ('A'..'F').mapIndexed { idx, c -> c to idx + 2 }.toMap()
        )
    }

    private fun inferQuestionType(answer: String, options: Map<Char, String>): String {
        val normalizedAnswer = normalizeHeader(answer)
        val answerLetters = answer.uppercase()
            .replace("选", "")
            .replace("答案", "")
            .replace("，", ",")
            .replace("、", ",")
            .replace(" ", "")

        return when {
            normalizedAnswer in judgmentAnswers -> "判断题"
            options.isNotEmpty() -> "选择题"
            answerLetters.matches(Regex("^[A-F](,[A-F])*$")) -> "选择题"
            answerLetters.matches(Regex("^[A-F]{1,6}$")) -> "选择题"
            answer.isNotBlank() -> "填空题"
            else -> "未知"
        }
    }

    private fun List<String>.cell(index: Int): String = getOrNull(index).orEmpty()

    private fun normalizeCell(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")

    private fun normalizeHeader(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase()
            .replace(Regex("\\s+"), "")
            .replace("：", ":")
            .trim()

    private fun normalizeForCompare(value: String): String =
        normalizeHeader(value).replace(Regex("[,，。.;；:：!?！？]"), "")

    private fun defaultBankName(fileName: String): String =
        fileName.substringBeforeLast(".").ifBlank { "未命名题库" }

    private fun <T> List<T>.indexOfFirstOrNull(predicate: (T) -> Boolean): Int? {
        val index = indexOfFirst(predicate)
        return if (index >= 0) index else null
    }

    private data class ColumnMapping(
        val fromHeader: Boolean,
        val stemIndex: Int?,
        val answerIndex: Int?,
        val optionIndices: Map<Char, Int>
    )

    companion object {
        private val stemHeaders = setOf("题干", "题目", "问题", "试题", "内容")
        private val answerHeaders = setOf("答案", "正确答案", "标准答案", "参考答案")
        private val judgmentAnswers = setOf("正确", "错误", "对", "错", "是", "否", "√", "×", "true", "false")
        private const val MAX_COLUMNS = 32

        private fun optionHeaders(letter: Char): Set<String> {
            val lower = letter.lowercaseChar()
            return setOf("选项$letter", "$letter", "${letter}选项", "答案$letter", "选项$lower", "$lower", "${lower}选项", "答案$lower")
                .map { it.lowercase() }
                .toSet()
        }
    }
}
