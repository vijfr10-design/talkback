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

package com.vinicius.leitor.gestos;

import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_FLUSH_ALL;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.screencapture.ScreenshotCapture;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.vinicius.leitor.latencia.ControleMedidor;
import com.vinicius.leitor.velocidade.ConfigVelocidade;
import java.util.Date;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Ações novas do Bro Blind Screen Reader que podem ser associadas a gestos. */
public final class AcoesBroBlind {

  private static final String TAG = "BBSR-Acoes";

  public static final String OCR_TELA = "BBSR_OCR_TELA";
  public static final String ULTIMA_NOTIFICACAO = "BBSR_ULTIMA_NOTIFICACAO";
  public static final String HORA_BATERIA = "BBSR_HORA_BATERIA";
  public static final String ALTERNAR_TURBO = "BBSR_ALTERNAR_TURBO";
  public static final String ALTERNAR_FALA_ENXUTA = "BBSR_ALTERNAR_FALA_ENXUTA";
  public static final String ALTERNAR_MEDIDOR = "BBSR_ALTERNAR_MEDIDOR";

  private static @Nullable String ultimaNotificacao;
  private static @Nullable TextRecognizer reconhecedor;

  private AcoesBroBlind() {}

  public static boolean ehAcao(String acao) {
    return acao != null && acao.startsWith("BBSR_") && !PerfisGestos.USAR_GLOBAL.equals(acao);
  }

  /** Guarda o texto da última notificação recebida (chamado para cada evento de notificação). */
  public static void registrarNotificacao(Context context, AccessibilityEvent event) {
    if (event.getEventType() != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
      return;
    }
    Parcelable dados = event.getParcelableData();
    if (!(dados instanceof Notification)) {
      // Toasts e outros avisos também chegam como este evento, sem Notification.
      return;
    }
    Notification notificacao = (Notification) dados;
    StringBuilder texto = new StringBuilder();
    CharSequence pacote = event.getPackageName();
    if (pacote != null) {
      try {
        PackageManager pm = context.getPackageManager();
        texto.append(pm.getApplicationLabel(pm.getApplicationInfo(pacote.toString(), 0)));
      } catch (PackageManager.NameNotFoundException e) {
        texto.append(pacote);
      }
      texto.append(". ");
    }
    Bundle extras = notificacao.extras;
    CharSequence titulo = extras == null ? null : extras.getCharSequence(Notification.EXTRA_TITLE);
    CharSequence conteudo =
        extras == null ? null : extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
    if (TextUtils.isEmpty(conteudo) && extras != null) {
      conteudo = extras.getCharSequence(Notification.EXTRA_TEXT);
    }
    if (!TextUtils.isEmpty(titulo)) {
      texto.append(titulo).append(". ");
    }
    if (!TextUtils.isEmpty(conteudo)) {
      texto.append(conteudo);
    } else if (TextUtils.isEmpty(titulo)) {
      List<CharSequence> textos = event.getText();
      for (CharSequence parte : textos) {
        texto.append(parte).append(' ');
      }
      if (textos.isEmpty() && !TextUtils.isEmpty(notificacao.tickerText)) {
        texto.append(notificacao.tickerText);
      }
    }
    String resultado = texto.toString().trim();
    if (!resultado.isEmpty()) {
      ultimaNotificacao = resultado;
    }
  }

  /** Executa uma ação do Bro Blind. Devolve falso se a ação falhou. */
  public static boolean executar(
      com.google.android.accessibility.talkback.TalkBackService servico,
      Pipeline.FeedbackReturner pipeline,
      String acao,
      EventId eventId) {
    switch (acao) {
      case OCR_TELA:
        return lerTelaComOcr(servico, pipeline, eventId);
      case ULTIMA_NOTIFICACAO:
        falar(
            pipeline,
            eventId,
            ultimaNotificacao == null
                ? "Nenhuma notificação recebida desde que o leitor foi ligado."
                : ultimaNotificacao);
        return true;
      case HORA_BATERIA:
        falar(pipeline, eventId, horaEBateria(servico));
        return true;
      case ALTERNAR_TURBO:
        falar(
            pipeline,
            eventId,
            alternar(servico, ConfigVelocidade.CHAVE_TURBO, false)
                ? "Modo turbo ligado"
                : "Modo turbo desligado");
        return true;
      case ALTERNAR_FALA_ENXUTA:
        falar(
            pipeline,
            eventId,
            alternar(servico, ConfigVelocidade.CHAVE_FALA_ENXUTA, false)
                ? "Fala enxuta ligada"
                : "Fala enxuta desligada");
        return true;
      case ALTERNAR_MEDIDOR:
        boolean ligado = !ControleMedidor.estaLigado(servico);
        ControleMedidor.definir(servico, ligado);
        falar(
            pipeline,
            eventId,
            ligado ? "Medidor de latência ligado" : "Medidor de latência desligado");
        return true;
      default:
        LogUtils.w(TAG, "Ação desconhecida: %s", acao);
        return false;
    }
  }

