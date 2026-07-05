package com.hhteam.armtv

import com.lagradost.cloudstream3.mainPageOf

/**
 * films.bz — DLE, серверный HTML, без antibot, без Cloudflare.
 * Карточки: <a> с <img src="/img-medium/uploads/posts/..."> и <h6> с названием.
 * Детали: /films/{id}-{slug}.html и /serial/{id}-{slug}.html
 */
class FilmsBzProvider : DleProvider() {
    override var mainUrl = "https://films.bz"
    override var name = "Films.bz"

    // Ряды главного экрана (категории DLE). При необходимости — сверить URL по сайту.
    override val mainPage = mainPageOf(
        "$mainUrl/films/" to "Ֆիլմեր (Фильмы)",
        "$mainUrl/serial/" to "Սերիալներ (Сериалы)",
        "$mainUrl/multfilmy/" to "Մուլտֆիլմեր (Мультфильмы)",
        "$mainUrl/anime/" to "Անիմե (Аниме)",
    )

    // TODO: сверить с реальным HTML films.bz и уточнить при необходимости.
    override val cardSelector = "div.th-item, div.shortstory, div.short, article"
    override val titleInCardSelector = "h6, .th-title, .title"
}
