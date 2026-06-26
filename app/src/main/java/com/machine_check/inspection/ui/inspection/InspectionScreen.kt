package com.machine_check.inspection.ui.inspection

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 点检页面
 * 根据设备型号加载模板，动态生成表单
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionScreen(
    deviceModel: String,
    employeeId: String,
    onBack: () -> Unit,
    viewModel: InspectionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = androidx.compose.material3.SnackbarHostState()

    // 加载模板
    LaunchedEffect(deviceModel) {
        viewModel.loadTemplates(deviceModel, employeeId)
    }

    // 错误 Snackbar
    LaunchedEffect((uiState as? InspectionUiState.Form)?.errorMessage) {
        val msg = (uiState as? InspectionUiState.Form)?.errorMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearError()
        }
    }

    // 提交成功 Snackbar + 返回
    LaunchedEffect((uiState as? InspectionUiState.Form)?.submitSuccess) {
        if ((uiState as? InspectionUiState.Form)?.submitSuccess == true) {
            snackbarHostState.showSnackbar("提交成功！")
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("点检 - $deviceModel") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (val state = uiState) {
            is InspectionUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在加载点检模板...")
                    }
                }
            }

            is InspectionUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.retry(deviceModel, employeeId)
                        }) {
                            Text("重试")
                        }
                    }
                }
            }

            is InspectionUiState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("该设备型号暂无点检模板")
                }
            }

            is InspectionUiState.Form -> {
                InspectionForm(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

/**
 * 点检表单
 */
@Composable
private fun InspectionForm(
    state: InspectionUiState.Form,
    viewModel: InspectionViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // 表单列表
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = state.items,
                key = { index, _ -> index }
            ) { index, itemState ->
                InspectionItemCard(
                    itemState = itemState,
                    index = index,
                    onNormalAbnormalChanged = { isNormal ->
                        viewModel.onNormalAbnormalChanged(index, isNormal)
                    },
                    onNumericValueChanged = { value ->
                        viewModel.onNumericValueChanged(index, value)
                    },
                    onRemarkChanged = { remark ->
                        viewModel.onRemarkChanged(index, remark)
                    }
                )
            }
        }

        // 底部提交按钮
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Button(
                onClick = { viewModel.submitInspection() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                enabled = !state.isSubmitting
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("提交中...")
                } else {
                    Text(
                        text = "提交点检",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * 单条点检项卡片
 * 支持 normal_abnormal（双按钮切换）和 numeric（数值输入）两种类型
 */
@Composable
private fun InspectionItemCard(
    itemState: InspectionItemState,
    index: Int,
    onNormalAbnormalChanged: (Boolean) -> Unit,
    onNumericValueChanged: (String) -> Unit,
    onRemarkChanged: (String) -> Unit
) {
    val template = itemState.template
    val isValid = itemState.isValid

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (!isValid) 2.dp else 0.dp,
                color = if (!isValid) MaterialTheme.colorScheme.error else Color.Transparent
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}. ${template.itemName}",
                    style = MaterialTheme.typography.titleSmall
                )
                if (!isValid) {
                    Text(
                        text = "必填",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // 根据类型渲染不同的输入控件
            when (template.itemType) {
                "normal_abnormal" -> {
                    NormalAbnormalInput(
                        selectedNormal = itemState.selectedNormal,
                        onSelectionChanged = onNormalAbnormalChanged
                    )
                }
                "numeric" -> {
                    NumericInput(
                        value = itemState.numericValue,
                        onValueChanged = onNumericValueChanged,
                        unit = template.unit,
                        normalMin = template.normalMin,
                        normalMax = template.normalMax
                    )
                }
            }

            // 备注输入
            OutlinedTextField(
                value = itemState.remark,
                onValueChange = onRemarkChanged,
                label = { Text("备注（可选）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

/**
 * normal_abnormal 类型输入：两个切换按钮
 */
@Composable
private fun NormalAbnormalInput(
    selectedNormal: Boolean?,
    onSelectionChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = { onSelectionChanged(true) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedNormal == true)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "✓ 正常",
                color = if (selectedNormal == true)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }

        Button(
            onClick = { onSelectionChanged(false) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedNormal == false)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "✗ 异常",
                color = if (selectedNormal == false)
                    MaterialTheme.colorScheme.onError
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * numeric 类型输入：数值输入框 + 实时范围检查
 */
@Composable
private fun NumericInput(
    value: String,
    onValueChanged: (String) -> Unit,
    unit: String?,
    normalMin: Double?,
    normalMax: Double?
) {
    // 判断当前值是否在正常范围内
    val numericValue = value.toDoubleOrNull()
    val isInRange = when {
        value.isBlank() -> null                      // 未输入，不显示颜色
        numericValue == null -> false                // 无效数字
        normalMin != null && normalMax != null ->
            numericValue >= normalMin && numericValue <= normalMax
        else -> true                                 // 无范围限制
    }

    val borderColor = when (isInRange) {
        null -> MaterialTheme.colorScheme.outline    // 默认
        true -> Color(0xFF4CAF50)                    // 绿色 - 正常
        false -> Color(0xFFF44336)                   // 红色 - 异常
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChanged,
            label = {
                val rangeText = buildString {
                    if (normalMin != null && normalMax != null) {
                        append("范围: $normalMin ~ $normalMax")
                    }
                    if (unit != null) {
                        if (isNotEmpty()) append(" ")
                        append(unit)
                    }
                }
                Text(rangeText.ifEmpty { "请输入数值" })
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = isInRange == false,
            supportingText = if (isInRange == false) {
                { Text("数值超出正常范围", color = Color(0xFFF44336)) }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = borderColor,
                unfocusedBorderColor = borderColor
            )
        )
    }
}
