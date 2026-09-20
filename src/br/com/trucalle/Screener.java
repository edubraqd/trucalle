package br.com.trucalle;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.provider.CallLog;
import android.telephony.PhoneNumberUtils;
import android.net.Uri;
import android.os.Build;
import android.content.pm.PackageManager;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.SmsManager;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.telecom.Connection;

import java.util.Calendar;

/**
 * Roda no Telecom antes de o telefone tocar. Decide em ordem:
 * auto-bloqueado (salvo se o usuario ligou de volta) > STIR reprovado > janela liberada > oculto
 * > sempre-permitir > agenda > usuario ligou para o numero > insistencia (score com decaimento)
 * > repeticao > modo (agenda | lista + prefixo quente).
 * Toda decisao vai para o historico (Hist).
 */
public final class Screener extends CallScreeningService {

    private static final String[] ID_ONLY = { PhoneLookup._ID };
    private static final String SEEN = "seen";

    @Override
    public void onScreenCall(Call.Details details) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && details.getCallDirection() != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, new CallResponse.Builder().build());
            return;
        }

        Cfg cfg = new Cfg(this);
        Uri handle = details.getHandle();
        String number = Cfg.normalize(handle == null ? null : handle.getSchemeSpecificPart());

        if (!number.isEmpty() && Hist.isAuto(this, number)) {
            if (cfg.callback && calledBack(number)) {
                Hist.removeAuto(this, number);
                Hist.pfxLegit(this, cfg.prefix(number));
                allow(details, number, "voce ligou");
            } else block(details, number, "auto-spam");
            return;
        }

        if (cfg.stir && !number.isEmpty() && spoofed(details)) {
            Hist.addAuto(this, number);
            Hist.pfxSpam(this, cfg.prefix(number));
            block(details, number, "spoof STIR");
            return;
        }

        if (cfg.inOpenWindow(Calendar.getInstance())) { allow(details, number, "janela"); return; }

        if (number.isEmpty()) {
            if (cfg.blockHidden) block(details, "", "oculto"); else allow(details, "", "oculto");
            return;
        }
        if (cfg.matches(cfg.allow, number)) { Hist.pfxLegit(this, cfg.prefix(number)); allow(details, number, "permitido"); return; }
        if (inContacts(number)) { Hist.pfxLegit(this, cfg.prefix(number)); allow(details, number, "agenda"); return; }
        if (cfg.callback && calledBack(number)) { Hist.pfxLegit(this, cfg.prefix(number)); allow(details, number, "voce ligou"); return; }

        int tries = Hist.bump(this, number);
        double score = Hist.bumpScore(this, number, cfg.halfH * 3600_000L);
        if (cfg.insistAuto && score >= cfg.insistN) {
            Hist.addAuto(this, number);
            Hist.pfxSpam(this, cfg.prefix(number));
            block(details, number, "insistente " + tries + "x");
            return;
        }
        if (cfg.repeat && seenRecently(number, cfg.repeatMin)) { allow(details, number, "repeticao"); return; }
        remember(number);

        if (!cfg.listMode) {
            block(details, number, "fora da agenda");
            if (cfg.sms && cfg.isBrMobile(number)
                    && Hist.pfxRate(this, cfg.prefix(number)) < Cfg.PFX_SMS_SKIP) smsOnce(number, cfg.smsText);
            return;
        }
        if (cfg.matches(cfg.block, number)) { block(details, number, "lista"); return; }
        if (cfg.pfxBlock && Hist.pfxRate(this, cfg.prefix(number)) >= Cfg.PFX_BLOCK) {
            block(details, number, "prefixo quente"); return;
        }
        if (cfg.blockIntl && cfg.isInternational(number)) { block(details, number, "internacional"); return; }
        allow(details, number, "modo lista");
    }

    private boolean spoofed(Call.Details d) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && d.getCallerNumberVerificationStatus() == Connection.VERIFICATION_STATUS_FAILED;
    }

    private void allow(Call.Details d, String number, String reason) {
        respondToCall(d, new CallResponse.Builder().build());
        Hist.append(this, number, false, reason);
    }

    private void block(Call.Details d, String number, String reason) {
        respondToCall(d, new CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build());
        Hist.append(this, number, true, reason);
    }

    /** SMS de aviso, uma vez por numero, fora do caminho critico. Sem SEND_SMS: nao faz nada. */
    private void smsOnce(final String number, final String text) {
        if (text.trim().isEmpty()) return;
        if (checkSelfPermission(android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return;
        if (!Hist.claimSms(this, number)) return;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    SmsManager.getDefault().sendTextMessage(number, null, text, null, null);
                    Hist.note(Screener.this, number, "sms enviado");
                } catch (RuntimeException e) {
                    Hist.note(Screener.this, number, "sms falhou: " + e.getClass().getSimpleName());
                }
            }
        }).start();
    }

    private boolean inContacts(String number) {
        Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, ID_ONLY, null, null, null);
            return c != null && c.getCount() > 0;
        } catch (SecurityException e) {
            return true; // sem READ_CONTACTS: nao bloqueia nada
        } finally {
            if (c != null) c.close();
        }
    }

    /**
     * Chamada de saida do usuario para o numero nos ultimos CALLBACK_DAYS (CallRank: quem voce liga e legitimo).
     * Sem READ_CALL_LOG devolve false: sem evidencia, nao libera.
     */
    private boolean calledBack(String number) {
        long since = System.currentTimeMillis() - Cfg.CALLBACK_DAYS * 86_400_000L;
        Cursor c = null;
        try {
            c = getContentResolver().query(CallLog.Calls.CONTENT_URI, new String[] { CallLog.Calls.NUMBER },
                    CallLog.Calls.TYPE + "=? AND " + CallLog.Calls.DATE + ">?",
                    new String[] { String.valueOf(CallLog.Calls.OUTGOING_TYPE), String.valueOf(since) },
                    CallLog.Calls.DATE + " DESC");
            if (c == null) return false;
            while (c.moveToNext()) {
                if (PhoneNumberUtils.compare(number, Cfg.normalize(c.getString(0)))) return true;
            }
            return false;
        } catch (SecurityException e) {
            return false;
        } finally {
            if (c != null) c.close();
        }
    }

    private boolean seenRecently(String number, int minutes) {
        long last = getSharedPreferences(SEEN, MODE_PRIVATE).getLong(number, 0);
        return last > 0 && System.currentTimeMillis() - last < minutes * 60_000L;
    }

    private void remember(String number) {
        SharedPreferences p = getSharedPreferences(SEEN, MODE_PRIVATE);
        SharedPreferences.Editor e = p.edit();
        if (p.getAll().size() > 200) e.clear();
        e.putLong(number, System.currentTimeMillis()).apply();
    }
}
