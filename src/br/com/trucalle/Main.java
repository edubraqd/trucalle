package br.com.trucalle;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unica tela, sem XML. Ordem: valor antes do pedido (uma frase + Ativar), feedback (contador do dia),
 * historico com reversao em um toque, e o resto atras de "Avancado".
 * Visual: cards sobre o tema DayNight do sistema; cores so para a decisao (vermelho bloqueou, verde liberou, azul SMS).
 */
public final class Main extends Activity {

    private static final int REQ_PERM = 1;
    private static final int REQ_ROLE = 2;
    private static final int REQ_OPT = 3;   // permissoes opcionais: SMS, registro de chamadas

    private static final int RED = 0xFFC62828, GREEN = 0xFF2E7D32, BLUE = 0xFF1565C0, AMBER = 0xFFEF6C00;

    private LinearLayout root, advanced, logBox;
    private TextView status, pill, blockedNum, allowedNum, autoView, advToggle;
    private Button activate;
    private CompoundButton listMode, blockHidden, blockIntl, repeat, insistAuto, stir, sms, callback, pfxBlock;
    private EditText cc, allow, block, repeatMin, windows, insistN, smsText, halfH;

    private int accent, textPrimary, textSecondary, cardBg, divider;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        accent = attr(android.R.attr.colorAccent);
        textPrimary = attr(android.R.attr.textColorPrimary);
        textSecondary = attr(android.R.attr.textColorSecondary);
        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        cardBg = night ? attr(android.R.attr.colorBackgroundFloating) : 0xFFFFFFFF;  // claro: card branco sobre fundo cinza
        divider = (textPrimary & 0x00FFFFFF) | 0x1F000000;

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(24), dp(16), dp(32));
        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);

        // ---- cabecalho ----
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(4), 0, dp(4), dp(12));
        root.addView(head);
        TextView title = new TextView(this);
        title.setText("Trucalle");
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(textPrimary);
        head.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        pill = new TextView(this);
        pill.setTextSize(12);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setTextColor(0xFFFFFFFF);
        pill.setPadding(dp(10), dp(4), dp(10), dp(4));
        head.addView(pill);

        // ---- estado + Ativar ----
        LinearLayout state1 = card(root);
        status = label(state1, "", 15, textPrimary);
        status.setPadding(0, 0, 0, 0);
        activate = button(state1, "Ativar", true, new Runnable() { public void run() { step(true); } });

        // ---- contador do dia ----
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(stats);
        blockedNum = tile(stats, "bloqueadas hoje", RED);
        allowedNum = tile(stats, "liberadas hoje", GREEN);

        // ---- historico ----
        LinearLayout hist = card(root);
        label(hist, "Historico", 17, textPrimary).setTypeface(Typeface.DEFAULT_BOLD);
        label(hist, "Toque numa linha para permitir ou bloquear o numero.", 13, textSecondary);
        logBox = new LinearLayout(this);
        logBox.setOrientation(LinearLayout.VERTICAL);
        hist.addView(logBox);

        // ---- avancado ----
        advToggle = new TextView(this);
        advToggle.setTextSize(15);
        advToggle.setTypeface(Typeface.DEFAULT_BOLD);
        advToggle.setTextColor(accent);
        advToggle.setPadding(dp(8), dp(12), dp(8), dp(12));
        advToggle.setBackground(getDrawable(attrRes(android.R.attr.selectableItemBackground)));
        root.addView(advToggle);
        advanced = new LinearLayout(this);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(View.GONE);
        root.addView(advanced);
        advToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setAdvanced(advanced.getVisibility() != View.VISIBLE); }
        });
        setAdvanced(false);

        LinearLayout c1 = card(advanced);
        section(c1, "Modo");
        listMode = toggle(c1, "Modo lista negra", "Libera tudo e bloqueia so os prefixos abaixo. Desligado: bloqueia quem nao esta na agenda.");
        block = field(c1, "Prefixos bloqueados (modo lista)", "0303, 0800", false);
        blockIntl = toggle(c1, "Bloquear internacional", "So no modo lista.");
        pfxBlock = toggle(c1, "Bloquear prefixo quente", "Modo lista: prefixo (DDD+4) com P(spam) >= 0.8, aprendida dos bloqueios.");
        blockHidden = toggle(c1, "Bloquear numero oculto", "Sem identificacao de chamada.");
        cc = field(c1, "Codigo do pais (sem +)", "55", false);
        allow = field(c1, "Sempre permitir", "prefixos ou numeros, separados por virgula", false);
        callback = toggle(c1, "Liberar quem voce ligou", "Numero para o qual voce ligou nos ultimos 90 dias passa. Le o registro de chamadas.");

        LinearLayout c2 = card(advanced);
        section(c2, "Horarios liberados");
        label(c2, "Nesses horarios tudo passa. Um por linha: HH:MM-HH:MM dias (1 seg ... 7 dom; vazio = todos).", 13, textSecondary);
        windows = field(c2, null, "22:00-06:00\n09:00-12:00 6", true);
        repeat = toggle(c2, "Deixar passar se ligar de novo", "Mesmo numero ligando outra vez em N minutos passa.");
        repeatMin = field(c2, "N minutos", null, false);
        repeatMin.setInputType(InputType.TYPE_CLASS_NUMBER);

        LinearLayout c3 = card(advanced);
        section(c3, "SMS de aviso");
        sms = toggle(c3, "Enviar SMS na 1a ligacao bloqueada", "So para celular BR. A operadora cobra 1 SMS por numero, uma unica vez.");
        smsText = field(c3, "Texto do SMS", null, true);

        LinearLayout c4 = card(advanced);
        section(c4, "Insistentes e spam");
        stir = toggle(c4, "Bloquear STIR/SHAKEN reprovado", "Numero que a rede marcou como falsificado vira spam (Android 11+).");
        insistAuto = toggle(c4, "Auto-bloquear insistente", "Fora da agenda com score de tentativas >= N.");
        insistN = field(c4, "N (score)", null, false);
        insistN.setInputType(InputType.TYPE_CLASS_NUMBER);
        label(c4, "Cada tentativa vale 1 e perde metade do peso a cada H horas: 3 ligacoes num dia contam 3, "
                + "3 ligacoes em 3 meses contam 1.", 13, textSecondary);
        halfH = field(c4, "H horas (meia-vida)", null, false);
        halfH.setInputType(InputType.TYPE_CLASS_NUMBER);

        button(advanced, "Salvar", true, new Runnable() { public void run() { save(); } });

        LinearLayout c5 = card(advanced);
        section(c5, "Auto-bloqueados");
        label(c5, "Spam, insistentes e prefixos aprendidos.", 13, textSecondary);
        autoView = label(c5, "", 13, textPrimary);
        autoView.setTypeface(Typeface.MONOSPACE);
        button(c5, "Denunciar na Anatel (WhatsApp)", false, new Runnable() { public void run() { anatel(); } });
        button(c5, "Compartilhar lista de insistentes", false, new Runnable() { public void run() { report(); } });
        button(c5, "Limpar auto-bloqueados e contadores", false, new Runnable() { public void run() {
            Hist.clearAuto(Main.this); refresh(); } });
        button(c5, "Limpar historico", false, new Runnable() { public void run() { Hist.clear(Main.this); refresh(); } });

        load();
    }

    private void setAdvanced(boolean on) {
        advanced.setVisibility(on ? View.VISIBLE : View.GONE);
        advToggle.setText(on ? "Configuracoes avancadas  ▴" : "Configuracoes avancadas  ▾");
    }

    @Override
    protected void onResume() {
        super.onResume();
        step(false);
        refresh();
    }

    private boolean hasContacts() { return granted(Manifest.permission.READ_CONTACTS); }

    private boolean granted(String perm) {
        return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true;
        return getSystemService(RoleManager.class).isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
    }

    private void setPill(String text, int color) {
        pill.setText(text);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(12));
        pill.setBackground(bg);
    }

    /** ask=false so mostra o estado; ask=true dispara o pedido que falta. Nunca pede sem o toque em Ativar. */
    private void step(boolean ask) {
        if (!hasContacts()) {
            setPill("INATIVO", AMBER);
            status.setText("Trucalle rejeita, antes de tocar, ligacoes de quem nao esta na sua agenda.\n\n"
                         + "Para isso precisa ler seus contatos (fica no aparelho) e ser o filtro de chamadas do Android.");
            activate.setVisibility(View.VISIBLE);
            if (ask) requestPermissions(new String[] { Manifest.permission.READ_CONTACTS,
                                                       Manifest.permission.SEND_SMS,
                                                       Manifest.permission.READ_CALL_LOG }, REQ_PERM);
            return;
        }
        if (!hasRole()) {
            setPill("INATIVO", AMBER);
            status.setText("Falta o Android entregar as chamadas ao Trucalle. Toque em Ativar e escolha Trucalle.");
            activate.setVisibility(View.VISIBLE);
            if (ask) startActivityForResult(getSystemService(RoleManager.class)
                    .createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), REQ_ROLE);
            return;
        }
        SharedPreferences p = Cfg.prefs(this);
        List<String> missing = new ArrayList<String>();
        if (p.getBoolean(Cfg.K_SMS, true) && !granted(Manifest.permission.SEND_SMS)) missing.add(Manifest.permission.SEND_SMS);
        if (p.getBoolean(Cfg.K_CALLBACK, true) && !granted(Manifest.permission.READ_CALL_LOG)) missing.add(Manifest.permission.READ_CALL_LOG);
        if (!missing.isEmpty()) {
            String what = missing.contains(Manifest.permission.SEND_SMS)
                    ? (missing.size() > 1 ? "SMS e registro de chamadas" : "SMS") : "registro de chamadas";
            setPill("ATIVO", GREEN);
            status.setText("Filtrando, mas sem permissao de " + what + ": o aviso por SMS e a liberacao de quem voce ligou "
                         + "nao funcionam.\n\nToque em Ativar, ou conceda em Configuracoes > Apps > Trucalle > Permissoes, "
                         + "ou desligue a opcao em Avancado.");
            activate.setVisibility(View.VISIBLE);
            if (ask) requestPermissions(missing.toArray(new String[0]), REQ_OPT);
            return;
        }
        setPill("ATIVO", GREEN);
        status.setText(Cfg.prefs(this).getBoolean(Cfg.K_LIST_MODE, false)
                ? "Modo lista negra: tudo passa, menos os prefixos bloqueados e o que o app aprendeu."
                : "Quem nao esta na sua agenda e rejeitado antes de tocar.");
        activate.setVisibility(View.GONE);
    }

    private void load() {
        SharedPreferences p = Cfg.prefs(this);
        listMode.setChecked(p.getBoolean(Cfg.K_LIST_MODE, false));
        blockHidden.setChecked(p.getBoolean(Cfg.K_BLOCK_HIDDEN, true));
        blockIntl.setChecked(p.getBoolean(Cfg.K_BLOCK_INTL, false));
        cc.setText(p.getString(Cfg.K_CC, "55"));
        allow.setText(p.getString(Cfg.K_ALLOW, ""));
        block.setText(p.getString(Cfg.K_BLOCK, Cfg.BLOCK_DEFAULT));
        repeat.setChecked(p.getBoolean(Cfg.K_REPEAT, false));
        repeatMin.setText(String.valueOf(p.getInt(Cfg.K_REPEAT_MIN, 5)));
        windows.setText(p.getString(Cfg.K_WINDOWS, ""));
        stir.setChecked(p.getBoolean(Cfg.K_STIR, true));
        insistAuto.setChecked(p.getBoolean(Cfg.K_INSIST_AUTO, true));
        insistN.setText(String.valueOf(p.getInt(Cfg.K_INSIST_N, 3)));
        halfH.setText(String.valueOf(p.getInt(Cfg.K_HALF_H, 12)));
        pfxBlock.setChecked(p.getBoolean(Cfg.K_PFX_BLOCK, true));
        sms.setChecked(p.getBoolean(Cfg.K_SMS, true));
        smsText.setText(p.getString(Cfg.K_SMS_TEXT, Cfg.SMS_DEFAULT));
        callback.setChecked(p.getBoolean(Cfg.K_CALLBACK, true));
    }

    private void save() {
        int min = intOr(repeatMin, 5);
        int nIns = Math.max(2, intOr(insistN, 3));
        int h = Math.max(1, intOr(halfH, 12));
        Cfg.prefs(this).edit()
            .putBoolean(Cfg.K_LIST_MODE, listMode.isChecked())
            .putBoolean(Cfg.K_BLOCK_HIDDEN, blockHidden.isChecked())
            .putBoolean(Cfg.K_BLOCK_INTL, blockIntl.isChecked())
            .putString(Cfg.K_CC, cc.getText().toString())
            .putString(Cfg.K_ALLOW, allow.getText().toString())
            .putString(Cfg.K_BLOCK, block.getText().toString())
            .putBoolean(Cfg.K_REPEAT, repeat.isChecked())
            .putInt(Cfg.K_REPEAT_MIN, min)
            .putString(Cfg.K_WINDOWS, windows.getText().toString())
            .putBoolean(Cfg.K_STIR, stir.isChecked())
            .putBoolean(Cfg.K_INSIST_AUTO, insistAuto.isChecked())
            .putInt(Cfg.K_INSIST_N, nIns)
            .putInt(Cfg.K_HALF_H, h)
            .putBoolean(Cfg.K_PFX_BLOCK, pfxBlock.isChecked())
            .putBoolean(Cfg.K_SMS, sms.isChecked())
            .putString(Cfg.K_SMS_TEXT, smsText.getText().toString())
            .putBoolean(Cfg.K_CALLBACK, callback.isChecked())
            .apply();
        insistN.setText(String.valueOf(nIns));
        halfH.setText(String.valueOf(h));
        step(false);
        toast("Salvo");
    }

    private static int intOr(EditText e, int dflt) {
        try { return Integer.parseInt(e.getText().toString().trim()); }
        catch (NumberFormatException x) { return dflt; }
    }

    private void refresh() {
        Map<String, ?> counts = Hist.countAll(this);
        StringBuilder a = new StringBuilder();
        for (Map.Entry<String, ?> e : Hist.autoAll(this).entrySet()) {
            Object n = counts.get(e.getKey());
            a.append(e.getKey()).append("  ").append(n == null ? "" : n + "x").append('\n');
        }
        for (Map.Entry<String, ?> e : Hist.pfxAll(this).entrySet()) {
            double r = Hist.pfxRate(this, e.getKey());
            if (r >= Cfg.PFX_SMS_SKIP) a.append("prefixo ").append(e.getKey()).append("*  P(spam)=")
                    .append(String.format(Locale.US, "%.2f", r)).append('\n');
        }
        autoView.setText(a.length() == 0 ? "(nenhum)" : a.toString().trim());

        Calendar day = Calendar.getInstance();
        day.set(Calendar.HOUR_OF_DAY, 0); day.set(Calendar.MINUTE, 0); day.set(Calendar.SECOND, 0);
        long dayStart = day.getTimeInMillis();
        int blockedToday = 0, allowedToday = 0;

        SimpleDateFormat today = new SimpleDateFormat("HH:mm", Locale.getDefault());
        SimpleDateFormat older = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        String[] lines = Hist.read(this).split("\n");
        logBox.removeAllViews();
        int shown = 0;
        for (int i = lines.length - 1; i >= 0; i--) {
            String[] f = lines[i].split("\\|", 4);
            if (f.length < 4) continue;
            long ts;
            try { ts = Long.parseLong(f[0]); } catch (NumberFormatException e) { ts = 0; }
            boolean blocked = "B".equals(f[2]);
            boolean smsEvent = "S".equals(f[2]);
            if (ts >= dayStart && !smsEvent) { if (blocked) blockedToday++; else allowedToday++; }
            if (shown >= 100) continue;
            final String number = f[1];
            String when = (ts >= dayStart ? today : older).format(new Date(ts));
            View row = logRow(number.isEmpty() ? "Numero oculto" : number, f[3], when,
                    smsEvent ? BLUE : blocked ? RED : GREEN, smsEvent ? "SMS" : blocked ? "Bloqueou" : "Liberou");
            if (!number.isEmpty()) row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { decide(number); }
            });
            shown++;
        }
        if (shown == 0) label(logBox, "Nenhuma chamada filtrada ainda.", 14, textSecondary);
        blockedNum.setText(String.valueOf(blockedToday));
        allowedNum.setText(String.valueOf(allowedToday));
    }

    /** Reversao em um toque: o bloqueio foi decisao do app, o usuario precisa poder desfazer sem procurar. */
    private void decide(final String number) {
        final boolean auto = Hist.isAuto(this, number);
        final String[] opts = auto
                ? new String[] { "Sempre permitir", "Tirar da lista automatica" }
                : new String[] { "Sempre permitir", "Bloquear sempre" };
        new AlertDialog.Builder(this)
            .setTitle(number)
            .setItems(opts, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface d, int which) {
                    if (which == 0) allowForever(number);
                    else if (auto) { Hist.removeAuto(Main.this, number); toast("Removido da lista automatica"); }
                    else { Hist.addAuto(Main.this, number); Hist.pfxSpam(Main.this, new Cfg(Main.this).prefix(number)); toast("Bloqueado sempre"); }
                    load(); refresh();
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }

    private void allowForever(String number) {
        SharedPreferences p = Cfg.prefs(this);
        String cur = p.getString(Cfg.K_ALLOW, "").trim();
        if (!new Cfg(this).matches(new Cfg(this).allow, number)) {
            p.edit().putString(Cfg.K_ALLOW, cur.isEmpty() ? number : cur + ", " + number).apply();
        }
        Hist.removeAuto(this, number);
        Hist.pfxLegit(this, new Cfg(this).prefix(number));
        toast("Sempre permitido");
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    /** Lista "numero - N tentativas" dos insistentes/auto-bloqueados; null se vazia. */
    private String insistentes() {
        Cfg cfg = new Cfg(this);
        Map<String, ?> auto = Hist.autoAll(this);
        Map<String, ?> counts = Hist.countAll(this);
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, ?> e : counts.entrySet()) {
            int c = e.getValue() instanceof Integer ? (Integer) e.getValue() : 0;
            if (c >= cfg.insistN || auto.containsKey(e.getKey())) {
                b.append(e.getKey()).append(" - ").append(c).append(" tentativas\n");
            }
        }
        for (String n : auto.keySet()) {
            if (!counts.containsKey(n)) b.append(n).append(" - spam (STIR/rede)\n");
        }
        if (b.length() == 0) { toast("Nenhum insistente ainda"); return null; }
        return b.toString();
    }

    private void report() {
        String list = insistentes();
        if (list == null) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Relatorio de spam - Trucalle");
        i.putExtra(Intent.EXTRA_TEXT, "Numeros insistentes / spam bloqueados pelo Trucalle:\n" + list);
        startActivity(Intent.createChooser(i, "Reportar para"));
    }

    /**
     * Canal oficial da Anatel no WhatsApp. Nao ha API: abre a conversa com a denuncia pronta.
     * Exige cadastro previo no Anatel Consumidor (site, app ou 1331).
     */
    private void anatel() {
        String list = insistentes();
        if (list == null) return;
        String msg = "Denuncia de chamadas abusivas / telemarketing insistente.\n"
                   + "Numeros e quantidade de tentativas registradas no meu aparelho:\n" + list
                   + "Solicito registro de denuncia contra os responsaveis.";
        Intent i = new Intent(Intent.ACTION_VIEW,
                android.net.Uri.parse("https://api.whatsapp.com/send/?phone=558006101331&text=" + android.net.Uri.encode(msg)));
        try { startActivity(i); }
        catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "Sem WhatsApp/navegador para abrir o canal da Anatel", Toast.LENGTH_LONG).show();
        }
    }

    // ---- blocos de UI ----

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private int attrRes(int id) {
        TypedValue v = new TypedValue();
        getTheme().resolveAttribute(id, v, true);
        return v.resourceId;
    }

    private int attr(int id) {
        TypedValue v = new TypedValue();
        getTheme().resolveAttribute(id, v, true);
        return v.resourceId != 0 ? getColor(v.resourceId) : v.data;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardBg);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), divider);
        c.setBackground(bg);
        c.setElevation(dp(1));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        parent.addView(c, lp);
        return c;
    }

    private TextView tile(LinearLayout parent, String caption, int color) {
        LinearLayout t = card(parent);
        t.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) t.getLayoutParams();
        lp.width = 0; lp.weight = 1;
        lp.rightMargin = parent.getChildCount() == 1 ? dp(6) : 0;
        lp.leftMargin = parent.getChildCount() == 2 ? dp(6) : 0;
        TextView n = label(t, "0", 32, color);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        n.setPadding(0, 0, 0, 0);
        TextView c = label(t, caption, 12, textSecondary);
        c.setPadding(0, 0, 0, 0);
        return n;
    }

    private TextView label(LinearLayout parent, String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setPadding(0, dp(4), 0, dp(4));
        parent.addView(t);
        return t;
    }

    private void section(LinearLayout parent, String s) {
        TextView t = label(parent, s.toUpperCase(Locale.ROOT), 12, accent);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.08f);
        t.setPadding(0, 0, 0, dp(6));
    }

    private CompoundButton toggle(LinearLayout parent, String title, String help) {
        Switch s = new Switch(this);
        s.setText(title);
        s.setTextSize(15);
        s.setTextColor(textPrimary);
        s.setPadding(0, dp(10), 0, 0);
        parent.addView(s);
        TextView h = label(parent, help, 12, textSecondary);
        h.setPadding(0, 0, dp(56), dp(6));
        return s;
    }

    private EditText field(LinearLayout parent, String title, String hint, boolean multiline) {
        if (title != null) {
            TextView t = label(parent, title, 12, textSecondary);
            t.setPadding(0, dp(8), 0, 0);
        }
        EditText e = new EditText(this);
        e.setTextSize(15);
        if (hint != null) e.setHint(hint);
        if (multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            e.setMinLines(2);
        } else {
            e.setSingleLine(true);
        }
        parent.addView(e);
        return e;
    }

    private Button button(LinearLayout parent, String label, boolean primary, final Runnable r) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        if (primary) {
            b.setBackgroundTintList(ColorStateList.valueOf(accent));
            b.setTextColor(0xFFFFFFFF);
        }
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { r.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        parent.addView(b, lp);
        return b;
    }

    /** Linha do historico: barra de cor da decisao | numero + motivo | hora. */
    private View logRow(String number, String reason, String when, int color, String verb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(8), dp(4), dp(8));
        row.setBackground(getDrawable(attrRes(android.R.attr.selectableItemBackground)));

        View bar = new View(this);
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(2));
        bar.setBackground(d);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(4), dp(36));
        blp.rightMargin = dp(12);
        row.addView(bar, blp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView n = label(col, number, 15, textPrimary);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        n.setPadding(0, 0, 0, 0);
        TextView r = label(col, verb + " · " + reason, 12, textSecondary);
        r.setPadding(0, 0, 0, 0);

        TextView t = label(row, when, 12, textSecondary);
        t.setPadding(dp(8), 0, 0, 0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        logBox.addView(row, lp);
        View line = new View(this);
        line.setBackgroundColor(divider);
        logBox.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        return row;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_OPT) { step(false); return; }
        if (code == REQ_PERM && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            step(true); // segue direto para o papel de filtro
        } else if (code == REQ_PERM) {
            status.setText("Sem acesso aos contatos o app nao diferencia quem esta na agenda.\n"
                         + "Conceda em Configuracoes > Apps > Trucalle > Permissoes, ou toque em Ativar de novo.");
        }
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        if (code == REQ_ROLE && result != RESULT_OK) {
            status.setText("Papel de filtro recusado. Sem ele o Android nao entrega as chamadas ao app.\n"
                         + "Toque em Ativar para tentar outra vez.");
        }
    }
}
