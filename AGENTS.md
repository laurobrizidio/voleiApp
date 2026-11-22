# AGENTS.md — Vôlei Pelada Pro (Android Native)

## 1. Visão Geral do Projeto
O **Vôlei Pro** é um aplicativo Android nativo (offline-first) projetado para gerenciar partidas recreativas ("peladas") de vôlei de quadra, areia ou praia. O aplicativo resolve o controle de placar ao vivo, a formação/sorteio de equipes, o gerenciamento dinâmico da fila de espera e um **motor configurável de regras de alternância** (incluindo limite de vitórias consecutivas e prioridade de retorno à fila).

### Requisitos Funcionais Principais (Baseados no Protótipo)
1. **Placar Ao Vivo (Scoreboard):**
   - Incremento (`+1`) e decremento (`-1`) rápido para Time A e Time B, além de toque direto no visor.
   - Controle de posse de saque automático (alterna para quem pontua) e manual.
   - Inversão rápida de lados da quadra.
   - Alerta visual e tátil (vibração/som opcional) de *Set Point*.
   - Cronômetro de partida opcional (Play/Pause/Reset).
   - Tags dinâmicas de status da sequência em quadra (ex.: `"1ª vit. — Joga pela última e sai!"`).
2. **Motor de Regras de Alternância (Rotation Engine):**
   - **Pontuação:** Meta configurável (`12`, `15`, `21`, `25` ou valor customizado) e regra de diferença mínima de 2 pontos ("vai a 2" ativado/desativado) com teto opcional.
   - **Limite de Vitórias Seguidas:** Alternável (ex.: desativado ou máximo de `1`, `2`, `3`... vitórias consecutivas).
   - **Regra de Retorno do Campeão que Atinge o Limite:**
     - `AHEAD_OF_LOSER`: O campeão que atingiu o limite sai e entra na fila **imediatamente à frente do perdedor** daquela partida.
     - `END_OF_QUEUE`: Ambos saem, mas o campeão vai para o **final absoluto da fila** (atrás do perdedor).
     - `FIXED_POSITION`: O campeão marca sua volta em uma posição específica configurável da fila (ex.: 2º da fila).
   - **Regra do Perdedor:** Vai para o final da fila (ou posição definida pela regra de retorno do campeão).
3. **Gerenciamento de Times, Jogadores e Fila:**
   - Cadastro de times fixos ou sorteio automático por formato (`2x2`, `3x3`, `4x4`, `5x5`, `6x6`) a partir de uma lista de jogadores presentes.
   - Reordenação manual da fila de espera (Drag & Drop ou botões subir/descer).
   - Substituição manual de time em quadra.
4. **Histórico e Classificação (Standings):**
   - Registro persistente de todas as partidas finalizadas (placar, duração, motivo da saída de cada time).
   - Tabela de classificação calculada (Jogos, Vitórias, Derrotas, Pontos Pró, Pontos Contra, Saldo, Aproveitamento %).

---

## 2. Stack Tecnológica Obrigatória
- **Linguagem:** Kotlin (100%, strict null-safety, Coroutines e Flow).
- **UI Framework:** Jetpack Compose (Material 3) *ou* XML Views (preferência por **Jetpack Compose + Material 3** com suporte a tema escuro/claro e alto contraste para uso sob luz solar em quadras externas).
- **Injeção de Dependência:** Dagger Hilt (`@HiltViewModel`, `@Module`, `@InstallIn`).
- **Persistência Local:** Room Database (`androidx.room`) com KSP (Kotlin Symbol Processing).
- **Preferências Simples:** Jetpack

3. Regras de Dependência Entre Camadas
domain: Não pode importar nada de android.*, androidx.* ou room.*. Deve conter apenas classes Kotlin puras e kotlinx.coroutines.flow.Flow.

data: Depende apenas de domain. Converte @Entity para modelos de domain antes de retornar qualquer dado aos repositórios.

ui: Depende apenas de domain. Nunca acessa DAOs, Entities do Room ou Contextos diretamente nas telas. Toda comunicação ocorre via ViewModel expondo um único StateFlow<UiState> imutável e recebendo eventos via funções ou onEvent(UiEvent).

4. Modelagem do Banco de Dados Local (Room)
O banco de dados volei_pelada.db deve ser configurado com exportSchema = true e conter as seguintes entidades:

