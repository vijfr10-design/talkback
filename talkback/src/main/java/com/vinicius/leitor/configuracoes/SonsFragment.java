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

package com.vinicius.leitor.configuracoes;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.preference.base.TalkbackBaseFragment;
import com.vinicius.leitor.sons.PacoteSons;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Tela Sons: escolhe a pasta do pacote de sons e lista cada som com um botão para ouvir. */
public class SonsFragment extends TalkbackBaseFragment {

  /** Som do leitor: nome do arquivo, descrição em português e recurso padrão. */
  private static final class Som {
    final String nome;
    final String descricao;
    final int recurso;

    Som(String nome, String descricao, int recurso) {
      this.nome = nome;
      this.descricao = descricao;
      this.recurso = recurso;
    }
  }

  private static final Som[] SONS = {
    new Som("focus", "Foco em um elemento de texto ou imagem", R.raw.focus),
    new Som("focus_actionable", "Foco em um elemento que pode ser ativado", R.raw.focus_actionable),
    new Som("view_entered", "Dedo entrou em um elemento na exploração por toque", R.raw.view_entered),
    new Som("chime_up", "Entrou em uma lista que rola", R.raw.chime_up),
    new Som("chime_down", "Saiu de uma lista que rola", R.raw.chime_down),
    new Som("gesture_begin", "Gesto em andamento", R.raw.gesture_begin),
    new Som("gesture_end", "Gesto reconhecido", R.raw.gesture_end),
    new Som("tick", "Tique, como ao mudar de item em um controle", R.raw.tick),
    new Som("scroll_tone", "Rolagem da tela", R.raw.scroll_tone),
    new Som("complete", "Ação concluída ou fim da tela", R.raw.complete),
    new Som("long_clicked", "Toque longo", R.raw.long_clicked),
    new Som("loading", "Carregando", R.raw.loading),
    new Som("window_state", "Janela ou tela nova", R.raw.window_state),
    new Som("hyperlink", "Link no texto", R.raw.hyperlink),
    new Som("formatting", "Formatação no texto", R.raw.formatting),
    new Som("typo", "Erro de digitação", R.raw.typo),
    new Som("volume_beep", "Bipe de volume", R.raw.volume_beep),
    new Som("browse_mode_on_v4_2", "Modo de navegação ligado", R.raw.browse_mode_on_v4_2),
    new Som("browse_mode_off_v4_2", "Modo de navegação desligado", R.raw.browse_mode_off_v4_2),
  };

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private @Nullable MediaPlayer tocador;

  private final ActivityResultLauncher<Uri> seletorPasta =
      registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(), this::aoEscolher);

  public SonsFragment() {
    super(R.xml.bbsr_sons_preferences);
  }

  @Override
  public CharSequence getTitle() {
    return getText(R.string.title_pref_bbsr_sons);
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);
    PacoteSons.recarregar(requireContext());
    Preference escolher = findPreference("bbsr_pacote_escolher");
    if (escolher != null) {
      escolher.setOnPreferenceClickListener(
          preference -> {
            try {
              seletorPasta.launch(null);
            } catch (RuntimeException e) {
              avisar("Não foi possível abrir o seletor de pastas.");
            }
            return true;
          });
    }
    Preference remover = findPreference("bbsr_pacote_remover");
    if (remover != null) {
      remover.setOnPreferenceClickListener(
          preference -> {
            PacoteSons.remover(requireContext());
            PacoteSons.recarregar(requireContext());
            atualizar();
            avisar("Pacote de sons removido. Todos os sons voltaram ao padrão.");
            return true;
          });
    }
    atualizar();
  }

  @Override
  public void onDestroy() {
    pararTocador();
    executor.shutdown();
    super.onDestroy();
  }

  private void aoEscolher(@Nullable Uri arvore) {
    if (arvore == null) {
      return;
    }
    Context app = requireContext().getApplicationContext();
    avisar("Importando sons…");
    executor.execute(
        () -> {
          String mensagem;
          try {
            int quantos = PacoteSons.importar(app, arvore);
            mensagem =
                quantos == 0
                    ? "Nenhum arquivo da pasta tem o nome de um som do leitor."
                    : (quantos == 1 ? "1 som importado." : quantos + " sons importados.");
          } catch (IOException | RuntimeException e) {
            mensagem = "Não foi possível ler a pasta.";
          }
          final String texto = mensagem;
          handler.post(
              () -> {
                PacoteSons.recarregar(app);
                if (isAdded()) {
                  atualizar();
                  avisar(texto);
                }
              });
        });
  }

  private void atualizar() {
    Preference estado = findPreference("bbsr_pacote_estado");
    if (estado != null) {
      int quantidade = PacoteSons.quantidade();
      estado.setSummary(
          quantidade == 0
              ? "Nenhum pacote. Todos os sons são os padrões."
              : (quantidade == 1
                  ? "1 som personalizado."
                  : quantidade + " sons personalizados."));
    }
    PreferenceCategory lista = findPreference("bbsr_categoria_lista_sons");
    if (lista == null) {
      return;
    }
    lista.removeAll();
    Context context = requireContext();
    for (Som som : SONS) {
      Preference item = new Preference(context);
      item.setKey("bbsr_som_" + som.nome);
      item.setPersistent(false);
      item.setTitle(som.descricao);
      File personalizado = PacoteSons.arquivoPara(som.nome);
      item.setSummary(
          (personalizado != null
                  ? "Personalizado, arquivo " + personalizado.getName()
                  : "Som padrão")
              + ". Nome do arquivo para substituir: "
              + som.nome
              + ". Toque para ouvir.");
      item.setOnPreferenceClickListener(
          preference -> {
            ouvir(som);
            return true;
          });
      lista.addPreference(item);
    }
  }

  private void ouvir(Som som) {
    pararTocador();
    Context context = requireContext();
    AudioAttributes atributos =
        new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
    try {
      File personalizado = PacoteSons.arquivoPara(som.nome);
      MediaPlayer novo;
      if (personalizado != null) {
        novo = new MediaPlayer();
        novo.setAudioAttributes(atributos);
        novo.setDataSource(personalizado.getAbsolutePath());
        novo.prepare();
      } else {
        novo = MediaPlayer.create(context, som.recurso, atributos, /* audioSessionId= */ 0);
        if (novo == null) {
          avisar("Não foi possível tocar o som.");
          return;
        }
      }
      novo.setOnCompletionListener(MediaPlayer::release);
      novo.start();
      tocador = novo;
    } catch (IOException | RuntimeException e) {
      avisar("Não foi possível tocar o som.");
    }
  }

  private void pararTocador() {
    if (tocador != null) {
      try {
        tocador.release();
      } catch (RuntimeException e) {
        // Já liberado.
      }
      tocador = null;
    }
  }

  private void avisar(String texto) {
    Context context = getContext();
    if (context != null) {
      Toast.makeText(context, texto, Toast.LENGTH_SHORT).show();
    }
  }
}
