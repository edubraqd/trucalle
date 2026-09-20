package br.com.trucalle;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Configuracao lida de SharedPreferences + regras puras de decisao. Sem UI. */
public final class Cfg {

    static final String FILE = "cfg";
    static final String K_LIST_MODE = "list_mode";      // false = agenda, true = lista negra
    static final String K_BLOCK_HIDDEN = "block_hidden";
    static final String K_BLOCK_INTL = "block_intl";
    static final String K_CC = "cc";
    static final String K_ALLOW = "allow";              // prefixos sempre permitidos
    static final String K_BLOCK = "block";              // prefixos bloqueados (modo lista)
    static final String K_REPEAT = "repeat";
    static final String K_REPEAT_MIN = "repeat_min";
    static final String K_WINDOWS = "windows";          // "HH:MM-HH:MM dias" por linha
    static final String K_INSIST_N = "insist_n";        // score de insistencia que vira auto-bloqueado
    static final String K_INSIST_AUTO = "insist_auto";
    static final String K_STIR = "stir";                // bloquear numero com STIR/SHAKEN reprovado
    static final String K_HALF_H = "half_h";            // meia-vida do score de insistencia, em horas
    static final String K_PFX_BLOCK = "pfx_block";      // modo lista: bloquear prefixo com P(spam) alta
    static final String K_SMS = "sms";                  // SMS de aviso na 1a ligacao bloqueada (so celular BR)
    static final String K_SMS_TEXT = "sms_text";
    static final String K_CALLBACK = "callback";        // liberar numero para o qual o usuario ligou (registro de chamadas)
    static final int CALLBACK_DAYS = 90;
    static final String BLOCK_DEFAULT = "0303";         // telemarketing (Anatel) no modo lista
    static final String SMS_DEFAULT =
            "Estou ocupado agora, por favor me chame pelo WhatsApp neste mesmo numero.";

    private static final Pattern WINDOW =
            Pattern.compile("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})\\s*([1-7]*)");

    final boolean listMode, blockHidden, blockIntl, repeat, insistAuto, stir, sms, callback;
    final String cc, smsText;
    final String[] allow, block;
    final int repeatMin, insistN, halfH;
    final boolean pfxBlock;
    final String windows;

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    Cfg(Context c) {
        SharedPreferences p = prefs(c);
        listMode = p.getBoolean(K_LIST_MODE, false);
        blockHidden = p.getBoolean(K_BLOCK_HIDDEN, true);
        blockIntl = p.getBoolean(K_BLOCK_INTL, false);
        cc = digits(p.getString(K_CC, "55"));
        allow = split(p.getString(K_ALLOW, ""));
        block = split(p.getString(K_BLOCK, BLOCK_DEFAULT));
        repeat = p.getBoolean(K_REPEAT, false);
        repeatMin = p.getInt(K_REPEAT_MIN, 5);
        windows = p.getString(K_WINDOWS, "");
        insistN = p.getInt(K_INSIST_N, 3);
        insistAuto = p.getBoolean(K_INSIST_AUTO, true);
        stir = p.getBoolean(K_STIR, true);
        halfH = p.getInt(K_HALF_H, 12);
        pfxBlock = p.getBoolean(K_PFX_BLOCK, true);
        sms = p.getBoolean(K_SMS, true);
        smsText = p.getString(K_SMS_TEXT, SMS_DEFAULT);
        callback = p.getBoolean(K_CALLBACK, true);
    }

