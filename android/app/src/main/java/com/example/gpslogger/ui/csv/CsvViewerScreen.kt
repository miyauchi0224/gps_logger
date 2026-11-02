package com.example.gpslogger.ui.csv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.gpslogger.viewmodel.CsvTable
import com.example.gpslogger.viewmodel.CsvViewerViewModel

/** 等幅フォント12spの1文字あたりの幅の目安（列幅の計算用） */
private val CHAR_WIDTH = 7.5.dp
private val CELL_PADDING = 8.dp
private val ROW_NUMBER_WIDTH = 56.dp

/**
 * セッションのデータをCSVの表として表示する画面。
 * 見出し行は固定し、横にスクロールすると全列が見られる。文字は長押しで選択・コピーできる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvViewerScreen(
    onBack: () -> Unit,
    onExport: (String, String) -> Unit,
    vm: CsvViewerViewModel = hiltViewModel()
) {
    val table by vm.table.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(vm.sessionName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        table?.let {
                            Text(
                                "CSV・${it.rows.size}行",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { onExport(vm.sessionId, vm.sessionName) }) {
                        Icon(Icons.Default.Share, contentDescription = "エクスポート")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                table == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                table!!.rows.isEmpty() -> Text(
                    "記録データがありません",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> CsvTableView(table!!)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CsvTableView(table: CsvTable) {
    // 見出しとすべての行で横スクロールの位置を共有し、列がずれないようにする
    val hScroll = rememberScrollState()
    val widths = remember(table) { table.widths.map { CHAR_WIDTH * it + CELL_PADDING * 2 } }

    SelectionContainer {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            stickyHeader {
                CsvRow(
                    number = "#",
                    cells = table.header,
                    widths = widths,
                    hScroll = hScroll,
                    modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer),
                    bold = true
                )
                HorizontalDivider()
            }
            itemsIndexed(table.rows) { index, row ->
                CsvRow(
                    number = "${index + 1}",
                    cells = row,
                    widths = widths,
                    hScroll = hScroll,
                    // 1行おきに色を変えて読みやすくする
                    modifier = if (index % 2 == 1) {
                        Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    } else Modifier
                )
            }
        }
    }
}

@Composable
private fun CsvRow(
    number: String,
    cells: List<String>,
    widths: List<androidx.compose.ui.unit.Dp>,
    hScroll: ScrollState,
    modifier: Modifier = Modifier,
    bold: Boolean = false
) {
    val style = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        // 文字間隔があると CHAR_WIDTH での列幅計算より広がり、列の末尾が切れる
        letterSpacing = 0.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )
    Row(modifier = modifier.fillMaxWidth()) {
        // 行番号は横スクロールしても見えるように固定する
        Text(
            number,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(ROW_NUMBER_WIDTH).padding(horizontal = CELL_PADDING, vertical = 6.dp)
        )
        Row(modifier = Modifier.horizontalScroll(hScroll)) {
            widths.forEachIndexed { i, w ->
                Text(
                    cells.getOrElse(i) { "" },
                    style = style,
                    maxLines = 1,
                    modifier = Modifier.width(w).padding(horizontal = CELL_PADDING, vertical = 6.dp)
                )
            }
        }
    }
}