  private static boolean alternar(Context context, String chave, boolean padrao) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    boolean novo = !prefs.getBoolean(chave, padrao);
    prefs.edit().putBoolean(chave, novo).apply();
    return novo;
  }

  private static String horaEBateria(Context context) {
    String hora = DateFormat.getTimeFormat(context).format(new Date());
    BatteryManager bateria = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    if (bateria == null) {
      return hora;
    }
    int nivel = bateria.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    StringBuilder texto = new StringBuilder(hora);
    if (nivel > 0 && nivel <= 100) {
      texto.append(". Bateria ").append(nivel).append(" por cento");
      if (bateria.isCharging()) {
        texto.append(", carregando");
      }
    }
    return texto.append('.').toString();
  }

  private static boolean lerTelaComOcr(
      com.google.android.accessibility.talkback.TalkBackService servico,
      Pipeline.FeedbackReturner pipeline,
      EventId eventId) {
    falar(pipeline, eventId, "Lendo o texto da tela");
    ScreenshotCapture.takeScreenshot(
        servico,
        (Bitmap captura, boolean formatoSuportado) -> {
          if (captura == null) {
            falar(pipeline, eventId, "Não foi possível capturar a tela.");
            return;
          }
          try {
            if (reconhecedor == null) {
              reconhecedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            }
            reconhecedor
                .process(InputImage.fromBitmap(captura, 0))
                .addOnSuccessListener(
                    resultado -> {
                      String texto = resultado.getText().trim();
                      falar(
                          pipeline,
                          eventId,
                          texto.isEmpty() ? "Nenhum texto encontrado na tela." : texto);
                    })
                .addOnFailureListener(
                    erro -> {
                      LogUtils.w(TAG, "OCR falhou: %s", erro);
                      falar(
                          pipeline,
                          eventId,
                          "O reconhecimento de texto não está disponível. Verifique se o Google"
                              + " Play Services está atualizado.");
                    });
          } catch (RuntimeException e) {
            LogUtils.w(TAG, "OCR falhou: %s", e);
            falar(pipeline, eventId, "O reconhecimento de texto não está disponível.");
          }
        });
    return true;
  }

  private static void falar(Pipeline.FeedbackReturner pipeline, EventId eventId, String texto) {
    pipeline.returnFeedback(
        eventId,
        Feedback.speech(
            texto,
            SpeakOptions.create()
                .setQueueMode(QUEUE_MODE_FLUSH_ALL)
                .setFlags(FeedbackItem.FLAG_FORCE_FEEDBACK)));
  }

  /** Texto de cada ação nova, para a lista de ações dos gestos. */
  public static String[][] itensDaLista(Context context) {
    return new String[][] {
      {"Ler o texto da tela com reconhecimento de texto (OCR)", OCR_TELA},
      {"Ler a última notificação", ULTIMA_NOTIFICACAO},
      {"Ler hora e bateria", HORA_BATERIA},
      {
        context.getString(R.string.title_repeat_last_spoken_phrase),
        context.getString(R.string.shortcut_value_repeat_last_spoken_phrase)
      },
      {
        context.getString(R.string.title_copy_last_spoken_phrase),
        context.getString(R.string.shortcut_value_copy_last_spoken_phrase)
      },
      {
        context.getString(R.string.shortcut_pause_or_resume_feedback),
        context.getString(R.string.shortcut_value_pause_or_resume_feedback)
      },
      {"Ligar ou desligar o Modo turbo", ALTERNAR_TURBO},
      {"Ligar ou desligar a Fala enxuta", ALTERNAR_FALA_ENXUTA},
      {"Ligar ou desligar o medidor de latência", ALTERNAR_MEDIDOR},
    };
  }
}
