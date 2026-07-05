package com.hhteam.armtv

import com.lagradost.cloudstream3.*

/**
 * armfilm.co — DLE, локаль в пути: /hy/. Без Cloudflare.
 * Карточка каталога: <div class="shortstory …"> (.short-title, .short-imgposter).
 * Плеер: iframe, src которого JS выставляет на armdb.org/player… (разбирается в DleProvider.loadLinks).
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

    /**
     * Обычный DLE-поиск (index.php?do=search) у armfilm отдаёт «MySQL Fatal Error»,
     * а кастомный ajax-эндпоинт удалён (404). Рабочий поиск сайта — friendly-URL
     * /hy/search/<запрос>/ (GET), результаты в тех же .shortstory-карточках.
     */
    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim().replace(" ", "+")
        val doc = app.get("$hy/search/$q/", referer = "$hy/").document
        return doc.select(cardSelector).mapNotNull { it.toSearchResult() }.distinctBy { it.url }
    }
}
