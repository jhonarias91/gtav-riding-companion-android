package com.fariddroid.gtbikecompanion.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

enum class AppLanguage(val tag: String) { ENGLISH("en"), SPANISH("es") }

/**
 * Per-app language through AppCompat (system per-app setting on API 33+, autoStoreLocales below).
 * Until the user picks a language (in More or in Android settings) nothing is pinned: the app follows
 * the phone's language when it is English or Spanish, and resource fallback (`values/`) gives English
 * for any other language.
 */
object LocaleController {
    fun set(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    fun current(): AppLanguage {
        val tag = AppCompatDelegate.getApplicationLocales()[0]?.language ?: Locale.getDefault().language
        return if (tag == AppLanguage.SPANISH.tag) AppLanguage.SPANISH else AppLanguage.ENGLISH
    }
}
