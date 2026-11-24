package com.dicren.autospot

import android.app.Application
import com.dicren.autospot.di.AppContainer
import com.dicren.autospot.di.DefaultAppContainer

class AutoSpotApp : Application() {
    val container: AppContainer by lazy { DefaultAppContainer(this) }
}
