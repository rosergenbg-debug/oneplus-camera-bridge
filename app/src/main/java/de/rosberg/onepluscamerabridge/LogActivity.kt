package de.rosberg.onepluscamerabridge

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import de.rosberg.onepluscamerabridge.databinding.ActivityLogBinding

class LogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        refresh()
        binding.copyButton.setOnClickListener {
            val text = LogStore.text(this)
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("OnePlus Camera Bridge log", text))
            Toast.makeText(this, "Журнал скопирован", Toast.LENGTH_SHORT).show()
        }
        binding.clearLogButton.setOnClickListener {
            LogStore.clear(this)
            refresh()
        }
    }

    private fun refresh() {
        binding.logText.text = LogStore.text(this).ifBlank { "Журнал пока пуст." }
    }
}
