package com.example

import android.app.Application
import com.example.data.local.FindAnythingDatabase
import com.example.data.repository.SearchRepository
import com.example.engine.ActionEngine
import com.example.engine.VoiceEngine

class FindAnythingApp : Application() {

    lateinit var database: FindAnythingDatabase
        private set

    lateinit var repository: SearchRepository
        private set

    lateinit var actionEngine: ActionEngine
        private set

    lateinit var voiceEngine: VoiceEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = FindAnythingDatabase.getInstance(this)
        repository = SearchRepository(this, database)
        actionEngine = ActionEngine(this)
        voiceEngine = VoiceEngine(this)
    }

    companion object {
        lateinit var instance: FindAnythingApp
            private set
    }
}
