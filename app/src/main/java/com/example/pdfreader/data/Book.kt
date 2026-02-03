package com.example.pdfreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val uri: String,
    val addedAt: Long,
    val lastOpenedPage: Int,
    val totalPages: Int
)
