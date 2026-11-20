package it.casatv.tv

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class CasaTvApplication : Application(), ImageLoaderFactory {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun newImageLoader(): ImageLoader {
        val lowRam = isLowRamDevice(this)
        return ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (Linux; Android 14; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 CasaTV/1.0",
                            )
                            .header(
                                "Accept",
                                "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8",
                            )
                            .build()
                        chain.proceed(request)
                    }
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (lowRam) 0.10 else 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(if (lowRam) 32L * 1024 * 1024 else 64L * 1024 * 1024)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowRgb565(true)
            .apply {
                if (lowRam) bitmapConfig(Bitmap.Config.RGB_565)
            }
            .components {
                add(SvgDecoder.Factory())
            }
            .crossfade(!lowRam)
            .decoderDispatcher(kotlinx.coroutines.Dispatchers.IO.limitedParallelism(if (lowRam) 2 else 3))
            .build()
    }

    companion object {
        @Volatile
        private var cachedLowRam: Boolean? = null

        fun isLowRamDevice(context: Context): Boolean {
            cachedLowRam?.let { return it }
            val am = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val lowRam = (am?.isLowRamDevice == true) ||
                (Runtime.getRuntime().maxMemory() <= 160L * 1024L * 1024L)
            cachedLowRam = lowRam
            return lowRam
        }
    }
}
