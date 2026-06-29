package com.soutiwang.app

import android.app.Application
import com.soutiwang.app.data.AppDatabase
import com.soutiwang.app.data.QuestionRepository

class SoutiWangApp : Application() {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { QuestionRepository(database.questionDao()) }
}
