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

import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Perfis de gestos do Bro Blind Screen Reader.
 *
 * <p>O perfil global é o conjunto de gestos do TalkBack (preferências {@code pref_shortcut_*}).
 * Cada aplicativo pode ter um perfil próprio, que só guarda os gestos alterados: a preferência
 * {@code bbsr_perfil|<pacote>|<id do gesto>} com o valor da ação, ou {@link #USAR_GLOBAL}. A
 * lista de aplicativos com perfil fica em {@link #CHAVE_APPS}. O perfil troca sozinho com o
 * aplicativo em primeiro plano: eventos de mudança de janela marcam o pacote como desatualizado,
 * e ele é lido de novo só no próximo gesto (e só se existir algum perfil de aplicativo).
 */
public final class PerfisGestos {

  public static final String CHAVE_APPS = "bbsr_perfis_apps";
  public static final String USAR_GLOBAL = "BBSR_USAR_GLOBAL";
  private static final String PREFIXO = "bbsr_perfil|";

  private static final String FORMATO = "bro-blind-perfis-gestos";

  private static volatile @Nullable Supplier<String> fontePacote;
  private static volatile @Nullable String pacoteAtual;
  private static volatile boolean desatualizado = true;

  private PerfisGestos() {}

  /** Chamado pelo serviço ao ligar: diz como descobrir o aplicativo em primeiro plano. */
  public static void iniciar(Supplier<String> fonte) {
    fontePacote = fonte;
    desatualizado = true;
  }

  public static void parar() {
    fontePacote = null;
    pacoteAtual = null;
  }

  /** Marca o aplicativo em primeiro plano como desatualizado quando as janelas mudam. */
  public static void aoReceberEvento(AccessibilityEvent event) {
    int tipo = event.getEventType();
    if (tipo == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        || tipo == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
      desatualizado = true;
    }
  }

  /** Pacote do aplicativo em primeiro plano, ou null se não for possível saber. */
  public static @Nullable String pacoteEmPrimeiroPlano() {
    if (desatualizado) {
      Supplier<String> fonte = fontePacote;
      if (fonte != null) {
        try {
          pacoteAtual = fonte.get();
        } catch (RuntimeException e) {
          pacoteAtual = null;
        }
      }
      desatualizado = false;
    }
    return pacoteAtual;
  }

  public static String chave(String pacote, int idGesto) {
    return PREFIXO + pacote + "|" + idGesto;
  }

  /** Ação do perfil do aplicativo em primeiro plano para o gesto, ou null para usar o global. */
  public static @Nullable String acaoDoPerfil(SharedPreferences prefs, int idGesto) {
    Set<String> apps = prefs.getStringSet(CHAVE_APPS, Collections.emptySet());
    if (apps == null || apps.isEmpty()) {
      return null;
    }
    String pacote = pacoteEmPrimeiroPlano();
    if (pacote == null || !apps.contains(pacote)) {
      return null;
    }
    String acao = prefs.getString(chave(pacote, idGesto), null);
    return (acao == null || USAR_GLOBAL.equals(acao)) ? null : acao;
  }

  public static Set<String> apps(SharedPreferences prefs) {
    Set<String> apps = prefs.getStringSet(CHAVE_APPS, Collections.emptySet());
    return apps == null ? Collections.emptySet() : new HashSet<>(apps);
  }

  public static void adicionar(SharedPreferences prefs, String pacote) {
    Set<String> apps = apps(prefs);
    apps.add(pacote);
    prefs.edit().putStringSet(CHAVE_APPS, apps).apply();
  }

  public static void excluir(SharedPreferences prefs, String pacote) {
    Set<String> apps = apps(prefs);
    apps.remove(pacote);
    SharedPreferences.Editor editor = prefs.edit().putStringSet(CHAVE_APPS, apps);
    String prefixo = PREFIXO + pacote + "|";
    for (String chave : prefs.getAll().keySet()) {
      if (chave.startsWith(prefixo)) {
        editor.remove(chave);
      }
    }
    editor.apply();
  }

  /**
   * Exporta o perfil global (todas as preferências de gesto do TalkBack) e os perfis de
   * aplicativo para um objeto JSON.
   */
  public static JSONObject exportar(SharedPreferences prefs) throws JSONException {
    JSONObject raiz = new JSONObject();
    raiz.put("formato", FORMATO);
    raiz.put("versao", 1);
    JSONObject global = new JSONObject();
    JSONObject perfis = new JSONObject();
    Set<String> apps = apps(prefs);
    for (Map.Entry<String, ?> item : prefs.getAll().entrySet()) {
      String chave = item.getKey();
      Object valor = item.getValue();
      if (!(valor instanceof String)) {
        continue;
      }
      if (chave.startsWith("pref_shortcut_")) {
        global.put(chave, valor);
      } else if (chave.startsWith(PREFIXO)) {
        String resto = chave.substring(PREFIXO.length());
        int barra = resto.lastIndexOf('|');
        if (barra <= 0) {
          continue;
        }
        String pacote = resto.substring(0, barra);
        if (!apps.contains(pacote)) {
          continue;
        }
        JSONObject perfil = perfis.optJSONObject(pacote);
        if (perfil == null) {
          perfil = new JSONObject();
          perfis.put(pacote, perfil);
        }
        perfil.put(resto.substring(barra + 1), valor);
      }
    }
    for (String pacote : apps) {
      if (!perfis.has(pacote)) {
        perfis.put(pacote, new JSONObject());
      }
    }
    raiz.put("global", global);
    raiz.put("apps", perfis);
    return raiz;
  }

  /**
   * Importa perfis exportados por {@link #exportar}. Substitui os perfis de aplicativo atuais e
   * os gestos globais presentes no arquivo.
   *
   * @return quantos perfis de aplicativo foram importados
   */
  public static int importar(SharedPreferences prefs, JSONObject raiz) throws JSONException {
    if (!FORMATO.equals(raiz.optString("formato"))) {
      throw new JSONException("Arquivo não é de perfis do Bro Blind Screen Reader");
    }
    SharedPreferences.Editor editor = prefs.edit();
    for (String chave : prefs.getAll().keySet()) {
      if (chave.startsWith(PREFIXO)) {
        editor.remove(chave);
      }
    }
    JSONObject global = raiz.optJSONObject("global");
    if (global != null) {
      for (Iterator<String> it = global.keys(); it.hasNext(); ) {
        String chave = it.next();
        if (chave.startsWith("pref_shortcut_")) {
          editor.putString(chave, global.getString(chave));
        }
      }
    }
    Set<String> apps = new HashSet<>();
    JSONObject perfis = raiz.optJSONObject("apps");
    if (perfis != null) {
      for (Iterator<String> it = perfis.keys(); it.hasNext(); ) {
        String pacote = it.next();
        JSONObject perfil = perfis.getJSONObject(pacote);
        apps.add(pacote);
        for (Iterator<String> gestos = perfil.keys(); gestos.hasNext(); ) {
          String idGesto = gestos.next();
          editor.putString(PREFIXO + pacote + "|" + idGesto, perfil.getString(idGesto));
        }
      }
    }
    editor.putStringSet(CHAVE_APPS, apps);
    editor.apply();
    return apps.size();
  }
}
