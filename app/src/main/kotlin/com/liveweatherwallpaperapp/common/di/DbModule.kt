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

package com.liveweatherwallpaperapp.common.di

import android.content.Context
import android.os.Build
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.liveweatherwallpaperapp.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory
import livewallpaperweather.data.AlertSeverityColumnAdapter
import livewallpaperweather.data.Alerts
import livewallpaperweather.data.AndroidDatabaseHandler
import livewallpaperweather.data.Dailys
import livewallpaperweather.data.Database
import livewallpaperweather.data.DatabaseHandler
import livewallpaperweather.data.DistanceColumnAdapter
import livewallpaperweather.data.DurationColumnAdapter
import livewallpaperweather.data.Hourlys
import livewallpaperweather.data.Locations
import livewallpaperweather.data.Minutelys
import livewallpaperweather.data.Normals
import livewallpaperweather.data.PollenConcentrationColumnAdapter
import livewallpaperweather.data.PollutantConcentrationColumnAdapter
import livewallpaperweather.data.PrecipitationColumnAdapter
import livewallpaperweather.data.PressureColumnAdapter
import livewallpaperweather.data.RatioColumnAdapter
import livewallpaperweather.data.SpeedColumnAdapter
import livewallpaperweather.data.TemperatureColumnAdapter
import livewallpaperweather.data.TimeZoneColumnAdapter
import livewallpaperweather.data.WeatherCodeColumnAdapter
import livewallpaperweather.data.Weathers
import livewallpaperweather.data.location.LocationRepository
import livewallpaperweather.data.wallpaper.WallpaperPhotoRepository
import livewallpaperweather.data.weather.WeatherRepository
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class DbModule {

    @Provides
    @Singleton
    fun provideSqlDriver(@ApplicationContext context: Context): SqlDriver {
        return AndroidSqliteDriver(
            schema = Database.Schema,
            context = context,
            name = "breezyweather.db",
            factory = if (BuildConfig.DEBUG && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Support database inspector in Android Studio
                FrameworkSQLiteOpenHelperFactory()
            } else {
                RequerySQLiteOpenHelperFactory()
            },
            callback = object : AndroidSqliteDriver.Callback(Database.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    setPragma(db, "foreign_keys = ON")
                    setPragma(db, "journal_mode = WAL")
                    setPragma(db, "synchronous = NORMAL")
                }
                private fun setPragma(db: SupportSQLiteDatabase, pragma: String) {
                    val cursor = db.query("PRAGMA $pragma")
                    cursor.moveToFirst()
                    cursor.close()
                }
            }
        )
    }

    @Provides
    @Singleton
    fun provideDatabase(driver: SqlDriver): Database {
        return Database(
            driver,
            locationsAdapter = Locations.Adapter(
                timezoneAdapter = TimeZoneColumnAdapter
            ),
            weathersAdapter = Weathers.Adapter(
                weather_codeAdapter = WeatherCodeColumnAdapter,
                temperatureAdapter = TemperatureColumnAdapter,
                temperature_source_feels_likeAdapter = TemperatureColumnAdapter,
                temperature_apparentAdapter = TemperatureColumnAdapter,
                temperature_wind_chillAdapter = TemperatureColumnAdapter,
                humidexAdapter = TemperatureColumnAdapter,
                wind_speedAdapter = SpeedColumnAdapter,
                wind_gustsAdapter = SpeedColumnAdapter,
                pm25Adapter = PollutantConcentrationColumnAdapter,
                pm10Adapter = PollutantConcentrationColumnAdapter,
                so2Adapter = PollutantConcentrationColumnAdapter,
                no2Adapter = PollutantConcentrationColumnAdapter,
                o3Adapter = PollutantConcentrationColumnAdapter,
                coAdapter = PollutantConcentrationColumnAdapter,
                relative_humidityAdapter = RatioColumnAdapter,
                dew_pointAdapter = TemperatureColumnAdapter,
                pressureAdapter = PressureColumnAdapter,
                visibilityAdapter = DistanceColumnAdapter,
                cloud_coverAdapter = RatioColumnAdapter,
                ceilingAdapter = DistanceColumnAdapter
            ),
            dailysAdapter = Dailys.Adapter(
                daytime_weather_codeAdapter = WeatherCodeColumnAdapter,
                daytime_temperatureAdapter = TemperatureColumnAdapter,
                daytime_temperature_source_feels_likeAdapter = TemperatureColumnAdapter,
                daytime_temperature_apparentAdapter = TemperatureColumnAdapter,
                daytime_temperature_wind_chillAdapter = TemperatureColumnAdapter,
                daytime_humidexAdapter = TemperatureColumnAdapter,
                daytime_total_precipitationAdapter = PrecipitationColumnAdapter,
                daytime_thunderstorm_precipitationAdapter = PrecipitationColumnAdapter,
                daytime_rain_precipitationAdapter = PrecipitationColumnAdapter,
                daytime_snow_precipitationAdapter = PrecipitationColumnAdapter,
                daytime_ice_precipitationAdapter = PrecipitationColumnAdapter,
                daytime_total_precipitation_probabilityAdapter = RatioColumnAdapter,
                daytime_thunderstorm_precipitation_probabilityAdapter = RatioColumnAdapter,
                daytime_rain_precipitation_probabilityAdapter = RatioColumnAdapter,
                daytime_snow_precipitation_probabilityAdapter = RatioColumnAdapter,
                daytime_ice_precipitation_probabilityAdapter = RatioColumnAdapter,
                daytime_total_precipitation_durationAdapter = DurationColumnAdapter,
                daytime_thunderstorm_precipitation_durationAdapter = DurationColumnAdapter,
                daytime_rain_precipitation_durationAdapter = DurationColumnAdapter,
                daytime_snow_precipitation_durationAdapter = DurationColumnAdapter,
                daytime_ice_precipitation_durationAdapter = DurationColumnAdapter,
                daytime_wind_speedAdapter = SpeedColumnAdapter,
                daytime_wind_gustsAdapter = SpeedColumnAdapter,
                nighttime_temperatureAdapter = TemperatureColumnAdapter,
                nighttime_temperature_source_feels_likeAdapter = TemperatureColumnAdapter,
                nighttime_temperature_apparentAdapter = TemperatureColumnAdapter,
                nighttime_temperature_wind_chillAdapter = TemperatureColumnAdapter,
                nighttime_humidexAdapter = TemperatureColumnAdapter,
                nighttime_weather_codeAdapter = WeatherCodeColumnAdapter,
                nighttime_total_precipitationAdapter = PrecipitationColumnAdapter,
                nighttime_thunderstorm_precipitationAdapter = PrecipitationColumnAdapter,
                nighttime_rain_precipitationAdapter = PrecipitationColumnAdapter,
                nighttime_snow_precipitationAdapter = PrecipitationColumnAdapter,
                nighttime_ice_precipitationAdapter = PrecipitationColumnAdapter,
                nighttime_total_precipitation_probabilityAdapter = RatioColumnAdapter,
                nighttime_thunderstorm_precipitation_probabilityAdapter = RatioColumnAdapter,
                nighttime_rain_precipitation_probabilityAdapter = RatioColumnAdapter,
                nighttime_snow_precipitation_probabilityAdapter = RatioColumnAdapter,
                nighttime_ice_precipitation_probabilityAdapter = RatioColumnAdapter,
                nighttime_total_precipitation_durationAdapter = DurationColumnAdapter,
                nighttime_thunderstorm_precipitation_durationAdapter = DurationColumnAdapter,
                nighttime_rain_precipitation_durationAdapter = DurationColumnAdapter,
                nighttime_snow_precipitation_durationAdapter = DurationColumnAdapter,
                nighttime_ice_precipitation_durationAdapter = DurationColumnAdapter,
                nighttime_wind_speedAdapter = SpeedColumnAdapter,
                nighttime_wind_gustsAdapter = SpeedColumnAdapter,
                degree_day_heatingAdapter = TemperatureColumnAdapter,
                degree_day_coolingAdapter = TemperatureColumnAdapter,
                pm25Adapter = PollutantConcentrationColumnAdapter,
                pm10Adapter = PollutantConcentrationColumnAdapter,
                so2Adapter = PollutantConcentrationColumnAdapter,
                no2Adapter = PollutantConcentrationColumnAdapter,
                o3Adapter = PollutantConcentrationColumnAdapter,
                coAdapter = PollutantConcentrationColumnAdapter,
                alderAdapter = PollenConcentrationColumnAdapter,
                ashAdapter = PollenConcentrationColumnAdapter,
                birchAdapter = PollenConcentrationColumnAdapter,
                chestnutAdapter = PollenConcentrationColumnAdapter,
                cypressAdapter = PollenConcentrationColumnAdapter,
                grassAdapter = PollenConcentrationColumnAdapter,
                hazelAdapter = PollenConcentrationColumnAdapter,
                hornbeamAdapter = PollenConcentrationColumnAdapter,
                lindenAdapter = PollenConcentrationColumnAdapter,
                moldAdapter = PollenConcentrationColumnAdapter,
                mugwortAdapter = PollenConcentrationColumnAdapter,
                oakAdapter = PollenConcentrationColumnAdapter,
                oliveAdapter = PollenConcentrationColumnAdapter,
                planeAdapter = PollenConcentrationColumnAdapter,
                plantainAdapter = PollenConcentrationColumnAdapter,
                poplarAdapter = PollenConcentrationColumnAdapter,
                ragweedAdapter = PollenConcentrationColumnAdapter,
                sorrelAdapter = PollenConcentrationColumnAdapter,
                treeAdapter = PollenConcentrationColumnAdapter,
                urticaceaeAdapter = PollenConcentrationColumnAdapter,
                willowAdapter = PollenConcentrationColumnAdapter,
                sunshine_durationAdapter = DurationColumnAdapter,
                relative_humidity_averageAdapter = RatioColumnAdapter,
                relative_humidity_minAdapter = RatioColumnAdapter,
                relative_humidity_maxAdapter = RatioColumnAdapter,
                dewpoint_averageAdapter = TemperatureColumnAdapter,
                dewpoint_minAdapter = TemperatureColumnAdapter,
                dewpoint_maxAdapter = TemperatureColumnAdapter,
                pressure_averageAdapter = PressureColumnAdapter,
                pressure_maxAdapter = PressureColumnAdapter,
                pressure_minAdapter = PressureColumnAdapter,
                cloud_cover_averageAdapter = RatioColumnAdapter,
                cloud_cover_minAdapter = RatioColumnAdapter,
                cloud_cover_maxAdapter = RatioColumnAdapter,
                visibility_averageAdapter = DistanceColumnAdapter,
                visibility_maxAdapter = DistanceColumnAdapter,
                visibility_minAdapter = DistanceColumnAdapter
            ),
            hourlysAdapter = Hourlys.Adapter(
                weather_codeAdapter = WeatherCodeColumnAdapter,
                temperatureAdapter = TemperatureColumnAdapter,
                temperature_source_feels_likeAdapter = TemperatureColumnAdapter,
                temperature_apparentAdapter = TemperatureColumnAdapter,
                temperature_wind_chillAdapter = TemperatureColumnAdapter,
                humidexAdapter = TemperatureColumnAdapter,
                total_precipitationAdapter = PrecipitationColumnAdapter,
                thunderstorm_precipitationAdapter = PrecipitationColumnAdapter,
                rain_precipitationAdapter = PrecipitationColumnAdapter,
                snow_precipitationAdapter = PrecipitationColumnAdapter,
                ice_precipitationAdapter = PrecipitationColumnAdapter,
                total_precipitation_probabilityAdapter = RatioColumnAdapter,
                thunderstorm_precipitation_probabilityAdapter = RatioColumnAdapter,
                rain_precipitation_probabilityAdapter = RatioColumnAdapter,
                snow_precipitation_probabilityAdapter = RatioColumnAdapter,
                ice_precipitation_probabilityAdapter = RatioColumnAdapter,
                wind_speedAdapter = SpeedColumnAdapter,
                wind_gustsAdapter = SpeedColumnAdapter,
                pm25Adapter = PollutantConcentrationColumnAdapter,
                pm10Adapter = PollutantConcentrationColumnAdapter,
                so2Adapter = PollutantConcentrationColumnAdapter,
                no2Adapter = PollutantConcentrationColumnAdapter,
                o3Adapter = PollutantConcentrationColumnAdapter,
                coAdapter = PollutantConcentrationColumnAdapter,
                relative_humidityAdapter = RatioColumnAdapter,
                dew_pointAdapter = TemperatureColumnAdapter,
                pressureAdapter = PressureColumnAdapter,
                cloud_coverAdapter = RatioColumnAdapter,
                visibilityAdapter = DistanceColumnAdapter
            ),
            minutelysAdapter = Minutelys.Adapter(
                precipitation_intensityAdapter = PrecipitationColumnAdapter
            ),
            alertsAdapter = Alerts.Adapter(
                severityAdapter = AlertSeverityColumnAdapter
            ),
            normalsAdapter = Normals.Adapter(
                temperature_max_averageAdapter = TemperatureColumnAdapter,
                temperature_min_averageAdapter = TemperatureColumnAdapter
            )
        )
    }

    @Provides
    @Singleton
    fun provideDatabaseHandler(db: Database, driver: SqlDriver): DatabaseHandler {
        return AndroidDatabaseHandler(db, driver)
    }

    @Provides
    @Singleton
    fun provideLocationRepository(databaseHandler: DatabaseHandler): LocationRepository {
        return LocationRepository(databaseHandler)
    }

    @Provides
    @Singleton
    fun provideWeatherRepository(databaseHandler: DatabaseHandler): WeatherRepository {
        return WeatherRepository(databaseHandler)
    }

    @Provides
    @Singleton
    fun provideWallpaperPhotoRepository(databaseHandler: DatabaseHandler): WallpaperPhotoRepository {
        return WallpaperPhotoRepository(databaseHandler)
    }
}
