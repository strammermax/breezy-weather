/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package com.livewallpaperweather.common.di

import android.app.Application
import android.os.Build
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.serialization.XML
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.tls.HandshakeCertificates
import com.livewallpaperweather.BreezyWeather
import com.livewallpaperweather.R
import com.livewallpaperweather.common.utils.DiagnosticLogger
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class HttpModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(app: Application, loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        val client = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            /*
             * Add support for Let’s encrypt certificate authority on Android < 7.0
             */
            try {
                val certificateFactory = CertificateFactory.getInstance("X.509")
                val certificateIsrgRootX1 = certificateFactory
                    .generateCertificates(app.resources.openRawResource(R.raw.isrg_root_x1))
                    .single() as X509Certificate
                val certificateIsrgRootX2 = certificateFactory
                    .generateCertificates(app.resources.openRawResource(R.raw.isrg_root_x2))
                    .single() as X509Certificate
                val certificates = HandshakeCertificates.Builder()
                    .addTrustedCertificate(certificateIsrgRootX1)
                    .addTrustedCertificate(certificateIsrgRootX2)
                    .addPlatformTrustedCertificates()
                    .build()

                OkHttpClient.Builder()
                    .sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
            } catch (ignored: Exception) {
                OkHttpClient.Builder()
            }
        } else {
            OkHttpClient.Builder()
        }

        return client
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .cache(
                Cache(
                    File(app.cacheDir, "http_cache"), // $0.05 worth of phone storage in 2020
                    50L * 1024L * 1024L // 50 MiB
                )
            )
            .addInterceptor { chain ->
                val request = chain.request()
                if (!DiagnosticLogger.isEnabled(app)) return@addInterceptor chain.proceed(request)
                val started = System.nanoTime()
                val safeUrl = "${request.url.scheme}://${request.url.host}${request.url.encodedPath}"
                DiagnosticLogger.log(app, "Network", "${request.method} $safeUrl started")
                try {
                    chain.proceed(request).also { response ->
                        val elapsedMs = (System.nanoTime() - started) / 1_000_000
                        DiagnosticLogger.log(app, "Network", "${request.method} $safeUrl -> ${response.code} (${elapsedMs} ms)")
                        val contentType = response.body.contentType()?.toString().orEmpty()
                        if (contentType.startsWith("text/") || contentType.contains("json")) {
                            val body = runCatching { response.peekBody(512L * 1024L).string() }.getOrNull()
                            body?.takeIf(String::isNotBlank)?.let {
                                DiagnosticLogger.log(app, "Server response $safeUrl", it)
                            }
                        }
                    }
                } catch (error: Exception) {
                    DiagnosticLogger.log(app, "Network", "${request.method} $safeUrl failed", error)
                    throw error
                }
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRxJava3CallAdapterFactory(): RxJava3CallAdapterFactory {
        return RxJava3CallAdapterFactory.create()
    }

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BreezyWeather.instance.debugMode) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    @Named("JsonSerializer")
    fun provideKotlinxJsonSerializationConverterFactory(): Converter.Factory {
        val contentType = "application/json".toMediaType()
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            isLenient = !BreezyWeather.instance.debugMode
        }
        return json.asConverterFactory(contentType)
    }

    @Provides
    @Named("JsonClient")
    fun provideJsonRetrofitBuilder(
        client: OkHttpClient,
        @Named("JsonSerializer") jsonConverterFactory: Converter.Factory,
        callAdapterFactory: RxJava3CallAdapterFactory,
    ): Retrofit.Builder {
        return Retrofit.Builder()
            .client(client)
            .addConverterFactory(jsonConverterFactory)
            // TODO: We should probably migrate to suspend
            // https://github.com/square/retrofit/blob/master/CHANGELOG.md#version-260-2019-06-05
            .addCallAdapterFactory(callAdapterFactory)
    }

    @Provides
    @Singleton
    @Named("XmlSerializer")
    fun provideKotlinxXmlSerializationConverterFactory(): Converter.Factory {
        val contentType = "application/xml".toMediaType()
        return XML {
            defaultPolicy {
                pedantic = false
                ignoreUnknownChildren()
            }
            autoPolymorphic = true
        }.asConverterFactory(contentType)
    }

    @Provides
    @Named("XmlClient")
    fun provideXmlRetrofitBuilder(
        client: OkHttpClient,
        @Named("XmlSerializer") xmlConverterFactory: Converter.Factory,
        callAdapterFactory: RxJava3CallAdapterFactory,
    ): Retrofit.Builder {
        return Retrofit.Builder()
            .client(client)
            .addConverterFactory(xmlConverterFactory)
            // TODO: We should probably migrate to suspend
            // https://github.com/square/retrofit/blob/master/CHANGELOG.md#version-260-2019-06-05
            .addCallAdapterFactory(callAdapterFactory)
    }

    /*@Provides
    @Singleton
    @Named("CsvSerializer")
    fun provideKotlinxCsvSerializationConverterFactory(): Converter.Factory {
        val contentType = "text/csv".toMediaType() // RFC 7111
        val csv = Csv {
            hasHeaderRecord = true
            delimiter = ';'
            recordSeparator = "\r\n"
            ignoreUnknownColumns = true
        }
        return csv.asConverterFactory(contentType)
    }

    @Provides
    @Named("CsvClient")
    fun provideCsvRetrofitBuilder(
        client: OkHttpClient,
        @Named("CsvSerializer") csvConverterFactory: Converter.Factory,
        callAdapterFactory: RxJava3CallAdapterFactory,
    ): Retrofit.Builder {
        return Retrofit.Builder()
            .client(client)
            .addConverterFactory(csvConverterFactory)
            // TODO: We should probably migrate to suspend
            // https://github.com/square/retrofit/blob/master/CHANGELOG.md#version-260-2019-06-05
            .addCallAdapterFactory(callAdapterFactory)
    }*/
}
