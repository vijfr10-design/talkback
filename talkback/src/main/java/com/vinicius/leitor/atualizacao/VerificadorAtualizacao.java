/*
 * Copyright (C) 2026 Vinicius
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vinicius.leitor.atualizacao;

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_QUEUE;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;
import com.google.android.accessibility.talkback.BuildConfig;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.utils.NotificationUtils;
import com.google.android.accessibility.utils.PackageManagerUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Verifica se há uma versão mais nova do Leitor Vini no GitHub Releases.
 *
 * <p>Ao iniciar o serviço e depois uma vez por dia, consulta o último Release pela API do GitHub.
 * O workflow de compilação escreve "versionCode=N" no texto do Release; se N for maior que o
 * versionCode instalado, o leitor avisa com uma fala curta e uma notificação. Tocar na
 * notificação baixa o APK e abre a tela de instalação do Android.
 */
public final class VerificadorAtualizacao {

  private static final String TAG = "LeitorViniAtualizacao";

  private static final String URL_ULTIMO_RELEASE =
      "https://api.github.com/repos/vijfr10-design/talkback/releases/latest";
  private static final String AUTORIDADE_ARQUIVOS =
      BuildConfig.APPLICATION_ID + ".providers.FileProvider";
  private static final String TIPO_APK = "application/vnd.android.package-archive";

  private static final long INTERVALO_VERIFICACAO_MS = TimeUnit.DAYS.toMillis(1);
  private static final long INTERVALO_RELOGIO_MS = TimeUnit.HOURS.toMillis(1);
  private static final int TEMPO_LIMITE_REDE_MS = (int) TimeUnit.SECONDS.toMillis(30);
  private static final String PREF_ULTIMA_VERIFICACAO = "leitor_vini_ultima_verificacao_ms";

  private static final int ID_NOTIFICACAO = 0x4c56;
  private static final Pattern PADRAO_VERSION_CODE = Pattern.compile("versionCode=(\\d+)");

  private static final ExecutorService executor = Executors.newSingleThreadExecutor();
  private static final Handler handlerPrincipal = new Handler(Looper.getMainLooper());

  /** Canal de fala do serviço em execução, ou null quando o serviço está desligado. */
  private static Pipeline.@Nullable FeedbackReturner falante;

  /** Dados do Release mais recente. */
  public static final class NovaVersao {
    public final String tag;
    public final long versionCode;
    public final String urlApk;

    NovaVersao(String tag, long versionCode, String urlApk) {
      this.tag = tag;
      this.versionCode = versionCode;
      this.urlApk = urlApk;
    }
  }

  /** Resultado de uma verificação. Executado na thread principal. */
  public interface Resultado {
    /**
     * @param novaVersao a versão mais nova encontrada, ou null se o app já está atualizado
     * @param erro descrição do erro, ou null se a verificação funcionou
     */
    void aoTerminar(@Nullable NovaVersao novaVersao, @Nullable String erro);
  }

  private final Context context;
  private final Pipeline.FeedbackReturner pipeline;
  private final Runnable relogio =
      new Runnable() {
        @Override
        public void run() {
          SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
          long ultima = prefs.getLong(PREF_ULTIMA_VERIFICACAO, 0);
          if (System.currentTimeMillis() - ultima >= INTERVALO_VERIFICACAO_MS) {
            verificarSeLigado(context);
          }
          handlerPrincipal.postDelayed(this, INTERVALO_RELOGIO_MS);
        }
      };

  public VerificadorAtualizacao(Context context, Pipeline.FeedbackReturner pipeline) {
    this.context = context;
    this.pipeline = pipeline;
  }

  /** Chamado quando o serviço de acessibilidade liga: verifica agora e depois uma vez por dia. */
  public void iniciar() {
    falante = pipeline;
    handlerPrincipal.removeCallbacks(relogio);
    verificarSeLigado(context);
    handlerPrincipal.postDelayed(relogio, INTERVALO_RELOGIO_MS);
  }

  /** Chamado quando o serviço de acessibilidade desliga. */
  public void parar() {
    handlerPrincipal.removeCallbacks(relogio);
    if (falante == pipeline) {
      falante = null;
    }
  }

