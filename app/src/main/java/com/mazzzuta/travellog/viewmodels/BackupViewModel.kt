package com.mazzzuta.travellog.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazzzuta.travellog.backup.BackupManager
import com.mazzzuta.travellog.backup.PreparedImport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ImportPreview(val entries: Int, val trips: Int, val photos: Int)
data class BackupUiState(val busy: Boolean = false, val operation: String = "",
    val preview: ImportPreview? = null, val message: String? = null)

class BackupViewModel(private val manager: BackupManager) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state = _state.asStateFlow()
    private var pending: PreparedImport? = null

    fun export(uri: Uri) {
        if (_state.value.busy || pending != null) return
        run("Создание архива…") {
            manager.export(uri)
            "Архив сохранён"
        }
    }

    fun prepareImport(uri: Uri) {
        if (_state.value.busy || pending != null) return
        run("Проверка архива…") {
            val prepared = manager.prepareImport(uri)
            pending = prepared
            _state.value = BackupUiState(preview = ImportPreview(prepared.data.entries.size,
                prepared.data.trips.size, prepared.data.photos.size))
            null
        }
    }

    fun cancelImport() {
        if (_state.value.busy) return
        val prepared = pending
        pending = null
        run("Отмена…") {
            withContext(Dispatchers.IO) { prepared?.discard() }
            _state.value = BackupUiState()
            null
        }
    }

    fun confirmImport() {
        val prepared = pending ?: return
        if (_state.value.busy) return
        run("Восстановление дневника…") {
            try {
                if (manager.restore(prepared)) "Дневник и настройки восстановлены"
                else "Дневник восстановлен, но настройки сохранить не удалось. Настройте оформление вручную."
            } finally {
                withContext(NonCancellable + Dispatchers.IO) { prepared.discard() }
                pending = null
            }
        }
    }

    private fun run(operation: String, block: suspend () -> String?) {
        _state.value = BackupUiState(busy = true, operation = operation)
        viewModelScope.launch {
            try {
                val message = block()
                if (message != null) _state.value = BackupUiState(message = message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = BackupUiState(message = when (operation) {
                    "Создание архива…" -> "Не удалось сохранить архив. Проверьте свободное место и доступ к файлу."
                    "Проверка архива…" -> "Архив повреждён, несовместим или недоступен. Дневник не изменён."
                    else -> "Не удалось восстановить дневник. Текущие записи сохранены."
                })
            }
        }
    }

    override fun onCleared() {
        val prepared = pending
        if (!_state.value.busy && prepared != null) {
            viewModelScope.launch(NonCancellable + Dispatchers.IO) { prepared.discard() }
        }
    }
}
