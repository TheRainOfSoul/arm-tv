package com.hhteam.armtv

import com.lagradost.cloudstream3.mainPageOf

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

    // Реальный контейнер карточки hayertv. Меню/служебные .item отсекаются
    // в toSearchResult по признаку ссылки на страницу деталей (/{цифры}-...).
    override val cardSelector = "div.item"
}
