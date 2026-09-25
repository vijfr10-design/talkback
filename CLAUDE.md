# CLAUDE.md — Bro Blind Screen Reader

## Contexto
- Fork do TalkBack do Google (Apache 2.0). Objetivo de longo prazo: leitor de tela
  próprio para Android chamado **Bro Blind Screen Reader** (antes "Leitor Vini";
  o nome antigo ainda aparece em nomes internos, como pacotes Java, keystore e
  nome do arquivo APK, que foram mantidos de propósito).
- O dono do projeto é cego, usa leitor de tela e não tem experiência com programação.
  Responda a ele em português, em texto corrido simples, sem tabelas, sem listas e
  sem formatação markdown (atrapalham o leitor de tela). Explique o que ele precisa
  fazer no celular ou no GitHub de forma concreta.
- Não há Android SDK no ambiente de sessão do Claude; a compilação é validada pelo
  GitHub Actions (workflow `.github/workflows/compilar-apk.yml`). Acompanhe os runs
  com as ferramentas do GitHub e corrija até ficar verde.

## Prioridades do projeto
1. Gestos com resposta muito mais rápida que o TalkBack.
2. Personalização extrema, no estilo do leitor de tela chinês Jieshuo (Commentary
   Screen Reader).

## Decisões tomadas (etapa 1: só renomear e compilar, sem mudar comportamento)
- `applicationId` = `com.vinicius.leitor`, definido em `shared.gradle`
  (`talkbackApplicationId`). Todo o resto deriva dele: `BuildConfig.APPLICATION_ID`,
  `BuildConfig.TALKBACK_APPLICATION_ID` (módulo utils), recurso `@string/app_id`,
  e as authorities dos providers (`${applicationId}.providers.*`) no manifest.
- O `namespace` do módulo raiz continua `com.android.talkback`, e os pacotes Java
  (`com.android.talkback.*`, `com.google.android.accessibility.*`,
  `com.google.android.marvin.talkback.TalkBackService`) NÃO foram renomeados.
  Isso é intencional: nomes de classe não conflitam entre apps, e renomear
  quebraria referências (ex.: `settingsActivity` nos XML de accessibilityservice).
- Nome visível: `@string/talkback_title` = "Bro Blind Screen Reader" (rótulo do
  serviço de acessibilidade e do app). `talkback_preferences_title` e os textos de
  `leitor_vini.xml` citam "Bro Blind Screen Reader" em todos os idiomas. Outros textos da interface (tutoriais etc.)
  ainda mencionam "TalkBack"; trocar depois, se desejado.
- Assinatura: keystore fixa em `keystore/leitor-vini.jks` (alias `leitorvini`,
  senhas `leitorvini`), guardada no repositório por ser projeto pessoal. Usada em
  debug e release. NUNCA trocar a chave, senão o celular exige desinstalar.
- `versionCode` = segundos desde 2024-01-01 UTC do instante da compilação
  (`LEITOR_BUILD_EPOCH`, definido pelo workflow; localmente usa a hora atual). Sempre
  crescente. `versionName` = tag do Release sem o "v" (`LEITOR_VERSION_NAME`), para o
  Obtainium comparar versões; localmente cai no valor antigo de `version.gradle`.
- Build no CI: Java 17 (Temurin), Gradle 8.14.3 (AGP 8.11.1 exige Gradle 8.13+),
  NDK 21.4.7075529 instalado via sdkmanager, tarefa `assemblePhoneRelease`.
- Publicação: cada run cria um GitHub Release novo com tag única
  `vAAAA.MM.DD-HHMMSS` (horário de Brasília), marcado como latest, com
  `LeitorVini_<AAAA-MM-DD_HHhMM>.apk` e uma cópia `LeitorVini.apk`. As notas do
  Release contêm a linha `versionCode=N`, lida pelo app. Compatível com Obtainium.
  (Os primeiros Releases usaram tags `build-<número>`.) Link fixo:
  https://github.com/vijfr10-design/talkback/releases/latest/download/LeitorVini.apk
- Licença: LICENSE original mantido; `NOTICE` informa que é baseado no TalkBack.
  Não usar "TalkBack" nem marcas do Google no nome do app.

## Renomeação para Bro Blind Screen Reader
- Só o nome visível mudou. O `applicationId` continua `com.vinicius.leitor` e os
  arquivos do Release continuam `LeitorVini*.apk`: mudar o identificador faria o
  Android tratar como outro app (sem atualizar por cima), e mudar o nome do arquivo
  quebraria o link fixo e o filtro `^LeitorVini\.apk$` configurado no Obtainium.

## Etapa 2: verificação de atualização no app
- Código em `talkback/src/main/java/com/vinicius/leitor/atualizacao/`
  (`VerificadorAtualizacao`, `ReceptorAtualizacao`). Textos e chaves em
  `talkback/src/main/res/values/leitor_vini.xml`.
