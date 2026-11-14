package com.example.robottirabasura

import android.app.Application
import com.example.robottirabasura.data.history.HistoryRecorder
import com.example.robottirabasura.data.mirror.VisorMirror
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class RobotApplication : Application() {

    @Inject
    lateinit var historyRecorder: HistoryRecorder

    /** Does nothing unless robot.mujocoVisorUrl is set in local.properties. */
    @Inject
    lateinit var visorMirror: VisorMirror

    override fun onCreate() {
        super.onCreate()
        historyRecorder.start()
        visorMirror.start()
    }
}
