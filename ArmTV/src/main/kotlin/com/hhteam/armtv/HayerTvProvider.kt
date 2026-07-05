package com.hhteam.armtv

import com.lagradost.cloudstream3.mainPageOf

/**
 * hayertv.com — DLE, серверный HTML.
 * Особенность: активен модуль /engine/modules/antibot/antibot.php (JS-кука).
 * Обычный app.get это переживает, а loadLinks в любом случае идёт через WebView,
 * который исполняет JS и проходит antibot автоматически.
 *
 * Категории (из разведки): /armyanskie-seriali/, /filmer-hayeren/, /multfilmy/, /telekanali/.
 */
class HayerTvProvider : DleProvider() {
    override var mainUrl = "https://hayertv.com"
    override var name = "HayerTV"

    override val mainPage = mainPageOf(
        "$mainUrl/filmer-hayeren/" to "Հայերեն ֆիլմեր (Фильмы)",
        "$mainUrl/armyanskie-seriali/" to "Հայկական սերիալներ (Сериалы)",
        "$mainUrl/doramy/" to "Դորամաներ (Дорамы)",
        "$mainUrl/multfilmy/" to "Մուլտֆիլմեր (Мультфильмы)",
    )

    // TODO: сверить с реальным HTML hayertv.com.
    override val cardSelector = "div.th-item, div.shortstory, div.short, article"
    override val titleInCardSelector = "h6, h3, .th-title, .title"
}