  /** Indica se a verificação automática está ligada nas configurações. */
  public static boolean estaLigado(Context context) {
    return SharedPreferencesUtils.getSharedPreferences(context)
        .getBoolean(
            context.getString(R.string.pref_leitor_verificar_atualizacao_key),
            context.getResources().getBoolean(R.bool.pref_leitor_verificar_atualizacao_default));
  }

  private static void verificarSeLigado(Context context) {
    if (!estaLigado(context)) {
      return;
    }
    verificar(
        context,
        (novaVersao, erro) -> {
          if (novaVersao != null) {
            avisar(context, novaVersao);
          }
        });
  }

  /** Verifica agora, em segundo plano, e entrega o resultado na thread principal. */
  public static void verificar(Context context, Resultado resultado) {
    Context app = context.getApplicationContext();
    executor.execute(
        () -> {
          NovaVersao novaVersao = null;
          String erro = null;
          try {
            NovaVersao ultima = buscarUltimoRelease();
            SharedPreferencesUtils.getSharedPreferences(app)
                .edit()
                .putLong(PREF_ULTIMA_VERIFICACAO, System.currentTimeMillis())
                .apply();
            if (ultima != null && ultima.versionCode > PackageManagerUtils.getVersionCode(app)) {
              novaVersao = ultima;
            }
          } catch (IOException | JSONException | RuntimeException e) {
            LogUtils.w(TAG, "Falha ao verificar atualização: %s", e);
            erro = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
          }
          final NovaVersao encontrada = novaVersao;
          final String mensagemErro = erro;
          handlerPrincipal.post(() -> resultado.aoTerminar(encontrada, mensagemErro));
        });
  }