4.1. Entidades (data/local/entity/)
TeamEntity (teams)

id: Long (PrimaryKey, autoGenerate = true)

name: String

colorHex: String (Identificação visual rápida na quadra)

consecutiveWins: Int (Sequência atual de vitórias seguidas em quadra)

status: String (ON_COURT_A, ON_COURT_B, IN_QUEUE, BENCHED)

queuePosition: Int (Índice de ordenação na fila de espera; -1 se estiver em quadra)

createdAt: Long

PlayerEntity (players) (Opcional para sorteio de times)

id: Long (PrimaryKey, autoGenerate = true)

name: String

skillLevel: Int (1 a 5, para sorteio equilibrado)

isPresent: Boolean

assignedTeamId: Long? (ForeignKey para teams.id, onDelete = SET_NULL)

RotationRulesEntity (rotation_rules)

id: Int (PrimaryKey = 1 — registro singleton de configuração ativa)

targetPoints: Int (Padrão: 15)

winByTwo: Boolean (Padrão: true)

maxPointsCap: Int? (Ex.: 17 ou null para ilimitado)

maxConsecutiveWins: Int? (Ex.: 2; null = rei da quadra sem limite)

championReturnPolicy: String (AHEAD_OF_LOSER, END_OF_QUEUE, FIXED_POSITION)

fixedReturnPosition: Int (Usado se FIXED_POSITION, padrão: 2)

keepScreenOn: Boolean (Padrão: true)

enableSoundAndHaptics: Boolean (Padrão: true)

MatchHistoryEntity (match_history)

id: Long (PrimaryKey, autoGenerate = true)

teamAId: Long

teamAName: String (Snapshot do nome no momento do jogo)

teamAScore: Int

teamBId: Long

teamBName: String (Snapshot do nome no momento do jogo)

teamBScore: Int

winnerTeamId: Long

loserTeamId: Long

championExitedByStreakLimit: Boolean (Indica se o vencedor também saiu por atingir o limite de vitórias)

exitReasonSummary: String (Ex.: "Time Azul venceu (2ª seguida) e marcou volta em 2º na fila")

durationSeconds: Long

finishedAt: Long

ActiveMatchStateEntity (active_match_state)

Persistência de estado da partida em andamento para sobreviver a fechamento do app ou rotação de tela:

id: Int (PrimaryKey = 1)

teamAId: Long?

teamBId: Long?

scoreA: Int

scoreB: Int

servingTeam: String (TEAM_A, TEAM_B, NONE)

sidesSwapped: Boolean

elapsedSeconds: Long

pointHistoryStackJson: String (Lista serializada de eventos de ponto para suportar Desfazer / Undo ilimitado da partida atual)

4.2. Transações Atômicas nos DAOs
A transição de fim de partida (finishMatchAndRotateTeams) deve ser executada dentro de uma anotação @Transaction no Room DAO para garantir consistência absoluta entre:

Inserção do registro em match_history.

Atualização de consecutiveWins, status e queuePosition dos times envolvidos e da fila de espera.

Promoção dos próximos times da fila para ON_COURT_A e ON_COURT_B.

Reset de active_match_state para o novo confronto.

5. Especificação do Motor de Regras (domain/usecase/rotation/)
O caso de uso ProcessMatchEndUseCase é o coração do sistema. Ele deve ser 100% determinístico e coberto por testes unitários exaustivos.

Algoritmo de Alternância Pós-Partida:
Dado winnerTeam, loserTeam, currentQueue (ordenada por queuePosition ascendente) e rules: RotationRules:

Incrementar newWinStreak = winnerTeam.consecutiveWins + 1.

Zerar loserTeam.consecutiveWins = 0.

Verificar se o campeão atingiu o teto de vitórias:val championMustExit = rules.maxConsecutiveWins != null && newWinStreak >= rules.maxConsecutiveWins

Caso A (championMustExit == false):

winnerTeam permanece em quadra com consecutiveWins = newWinStreak.

loserTeam vai para o final de currentQueue.

O primeiro time de currentQueue (index 0) entra em quadra no lugar do perdedor.

Caso B (championMustExit == true):

winnerTeam.consecutiveWins é resetado para 0 ao sair de quadra.

Os dois próximos times de currentQueue (index 0 e index 1, se houver) assumem a quadra (ON_COURT_A e ON_COURT_B).

