请帮我用 Kotlin + Jetpack Compose 开发一个完整的 Android 点检应用。

## 后端 API 信息
- 基础地址: http://10.0.2.2:5039 (Android 模拟器访问本地 localhost)
- 真实设备调试时改为电脑局域网 IP，如 http://192.168.x.x:5039
- 两个接口：
  GET /api/Inspection/templates/{deviceModel} - 获取点检模板
  POST /api/Inspection/submit-full - 提交点检记录

## 功能需求

### 1. 扫码功能
- 扫描工号二维码（保存到 DataStore，下次自动填充，也可手动修改）
- 扫描设备型号二维码
- 扫描成功后自动跳转到点检页面
- 使用 CameraX + ML Kit Barcode Scanning

### 2. 点检页面
- 顶部显示设备型号
- 根据设备型号从 API 获取点检模板列表
- 动态生成点检表单，支持两种类型：
  a) normal_abnormal 类型：用两个按钮（正常/异常）切换，默认未选中，必须选择一个
  b) numeric 类型：用 OutlinedTextField 输入数值，实时显示是否在正常范围内（绿色正常/红色异常）
- 每个点检项可填写备注（可选）
- 底部固定一个"提交点检"按钮

### 3. 提交功能
- 验证所有必填项已填写（normal_abnormal 必须选择，numeric 必须填写数值且在范围内）
- 提交时显示加载动画
- 提交成功后清空当前表单，返回扫码页面或显示成功提示

## 数据模型
```kotlin
// 点检模板
data class InspectionTemplate(
    val id: Int,
    val deviceModel: String,
    val itemName: String,
    val itemType: String, // "normal_abnormal" 或 "numeric"
    val unit: String?,
    val normalMin: Double?,
    val normalMax: Double?,
    val sortOrder: Int
)

// 提交请求
data class FullInspectionRequest(
    val employeeId: String,
    val deviceModel: String,
    val results: List<InspectionResultItem>
)

data class InspectionResultItem(
    val itemName: String,
    val resultValue: String,
    val isNormal: Boolean,
    val remark: String
)


项目结构要求
app/src/main/java/com/machine_check/inspection/
├── MainActivity.kt
├── data/
│   ├── models/
│   ├── network/ (ApiService, RetrofitClient)
│   └── repository/ (InspectionRepository)
├── ui/
│   ├── scan/ (ScanScreen, ScanViewModel)
│   ├── inspection/ (InspectionScreen, InspectionViewModel)
│   └── components/ (QrCodeScanner)
└── utils/ (PreferencesManager)


技术栈
Jetpack Compose + Material 3

CameraX + ML Kit Barcode Scanning (v1.4.2 + v17.3.0)

Retrofit (2.9.0) + Gson + OkHttp Logging Interceptor

ViewModel + StateFlow

DataStore (Preferences) 存储工号

Coroutines 处理异步



其他要求
所有代码使用 Kotlin

使用 Material 3 组件（Card, Button, TextField, Snackbar 等）

添加加载状态（CircularProgressIndicator）

添加错误处理（网络错误显示提示）

代码要有中文注释