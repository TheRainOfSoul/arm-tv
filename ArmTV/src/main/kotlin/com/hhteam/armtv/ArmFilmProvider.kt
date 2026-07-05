package com.hhteam.armtv

import com.lagradost.cloudstream3.mainPageOf

/**
 * armfilm.co — DLE, локаль в пути: /hy/. Без Cloudflare.
 * Карточка каталога: <div class="shortstory …"> (.short-title, .short-imgposter).
 * Плеер: iframe, src которого JS выставляет на armplayer.armsites.am/player/…
 * (разбирается в DleProvider.loadLinks). Реклама (ads.caramel.am) в плеер не проходит.
 *
 * Категории сверены по живому сайту.
 */
class ArmFilmProvider : DleProvider() {
    override var mainUrl = "https://armfilm.co"
    override var name = "ArmFilm"

    // База с локалью /hy/.
    private val hy = "$mainUrl/hy"

    override val mainPage = mainPageOf(
        "$hy/hayeren-filmer/" to "Հայերեն ֆիլմեր (Фильмы)",
        "$hy/serialner/" to "Սերիալներ (Сериалы)",
        "$hy/serialner-hayeren-tarkmanutyamb/" to "Սերիալներ (Հայ. թարգմ.)",
        "$hy/doramaner-hayeren/" to "Դորամաներ (Дорамы)",
        "$hy/hayeren-multer/" to "Մուլտֆիլմեր (Мультфильмы)",
    )

    // Карточки armfilm: обычные div.shortstory + блок премьер article.shortstory-premiere.
    override val cardSelector = ".shortstory, .shortstory-premiere"
}
