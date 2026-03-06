package icu.aetherland.dynamicd.runtime

interface ListenerHandle {
    fun unregister()
}

interface TaskHandle {
    fun cancel()
}

interface RuntimeBridge {
    fun bindEvent(moduleId: String, eventPath: String): ListenerHandle?
    fun bindTimer(moduleId: String, timerSpec: String): TaskHandle?
}
