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

package com.vinicius.leitor.sons;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.SparseArray;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Pacote de sons personalizado do Bro Blind Screen Reader.
 *
 * <p>O usuário escolhe uma pasta do celular. Os arquivos da pasta cujo nome (sem extensão) é igual
 * ao nome de um som do leitor (ex.: {@code focus.ogg}, {@code gesture_end.mp3}) são copiados para
 * o armazenamento interno do app e passam a substituir o som original. Os sons que faltam na pasta
 * continuam com o som padrão. Formatos aceitos: ogg, mp3 e wav (tocados pelo SoundPool, como os
 * originais) e mid/midi (tocados pelo MediaPlayer, pois o SoundPool não toca MIDI).
 */
public final class PacoteSons {

  private static final String TAG = "BBSR-Sons";
  private static final String PASTA_INTERNA = "pacote_sons";
  private static final String[] EXTENSOES = {"ogg", "mp3", "wav", "mid", "midi"};

  /** Nomes dos sons do leitor, na ordem mostrada na tela Sons. */
  public static final String[] NOMES = {
    "focus",
    "focus_actionable",
    "view_entered",
    "chime_up",
    "chime_down",
    "gesture_begin",
    "gesture_end",
    "tick",
    "scroll_tone",
    "complete",
    "long_clicked",
    "loading",
    "window_state",
    "hyperlink",
    "formatting",
    "typo",
    "volume_beep",
    "browse_mode_on_v4_2",
    "browse_mode_off_v4_2",
  };

  private static volatile int versao = 0;
  private static volatile Map<String, File> arquivos = Collections.emptyMap();
  private static final SparseArray<String> cacheNomes = new SparseArray<>();
  private static @Nullable MediaPlayer tocadorMidi;

  private PacoteSons() {}

  /** Muda sempre que o pacote é recarregado (o FeedbackController descarta os sons antigos). */
  public static int versao() {
    return versao;
  }

  /** Lê a pasta interna do pacote. Chamado ao ligar o serviço e depois de importar. */
  public static synchronized void recarregar(Context context) {
    Map<String, File> novo = new HashMap<>();
    File[] lista = pastaInterna(context).listFiles();
    if (lista != null) {
      for (File arquivo : lista) {
        String nome = nomeSemExtensao(arquivo.getName());
        if (nome != null) {
          novo.put(nome, arquivo);
        }
      }
    }
    arquivos = novo;
    versao++;
    LogUtils.i(TAG, "Pacote de sons carregado: %d sons personalizados", novo.size());
  }

  /** Quantos sons estão substituídos. */
  public static int quantidade() {
    return arquivos.size();
  }

  /** Arquivo personalizado para o som com este nome, ou null se usa o padrão. */
  public static @Nullable File arquivoPara(String nome) {
    return arquivos.get(nome);
  }

  /** Arquivo personalizado para o recurso de som, ou null se usa o som padrão. */
  public static @Nullable File arquivoPara(Context context, int resId) {
    Map<String, File> atuais = arquivos;
    if (atuais.isEmpty()) {
      return null;
    }
    String nome;
    synchronized (cacheNomes) {
      nome = cacheNomes.get(resId);
      if (nome == null) {
        try {
          nome = context.getResources().getResourceEntryName(resId);
        } catch (Resources.NotFoundException e) {
          nome = "";
        }
        cacheNomes.put(resId, nome);
      }
    }
    return atuais.get(nome);
  }

  public static boolean ehMidi(File arquivo) {
    String nome = arquivo.getName().toLowerCase(Locale.ROOT);
    return nome.endsWith(".mid") || nome.endsWith(".midi");
  }

