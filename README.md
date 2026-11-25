# Volei Pro

Volei Pro is an offline-first Android app for pickup volleyball. Keep score live, form balanced teams, manage the waiting queue with customizable rotation rules, and track match history and standings.

## Como usar

| Aba | O que você pode fazer |
| --- | --- |
| **Placar** | Marcar ou corrigir pontos, desfazer jogadas, trocar o saque, inverter os lados e usar o cronômetro. |
| **Times** | Cadastrar equipes e jogadores, sortear times de 2×2 a 6×6 e compartilhar a escalação como imagem. |
| **Fila** | Conferir e reordenar os times que aguardam, ou substituir um time em quadra. |
| **Regras** | Ajustar a pontuação, o desempate no set point, o rodízio e as preferências de uso em quadra. |
| **Histórico** | Consultar partidas finalizadas e a classificação das equipes. |

Na primeira abertura, o app cria quatro times de exemplo. O placar, as equipes, a fila e o histórico ficam no aparelho e funcionam offline.

## Pontuação e rodízio

- **Pontuação padrão.** O set vai a 15 pontos com diferença mínima de dois. Em **Regras**, você pode alterar a meta, a diferença exigida e o teto de pontos.
- **Empate no set point.** Se você escolher **2 pontos diretos** ou **3 pontos diretos**, um empate em 14×14 num set de 15 leva o jogo até 16 ou 17, respectivamente. Um ponto de vantagem basta quando um time chega à meta do desempate.
- **Rodízio padrão.** O vencedor fica em quadra depois da primeira vitória. Ao ganhar a segunda consecutiva, sai e assume a primeira posição da fila. Com apenas dois times, o app prepara uma revanche e zera a sequência.
- **Aplicar mudanças.** O botão **Salvar regras** é habilitado quando há alterações válidas. Depois de salvar, o app confirma a gravação e indica **Regras em vigor**.

## Compilar e executar

Use **JDK 17**, **Android SDK Platform 35** e **Build-Tools 35.0.0**. O app roda em dispositivos com Android 7.0 (API 24) ou superior.

No Android Studio, abra a pasta que contém `settings.gradle.kts`, sincronize o Gradle e execute a configuração **app** em um emulador ou aparelho Android.

Na linha de comando, use o Gradle Wrapper para compilar o APK de debug e executar os testes unitários. No Windows, substitua `./gradlew` por `gradlew.bat`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

O APK de debug é gerado em `app/build/outputs/apk/debug/app-debug.apk`. As dependências são resolvidas pelo Google Maven e pelo Maven Central.

## Arquitetura

- `domain` define os modelos, os contratos de repositório e os casos de uso de pontuação, sorteio e rodízio em Kotlin.
- `data` implementa a persistência com Room e DataStore. Ao confirmar uma partida, o app registra o resultado, atualiza a fila e prepara o próximo placar na mesma transação.
- `ui` usa Jetpack Compose, Material 3 e Navigation Compose, com um ViewModel Hilt que expõe o estado da tela por `StateFlow`.

Os dados da partida e a pilha de desfazer ficam em `volei_pelada.db`. O esquema do Room está em `app/schemas`. Na primeira execução, o banco começa vazio. Os testes da camada de dados usam Room e Robolectric para verificar a inicialização, a persistência e as transações.

## Desenvolvedor

**Lauro Brizidio** · Desenvolvedor Android

- E-mail [laurobrizidio@hotmail.com](mailto:laurobrizidio@hotmail.com)
- [Perfil no LinkedIn](https://www.linkedin.com/in/laurobrizidio/)

## Licença

O Volei Pro é distribuído sob a licença [Apache 2.0](LICENSE).
