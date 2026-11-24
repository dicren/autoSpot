package com.dicren.autospot

import android.app.Application
import com.dicren.autospot.di.AppContainer
import com.dicren.autospot.di.DefaultAppContainer

class AutoSpotApp : Application() {
    private val defaultContainer: AppContainer by lazy { DefaultAppContainer(this) }

    /** Test seam: services and screens read the container through this getter. */
    internal var containerOverride: AppContainer? = null

    val container: AppContainer
        get() = containerOverride ?: defaultContainer
}
