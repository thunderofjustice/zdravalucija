package hr.zdravalucija

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

class LucijaApplication : Application(), ImageLoaderFactory {
    val container: AppContainer by lazy { AppContainer(this) }
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(File(cacheDir, "coil-images")).maxSizeBytes(64L * 1024 * 1024).build() }
        .crossfade(true).build()
}
