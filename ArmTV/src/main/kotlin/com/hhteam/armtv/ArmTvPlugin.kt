package com.hhteam.armtv

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

/**
 * Точка входа расширения. Один .cs3 = все три сайта сразу.
 * Добавить новый сайт = ещё один DleProvider-наследник + строка registerMainAPI ниже.
 */
@CloudstreamPlugin
class ArmTvPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FilmsBzProvider())
        registerMainAPI(HayerTvProvider())
        registerMainAPI(ArmFilmProvider())
    }
}
