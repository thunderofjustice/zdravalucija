package hr.zdravalucija.data

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import java.io.IOException

interface NameTranslator { suspend fun toEnglish(name: String): String }

/** Names stay on-device. Only the language model is downloaded the first time. */
class OnDeviceNameTranslator : NameTranslator {
    override suspend fun toEnglish(name: String): String {
        val translator = Translation.getClient(TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.CROATIAN)
            .setTargetLanguage(TranslateLanguage.ENGLISH).build())
        try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            return translator.translate(name.trim()).await().trim().takeIf { it.isNotBlank() }
                ?: throw IOException("Prijevod naziva nije dostupan.")
        } finally { translator.close() }
    }
}
