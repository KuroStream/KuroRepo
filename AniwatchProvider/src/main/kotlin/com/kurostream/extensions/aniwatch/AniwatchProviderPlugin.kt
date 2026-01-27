package com.kurostream.extensions.aniwatch

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AniwatchProviderPlugin: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AniwatchProvider())
    }
}
