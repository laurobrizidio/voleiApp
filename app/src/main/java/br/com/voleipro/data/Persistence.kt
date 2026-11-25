package br.com.voleipro.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.Preferences as StoredPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import br.com.voleipro.domain.*
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

@Singleton
class RoomLeagueRepository @Inject constructor(db: LeagueDatabase) : LeagueRepository {
    private val tracker = db.invalidationTracker
    private val dao = db.dao()
    override fun observe(leagueId: Long) = tracker.createFlow(
        "peladas", "player_peladas", "teams", "players", "rotation_rules", "active_match_state", "match_history"
    ).map { dao.readSelection(leagueId) }

    override suspend fun savePelada(profile: Pelada, initialTarget: Int) = dao.savePelada(profile, initialTarget)
    override suspend fun deletePeladas(ids: Set<Long>) = dao.deletePeladas(ids)
    override suspend fun update(leagueId: Long, transform: (Session) -> Session) = dao.update(leagueId, transform)
    override suspend fun finish(expected: Session, now: Long) = dao.finishMatchAndRotateTeams(expected, now)
    override suspend fun draw(leagueId: Long, playerIds: Set<Long>, size: Int, replace: Boolean, minWomen: Int, minMen: Int) =
        dao.drawTeams(playerIds, size, replace, minWomen, leagueId, minMen)
    override suspend fun clearHistory(leagueId: Long) = dao.clearHistory(leagueId)
    override suspend fun resetStandings(leagueId: Long) = dao.resetStandings(leagueId)
}

private val Context.settings by preferencesDataStore("settings")

@Singleton
class DataStorePreferences @Inject constructor(@ApplicationContext context: Context) : PreferencesRepository {
    private val store = context.settings
    private val appearance = stringPreferencesKey("appearance")
    private val vibration = booleanPreferencesKey("vibration")
    private val sound = booleanPreferencesKey("sound")
    private val leagueName = stringPreferencesKey("league_name")
    private val activeLeagueId = longPreferencesKey("active_league_id")
    private val palette = stringPreferencesKey("theme_palette")

    override val preferences = store.data.map(::read)

    private fun read(values: StoredPreferences) = Preferences(
        appearance = Appearance.entries.find { it.name == values[appearance] } ?: Appearance.SYSTEM,
        vibration = values[vibration] ?: true,
        sound = values[sound] ?: false,
        leagueName = values[leagueName].orEmpty(),
        activeLeagueId = values[activeLeagueId] ?: 1L,
        palette = ThemePalette.entries.find { it.name == values[palette] } ?: ThemePalette.SHIELD,
    )

    override suspend fun selectPelada(id: Long) {
        store.edit { it[activeLeagueId] = id }
    }

    override suspend fun update(transform: (Preferences) -> Preferences) {
        store.edit {
            val preferences = transform(read(it))
            it[appearance] = preferences.appearance.name
            it[vibration] = preferences.vibration
            it[sound] = preferences.sound
            it[palette] = preferences.palette.name
        }
    }
}
