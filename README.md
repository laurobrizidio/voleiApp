# Volei Pro

Volei Pro is an offline-first Android app for pickup volleyball. Keep score live, form balanced teams, manage the waiting queue with customizable rotation rules, and track match history and standings.

App Android nativo e offline para placar, sorteio de times, fila, rodízio e classificação. Na primeira abertura, o banco cria quatro times de exemplo e configura partidas de 15 pontos, com diferença mínima de dois pontos. Pela regra padrão, o vencedor permanece em quadra após a primeira vitória e vai para o primeiro lugar da fila após a segunda vitória consecutiva, quando há times para o rodízio.

## Abrir no Android Studio

1. Abra esta pasta, que contém `settings.gradle.kts`.
2. Use JDK 17 em **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**.
3. Instale **Android SDK Platform 35** e **Build-Tools 35.0.0** pelo SDK Manager e sincronize o Gradle.
4. Selecione a configuração **app** e execute em um dispositivo ou emulador com Android 7.0 (API 24) ou superior.

Na linha de comando, com o JDK e o SDK configurados, use `./gradlew :app:assembleDebug :app:testDebugUnitTest`. No Windows, use `gradlew.bat`. Os testes do DAO usam Room em memória com Robolectric.

## Arquitetura

- `domain` define modelos, contratos dos repositórios e casos de uso de pontuação, sorteio e rodízio.
- `data` implementa os repositórios com Room e DataStore. A confirmação grava o histórico, atualiza a fila e prepara o próximo placar na mesma transação.
- `ui` usa Compose Material 3, Navigation Compose e um ViewModel Hilt com `StateFlow<UiState>`.

Os dados da partida e a pilha de desfazer ficam em `volei_pelada.db`. O esquema exportado do Room está em `app/schemas`.

## Licença

O Volei Pro é distribuído sob a licença [Apache 2.0](LICENSE).
