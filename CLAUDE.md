# CLAUDE.md — Leitor Vini

## Contexto
- Fork do TalkBack do Google (Apache 2.0). Objetivo de longo prazo: leitor de tela
  próprio para Android chamado **Leitor Vini**.
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
- Nome visível: `@string/talkback_title` = "Leitor Vini" (rótulo do serviço de
  acessibilidade e do app). `talkback_preferences_title` passou a citar
  "Leitor Vini" em todos os idiomas. Outros textos da interface (tutoriais etc.)
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
