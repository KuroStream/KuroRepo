package com.kurostream.extensions.aniwave

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import android.os.Handler

@CloudstreamPlugin
class AniwaveProviderPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(AniwaveProvider())
    }

    companion object {
        inline fun Handler.postFunction(crossinline function: () -> Unit) {
            this.post {
                function()
            }
        }
    }
}
