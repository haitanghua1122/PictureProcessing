package com.example.pictureprocessing

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pictureprocessing.ui.theme.MyApplicationTheme
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    enum class ProcessType {
        FORMAT_CONVERT,  // 格式转换
        COMPRESS,        // 压缩
        ENLARGE          // 增大
    }

    // 压缩强度枚举
    enum class CompressionLevel {
        LIGHT,      // 轻度
        NORMAL,     // 普通
        STRONG,     // 强力
        EXTREME     // 极强
    }

    // 数据单位枚举
    enum class DataUnit {
        KB,  // 千字节
        MB   // 兆字节
    }

    private lateinit var selectImageLauncher: ActivityResultLauncher<Intent>
    private var selectedImageUri by mutableStateOf<Uri?>(null)
    private var convertedImagePath by mutableStateOf<String?>(null)
    private var isConverting by mutableStateOf(false)

    // 添加处理类型状态
    private var processType by mutableStateOf(ProcessType.FORMAT_CONVERT)

    // 添加目标格式状态
    private var targetFormat by mutableStateOf(Bitmap.CompressFormat.JPEG)

    // 添加压缩强度状态
    private var compressionLevel by mutableStateOf(CompressionLevel.NORMAL)

    // 添加缩放比例状态（用于增大功能）
    private var scaleValue by mutableStateOf(100f)

    // 添加数据单位状态
    private var dataUnit by mutableStateOf(DataUnit.KB)

    // 添加数据大小状态
    private var dataSize by mutableStateOf(100)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 注册图片选择器
        selectImageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    selectedImageUri = result.data?.data
                    convertedImagePath = null
                }
            }

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ImageConverterScreen(
                        modifier = Modifier.padding(innerPadding),
                        selectedImageUri = selectedImageUri,
                        convertedImagePath = convertedImagePath,
                        isConverting = isConverting,
                        processType = processType,
                        targetFormat = targetFormat,
                        compressionLevel = compressionLevel,
                        scaleValue = scaleValue,
                        dataSize = dataSize,
                        dataUnit = dataUnit,
                        onSelectImage = { selectImage() },
                        onConvertImage = { format ->
                            isConverting = true
                            convertImage(format)
                        },
                        onProcessTypeChange = { processType = it },
                        onTargetFormatChange = { targetFormat = it },
                        onCompressionLevelChange = { compressionLevel = it },
                        onScaleValueChange = { scaleValue = it },
                        onDataSizeChange = { dataSize = it },
                        onDataUnitChange = { dataUnit = it }
                    )
                }
            }
        }
    }

    private fun selectImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        selectImageLauncher.launch(intent)
    }

    // 修复闪退问题的代码
    private fun convertImage(format: Bitmap.CompressFormat) {
        val uri = selectedImageUri ?: run {
            isConverting = false
            return
        }

        Thread {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (originalBitmap != null) {
                    val bitmap = when (processType) {
                        ProcessType.FORMAT_CONVERT -> {
                            // 仅格式转换，不改变尺寸
                            originalBitmap
                        }

                        ProcessType.COMPRESS -> {
                            // 压缩处理：根据预设档位调整图片尺寸
                            val scale = when (compressionLevel) {
                                MainActivity.CompressionLevel.LIGHT -> 0.8f   // 轻度压缩到80%
                                MainActivity.CompressionLevel.NORMAL -> 0.6f  // 普通压缩到60%
                                MainActivity.CompressionLevel.STRONG -> 0.4f  // 强力压缩到40%
                                MainActivity.CompressionLevel.EXTREME -> 0.2f // 极强压缩到20%
                            }

                            val newWidth = (originalBitmap.width * scale).toInt()
                            val newHeight = (originalBitmap.height * scale).toInt()

                            // 只有当尺寸发生变化时才进行缩放
                            if (newWidth != originalBitmap.width || newHeight != originalBitmap.height) {
                                Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
                            } else {
                                originalBitmap
                            }
                        }

                        ProcessType.ENLARGE -> {
                            // 增大处理：保持原始图片不变
                            originalBitmap
                        }
                    }

                    val extension = when (format) {
                        Bitmap.CompressFormat.JPEG -> "jpg"
                        Bitmap.CompressFormat.PNG -> "png"
                        Bitmap.CompressFormat.WEBP -> "webp"
                        else -> "img"
                    }

                    // 生成带时间戳的文件名
                    val timeStamp =
                        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val fileName = "converted_$timeStamp.$extension"

                    // 保存到公共图片目录（DCIM目录会在相册中显示）
                    val picturesDir =
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val outputFile = File(picturesDir, fileName)

                    // 确保目录存在
                    if (!picturesDir.exists()) {
                        picturesDir.mkdirs()
                    }

                    FileOutputStream(outputFile).use { out ->
                        // 根据处理类型调整压缩质量
                        val quality = when (processType) {
                            ProcessType.FORMAT_CONVERT -> 90
                            ProcessType.COMPRESS -> {
                                // 压缩功能：根据压缩强度调整质量
                                when (compressionLevel) {
                                    MainActivity.CompressionLevel.LIGHT -> 85
                                    MainActivity.CompressionLevel.NORMAL -> 75
                                    MainActivity.CompressionLevel.STRONG -> 60
                                    MainActivity.CompressionLevel.EXTREME -> 40
                                }
                            }

                            ProcessType.ENLARGE -> {
                                // 增大功能：使用最高质量
                                100
                            }
                        }

                        // 先正常保存图片
                        var success = bitmap.compress(format, quality, out)

                        // 如果是增大操作，添加额外数据来增加文件大小
                        if (success && processType == ProcessType.ENLARGE) {
                            try {
                                // 添加额外数据块来增加文件大小
                                val extraBytes = when (dataUnit) {
                                    DataUnit.KB -> dataSize * 1024L
                                    DataUnit.MB -> dataSize * 1024L * 1024L
                                }

                                if (extraBytes > 0) {
                                    // 分块写入，避免内存不足
                                    val maxBytes = 1000L * 1024L * 1024L // 1GB限制
                                    val bytesToWrite = extraBytes.coerceAtMost(maxBytes)

                                    // 分块写入，每块1MB
                                    val chunkSize = 1024 * 1024
                                    var remainingBytes = bytesToWrite

                                    while (remainingBytes > 0) {
                                        val currentChunkSize =
                                            chunkSize.coerceAtMost(remainingBytes.toInt())
                                        val buffer = ByteArray(currentChunkSize)
                                        out.write(buffer)
                                        remainingBytes -= currentChunkSize
                                    }
                                }
                            } catch (e: OutOfMemoryError) {
                                runOnUiThread {
                                    isConverting = false
                                    Toast.makeText(
                                        this,
                                        "内存不足，无法增加如此大的数据",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                return@Thread
                            } catch (e: Exception) {
                                runOnUiThread {
                                    isConverting = false
                                    Toast.makeText(
                                        this,
                                        "写入数据时出错: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                return@Thread
                            }
                        }

                        if (success) {
                            convertedImagePath = outputFile.absolutePath

                            // 通知媒体库扫描新文件
                            MediaScannerConnection.scanFile(
                                this,
                                arrayOf(outputFile.absolutePath),
                                arrayOf("image/$extension")
                            ) { path, uri ->
                                runOnUiThread {
                                    isConverting = false
                                    Toast.makeText(
                                        this,
                                        "图片处理成功！已保存到相册：$path",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } else {
                            runOnUiThread {
                                isConverting = false
                                Toast.makeText(this, "图片处理失败", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    runOnUiThread {
                        isConverting = false
                        Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    isConverting = false
                    Toast.makeText(this, "转换出错: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ImageConverterScreen(
        modifier: Modifier = Modifier,
        selectedImageUri: Uri?,
        convertedImagePath: String?,
        isConverting: Boolean,
        processType: MainActivity.ProcessType = MainActivity.ProcessType.FORMAT_CONVERT,
        targetFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        compressionLevel: MainActivity.CompressionLevel = MainActivity.CompressionLevel.NORMAL,
        scaleValue: Float = 100f,
        dataSize: Int = 100,
        dataUnit: MainActivity.DataUnit = MainActivity.DataUnit.KB,
        onSelectImage: () -> Unit,
        onConvertImage: (Bitmap.CompressFormat) -> Unit,
        onProcessTypeChange: (MainActivity.ProcessType) -> Unit,
        onTargetFormatChange: (Bitmap.CompressFormat) -> Unit,
        onCompressionLevelChange: (MainActivity.CompressionLevel) -> Unit,
        onScaleValueChange: (Float) -> Unit,
        onDataSizeChange: (Int) -> Unit,
        onDataUnitChange: (MainActivity.DataUnit) -> Unit
    ) {
        var showOptions by remember { mutableStateOf(false) }

        // 当图片被选中且是第一次选择时，自动显示格式转换选项
        LaunchedEffect(selectedImageUri) {
            if (selectedImageUri != null && !showOptions) {
                showOptions = true
            }
        }

        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxSize()
            ) {
                // 主要内容区域
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()) // 添加垂直滚动支持
                ) {
                    Text(
                        text = "图片处理工具",
                        fontSize = 24.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Button(
                        onClick = {
                            onSelectImage()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isConverting
                    ) {
                        Text("选择图片")
                    }

                    if (selectedImageUri != null) {
                        // 改进的已选择图片显示样式
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Image,
                                    contentDescription = "已选择图片",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .padding(end = 8.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "已选择图片",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = selectedImageUri.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Text(
                            text = "选择处理方式:",
                            fontSize = 18.sp,
                            modifier = Modifier.padding(top = 16.dp)
                        )

                        // 处理方式选择
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Button(
                                onClick = {
                                    onProcessTypeChange(MainActivity.ProcessType.FORMAT_CONVERT)
                                    showOptions = true
                                },
                                enabled = !isConverting,
                                colors = if (processType == MainActivity.ProcessType.FORMAT_CONVERT) {
                                    ButtonDefaults.buttonColors()
                                } else {
                                    ButtonDefaults.outlinedButtonColors()
                                }
                            ) {
                                Text("格式转换")
                            }
                            Button(
                                onClick = {
                                    onProcessTypeChange(MainActivity.ProcessType.COMPRESS)
                                    showOptions = true
                                },
                                enabled = !isConverting,
                                colors = if (processType == MainActivity.ProcessType.COMPRESS) {
                                    ButtonDefaults.buttonColors()
                                } else {
                                    ButtonDefaults.outlinedButtonColors()
                                }
                            ) {
                                Text("压缩")
                            }
                            Button(
                                onClick = {
                                    onProcessTypeChange(MainActivity.ProcessType.ENLARGE)
                                    showOptions = true
                                },
                                enabled = !isConverting,
                                colors = if (processType == MainActivity.ProcessType.ENLARGE) {
                                    ButtonDefaults.buttonColors()
                                } else {
                                    ButtonDefaults.outlinedButtonColors()
                                }
                            ) {
                                Text("增大")
                            }
                        }

                        // 根据处理方式显示不同选项
                        AnimatedVisibility(
                            visible = showOptions,
                            enter = fadeIn() + slideInVertically(),
                            exit = fadeOut() + slideOutVertically()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                            ) {
                                when (processType) {
                                    MainActivity.ProcessType.FORMAT_CONVERT -> {
                                        Text(
                                            text = "选择目标格式:",
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        )

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(vertical = 8.dp)
                                        ) {
                                            Button(
                                                onClick = { onTargetFormatChange(Bitmap.CompressFormat.JPEG) },
                                                colors = if (targetFormat == Bitmap.CompressFormat.JPEG) {
                                                    ButtonDefaults.buttonColors()
                                                } else {
                                                    ButtonDefaults.outlinedButtonColors()
                                                }
                                            ) {
                                                Text("JPEG")
                                            }
                                            Button(
                                                onClick = { onTargetFormatChange(Bitmap.CompressFormat.PNG) },
                                                colors = if (targetFormat == Bitmap.CompressFormat.PNG) {
                                                    ButtonDefaults.buttonColors()
                                                } else {
                                                    ButtonDefaults.outlinedButtonColors()
                                                }
                                            ) {
                                                Text("PNG")
                                            }
                                            Button(
                                                onClick = { onTargetFormatChange(Bitmap.CompressFormat.WEBP) },
                                                colors = if (targetFormat == Bitmap.CompressFormat.WEBP) {
                                                    ButtonDefaults.buttonColors()
                                                } else {
                                                    ButtonDefaults.outlinedButtonColors()
                                                }
                                            ) {
                                                Text("WEBP")
                                            }
                                        }
                                    }

                                    MainActivity.ProcessType.COMPRESS -> {
                                        Text(
                                            text = "选择压缩强度:",
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        )

                                        // 自定义分段控件
                                        val options = listOf("轻度", "普通", "强力", "极强")
                                        val selectedIndex = when (compressionLevel) {
                                            MainActivity.CompressionLevel.LIGHT -> 0
                                            MainActivity.CompressionLevel.NORMAL -> 1
                                            MainActivity.CompressionLevel.STRONG -> 2
                                            MainActivity.CompressionLevel.EXTREME -> 3
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp)
                                                .height(56.dp) // 增加高度
                                        ) {
                                            options.forEachIndexed { index, option ->
                                                Button(
                                                    onClick = {
                                                        when (index) {
                                                            0 -> onCompressionLevelChange(
                                                                MainActivity.CompressionLevel.LIGHT
                                                            )

                                                            1 -> onCompressionLevelChange(
                                                                MainActivity.CompressionLevel.NORMAL
                                                            )

                                                            2 -> onCompressionLevelChange(
                                                                MainActivity.CompressionLevel.STRONG
                                                            )

                                                            3 -> onCompressionLevelChange(
                                                                MainActivity.CompressionLevel.EXTREME
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight(),
                                                    colors = if (index == selectedIndex) {
                                                        ButtonDefaults.buttonColors()
                                                    } else {
                                                        ButtonDefaults.outlinedButtonColors()
                                                    },
                                                    shape = when (index) {
                                                        0 -> RoundedCornerShape(
                                                            topStart = 8.dp,
                                                            bottomStart = 8.dp,
                                                            topEnd = 0.dp,
                                                            bottomEnd = 0.dp
                                                        )

                                                        options.size - 1 -> RoundedCornerShape(
                                                            topStart = 0.dp,
                                                            bottomStart = 0.dp,
                                                            topEnd = 8.dp,
                                                            bottomEnd = 8.dp
                                                        )

                                                        else -> RoundedCornerShape(0.dp)
                                                    },
                                                    contentPadding = PaddingValues(0.dp) // 移除默认内边距
                                                ) {
                                                    Text(
                                                        text = option,
                                                        fontSize = 14.sp,
                                                        modifier = Modifier.padding(horizontal = 4.dp)
                                                    )
                                                }

                                                // 添加间距（除了最后一个按钮）
                                                if (index < options.size - 1) {
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                }
                                            }
                                        }

                                        // 详细说明
                                        Text(
                                            text = when (compressionLevel) {
                                                MainActivity.CompressionLevel.LIGHT -> "质量: 85%"
                                                MainActivity.CompressionLevel.NORMAL -> "质量: 75%"
                                                MainActivity.CompressionLevel.STRONG -> "质量: 60%"
                                                MainActivity.CompressionLevel.EXTREME -> "质量: 40%"
                                            },
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 12.dp)
                                        )
                                    }

                                    MainActivity.ProcessType.ENLARGE -> {
                                        Text(
                                            text = "图像体积:",
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(bottom = 16.dp)
                                        )

                                        // 图像体积设置行
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // 标签
                                            Text(
                                                text = "图像体积:",
                                                fontSize = 16.sp,
                                                modifier = Modifier.width(80.dp)
                                            )

                                            // 输入框
                                            OutlinedTextField(
                                                value = if (dataSize == 0) "" else dataSize.toString(),
                                                onValueChange = { newValue ->
                                                    if (newValue.isEmpty()) {
                                                        onDataSizeChange(0)
                                                    } else {
                                                        val intValue = newValue.toIntOrNull()
                                                        if (intValue != null && intValue in 0..999) {
                                                            onDataSizeChange(intValue)
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .width(100.dp)
                                                    .height(56.dp),
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Number
                                                ),
                                                textStyle = androidx.compose.ui.text.TextStyle(
                                                    fontSize = 16.sp
                                                ),
                                                placeholder = { Text("0") }
                                            )


                                            // 单位下拉选择
                                            var expanded by remember { mutableStateOf(false) }
                                            val options = listOf(
                                                MainActivity.DataUnit.KB,
                                                MainActivity.DataUnit.MB
                                            )

                                            ExposedDropdownMenuBox(
                                                expanded = expanded,
                                                onExpandedChange = { expanded = it }
                                            ) {
                                                OutlinedTextField(
                                                    value = dataUnit.name,
                                                    onValueChange = {},
                                                    readOnly = true,
                                                    modifier = Modifier
                                                        .menuAnchor()
                                                        .width(100.dp)
                                                        .height(56.dp),
                                                    textStyle = androidx.compose.ui.text.TextStyle(
                                                        fontSize = 16.sp
                                                    ),
                                                    trailingIcon = {
                                                        Icon(
                                                            imageVector = androidx.compose.material.icons.Icons.Filled.ArrowDropDown,
                                                            contentDescription = null
                                                        )
                                                    }
                                                )
                                                ExposedDropdownMenu(
                                                    expanded = expanded,
                                                    onDismissRequest = { expanded = false }
                                                ) {
                                                    options.forEach { unit ->
                                                        DropdownMenuItem(
                                                            text = { Text(unit.name) },
                                                            onClick = {
                                                                onDataUnitChange(unit)
                                                                expanded = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 显示当前设置的文件大小信息
                                        Text(
                                            text = "将增加约 ${dataSize}${dataUnit.name} 的数据",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .padding(top = 12.dp)
                                                .padding(horizontal = 16.dp)
                                        )
                                    }
                                }

                                // 确认处理按钮
                                Button(
                                    onClick = {
                                        onConvertImage(targetFormat)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                    enabled = !isConverting
                                ) {
                                    Text("确认处理")
                                }

                                // 处理完成提示文字
                                if (convertedImagePath != null) {
                                    Text(
                                        text = "处理完成！文件已保存到相册",
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 底部署名和名言
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp)
                ) {
                    Divider(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        thickness = 1.dp
                    )

                    Text(
                        text = "—— yq",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Text(
                        text = "技术让生活更美好",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }

            // 遮罩层进度条
            AnimatedVisibility(
                visible = isConverting,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "正在处理图片...",
                            modifier = Modifier.padding(top = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
