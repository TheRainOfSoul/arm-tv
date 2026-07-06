package com.hhteam.armtv

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.mainPageOf
import org.jsoup.nodes.Document

/**
 * hayertv.com — DLE, серверный HTML.
 * Карточка каталога: <div class="item"> с постером .image-poster и <a href title>.
 * (Раньше каталог был пуст: старый селектор .shortstory не совпадал — реальный класс .item.)
 * Плеер: несколько статических iframe (armdb.net/embed, fsst.online/embed) — их ловит
 * WebView-сниффер в DleProvider.loadLinks.
 *
 * Категории сверены по живому сайту.
 */
class HayerTvProvider : DleProvider() {
    override var mainUrl = "https://hayertv.com"
    override var name = "HayerTV"

    override val mainPage = mainPageOf(
        "$mainUrl/filmer-hayeren/" to "Հայերեն ֆիլմեր (Фильмы)",
        "$mainUrl/armyanskie-seriali/" to "Հայկական սերիալներ (Сериалы)",
        "$mainUrl/seriali-armyanskim-perevodom/" to "Սերիալներ (Рус. перевод)",
        "$mainUrl/doramaarm/" to "Դորամաներ (Дорамы)",
        "$mainUrl/multfilm-hayeren/" to "Մուլտֆիլմեր (Мультфильмы)",
    )

    // Карточки основного списка категории: фильмы → .shortstory-news, сериалы/поиск → .short-collections.
    // ВАЖНО: не использовать .item — это общий сайдбар-виджет «последнее», одинаковый на всех
    // страницах (из-за него раньше во всех категориях были одни и те же фильмы).
    override val cardSelector = "div.short-collections, div.shortstory-news"

    /**
     * Список серий hayertv лежит прямо на странице сериала — в одном из двух шаблонов:
     *  1) новый video.js-плеер: JS-массив `const episodes = [{ id, title, url }]` c ПРЯМЫМИ
     *     mp4 на hayertv.com/Stream/… — предпочитаем его (надёжнее старой fsst→incvideo цепочки);
     *  2) старый шаблон: <select id="series"><option value="EMBED">Название</option>, где value —
     *     это src iframe: youtube/embed, fsst.online/embed, //ok.ru/videoembed и т.п.
     * Каждый источник (mp4 / эмбед) раскрывается в DleProvider.loadLinks по значению Episode.data.
     */
    override suspend fun parseEpisodes(doc: Document, pageUrl: String): List<Episode> {
        val html = doc.html()

        // (1) Новый шаблон: прямые mp4.
        val direct = Regex("""\{\s*id:\s*\d+\s*,\s*title:\s*'([^']*)'\s*,\s*url:\s*'([^']*)'\s*}""")
            .findAll(html)
            .map { it.groupValues[1].trim() to it.groupValues[2].trim() }
            .toList()

        // (2) Универсальный <select id="series"> (и на всякий — #episodeSelect).
        val pairs: List<Pair<String, String>> = if (direct.isNotEmpty()) {
            direct
        } else {
            doc.select("select#series option, select#episodeSelect option")
                .map { it.text().trim() to it.attr("value").trim() }
                .filter { it.second.isNotBlank() }
        }

        return pairs.mapNotNull { (title, url) -> buildEpisode(title, url) }
            .distinctBy { it.data }
    }
}
