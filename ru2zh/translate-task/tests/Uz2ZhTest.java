package com.stand.bridge;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Offline unit tests for {@link Uz2Ru} — Uzbek phrase → (Russian keyword phrase) → Ru2Zh → Chinese
 * command. Same file format as Ru2ZhTest: tests_uz.tsv "phrase<TAB>expected_ZH[<TAB>UNSAFE|DIR=n]",
 * chatter_uz.txt = lines that must NOT become a command. A failure prints the intermediate Russian.
 */
public final class Uz2ZhTest {
    private static void setSwitches(boolean on) {
        Ru2Zh.FM_RADIO_OFFLINE = on; Ru2Zh.WIPER_ONOFF_OFFLINE = on; Ru2Zh.DRIVE_MODE_OFFLINE = on; Ru2Zh.SIDE_LIGHTS_OFFLINE = on;
    }

    public static void main(String[] args) throws Exception {
        int pass = 0, fail = 0;
        java.io.PrintStream out = new java.io.PrintStream(System.out, true, "UTF-8");
        for (String line : Files.readAllLines(Paths.get(args[0]))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] p = line.split("\\t");
            if (p.length < 2) continue;
            String uz = p[0].trim();
            String exp = p[1].trim().equals("NULL") ? null : p[1].trim();
            String flag = p.length > 2 ? p[2].trim() : "";
            boolean unsafeCase = flag.equals("UNSAFE");
            int dir = flag.startsWith("DIR=") ? Integer.parseInt(flag.substring(4)) : 1;
            if (flag.equals("FIELD_OFF")) {
                Ru2Zh.ALLOW_UNSAFE = true;
                setSwitches(false); String shipped = Uz2Ru.uz2zh(uz, dir);
                setSwitches(true);  String known = Uz2Ru.uz2zh(uz, dir);
                setSwitches(false);
                if (shipped == null && Objects.equals(known, exp)) pass++;
                else { fail++; out.println("FAIL[field_off] '" + uz + "' -> shipped=" + shipped + " on=" + known + "  (exp " + exp + ")"); }
                continue;
            }
            if (exp != null && exp.contains(" + ")) {
                Ru2Zh.ALLOW_UNSAFE = true;
                String[] all = Ru2Zh.ru2zhAll(uz, dir);
                String got = all == null ? null : String.join(" + ", all);
                if (Objects.equals(got, exp)) pass++;
                else { fail++; out.println("FAIL[compound] '" + uz + "' -> " + got + "  (exp " + exp + ")"); }
                continue;
            }
            if (unsafeCase) {
                Ru2Zh.ALLOW_UNSAFE = false;
                String blocked = Uz2Ru.uz2zh(uz, dir);
                Ru2Zh.ALLOW_UNSAFE = true;
                String got = Uz2Ru.uz2zh(uz, dir);
                if (blocked == null && Objects.equals(got, exp)) pass++;
                else { fail++; out.println("FAIL[unsafe] '" + uz + "' -> blocked=" + blocked + " open=" + got + "  (exp " + exp + ")  ru=[" + Uz2Ru.uz2ru(uz) + "]"); }
            } else {
                Ru2Zh.ALLOW_UNSAFE = false;
                String got = Uz2Ru.uz2zh(uz, dir);
                if (Objects.equals(got, exp)) pass++;
                else { fail++; out.println("FAIL '" + uz + "' -> " + got + "  (exp " + exp + ")  ru=[" + Uz2Ru.uz2ru(uz) + "]"); }
            }
        }
        Path ch = Paths.get(args.length > 1 ? args[1] : "chatter_uz.txt");
        if (Files.exists(ch)) {
            Ru2Zh.ALLOW_UNSAFE = true;
            for (String line : Files.readAllLines(ch)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String got = Uz2Ru.uz2zh(line.trim());
                if (got == null) pass++;
                else { fail++; out.println("FAIL[chatter] '" + line.trim() + "' -> " + got + "  (must be chat)  ru=[" + Uz2Ru.uz2ru(line.trim()) + "]"); }
            }
        }
        out.println("UZ PASS=" + pass + " FAIL=" + fail);
        System.exit(fail == 0 ? 0 : 1);
    }
}
