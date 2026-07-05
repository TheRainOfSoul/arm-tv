package com.hhteam.armtv

import com.lagradost.cloudstream3.mainPageOf

/**
 * films.bz — DLE, серверный HTML, без antibot, без Cloudflare.
 * Карточка каталога: <div class="short …"> с <a class="short_img" title="…"> и <img>.
 * Детали: /films/{id}-{slug}.html, /serial/{id}-{slug}.html и т.п.
 * Плеер: iframe.player, src которого JS выставляет на api.ortified.ws/embed/movie/{id}
 * (разбирается в DleProvider.loadLinks).
 *
 * Категории сверены по живому сайту (детальные ссылки на главной).
 */
class FilmsBzProvider : DleProvider() {
    override var mainUrl = "https://films.bz"
    override var name = "Films.bz"

    override val mainPage = mainPageOf(
        "$mainUrl/films/" to "Ֆիլմեր (Фильмы)",
        "$mainUrl/serial/" to "Սերիալներ (Сериалы)",
        "$mainUrl/hayeren-targmanutyamb/" to "Հայերեն թարգմանությամբ",
        "$mainUrl/cartoons/" to "Մուլտֆիլմեր (Мультфильмы)",
        "$mainUrl/anime/" to "Անիմե (Аниме)",
    )

    // Реальный контейнер карточки films.bz — это <article class="short …">, НЕ div.
    // Поэтому селектор по классу без тега (иначе каталог пуст).
    override val cardSelector = ".short"
}