- `TalkBackService.onServiceConnected` cria e inicia o verificador; `onUnbind` para.
  Verifica ao iniciar e, por um relógio de 1 hora, sempre que passaram 24 h da última
  verificação. Consulta `https://api.github.com/repos/vijfr10-design/talkback/releases/latest`.
- Se o `versionCode=N` das notas for maior que o instalado: fala curta + notificação.
  Tocar na notificação baixa o APK para `cacheDir/atualizacao/` e abre o instalador
  via FileProvider (`cache-path` em `res/xml/file_paths.xml`). Permissão
  `REQUEST_INSTALL_PACKAGES` no manifest.
- Configurações (tela principal, categoria "Atualizações"): chave para ligar/desligar
  (padrão ligado) e botão "Verificar atualização agora" (mostra diálogo com o resultado),
  tratado em `TalkBackPreferenceFragment`.

## Compilação local (sessões do Claude)
- O ambiente não vem com Android SDK, mas dá para instalar: command-line tools em
  `/opt/android-sdk` (`platforms;android-36`, `build-tools;36.0.0`), JDK 17 Temurin
  em `/opt/jdk-17*`, `local.properties` com `sdk.dir=/opt/android-sdk` (ignorado pelo
  git) e um init script local em `~/.gradle/init.d/` trocando o Maven Central pelo
  espelho `https://maven-central.storage-download.googleapis.com/maven2/` (o Central
  devolve 429). Com isso, `gradle :compilePhoneReleaseJavaWithJavac` checa o Java em
  ~1 min, sem NDK. O APK final continua sendo feito pelo GitHub Actions.

## Melhorias de velocidade e personalização
### Etapa 1: medidor de latência
- `utils/src/main/java/com/vinicius/leitor/latencia/MedidorLatencia.java`: medição
  estática, barata quando desligada. Pontos de marcação: `TalkBackService.onGesture`
  (início; usa o horário do último MotionEvent do gesto quando o Android entrega),
  `FocusProcessorForLogicalNavigation.navigateToDefaultOrMacroGranularityTarget`
  (árvore de travessia e cálculo do próximo), `FocusManagerInternal.
  performAccessibilityFocusActionInternal` (aplicação do foco), evento
  TYPE_VIEW_ACCESSIBILITY_FOCUSED, `SpeechControllerImpl.speak` (texto montado),
  `FailoverTextToSpeech.speak` (envio ao TTS) e `onStart` do TTS (início do áudio).
- Fala "Foco X, fala Y milissegundos" (fila, sem histórico) e registra as fases no
  log com a tag `BBSR-Latencia`. Preferência `pref_bbsr_medidor_latencia` (padrão
  desligado), controlada por `ControleMedidor` (módulo talkback).

### Etapa 2: velocidade de gesto e foco
- `utils/.../com/vinicius/leitor/velocidade/ConfigVelocidade.java`: todos os atrasos do
  caminho gesto → foco → som → fala, com valor original, novo padrão (reduzido só quando
  seguro) e mínimo seguro, e comentário do motivo. Grupos: toque, eventos (cliques),
  conteúdo e rolagem, dicas, som de gesto, janelas. Níveis por grupo: original, padrão,
  rápido, mínimo. Modo turbo = mínimo em tudo + cache + som imediato.
- Mantidos no original de propósito (são janelas de filtro contra fala duplicada, não
  esperas): DELAY_AUTO_AFTER_STATE, DELAY_SELECTED_AFTER_FOCUS, TIMEOUT_TOLERANCE_MS
  (e web/TV), filtros de TextEventFilter.
- `CacheTravessia` (utils): guarda a OrderedTraversalStrategy (árvore + cache de nós que
  falam) por raiz de janela; invalidado no início de `TalkBackService.onAccessibilityEvent`
  por qualquer evento que possa mudar a estrutura; pré-calcula em uma HandlerThread depois
  de foco ou mudança de tela; prefetch de descendentes no Android 13+. Entra por
  `TraversalStrategyUtils.getTraversalStrategy`. Desligado por padrão (experimental).
- `AntecipacaoFoco` (talkback): ao aplicar o foco por navegação ou toque, interrompe a
  fala anterior e toca som + vibração na hora; o compositor
  (`EventTypeViewAccessibilityFocusedFeedbackRule`) não repete o som. Ligado por padrão.
- Fala enxuta: `GlobalVariables.getSpeakRoles/getUsageHintEnabled` e
  `TreeNodesDescription` (selecionado/não selecionado/somente leitura) consultam
  `ConfigVelocidade`.
- Velocidade de fala extra: multiplicador em `FailoverTextToSpeech` sobre a velocidade do
  sistema (até 4x).
- Telas: `VelocidadeFragment` (res/xml/bbsr_velocidade_preferences.xml) e
  `FalaEnxutaFragment` (bbsr_fala_enxuta_preferences.xml), na categoria "Personalização do
  Bro Blind" da tela principal. Chaves `bbsr_*` definidas em `ConfigVelocidade`.
  `ControleVelocidade` carrega e observa as preferências no serviço.
