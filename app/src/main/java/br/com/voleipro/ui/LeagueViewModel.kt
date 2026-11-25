package br.com.voleipro.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.voleipro.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiState(
    val league: League? = null,
    val preferences: Preferences = Preferences(),
    val error: String? = null,
    val profiles: List<Pelada> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class LeagueViewModel @Inject constructor(
    private val repository: LeagueRepository,
    private val settings: PreferencesRepository,
) : ViewModel() {
    private val failure = MutableStateFlow<String?>(null)
    private val retry = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<UiState> = retry.flatMapLatest {
        settings.preferences.flatMapLatest { preferences ->
            combine(repository.observe(preferences.activeLeagueId), failure) { selection, error ->
                val profiles = selection.profiles.map { profile ->
                    if (profile.id == 1L && profile.name.isBlank()) profile.copy(name = preferences.leagueName)
                    else profile
                }
                UiState(selection.active, preferences, error, profiles, loaded = true)
            }
        }.catch { emit(UiState(error = "Não foi possível carregar os dados locais.")) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    private fun perform(onFailure: (String) -> Unit = { failure.value = it }, action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                val message = (exception as? PeladaException)?.message ?: "Não foi possível salvar a alteração."
                onFailure(message)
            }
        }
    }

    fun retry() { retry.update { it + 1 } }
    fun clearError() { failure.value = null }
    fun update(leagueId: Long, onSaved: () -> Unit = {}, onFailure: () -> Unit = {},
               transform: (Session) -> Session) = perform(onFailure = { message ->
        failure.value = message
        onFailure()
    }) {
        repository.update(leagueId, transform)
        onSaved()
    }

    fun quickAddPlayers(
        leagueId: Long, names: List<String>, experience: Experience, present: Boolean, sex: PlayerSex,
        position: PlayerPosition, onSaved: (Int) -> Unit, onFailure: (String) -> Unit,
    ) = perform(onFailure) {
        var added = 0
        repository.update(leagueId) { current ->
            val next = current.addPlayers(names, experience.level,
                present && current.pelada.composition.accepts(sex), sex, position)
            added = next.players.size - current.players.size
            if (added == 0) throw PeladaException("Esses nomes já estão cadastrados.")
            next
        }
        onSaved(added)
    }

    fun editPlayer(
        leagueId: Long, playerId: Long, name: String, experience: Experience, present: Boolean,
        sex: PlayerSex, position: PlayerPosition, onSaved: () -> Unit, onFailure: (String) -> Unit,
    ) = perform(onFailure) {
        repository.update(leagueId) { current ->
            val player = current.players.find { it.id == playerId }
                ?: throw PeladaException("Esse jogador não está mais cadastrado.")
            val skill = if (player.experience == experience) player.skillLevel else experience.level
            current.editPlayer(playerId, name, skill,
                present && current.pelada.composition.accepts(sex), sex, position)
        }
        onSaved()
    }
    fun preferences(transform: (Preferences) -> Preferences) = perform { settings.update(transform) }
    fun finish(session: Session) = perform { repository.finish(session, System.currentTimeMillis()) }

    fun draw(leagueId: Long, playerIds: Set<Long>, size: Int, replace: Boolean, minWomen: Int, minMen: Int) =
        perform { repository.draw(leagueId, playerIds, size, replace, minWomen, minMen) }

    fun selectPelada(id: Long) = perform { settings.selectPelada(id) }

    fun savePelada(profile: Pelada, initialTarget: Int, onSaved: () -> Unit, onFailure: (String) -> Unit) =
        perform(onFailure) {
            val id = repository.savePelada(profile, initialTarget)
            if (profile.id == 0L) settings.selectPelada(id)
            onSaved()
        }

    fun deletePeladas(ids: Set<Long>, onSaved: () -> Unit, onFailure: (String) -> Unit) = perform(onFailure) {
        repository.deletePeladas(ids)
        onSaved()
    }
    fun clearHistory(leagueId: Long) = perform { repository.clearHistory(leagueId) }

    fun resetStandings(leagueId: Long) = perform { repository.resetStandings(leagueId) }

    fun point(leagueId: Long, side: Int, delta: Int) = update(leagueId) { ScorePointUseCase()(it, side, delta) }

    fun undo(leagueId: Long) = update(leagueId) {
        val match = it.match
        val previous = match.points.lastOrNull() ?: return@update it
        it.copy(match = match.copy(scores = previous.scores, serve = previous.serve, points = match.points.dropLast(1)))
    }

    fun swap(leagueId: Long) = update(leagueId) {
        val match = it.match
        if (it.court.size < 2) return@update it
        it.copy(court = it.court.reversed(), match = match.copy(scores = match.scores.reversed(),
            serve = 1 - match.serve, swapped = !match.swapped,
            points = match.points.map { point -> PointSnapshot(point.scores.reversed(), 1 - point.serve) }))
    }

    fun timer(leagueId: Long) = update(leagueId) {
        val now = System.currentTimeMillis()
        val match = it.match
        it.copy(match = if (match.startedAt == null) match.copy(startedAt = now)
            else match.copy(startedAt = null, elapsedMs = match.duration(now)))
    }

    fun move(leagueId: Long, id: Long, offset: Int) = update(leagueId) {
        val index = it.queue.indexOf(id)
        val destination = index + offset
        if (index < 0 || destination !in it.queue.indices) return@update it
        it.copy(queue = it.queue.toMutableList().apply { add(destination, removeAt(index)) })
    }
}