  private static @Nullable NovaVersao buscarUltimoRelease() throws IOException, JSONException {
    HttpURLConnection conexao = abrir(URL_ULTIMO_RELEASE);
    conexao.setRequestProperty("Accept", "application/vnd.github+json");
    try {
      int codigo = conexao.getResponseCode();
      if (codigo != HttpURLConnection.HTTP_OK) {
        throw new IOException("GitHub respondeu " + codigo);
      }
      String corpo;
      try (InputStream entrada = conexao.getInputStream()) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        copiar(entrada, saida);
        corpo = new String(saida.toByteArray(), StandardCharsets.UTF_8);
      }
      JSONObject release = new JSONObject(corpo);
      Matcher matcher = PADRAO_VERSION_CODE.matcher(release.optString("body", ""));
      if (!matcher.find()) {
        return null;
      }
      long versionCode = Long.parseLong(matcher.group(1));
      String urlApk = null;
      JSONArray anexos = release.optJSONArray("assets");
      if (anexos != null) {
        for (int i = 0; i < anexos.length(); i++) {
          JSONObject anexo = anexos.getJSONObject(i);
          if (anexo.optString("name", "").endsWith(".apk")) {
            urlApk = anexo.optString("browser_download_url", null);
            break;
          }
        }
      }
      if (urlApk == null) {
        return null;
      }
      return new NovaVersao(release.optString("tag_name", ""), versionCode, urlApk);
    } finally {
      conexao.disconnect();
    }
  }

  /** Avisa com uma fala curta e uma notificação que há versão nova. */
  private static void avisar(Context context, NovaVersao novaVersao) {
    falar(context.getString(R.string.leitor_atualizacao_fala_disponivel));

    Intent intent =
        new Intent(context, ReceptorAtualizacao.class)
            .setAction(ReceptorAtualizacao.ACAO_BAIXAR)
            .putExtra(ReceptorAtualizacao.EXTRA_URL, novaVersao.urlApk);
    PendingIntent aoTocar =
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    String titulo = context.getString(R.string.leitor_atualizacao_notificacao_titulo);
    String texto =
        context.getString(R.string.leitor_atualizacao_notificacao_texto, novaVersao.tag);
    mostrarNotificacao(
        context,
        NotificationUtils.createDefaultNotificationBuilder(context)
            .setTicker(titulo)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(aoTocar)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH));
  }

  /** Baixa o APK em segundo plano e abre a tela de instalação do Android. */
  public static void baixarEInstalar(Context context, String urlApk) {
    Context app = context.getApplicationContext();
    falar(app.getString(R.string.leitor_atualizacao_fala_baixando));
    String titulo = app.getString(R.string.leitor_atualizacao_baixando);
    mostrarNotificacao(
        app,
        NotificationUtils.createDefaultNotificationBuilder(app)
            .setTicker(titulo)
            .setContentTitle(titulo)
            .setOngoing(true)
            .setProgress(0, 0, true));
    executor.execute(
        () -> {
          try {
            File arquivo = baixar(app, urlApk);
            handlerPrincipal.post(() -> abrirInstalador(app, arquivo));
          } catch (IOException | RuntimeException e) {
            LogUtils.w(TAG, "Falha ao baixar atualização: %s", e);
            handlerPrincipal.post(
                () -> {
                  String erro = app.getString(R.string.leitor_atualizacao_erro_download);
                  falar(erro);
                  mostrarNotificacao(
                      app,
                      NotificationUtils.createDefaultNotificationBuilder(app)
                          .setTicker(erro)
                          .setContentTitle(erro)
                          .setAutoCancel(true));
                });
          }
        });
  }

  private static File baixar(Context context, String urlApk) throws IOException {
    File pasta = new File(context.getCacheDir(), "atualizacao");
    if (!pasta.isDirectory() && !pasta.mkdirs()) {
      throw new IOException("Não foi possível criar " + pasta);
    }
    File arquivo = new File(pasta, "LeitorVini.apk");
    File temporario = new File(pasta, "LeitorVini.apk.parcial");
    HttpURLConnection conexao = abrir(urlApk);
    try {
      int codigo = conexao.getResponseCode();
      if (codigo != HttpURLConnection.HTTP_OK) {
        throw new IOException("Download respondeu " + codigo);
      }
      try (InputStream entrada = conexao.getInputStream();
          OutputStream saida = new FileOutputStream(temporario)) {
        copiar(entrada, saida);
      }
    } finally {
      conexao.disconnect();
    }
    if (arquivo.exists() && !arquivo.delete()) {
      throw new IOException("Não foi possível apagar " + arquivo);
    }
    if (!temporario.renameTo(arquivo)) {
      throw new IOException("Não foi possível salvar " + arquivo);
    }
    return arquivo;
  }

  private static void abrirInstalador(Context context, File arquivo) {
    Uri uri = FileProvider.getUriForFile(context, AUTORIDADE_ARQUIVOS, arquivo);
    Intent instalar =
        new Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, TIPO_APK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

    // Deixa uma notificação para abrir o instalador, caso o Android não abra a tela sozinho.
    String titulo = context.getString(R.string.leitor_atualizacao_pronta);
    mostrarNotificacao(
        context,
        NotificationUtils.createDefaultNotificationBuilder(context)
            .setTicker(titulo)
            .setContentTitle(titulo)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    instalar,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
            .setAutoCancel(true));
    try {
      context.startActivity(instalar);
    } catch (RuntimeException e) {
      LogUtils.w(TAG, "Não foi possível abrir o instalador: %s", e);
      falar(titulo);
    }
  }

  private static void falar(String texto) {
    Pipeline.FeedbackReturner atual = falante;
    if (atual == null) {
      return;
    }
    atual.returnFeedback(
        EVENT_ID_UNTRACKED,
        Feedback.speech(
            texto,
            SpeakOptions.create()
                .setQueueMode(QUEUE_MODE_QUEUE)
                .setFlags(FeedbackItem.FLAG_NO_HISTORY)));
  }

  private static void mostrarNotificacao(Context context, NotificationCompat.Builder builder) {
    if (!NotificationUtils.hasPostNotificationPermission(context)) {
      return;
    }
    try {
      NotificationManagerCompat.from(context).notify(ID_NOTIFICACAO, builder.build());
    } catch (SecurityException e) {
      LogUtils.w(TAG, "Sem permissão para notificar: %s", e);
    }
  }

  private static HttpURLConnection abrir(String url) throws IOException {
    HttpURLConnection conexao = (HttpURLConnection) new URL(url).openConnection();
    conexao.setConnectTimeout(TEMPO_LIMITE_REDE_MS);
    conexao.setReadTimeout(TEMPO_LIMITE_REDE_MS);
    conexao.setInstanceFollowRedirects(true);
    conexao.setRequestProperty("User-Agent", "LeitorVini");
    return conexao;
  }

  private static void copiar(InputStream entrada, OutputStream saida) throws IOException {
    byte[] buffer = new byte[64 * 1024];
    int lidos;
    while ((lidos = entrada.read(buffer)) != -1) {
      saida.write(buffer, 0, lidos);
    }
  }
}