    /** Mantem '+' inicial, descarta tudo que nao for digito. */
    static String normalize(String n) {
        if (n == null) return "";
        StringBuilder b = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char ch = n.charAt(i);
            if ((ch >= '0' && ch <= '9') || (ch == '+' && b.length() == 0)) b.append(ch);
        }
        return b.toString();
    }

    private static String digits(String s) {
        String d = normalize(s);
        return d.startsWith("+") ? d.substring(1) : d;
    }

    private static String[] split(String s) {
        String[] raw = s.split("[,;\\n]");
        int n = 0;
        for (String r : raw) if (!normalize(r).isEmpty()) n++;
        String[] out = new String[n];
        int i = 0;
        for (String r : raw) { String x = normalize(r); if (!x.isEmpty()) out[i++] = x; }
        return out;
    }

    /** Prefixo casa contra o numero cru e, se o numero vier com +CC/00CC, contra a parte local. */
    boolean matches(String[] prefixes, String number) {
        if (prefixes.length == 0) return false;
        String local = null;
        if (number.startsWith("+" + cc)) local = number.substring(cc.length() + 1);
        else if (number.startsWith("00" + cc)) local = number.substring(cc.length() + 2);
        for (String p : prefixes) {
            if (number.startsWith(p)) return true;
            if (local != null && !p.startsWith("+") && local.startsWith(p)) return true;
        }
        return false;
    }

    boolean isInternational(String number) {
        if (number.startsWith("+")) return !number.startsWith("+" + cc);
        if (number.startsWith("00")) return !number.startsWith("00" + cc);
        return false;
    }

    boolean isBrMobile(String number) { return isBrMobile(cc, number); }

    /**
     * Celular brasileiro: parte local com 11 digitos, DDD 11-99 e nono digito 9.
     * Fixo, 0300/0800/0303, internacional e curtos devolvem false (SMS nao chega e custa).
     */
    static boolean isBrMobile(String cc, String number) {
        String local = number;
        if (number.startsWith("+")) {
            if (!number.startsWith("+" + cc)) return false;
            local = number.substring(cc.length() + 1);
        } else if (number.startsWith("00")) {
            if (!number.startsWith("00" + cc)) return false;
            local = number.substring(cc.length() + 2);
        }
        if (local.length() != 11 || local.charAt(2) != '9') return false;
        if (local.charAt(0) == '0' || local.charAt(1) == '0') return false;
        return true;
    }

    // ---- matematica dos padroes ----
    // Score de insistencia com decaimento exponencial (Hyndman, Forecasting cap.7: pesos alpha(1-alpha)^k).
    // Guardado como (score, ultimo epoch); a cada chamada: score = score * 0.5^(dt/H) + 1.
    static double decayed(double score, long lastMs, long nowMs, long halfLifeMs) {
        long dt = nowMs - lastMs;
        if (dt <= 0 || halfLifeMs <= 0) return score;
        return score * Math.pow(0.5, (double) dt / halfLifeMs);
    }

    // Prior Beta por prefixo (AI-Powered Search cap.11): a = grade*peso, b = (1-grade)*peso.
    // Posterior media = (a + spam) / (a + b + spam + legit). Prior 0.3 com peso 10: 1 evidencia move pouco.
    static final double PFX_PRIOR = 0.3, PFX_WEIGHT = 10;
    static final double PFX_SMS_SKIP = 0.6;   // acima disso, nao gasta SMS
    static final double PFX_BLOCK = 0.8;      // acima disso, modo lista bloqueia o prefixo

    static double betaRate(int spam, int legit) {
        double a = PFX_PRIOR * PFX_WEIGHT, b = (1 - PFX_PRIOR) * PFX_WEIGHT;
        return (a + spam) / (a + b + spam + legit);
    }

    /** DDD + 4 digitos da parte local (6 chars); internacional: 6 primeiros chars com o '+'. */
    static String prefix(String cc, String number) {
        String local = number;
        if (number.startsWith("+" + cc)) local = number.substring(cc.length() + 1);
        else if (number.startsWith("00" + cc)) local = number.substring(cc.length() + 2);
        return local.length() <= 6 ? local : local.substring(0, 6);
    }

    String prefix(String number) { return prefix(cc, number); }

    /** true se agora cai em alguma janela liberada. Suporta virada de noite (22:00-06:00). */
    boolean inOpenWindow(Calendar now) {
        if (windows.isEmpty()) return false;
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        // Calendar: dom=1 ... sab=7  ->  nosso: seg=1 ... dom=7
        char today = (char) ('0' + ((now.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1));
        Matcher m = WINDOW.matcher(windows);
        while (m.find()) {
            String days = m.group(5);
            if (!days.isEmpty() && days.indexOf(today) < 0) continue;
            int a = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
            int b = Integer.parseInt(m.group(3)) * 60 + Integer.parseInt(m.group(4));
            boolean in = a <= b ? (minute >= a && minute < b) : (minute >= a || minute < b);
            if (in) return true;
        }
        return false;
    }
}