Reinsere winnerTeam e loserTeam na fila restante conforme rules.championReturnPolicy:

AHEAD_OF_LOSER: Adiciona winnerTeam ao fim da fila atual e, logo após ele, adiciona loserTeam.

END_OF_QUEUE: Adiciona loserTeam ao fim da fila atual e, logo após ele, adiciona winnerTeam.

FIXED_POSITION: Insere winnerTeam no índice (rules.fixedReturnPosition - 1).coerceIn(0, remainingQueue.size) e adiciona loserTeam ao final da fila.

(Edge Case — Apenas 3 times cadastrados no total): Se houver apenas 1 time na fila quando ambos precisarem sair, o winnerTeam (ou loserTeam, conforme prioridade) permanece/retorna imediatamente para completar o jogo contra o time que estava esperando, exibindo aviso claro na UI.

Recalcular queuePosition sequencial (0, 1, 2...) para todos os times com status IN_QUEUE.

6. Diretrizes de UI/UX (Android Nativo)
Ergonomia de Quadra (One-Handed & Outdoor Ready):

Botões +1 devem ocupar no mínimo 40% da área útil do card de cada time no modo Placar para evitar toques errados durante o jogo.

Suportar WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON enquanto a tela de Placar estiver ativa (controlado pela preferência keepScreenOn).

Feedback háptico (HapticFeedbackType.LongPress / TextHandleMove) ao marcar pontos, atingir Set Point e encerrar partida.

Banner Preditivo de Regra em Quadra:

Exibir abaixo do nome de cada time em quadra seu estado atual em relação à regra de alternância:

Ex. (limite = 2 vitórias, 0 vitórias atuais): "0 vitórias"

Ex. (limite = 2 vitórias, 1 vitória atual): "🔥 1ª vit. — Joga pela última e sai!"

Ex. (sem limite, 3 vitórias atuais): "👑 Rei da Quadra (3 vit. seguidas)"

Diálogo de Confirmação de Fim de Set/Partida:

Ao atingir a pontuação vencedora (score >= targetPoints e diferença >= 2 se winByTwo == true), exibir um modal claro mostrando:

Placar final.

Quem permanece em quadra e quem sai (explicando explicitamente se o campeão saiu por limite de vitórias e em qual posição da fila ele entrou).

Botões: "Confirmar e Rotacionar Times" ou "Continuar Pontuando (Ajustar Placar)".

7. Convenções de Código e Qualidade
Nomenclatura: Classes em PascalCase, funções/variáveis em camelCase, constantes em UPPER_SNAKE_CASE.

Imutabilidade: Todas as classes de UiState e Domain Model devem ser data class com propriedades val e coleções imutáveis (List, Set).

Tratamento de Erros: Nenhuma exceção de banco de dados ou parsing pode causar crash na UI. Utilizar Result<T> ou estados explícitos (Loading, Success, Error) nos ViewModels.

Seed Inicial: Na primeira execução (RoomDatabase.Callback.onCreate), popular o banco com a configuração padrão (15 pontos, vai a 2, máximo 2 vitórias seguidas, campeão volta à frente do perdedor) e 4 times iniciais (Time Azul, Time Vermelho, Time Amarelo, Time Verde) para permitir uso imediato sem fricção.

8. Plano de Execução Recomendado para o Agente
Ao implementar este projeto, siga rigorosamente esta ordem de etapas:

Setup & Gradle: Configurar libs.versions.toml com Kotlin, Compose Material 3, Hilt, Room (com KSP), Navigation Compose e dependências de teste.

Camada domain: Implementar todos os modelos, interfaces de repositório e casos de uso (ProcessMatchEndUseCase, ScorePointUseCase, GenerateBalancedTeamsUseCase). Escrever testes unitários para todos os cenários do motor de alternância antes de avançar.

Camada data: Implementar entidades Room, DAOs (incluindo @Transaction de rotação), AppDatabase com Callback de seed inicial, Mappers e Repositórios. Testar DAOs com banco em memória.

Camada di: Conectar AppDatabase, DAOs e Repositórios via módulos Hilt.

Camada ui: Construir o tema Material 3, navegação inferior (4 abas: Placar, Times & Fila, Regras, Histórico), ViewModels com StateFlow e telas Compose responsivas (Portrait e Landscape).
