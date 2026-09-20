package br.com.trucalle;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Historico em arquivo texto (uma linha por chamada) + contadores por numero.
 * Linha: epochMillis|numero|B, A ou S|motivo   (S = evento de SMS, nao conta como chamada)
 */
public final class Hist {

    private static final String LOG = "log.txt";
    private static final long MAX_BYTES = 200_000;
    private static final int KEEP_BYTES = 60_000;

    static final String COUNT = "count";   // numero -> tentativas fora da agenda
    static final String AUTO = "auto";     // numero -> epoch em que virou auto-bloqueado
    static final String SMS = "sms_sent";  // numero -> epoch do SMS de aviso (um por numero, nunca zera)

    private Hist() {}

    static void append(Context c, String number, boolean blocked, String reason) {
        write(c, number, blocked ? "B" : "A", reason);
    }

    static void note(Context c, String number, String reason) {
        write(c, number, "S", reason);
    }

    private static void write(Context c, String number, String flag, String reason) {
        File f = new File(c.getFilesDir(), LOG);
        String line = System.currentTimeMillis() + "|" + number + "|" + flag + "|" + reason + "\n";
        try (FileOutputStream out = new FileOutputStream(f, true)) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {}
        if (f.length() > MAX_BYTES) trim(f);
    }

    private static void trim(File f) {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            long start = Math.max(0, r.length() - KEEP_BYTES);
            r.seek(start);
            byte[] buf = new byte[(int) (r.length() - start)];
            r.readFully(buf);
            int nl = 0;
            while (nl < buf.length && buf[nl] != '\n') nl++;
            try (FileOutputStream out = new FileOutputStream(f, false)) {
                out.write(buf, nl + 1, buf.length - nl - 1);
            }
        } catch (IOException ignored) {}
    }

    static String read(Context c) {
        File f = new File(c.getFilesDir(), LOG);
        if (!f.exists()) return "";
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            byte[] buf = new byte[(int) r.length()];
            r.readFully(buf);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    static void clear(Context c) {
        new File(c.getFilesDir(), LOG).delete();
    }

    /** Incrementa e devolve o total de tentativas do numero. */
    static int bump(Context c, String number) {
        SharedPreferences p = c.getSharedPreferences(COUNT, Context.MODE_PRIVATE);
        int n = p.getInt(number, 0) + 1;
        SharedPreferences.Editor e = p.edit();
        if (p.getAll().size() > 500) e.clear();
        e.putInt(number, n).apply();
        return n;
    }

    static boolean isAuto(Context c, String number) {
        return c.getSharedPreferences(AUTO, Context.MODE_PRIVATE).contains(number);
    }

    static void addAuto(Context c, String number) {
        c.getSharedPreferences(AUTO, Context.MODE_PRIVATE).edit()
                .putLong(number, System.currentTimeMillis()).apply();
    }

    static void removeAuto(Context c, String number) {
        c.getSharedPreferences(AUTO, Context.MODE_PRIVATE).edit().remove(number).apply();
        c.getSharedPreferences(COUNT, Context.MODE_PRIVATE).edit().remove(number).apply();
        c.getSharedPreferences(SCORE, Context.MODE_PRIVATE).edit().remove(number).apply();
    }

    static final String SCORE = "score";   // numero -> "score,epoch" (insistencia com decaimento)
    static final String PFX = "pfx";       // prefixo -> "spam,legit" (evidencias para o prior Beta)

    /** Aplica o decaimento, soma 1 e devolve o score de insistencia do numero. */
    static double bumpScore(Context c, String number, long halfLifeMs) {
        SharedPreferences p = c.getSharedPreferences(SCORE, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        double s = 0; long last = now;
        String v = p.getString(number, null);
        if (v != null) {
            int i = v.indexOf(',');
            try { s = Double.parseDouble(v.substring(0, i)); last = Long.parseLong(v.substring(i + 1)); }
            catch (RuntimeException ignored) { s = 0; last = now; }
        }
        s = Cfg.decayed(s, last, now, halfLifeMs) + 1;
        SharedPreferences.Editor e = p.edit();
        if (p.getAll().size() > 500) e.clear();
        e.putString(number, s + "," + now).apply();
        return s;
    }

    static void pfxSpam(Context c, String prefix) { pfxAdd(c, prefix, 1, 0); }
    static void pfxLegit(Context c, String prefix) { pfxAdd(c, prefix, 0, 1); }

    private static void pfxAdd(Context c, String prefix, int spam, int legit) {
        if (prefix.isEmpty()) return;
        SharedPreferences p = c.getSharedPreferences(PFX, Context.MODE_PRIVATE);
        int[] n = pfxCounts(p, prefix);
        p.edit().putString(prefix, (n[0] + spam) + "," + (n[1] + legit)).apply();
    }

    private static int[] pfxCounts(SharedPreferences p, String prefix) {
        String v = p.getString(prefix, null);
        if (v == null) return new int[] { 0, 0 };
        int i = v.indexOf(',');
        try { return new int[] { Integer.parseInt(v.substring(0, i)), Integer.parseInt(v.substring(i + 1)) }; }
        catch (RuntimeException e) { return new int[] { 0, 0 }; }
    }

    /** P(spam | prefixo), media posterior Beta. Sem evidencia = prior (0.3). */
    static double pfxRate(Context c, String prefix) {
        int[] n = pfxCounts(c.getSharedPreferences(PFX, Context.MODE_PRIVATE), prefix);
        return Cfg.betaRate(n[0], n[1]);
    }

    static Map<String, ?> pfxAll(Context c) {
        return c.getSharedPreferences(PFX, Context.MODE_PRIVATE).getAll();
    }

    /** true se ainda nao mandou SMS para o numero; marca como mandado. */
    static boolean claimSms(Context c, String number) {
        SharedPreferences p = c.getSharedPreferences(SMS, Context.MODE_PRIVATE);
        if (p.contains(number)) return false;
        SharedPreferences.Editor e = p.edit();
        if (p.getAll().size() > 1000) e.clear();
        e.putLong(number, System.currentTimeMillis()).apply();
        return true;
    }

    static Map<String, ?> autoAll(Context c) {
        return c.getSharedPreferences(AUTO, Context.MODE_PRIVATE).getAll();
    }

    static Map<String, ?> countAll(Context c) {
        return c.getSharedPreferences(COUNT, Context.MODE_PRIVATE).getAll();
    }

    static void clearAuto(Context c) {
        c.getSharedPreferences(AUTO, Context.MODE_PRIVATE).edit().clear().apply();
        c.getSharedPreferences(COUNT, Context.MODE_PRIVATE).edit().clear().apply();
        c.getSharedPreferences(SCORE, Context.MODE_PRIVATE).edit().clear().apply();
        c.getSharedPreferences(PFX, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
