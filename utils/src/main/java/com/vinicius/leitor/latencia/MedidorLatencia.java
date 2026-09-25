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

package com.vinicius.leitor.latencia;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Medidor de latência do Bro Blind Screen Reader.
 *
 * <p>Quando ligado, cada gesto inicia uma medição. Os pontos do caminho do gesto até a fala
 * chamam os métodos estáticos desta classe, que anotam o instante de cada fase. Ao começar o
 * áudio da primeira fala enviada depois do gesto (ou depois de {@link #TEMPO_LIMITE_MS}), o
 * medidor registra todas as fases no log (tag {@link #TAG}) e fala dois números: o tempo do
 * gesto até a mudança de foco e o tempo do gesto até o início real da fala.
 *
 * <p>Quando desligado, todos os métodos retornam logo no primeiro teste, sem custo perceptível.
 */
public final class MedidorLatencia {

  public static final String TAG = "BBSR-Latencia";

  /** Tempo máximo esperando foco e fala depois do gesto. */
  private static final long TEMPO_LIMITE_MS = 3000;

  /** Fala o resultado da medição. Definido pelo serviço. */
  public interface Anunciador {
    void anunciar(String texto);
  }

  private static volatile boolean ligado = false;
  private static @Nullable Anunciador anunciador;

  private static final Object trava = new Object();
  private static final Handler handlerPrincipal = new Handler(Looper.getMainLooper());
  private static final Runnable finalizarPorTempo = () -> finalizar("tempo esgotado");

  // Estado da medição atual. Protegido por "trava".
  private static boolean medindo = false;
  private static long inicioMs;
  private static String nomeGesto = "";
  private static long focoMs = -1;
  private static long falaMs = -1;
  private static @Nullable String idFalaEsperada;
  private static final List<String> fases = new ArrayList<>();

  private MedidorLatencia() {}

  public static boolean estaLigado() {
    return ligado;
  }

  public static void setLigado(boolean valor) {
    ligado = valor;
    if (!valor) {
      synchronized (trava) {
        medindo = false;
      }
      handlerPrincipal.removeCallbacks(finalizarPorTempo);
    }
  }

  public static void setAnunciador(@Nullable Anunciador novo) {
    anunciador = novo;
  }

  /** Indica se há uma medição em andamento (para evitar trabalho extra quando não há). */
  public static boolean medindo() {
    if (!ligado) {
      return false;
    }
    synchronized (trava) {
      return medindo;
    }
  }

  /**
   * Início da medição: gesto recebido pelo serviço.
   *
   * @param nome nome do gesto, para o log
   * @param instanteGestoMs instante (uptime) do fim físico do gesto, se conhecido, ou -1
   */
  public static void iniciarGesto(String nome, long instanteGestoMs) {
    if (!ligado) {
      return;
    }
    long agora = SystemClock.uptimeMillis();
    synchronized (trava) {
      medindo = true;
      inicioMs = (instanteGestoMs > 0 && instanteGestoMs <= agora) ? instanteGestoMs : agora;
      nomeGesto = nome;
      focoMs = -1;
      falaMs = -1;
      idFalaEsperada = null;
      fases.clear();
      if (inicioMs != agora) {
        fases.add(
            String.format(
                Locale.ROOT, "fim físico do gesto: 0 ms; entrega do gesto pelo Android: %d ms",
                agora - inicioMs));
      }
      fases.add(String.format(Locale.ROOT, "recebimento do gesto: %d ms", agora - inicioMs));
    }
    handlerPrincipal.removeCallbacks(finalizarPorTempo);
    handlerPrincipal.postDelayed(finalizarPorTempo, TEMPO_LIMITE_MS);
  }

  /** Anota um instante do caminho do gesto (ex.: "evento de foco recebido"). */
  public static void marcar(String fase) {
    if (!ligado) {
      return;
    }
    long agora = SystemClock.uptimeMillis();
    synchronized (trava) {
      if (!medindo) {
        return;
      }
      fases.add(String.format(Locale.ROOT, "%s: %d ms", fase, agora - inicioMs));
    }
  }

  /** Anota a duração de uma fase que começou em {@code inicioFaseMs} (uptime) e terminou agora. */
  public static void duracao(String fase, long inicioFaseMs) {
    if (!ligado) {
      return;
    }
    long agora = SystemClock.uptimeMillis();
    synchronized (trava) {
      if (!medindo) {
        return;
      }
      fases.add(
          String.format(
              Locale.ROOT,
              "%s: durou %d ms (terminou em %d ms)",
              fase,
              agora - inicioFaseMs,
              agora - inicioMs));
    }
  }

  /** Instante atual para medir a duração de uma fase, ou 0 quando não há medição. */
  public static long agora() {
    return medindo() ? SystemClock.uptimeMillis() : 0;
  }

  /** Foco de acessibilidade aplicado com sucesso. */
  public static void registrarFoco() {
    if (!ligado) {
      return;
    }
    long agora = SystemClock.uptimeMillis();
    synchronized (trava) {
      if (!medindo || focoMs >= 0) {
        return;
      }
      focoMs = agora - inicioMs;
      fases.add(String.format(Locale.ROOT, "foco aplicado: %d ms", focoMs));
    }
  }

  /** Texto montado e entregue ao controlador de fala. */
  public static void registrarTextoMontado(@Nullable CharSequence texto) {
    if (!ligado || texto == null || texto.length() == 0) {
      return;
    }
    long agora = SystemClock.uptimeMillis();
    synchronized (trava) {
      if (!medindo || idFalaEsperada != null) {
        return;
      }
      fases.add(String.format(Locale.ROOT, "montagem do texto concluída: %d ms", agora - inicioMs));
    }
  }

  /** Fala enviada ao motor de TTS. */
  public static void registrarEnvioTts(@Nullable String idFala) {
    if (!ligado || idFala == null) {
      return;
    }
    long agora = SystemClock.uptimeMillis();
    synchronized (trava) {
      if (!medindo || idFalaEsperada != null) {
        return;
      }
      idFalaEsperada = idFala;
      fases.add(String.format(Locale.ROOT, "envio ao TTS: %d ms", agora - inicioMs));
    }
  }

  /** O motor de TTS (ou o cache de áudio) começou a tocar a fala. Pode vir de qualquer thread. */
  public static void registrarInicioAudio(@Nullable String idFala) {
    if (!ligado || idFala == null) {
      return;
    }
    long agora = SystemClock.uptimeMillis();
    synchronized (trava) {
      if (!medindo || !idFala.equals(idFalaEsperada)) {
        return;
      }
      falaMs = agora - inicioMs;
      fases.add(String.format(Locale.ROOT, "início do áudio: %d ms", falaMs));
    }
    handlerPrincipal.post(() -> finalizar("áudio iniciado"));
  }

  private static void finalizar(String motivo) {
    handlerPrincipal.removeCallbacks(finalizarPorTempo);
    long foco;
    long fala;
    StringBuilder log = new StringBuilder();
    synchronized (trava) {
      if (!medindo) {
        return;
      }
      medindo = false;
      foco = focoMs;
      fala = falaMs;
      log.append("Gesto ").append(nomeGesto).append(" (").append(motivo).append(")");
      for (String fase : fases) {
        log.append("\n  ").append(fase);
      }
    }
    Log.i(TAG, log.toString());
    if (foco < 0 && fala < 0) {
      return;
    }
    String texto =
        (foco >= 0 ? "Foco " + foco : "Sem foco")
            + ", "
            + (fala >= 0 ? "fala " + fala : "sem fala")
            + " milissegundos";
    Anunciador atual = anunciador;
    if (atual != null) {
      atual.anunciar(texto);
    }
  }
}
