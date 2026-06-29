package com.soutiwang.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.soutiwang.app.data.BankWithCount
import com.soutiwang.app.data.QuestionWithBank
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SoutiWangApp(viewModel)
            }
        }
    }
}

@Composable
private fun SoutiWangApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val message by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding)
        ) {
            composable("home") { HomeScreen(viewModel, navController) }
            composable("preview") { ImportPreviewScreen(viewModel, navController) }
            composable("search") { SearchScreen(viewModel, navController) }
            composable("detail/{bankId}") { entry ->
                val bankId = entry.arguments?.getString("bankId")?.toLongOrNull() ?: return@composable
                DetailScreen(viewModel, navController, bankId)
            }
        }
    }
}

@Composable
private fun HomeScreen(viewModel: MainViewModel, navController: NavHostController) {
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.parseImport(uri)
            navController.navigate("preview")
        }
    }

    Screen("搜题王") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { launcher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) }) {
                Text("导入题库")
            }
            OutlinedButton(onClick = {
                viewModel.selectedBankId.value = null
                viewModel.searchKeyword.value = ""
                navController.navigate("search")
            }) {
                Text("搜索题目")
            }
        }
        Spacer(Modifier.height(16.dp))
        if (banks.isEmpty()) {
            Text("还没有题库。点击“导入题库”选择 xlsx 文件。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(banks) { bank ->
                    BankCard(bank, onOpen = { navController.navigate("detail/${bank.id}") }, onDelete = { viewModel.deleteBank(bank.id) })
                }
            }
        }
    }
}

@Composable
private fun ImportPreviewScreen(viewModel: MainViewModel, navController: NavHostController) {
    val preview by viewModel.currentPreview.collectAsStateWithLifecycle()
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()

    Screen("导入预览", onBack = {
        viewModel.clearPreview()
        navController.popBackStack()
    }) {
        when {
            loading -> Text("正在读取 xlsx 文件...")
            preview == null -> Text("没有可预览的导入内容。")
            else -> {
                val data = preview!!
                OutlinedTextField(
                    value = data.bankName,
                    onValueChange = viewModel::updatePreviewBankName,
                    label = { Text("题库名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("总行数：${data.totalRows}")
                Text("可导入题目数：${data.importableQuestions.size}")
                Text("异常行数：${data.issues.size}")
                Spacer(Modifier.height(12.dp))
                Text("题目预览", fontWeight = FontWeight.Bold)
                data.importableQuestions.take(5).forEach { question ->
                    QuestionSummary(question.stem, question.answer, question.questionType)
                }
                if (data.issues.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("异常行", fontWeight = FontWeight.Bold)
                    data.issues.take(20).forEach { issue ->
                        Text("第 ${issue.rowNumber} 行：${issue.reason}")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { viewModel.confirmImport { navController.popBackStack("home", false) } }) {
                        Text("确认导入")
                    }
                    OutlinedButton(onClick = {
                        viewModel.clearPreview()
                        navController.popBackStack()
                    }) {
                        Text("取消")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchScreen(viewModel: MainViewModel, navController: NavHostController) {
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val keyword by viewModel.searchKeyword.collectAsStateWithLifecycle()
    val selectedBankId by viewModel.selectedBankId.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }

    Screen("搜索题目", onBack = { navController.popBackStack() }) {
        OutlinedTextField(
            value = keyword,
            onValueChange = { viewModel.searchKeyword.value = it },
            label = { Text("输入题干或答案关键词") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = banks.firstOrNull { it.id == selectedBankId }?.name ?: "全部题库",
                onValueChange = {},
                readOnly = true,
                label = { Text("搜索范围") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("全部题库") }, onClick = {
                    viewModel.selectedBankId.value = null
                    expanded = false
                })
                banks.forEach { bank ->
                    DropdownMenuItem(text = { Text(bank.name) }, onClick = {
                        viewModel.selectedBankId.value = bank.id
                        expanded = false
                    })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ResultsList(results, keyword)
    }
}

@Composable
private fun DetailScreen(viewModel: MainViewModel, navController: NavHostController, bankId: Long) {
    LaunchedEffect(bankId) {
        viewModel.detailBankId.value = bankId
    }
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val questions by viewModel.detailQuestions.collectAsStateWithLifecycle()
    val bank = banks.firstOrNull { it.id == bankId }
    var showDelete by remember { mutableStateOf(false) }

    Screen(bank?.name ?: "题库详情", onBack = { navController.popBackStack() }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                viewModel.selectedBankId.value = bankId
                viewModel.searchKeyword.value = ""
                navController.navigate("search")
            }) {
                Text("搜索本题库")
            }
            OutlinedButton(onClick = { showDelete = true }) {
                Text("删除题库")
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(questions) { question ->
                ResultCard(question, "")
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除题库") },
            text = { Text("删除后，该题库中的题目也会一起删除。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.deleteBank(bankId) { navController.popBackStack("home", false) }
                }) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun ResultsList(results: List<QuestionWithBank>, keyword: String) {
    if (keyword.isBlank()) {
        Text("请输入关键词开始搜索。")
        return
    }
    if (results.isEmpty()) {
        Text("没有找到匹配题目。")
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(results) { question -> ResultCard(question, keyword) }
    }
}

@Composable
private fun ResultCard(question: QuestionWithBank, keyword: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(question.stem, fontWeight = FontWeight.Bold)
            listOf(
                "A" to question.optionA,
                "B" to question.optionB,
                "C" to question.optionC,
                "D" to question.optionD,
                "E" to question.optionE,
                "F" to question.optionF
            ).filter { !it.second.isNullOrBlank() }.forEach { (label, value) ->
                Text("$label. $value")
            }
            Divider()
            Text("答案：${question.answer}")
            Text("题型：${question.questionType}")
            Text("所属题库：${question.bankName}")
            if (keyword.isNotBlank()) Text("匹配来源：${matchLabel(question, keyword)}")
            Button(onClick = {
                clipboard.setText(AnnotatedString(question.answer))
                android.widget.Toast.makeText(context, "已复制答案", android.widget.Toast.LENGTH_SHORT).show()
            }) {
                Text("复制答案")
            }
        }
    }
}

@Composable
private fun BankCard(bank: BankWithCount, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(bank.name, fontWeight = FontWeight.Bold)
            Text("题目数量：${bank.questionCount}")
            Text("导入时间：${formatTime(bank.createdAt)}")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpen) { Text("查看/搜索") }
                OutlinedButton(onClick = onDelete) { Text("删除") }
            }
        }
    }
}

@Composable
private fun QuestionSummary(stem: String, answer: String, type: String) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(stem, fontWeight = FontWeight.Bold)
            Text("答案：$answer")
            Text("题型：$type")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(title: String, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) TextButton(onClick = onBack) { Text("返回") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top,
            content = content
        )
    }
}

private fun matchLabel(question: QuestionWithBank, keyword: String): String {
    val key = keyword.trim().lowercase()
    val stem = question.stem.lowercase().contains(key)
    val answer = question.answer.lowercase().contains(key)
    return when {
        stem && answer -> "题干和答案均匹配"
        stem -> "匹配题干"
        answer -> "匹配答案"
        else -> "匹配题目"
    }
}

private fun formatTime(value: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(value))
