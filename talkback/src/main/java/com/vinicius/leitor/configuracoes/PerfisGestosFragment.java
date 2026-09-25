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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.vinicius.leitor.gestos.PerfisGestos;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONObject;

/** Tela Gestos e perfis: perfil global, perfis por aplicativo, exportar e importar. */
public class PerfisGestosFragment extends BaseGestosFragment {

  private final ActivityResultLauncher<String> criarArquivo =
      registerForActivityResult(
          new ActivityResultContracts.CreateDocument("application/json"), this::exportar);

  private final ActivityResultLauncher<String[]> abrirArquivo =
      registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::importar);

  public PerfisGestosFragment() {
    super(R.xml.bbsr_perfis_gestos_preferences);
  }

  @Override
  public CharSequence getTitle() {
    return getText(R.string.title_pref_bbsr_gestos);
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    super.onCreatePreferences(savedInstanceState, rootKey);
    Preference adicionar = findPreference("bbsr_adicionar_perfil");
    if (adicionar != null) {
      adicionar.setOnPreferenceClickListener(
          preference -> {
            abrirTela(EscolherAppFragment.class, new Bundle());
            return true;
          });
    }
    Preference exportar = findPreference("bbsr_exportar_perfis");
    if (exportar != null) {
      exportar.setOnPreferenceClickListener(
          preference -> {
            try {
              criarArquivo.launch("perfis-bro-blind.json");
            } catch (RuntimeException e) {
              avisar("Não foi possível abrir o seletor de arquivos.");
            }
            return true;
          });
    }
    Preference importar = findPreference("bbsr_importar_perfis");
    if (importar != null) {
      importar.setOnPreferenceClickListener(
          preference -> {
            try {
              abrirArquivo.launch(new String[] {"application/json", "text/plain", "*/*"});
            } catch (RuntimeException e) {
              avisar("Não foi possível abrir o seletor de arquivos.");
            }
            return true;
          });
    }
  }

  @Override
  void aoVoltar() {
    super.aoVoltar();
    listarPerfis();
  }

  private void listarPerfis() {
    PreferenceCategory categoria = findPreference("bbsr_categoria_perfis_apps");
    Context context = getContext();
    if (categoria == null || context == null) {
      return;
    }
    List<Preference> remover = new ArrayList<>();
    for (int i = 0; i < categoria.getPreferenceCount(); i++) {
      Preference item = categoria.getPreference(i);
      if (!"bbsr_adicionar_perfil".equals(item.getKey())) {
        remover.add(item);
      }
    }
    for (Preference item : remover) {
      categoria.removePreference(item);
    }
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    List<String> apps = new ArrayList<>(PerfisGestos.apps(prefs));
    List<String> nomes = new ArrayList<>();
    for (String pacote : apps) {
      nomes.add(nomeDoApp(context, pacote));
    }
    for (int i = 0; i < apps.size(); i++) {
      String pacote = apps.get(i);
      Preference item = new Preference(context);
      item.setPersistent(false);
      item.setKey("bbsr_perfil_app_" + pacote);
      item.setTitle("Perfil de " + nomes.get(i));
      item.setSummary("Toque para editar os gestos deste aplicativo.");
      item.setOrder(i + 1);
      item.setOnPreferenceClickListener(
          preference -> {
            Bundle argumentos = new Bundle();
            argumentos.putString(PerfilAppFragment.ARG_PACOTE, pacote);
            abrirTela(PerfilAppFragment.class, argumentos);
            return true;
          });
      categoria.addPreference(item);
    }
  }

  static String nomeDoApp(Context context, String pacote) {
    try {
      PackageManager pm = context.getPackageManager();
      return pm.getApplicationLabel(pm.getApplicationInfo(pacote, 0)).toString();
    } catch (PackageManager.NameNotFoundException e) {
      return pacote;
    }
  }

  private void exportar(@Nullable Uri destino) {
    if (destino == null) {
      return;
    }
    Context context = requireContext();
    try (OutputStream saida = context.getContentResolver().openOutputStream(destino)) {
      if (saida == null) {
        throw new java.io.IOException("sem saída");
      }
      JSONObject json =
          PerfisGestos.exportar(SharedPreferencesUtils.getSharedPreferences(context));
      saida.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
      avisar("Perfis exportados.");
    } catch (Exception e) {
      avisar("Não foi possível exportar os perfis.");
    }
  }

  private void importar(@Nullable Uri origem) {
    if (origem == null) {
      return;
    }
    Context context = requireContext();
    try (InputStream entrada = context.getContentResolver().openInputStream(origem)) {
      if (entrada == null) {
        throw new java.io.IOException("sem entrada");
      }
      ByteArrayOutputStream dados = new ByteArrayOutputStream();
      byte[] buffer = new byte[16 * 1024];
      int lidos;
      while ((lidos = entrada.read(buffer)) != -1) {
        dados.write(buffer, 0, lidos);
      }
      JSONObject json = new JSONObject(new String(dados.toByteArray(), StandardCharsets.UTF_8));
      int quantos =
          PerfisGestos.importar(SharedPreferencesUtils.getSharedPreferences(context), json);
      avisar(
          "Perfis importados: perfil global e "
              + quantos
              + (quantos == 1 ? " perfil de aplicativo." : " perfis de aplicativo."));
      aoVoltar();
    } catch (Exception e) {
      avisar("Não foi possível importar. Escolha um arquivo exportado pelo Bro Blind.");
    }
  }
}