  /** Toca um arquivo MIDI (um de cada vez; um novo interrompe o anterior). */
  public static synchronized void tocarMidi(Context context, File arquivo, float volume) {
    pararMidi();
    try {
      MediaPlayer tocador = new MediaPlayer();
      tocador.setAudioAttributes(
          new AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build());
      tocador.setDataSource(arquivo.getAbsolutePath());
      tocador.setVolume(volume, volume);
      tocador.setOnCompletionListener(MediaPlayer::release);
      tocador.prepare();
      tocador.start();
      tocadorMidi = tocador;
    } catch (IOException | RuntimeException e) {
      LogUtils.w(TAG, "Não foi possível tocar %s: %s", arquivo, e);
    }
  }

  private static void pararMidi() {
    if (tocadorMidi != null) {
      try {
        tocadorMidi.release();
      } catch (RuntimeException e) {
        // Já liberado.
      }
      tocadorMidi = null;
    }
  }

  /**
   * Copia da pasta escolhida (árvore do seletor de pastas do Android) os arquivos com nomes de
   * sons do leitor. Substitui o pacote anterior. Rode em segundo plano.
   *
   * @return quantos sons foram importados
   */
  public static int importar(Context context, Uri arvore) throws IOException {
    ContentResolver resolver = context.getContentResolver();
    Uri filhos =
        DocumentsContract.buildChildDocumentsUriUsingTree(
            arvore, DocumentsContract.getTreeDocumentId(arvore));
    Map<String, Uri> encontrados = new HashMap<>();
    Map<String, String> nomesArquivos = new HashMap<>();
    try (Cursor cursor =
        resolver.query(
            filhos,
            new String[] {
              DocumentsContract.Document.COLUMN_DOCUMENT_ID,
              DocumentsContract.Document.COLUMN_DISPLAY_NAME
            },
            null,
            null,
            null)) {
      if (cursor == null) {
        throw new IOException("Pasta não pode ser lida");
      }
      while (cursor.moveToNext()) {
        String id = cursor.getString(0);
        String nomeArquivo = cursor.getString(1);
        String nome = nomeArquivo == null ? null : nomeSemExtensao(nomeArquivo);
        if (nome != null && ehNomeDeSom(nome)) {
          encontrados.put(nome, DocumentsContract.buildDocumentUriUsingTree(arvore, id));
          nomesArquivos.put(nome, nomeArquivo);
        }
      }
    }
    File pasta = pastaInterna(context);
    apagarConteudo(pasta);
    int copiados = 0;
    for (Map.Entry<String, Uri> item : encontrados.entrySet()) {
      File destino = new File(pasta, nomesArquivos.get(item.getKey()).toLowerCase(Locale.ROOT));
      try (InputStream entrada = resolver.openInputStream(item.getValue());
          OutputStream saida = new FileOutputStream(destino)) {
        if (entrada == null) {
          continue;
        }
        byte[] buffer = new byte[32 * 1024];
        int lidos;
        while ((lidos = entrada.read(buffer)) != -1) {
          saida.write(buffer, 0, lidos);
        }
        copiados++;
      }
    }
    return copiados;
  }

  /** Apaga o pacote personalizado (todos os sons voltam ao padrão). */
  public static void remover(Context context) {
    apagarConteudo(pastaInterna(context));
  }

  private static boolean ehNomeDeSom(String nome) {
    for (String conhecido : NOMES) {
      if (conhecido.equals(nome)) {
        return true;
      }
    }
    return false;
  }

  private static @Nullable String nomeSemExtensao(String nomeArquivo) {
    String minusculo = nomeArquivo.toLowerCase(Locale.ROOT);
    for (String extensao : EXTENSOES) {
      if (minusculo.endsWith("." + extensao)) {
        return minusculo.substring(0, minusculo.length() - extensao.length() - 1);
      }
    }
    return null;
  }

  private static File pastaInterna(Context context) {
    File pasta = new File(context.getFilesDir(), PASTA_INTERNA);
    if (!pasta.isDirectory()) {
      pasta.mkdirs();
    }
    return pasta;
  }

  private static void apagarConteudo(File pasta) {
    File[] lista = pasta.listFiles();
    if (lista != null) {
      for (File arquivo : lista) {
        arquivo.delete();
      }
    }
  }
}
