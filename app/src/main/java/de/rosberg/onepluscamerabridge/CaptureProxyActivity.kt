package de.rosberg.onepluscamerabridge

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import de.rosberg.onepluscamerabridge.databinding.ActivityCaptureProxyBinding
import java.io.File

class CaptureProxyActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_TEST_MODE = "bridge_test_mode"
        const val EXTRA_INTERNAL_REQUEST = "bridge_internal_request"
        private const val STATE_STARTED = "capture_started"
        private const val STATE_PATH = "capture_path"
    }

    private lateinit var binding: ActivityCaptureProxyBinding
    private var tempFile: File? = null
    private var tempUri: Uri? = null
    private var selectedCamera: CameraHandler? = null
    private val testMode get() = intent.getBooleanExtra(EXTRA_TEST_MODE, false)

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val camera = selectedCamera
        tempUri?.let { uri -> camera?.packageName?.let { revokeUriPermission(it, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
        if (result.resultCode != Activity.RESULT_OK) {
            val reason = "Камера вернула отмену или ошибку: resultCode=${result.resultCode}"
            LogStore.add(this, reason)
            finishWithError(reason)
            return@registerForActivityResult
        }
        handleCapturedFile()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureProxyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent.getBooleanExtra(EXTRA_INTERNAL_REQUEST, false)) {
            finishWithError("Защита от рекурсии: CaptureProxyActivity получила внутренний запрос моста")
            return
        }
        if (!testMode && intent.action != MediaStore.ACTION_IMAGE_CAPTURE && intent.action != MediaStore.ACTION_IMAGE_CAPTURE_SECURE) {
            finishWithError("Отклонено неподдерживаемое действие: ${intent.action}")
            return
        }

        tempFile = savedInstanceState?.getString(STATE_PATH)?.let(::File)
        tempUri = tempFile?.let(::fileProviderUri)
        binding.closeButton.setOnClickListener { finishTest() }
        logIncomingRequest()

        val wasStarted = savedInstanceState?.getBoolean(STATE_STARTED, false) == true
        if (!wasStarted) selectAndLaunchCamera()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_STARTED, tempFile != null)
        outState.putString(STATE_PATH, tempFile?.absolutePath)
        super.onSaveInstanceState(outState)
    }

    private fun logIncomingRequest() {
        val keys = intent.extras?.keySet()?.sorted()?.joinToString().orEmpty().ifBlank { "нет" }
        val output = originalOutputUri()
        val caller = callingPackage ?: callingActivity?.packageName ?: "не определён"
        val ref = referrer?.toString() ?: "нет"
        LogStore.add(
            this,
            "Получен запрос: action=${intent.action}; caller=$caller; referrer=$ref; " +
                "extras=[$keys]; EXTRA_OUTPUT=${output != null}; outputUri=${output?.safeForLog() ?: "нет"}; test=$testMode"
        )
    }

    private fun selectAndLaunchCamera() {
        val action = intent.action?.takeIf {
            it == MediaStore.ACTION_IMAGE_CAPTURE || it == MediaStore.ACTION_IMAGE_CAPTURE_SECURE
        } ?: MediaStore.ACTION_IMAGE_CAPTURE
        val handlers = CameraDiscovery.find(this, action)
        if (handlers.isEmpty()) {
            finishWithError("Не найдена внешняя камера для $action. Собственный компонент исключён.")
            return
        }
        LogStore.add(this, "Доступные внешние обработчики (${handlers.size}): ${handlers.joinToString { it.component.flattenToShortString() }}")
        val preferred = Prefs.preferredCamera(this)
        val selected = handlers.firstOrNull { it.component.flattenToString() == preferred }
        if (selected != null) {
            launchCamera(selected, action)
            return
        }
        val labels = handlers.mapIndexed { index, camera ->
            val recommendation = if (index == 0 && camera.isSystem) "\nРекомендуется как системная камера" else ""
            camera.display() + recommendation
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Выберите штатную камеру")
            .setItems(labels) { dialog, which ->
                val camera = handlers[which]
                Prefs.setPreferredCamera(this, camera.component.flattenToString())
                dialog.dismiss()
                launchCamera(camera, action)
            }
            .setOnCancelListener { finishWithError("Выбор камеры отменён") }
            .show()
    }

    private fun launchCamera(camera: CameraHandler, action: String) {
        selectedCamera = camera
        try {
            val file = TempFiles.create(this)
            tempFile = file
            Prefs.setPendingPath(this, file.absolutePath)
            val uri = fileProviderUri(file)
            tempUri = uri
            val cameraIntent = Intent(action).apply {
                component = camera.component
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                putExtra(EXTRA_INTERNAL_REQUEST, true)
                clipData = ClipData.newRawUri("OnePlus Camera Bridge output", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            grantUriPermission(
                camera.packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            LogStore.add(this, "Запуск камеры: ${camera.component.flattenToShortString()}; tempUri=${uri.safeForLog()}")
            binding.resultText.text = "Открывается: ${camera.label}\n${camera.packageName}"
            cameraLauncher.launch(cameraIntent)
        } catch (e: Exception) {
            finishWithError("Не удалось запустить камеру ${camera.component.flattenToShortString()}: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun handleCapturedFile() {
        val file = tempFile ?: run {
            finishWithError("Путь временного файла потерян")
            return
        }
        try {
            val info = ImageUtils.inspect(file)
            LogStore.add(this, "JPEG получен: ${info.width}x${info.height}; ${info.bytes} байт; ${info.mime}; EXIF model=${info.model ?: "нет"}")
            if (testMode) {
                showTestResult(file, info)
            } else {
                deliverToCaller(file)
            }
        } catch (e: Exception) {
            finishWithError("Проверка фотографии не пройдена: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun showTestResult(file: File, info: CapturedImageInfo) {
        binding.progress.visibility = View.GONE
        binding.previewImage.setImageBitmap(ImageUtils.thumbnail(file, 1200))
        binding.previewImage.visibility = View.VISIBLE
        binding.resultText.text = "Тест успешно завершён\n\n${info.russianSummary()}"
        binding.closeButton.visibility = View.VISIBLE
        LogStore.add(this, "Внутренний тест: миниатюра показана; файл будет удалён при закрытии")
    }

    private fun deliverToCaller(file: File) {
        val destination = originalOutputUri()
        try {
            if (destination != null) {
                require(destination.scheme == "content") { "Разрешены только content:// URI назначения" }
                contentResolver.openOutputStream(destination, "w")?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                } ?: error("ContentResolver не открыл URI назначения")
                setResult(Activity.RESULT_OK)
                LogStore.add(this, "JPEG скопирован в URI вызывающего приложения; результат RESULT_OK")
            } else {
                val thumbnail = ImageUtils.thumbnail(file, 384)
                setResult(Activity.RESULT_OK, Intent().putExtra("data", thumbnail))
                LogStore.add(this, "EXTRA_OUTPUT отсутствовал; возвращён уменьшенный Bitmap ${thumbnail.width}x${thumbnail.height}")
            }
            cleanupTemp()
            finish()
        } catch (e: SecurityException) {
            finishWithError("Нет предоставленного разрешения на URI назначения: SecurityException: ${e.message}")
        } catch (e: Exception) {
            finishWithError("Не удалось передать JPEG вызывающему приложению: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun originalOutputUri(): Uri? = if (Build.VERSION.SDK_INT >= 33) {
        intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT)
    }

    private fun finishWithError(message: String) {
        LogStore.add(this, "ОШИБКА: $message")
        binding.progress.visibility = View.GONE
        binding.resultText.text = message
        setResult(Activity.RESULT_CANCELED, Intent().putExtra("bridge_error", message))
        cleanupTemp()
        finish()
    }

    private fun finishTest() {
        cleanupTemp()
        finish()
    }

    private fun cleanupTemp() {
        val file = tempFile
        tempUri?.let { uri ->
            runCatching {
                revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        }
        val deleted = TempFiles.delete(file)
        Prefs.setPendingPath(this, null)
        if (file != null) LogStore.add(this, "Временный файл удалён=$deleted")
        tempFile = null
        tempUri = null
    }

    private fun fileProviderUri(file: File): Uri = FileProvider.getUriForFile(
        this, "$packageName.fileprovider", file
    )

    private fun Uri.safeForLog(): String = "$scheme://${authority ?: ""}/…/${lastPathSegment ?: ""}"

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) cleanupTemp()
        super.onDestroy()
    }
}
