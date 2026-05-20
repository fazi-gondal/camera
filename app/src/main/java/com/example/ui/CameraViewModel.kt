package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.PhotoEntity
import com.example.data.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PhotoRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = PhotoRepository(database.photoDao())
    }

    // List of saved photos
    val photos: StateFlow<List<PhotoEntity>> = repository.allPhotos
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current capturing state
    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    // Note/tag to append to stamp
    private val _noteText = MutableStateFlow("")
    val noteText: StateFlow<String> = _noteText.asStateFlow()

    // Stamp colors to select from (Standard neon/material colors)
    val availableColors = listOf(
        "#FFEB3B" to "Yellow",
        "#FFFFFF" to "White",
        "#00E676" to "Neon Green",
        "#00B0FF" to "Blue Shade",
        "#E040FB" to "Electric Pink",
        "#FF9100" to "Retro Amber"
    )

    // Current selected stamp color
    private val _selectedColorHex = MutableStateFlow("#FFEB3B") // default yellow
    val selectedColorHex: StateFlow<String> = _selectedColorHex.asStateFlow()

    // Current chosen style index (0: Standard, 1: Vintage LED, 2: Cyber Banner, 3: Stamp Badge)
    private val _selectedStyleIndex = MutableStateFlow(0)
    val selectedStyleIndex: StateFlow<Int> = _selectedStyleIndex.asStateFlow()

    fun updateNoteText(text: String) {
        _noteText.value = text
    }

    fun updateSelectedColor(hex: String) {
        _selectedColorHex.value = hex
    }

    fun updateSelectedStyle(index: Int) {
        _selectedStyleIndex.value = index
    }

    fun setCapturing(capturing: Boolean) {
        _isCapturing.value = capturing
    }

    fun savePhotoMetadata(filePath: String, customLabel: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(now))
            val entity = PhotoEntity(
                filePath = filePath,
                timestamp = now,
                formattedDateString = formattedDate,
                note = customLabel,
                stampColorHex = _selectedColorHex.value
            )
            repository.insertPhoto(entity)
        }
    }

    fun deletePhoto(photo: PhotoEntity) {
        viewModelScope.launch {
            // Delete the local physical file first to release storage!
            try {
                val file = File(photo.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Ignore file deletion errors
            }
            repository.deletePhoto(photo)
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CameraViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return CameraViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
