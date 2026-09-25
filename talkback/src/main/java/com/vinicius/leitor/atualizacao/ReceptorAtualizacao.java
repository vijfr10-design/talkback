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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

/** Recebe o toque na notificação de atualização e começa o download do APK. */
public class ReceptorAtualizacao extends BroadcastReceiver {

  static final String ACAO_BAIXAR = "com.vinicius.leitor.atualizacao.BAIXAR";
  static final String EXTRA_URL = "url_apk";

  @Override
  public void onReceive(Context context, Intent intent) {
    if (!ACAO_BAIXAR.equals(intent.getAction())) {
      return;
    }
    String url = intent.getStringExtra(EXTRA_URL);
    if (!TextUtils.isEmpty(url)) {
      VerificadorAtualizacao.baixarEInstalar(context, url);
    }
  }
}
