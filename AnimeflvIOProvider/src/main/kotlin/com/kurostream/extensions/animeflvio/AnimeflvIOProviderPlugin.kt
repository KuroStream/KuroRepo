package com.kurostream.extensions.animeflvio

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.kurostream.extensions.animeflvio.AnimeflvIOProvider

@CloudstreamPlugin
class AnimeflvIOProviderPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(AnimeflvIOProvider())
    }
}
