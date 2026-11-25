package br.com.voleipro.di

import android.content.Context
import androidx.room.Room
import br.com.voleipro.data.DataStorePreferences
import br.com.voleipro.data.LeagueDatabase
import br.com.voleipro.data.RoomLeagueRepository
import br.com.voleipro.domain.LeagueRepository
import br.com.voleipro.domain.PreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun league(implementation: RoomLeagueRepository): LeagueRepository
    @Binds abstract fun preferences(implementation: DataStorePreferences): PreferencesRepository

    companion object {
        @Provides @Singleton
        fun database(@ApplicationContext context: Context): LeagueDatabase =
            Room.databaseBuilder(context, LeagueDatabase::class.java, "volei_pelada.db")
                .build()
    }
}
