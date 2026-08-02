package de.rosberg.onepluscamerabridge

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import de.rosberg.onepluscamerabridge.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val proxyComponent by lazy { ComponentName(this, CaptureProxyActivity::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.versionText.text = "Версия ${BuildConfig.VERSION_NAME}"

        binding.bridgeSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked != isBridgeEnabled()) {
                setBridgeEnabled(checked)
                LogStore.add(this, "Перехватчик внешней камеры ${if (checked) "включён" else "выключен"} пользователем")
                updateStatus()
            }
        }
        binding.testButton.setOnClickListener { runInternalTest() }
        binding.camerasButton.setOnClickListener { showCameras(select = false) }
        binding.selectCameraButton.setOnClickListener { showCameras(select = true) }
        binding.logButton.setOnClickListener { startActivity(Intent(this, LogActivity::class.java)) }
        binding.cleanButton.setOnClickListener {
            val count = TempFiles.cleanupAll(this)
            Prefs.setPendingPath(this, null)
            LogStore.add(this, "Ручная очистка временных файлов: удалено $count")
            toast("Удалено временных файлов: $count")
        }
        binding.emergencyButton.setOnClickListener { emergencyDisable() }
        binding.uninstallButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun isBridgeEnabled(): Boolean {
        return when (packageManager.getComponentEnabledSetting(proxyComponent)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> false
            else -> true
        }
    }

    private fun setBridgeEnabled(enabled: Boolean) {
        packageManager.setComponentEnabledSetting(
            proxyComponent,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun updateStatus() {
        val enabled = isBridgeEnabled()
        binding.bridgeSwitch.isChecked = enabled
        binding.statusText.text = if (enabled) {
            "Перехватчик внешней камеры включён"
        } else {
            "Перехватчик внешней камеры выключен"
        }
        val preferred = Prefs.preferredCamera(this)
        binding.preferredCameraText.text = "Предпочтительная камера: ${preferred ?: "не выбрана"}"
    }

    private fun runInternalTest() {
        if (!isBridgeEnabled()) {
            toast("Сначала включите мост")
            return
        }
        startActivity(Intent(this, CaptureProxyActivity::class.java).apply {
            action = MediaStore.ACTION_IMAGE_CAPTURE
            putExtra(CaptureProxyActivity.EXTRA_TEST_MODE, true)
        })
    }

    private fun showCameras(select: Boolean) {
        val cameras = CameraDiscovery.find(this)
        if (cameras.isEmpty()) {
            LogStore.add(this, "По ACTION_IMAGE_CAPTURE не найдено внешних обработчиков")
            toast("Приложения камер не найдены")
            return
        }
        LogStore.add(this, "Найденные камеры (${cameras.size}): ${cameras.joinToString { it.component.flattenToShortString() }}")
        val title = if (select) "Выберите штатную камеру" else "Найденные обработчики камеры"
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(cameras.map { it.display() }.toTypedArray()) { dialog, which ->
                if (select) {
                    Prefs.setPreferredCamera(this, cameras[which].component.flattenToString())
                    LogStore.add(this, "Выбрана камера ${cameras[which].component.flattenToShortString()}")
                    updateStatus()
                    toast("Камера выбрана")
                }
                dialog.dismiss()
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun emergencyDisable() {
        setBridgeEnabled(false)
        Prefs.setPreferredCamera(this, null)
        Prefs.setPendingPath(this, null)
        val count = TempFiles.cleanupAll(this)
        LogStore.add(this, "Аварийное отключение: компонент выключен, выбор камеры сброшен, удалено файлов: $count")
        updateStatus()
        toast("Мост аварийно отключён")
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
