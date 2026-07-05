package com.hhteam.armtv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.WebViewResolver   // переехал в пакет network
import com.lagradost.cloudstream3.mvvm.logError             // логер-хелпер лежит в mvvm
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Базовый провайдер для сайтов на движке DLE (DataLife Engine).
 *
 * films.bz, hayertv.com, armfilm.co — все три на DLE, серверный HTML, без Cloudflare.
 * Поэтому 90% логики (каталог, поиск, карточка) общая — она здесь.
 * Наследники задают только: mainUrl, name, mainPage-ряды и, при необходимости,
 * переопределяют CSS-селекторы карточек/описания.
 *
 * ВАЖНО (честно): точные CSS-классы карточек WebFetch по домашним страницам дать не смог
 * (плеер и часть вёрстки подставляются JS). Селекторы ниже — типовые для DLE и заданы как
 * open-поля. Первый шаг после открытия проекта в Android Studio — один раз сверить их
 * с реальным HTML (Elements Inspector в браузере или прямой лог doc.html()) и поправить.
 * Логика извлечения потока (loadLinks) от селекторов НЕ зависит — она универсальна.
 */
abstract class DleProvider : MainAPI() {

    override val hasMainPage = true
    override var lang = "hy"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // -------- Селекторы (переопределяемые в наследниках; дефолты — типовой DLE) --------

    /** Контейнер одной карточки фильма/сериала. Наследники задают точный класс. */
    open val cardSelector = "div.short, div.shortstory, div.item, article"

    /** Заголовок внутри карточки. */
    open val titleInCardSelector = "h6, h3, .th-title, .title, .short-title"

    /** Описание на странице деталей. */
    open val descriptionSelector = ".fdesc, .full-text, [itemprop=description], .fst-item p"

    /**
     * Ссылки на серии внутри страницы сериала (плейлист DLE-балансёра).
     * Если пусто — контент трактуется как фильм.
     */
    open val episodeListSelector = ".pgs-links a, .video-nav a, .serial-tabs a"

