package com.example.data

import kotlinx.coroutines.flow.Flow

class PhotoRepository(private val photoDao: PhotoDao) {
    val allPhotos: Flow<List<PhotoEntity>> = photoDao.getAllPhotos()

    suspend fun insertPhoto(photo: PhotoEntity): Long {
        return photoDao.insertPhoto(photo)
    }

    suspend fun deletePhoto(photo: PhotoEntity) {
        photoDao.deletePhoto(photo)
    }

    suspend fun deleteById(id: Int) {
        photoDao.deleteById(id)
    }
}