    // ------------------------------- Главная -------------------------------

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        // DLE-пагинация категорий: .../category/page/2/
        val url = if (page <= 1) request.data else "${request.data.trimEnd('/')}/page/$page/"
        val doc = app.get(url, referer = mainUrl).document
        val items = doc.select(cardSelector).mapNotNull { it.toSearchResult() }.distinctBy { it.url }
        return newHomePageResponse(request.name, items)
    }

    // ------------------------------- Поиск -------------------------------

    override suspend fun search(query: String): List<SearchResponse> {
        // Стандартный поиск DLE — один и тот же на всех трёх сайтах.
        val doc = app.post(
            "$mainUrl/index.php?do=search",
            referer = mainUrl,
            data = mapOf(
                "do" to "search",
                "subaction" to "search",
                "search_start" to "0",
                "full_search" to "0",
                "result_from" to "1",
                "story" to query,
            )
        ).document
        return doc.select(cardSelector).mapNotNull { it.toSearchResult() }.distinctBy { it.url }
    }

    // ------------------------------- Карточка -------------------------------

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, referer = mainUrl).document

        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
        val poster = fixUrlNull(
            doc.selectFirst("meta[property=og:image]")?.attr("content")
                ?: doc.selectFirst(".fposter img, .short-poster img")?.absUrl("src")
        )
        val plot = doc.selectFirst("meta[property=og:description]")?.attr("content")
            ?: doc.selectFirst(descriptionSelector)?.text()
        val year = Regex("""\b(19|20)\d{2}\b""")
            .find(doc.selectFirst(".fmeta, .short-meta")?.text() ?: "")
            ?.value?.toIntOrNull()

        val episodes = parseEpisodes(doc, url)

        return if (episodes.isNotEmpty()) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        } else {
            // dataUrl = url страницы: именно её потом «нюхает» loadLinks
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        }
    }

    /**
     * Разбор списка серий. На DLE-сайтах серии обычно — соседние страницы
     * (…-seria-11.html, …-seria-12.html), собранные в плейлист на странице.
     * Дефолт покрывает частый случай; при необходимости переопредели в наследнике.
     */
    protected open suspend fun parseEpisodes(doc: Document, pageUrl: String): List<Episode> {
        return doc.select(episodeListSelector).mapNotNull { a ->
            val href = a.absUrl("href").ifBlank { a.attr("data-url") }
            if (href.isBlank() || href == "#") return@mapNotNull null
            val label = a.text().trim().ifBlank { "Серия" }
            newEpisode(fixUrl(href)) {
                this.name = label
                this.episode = Regex("""\d+""").find(label)?.value?.toIntOrNull()
            }
        }
    }

    // ------------------------------- Извлечение потока -------------------------------

    /**
     * Универсальный сниффер. Не парсит зашифрованный плеер вручную, а:
     *   1) собирает URL встроенных плееров (iframe / data-атрибуты DLE-балансёра);
     *   2) грузит их в скрытом WebView (WebViewResolver), даёт JS отработать
     *      и ЛОВИТ .m3u8/.mp4 из сетевых запросов;
     *   3) master.m3u8 разворачивается в несколько качеств (M3u8Helper) —
     *      это и есть «смена качества» в плеере CloudStream.
     *
     * Реклама и попапы сюда не попадают: в плеер уходит ТОЛЬКО ссылка на видео.
     */
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, referer = mainUrl).document
        val pageHtml = doc.html()

        // 1) Явные iframe'ы и data-атрибуты DLE-балансёра (hayertv: armdb.net, fsst.online).
        val fromTags = doc.select("iframe[src], iframe[data-src], [data-file], [data-player], [data-src]")
            .map {
                it.attr("src")
                    .ifBlank { it.attr("data-src") }
                    .ifBlank { it.attr("data-file") }
                    .ifBlank { it.attr("data-player") }
            }

        // 2) URL плеера, который JS вписывает в iframe уже после загрузки страницы:
        //    films.bz → api.ortified.ws/embed/movie/{id}, armfilm → armplayer.armsites.am/player/…
        //    В сыром HTML такого iframe.src нет, поэтому достаём регуляркой из инлайн-скриптов.
        val fromJs = Regex("""["'](https?://[^"']*/(?:embed|player)/[^"']*)["']""")
            .findAll(pageHtml).map { it.groupValues[1] }.toList()

        val embeds = (fromTags + fromJs)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { httpsify(fixUrl(it)) }
            .filterNot { u -> BLOCKED_EMBED_HOSTS.any { u.contains(it, ignoreCase = true) } }
            .distinct()
            .take(8)

        // Фолбэк: если явных эмбедов нет — нюхаем саму страницу деталей.
        val targets = if (embeds.isNotEmpty()) embeds else listOf(data)

        var found = false
        for (target in targets) {
            try {
                // a) Прямой разбор: у части балансёров master.m3u8 лежит прямо в HTML эмбеда
                //    (напр. ortified.ws). Токен в URL живёт недолго — тянем на момент запуска.
                val embedBody = app.get(target, referer = mainUrl).text
                val directM3u8 = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""").find(embedBody)?.value
                if (directM3u8 != null) {
                    M3u8Helper.generateM3u8(name, directM3u8, target).forEach(callback)
                    found = true
                    continue
                }

                // b) Иначе — универсальный WebView-сниффер: JS сам догрузит поток
                //    (и заодно проходит DLE-antibot: исполняет JS и ставит куку).
                val resolved = app.get(
                    target,
                    referer = mainUrl,
                    interceptor = WebViewResolver(
                        interceptUrl = Regex("""\.m3u8|\.mp4"""),
                        additionalUrls = listOf(Regex("""\.m3u8|\.mp4"""))
                    )
                ).url

                when {
                    resolved.contains(".m3u8") -> {
                        M3u8Helper.generateM3u8(name, resolved, target).forEach(callback)
                        found = true
                    }
                    resolved.contains(".mp4") -> {
                        callback(
                            newExtractorLink(
                                source = name,
                                name = name,
                                url = resolved,
                                type = ExtractorLinkType.VIDEO,
                            ) {
                                this.referer = target
                                this.quality = Qualities.Unknown.value
                            }
                        )
                        found = true
                    }
                }
            } catch (e: Exception) {
                logError(e)
            }
        }
        return found
    }

    companion object {
        /** Хосты рекламы/соцсетей/аналитики — это не видео-балансёры, пропускаем. */
        private val BLOCKED_EMBED_HOSTS = listOf(
            "youtube.", "youtu.be", "facebook.", "google.", "gstatic.", "doubleclick",
            "yandex.", "vk.com", "ok.ru", "mail.ru", "caramel.am", "adfinity",
            "telegram.", "twitter.", "instagram.", "disqus.", "gravatar.",
        )
    }

    // ------------------------------- Общий парсер карточки -------------------------------

    protected open fun Element.toSearchResult(): SearchResponse? {
        // Берём именно ссылку на страницу деталей DLE: /{категория}/{id}-{slug}.html.
        // Признак /{цифры}- отсекает меню и служебные блоки, попавшие под селектор.
        val a = select("a[href]").firstOrNull { it.attr("href").contains(Regex("""/\d+-""")) }
            ?: return null
        val href = fixUrl(a.attr("href"))

        val title = selectFirst(titleInCardSelector)?.text()?.trim().takeUnless { it.isNullOrBlank() }
            ?: a.attr("title").takeUnless { it.isBlank() }
            ?: selectFirst("img[alt]")?.attr("alt")?.trim().takeUnless { it.isNullOrBlank() }
            ?: return null

        val poster = fixUrlNull(
            selectFirst("img")?.let { img ->
                img.attr("data-src").ifBlank { img.attr("src") }
            }
        )

        // Признак сериала: в URL сегмент серий/сериалов (serial/seriali/serialner).
        val isSeries = href.contains(Regex("""seri|serial"""))
        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
        }
    }
}
