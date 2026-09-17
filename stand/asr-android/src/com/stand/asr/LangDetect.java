/*
 * Russian Voice Assistant modification for Changan A06 (C390).
 * Copyright (c) 2026 Tecrow.
 * Licensed under the PolyForm Noncommercial License 1.0.0 — noncommercial use only. See LICENSE.
 * Independent modification — not affiliated with or endorsed by Changan Automobile.
 */
package com.stand.asr;

/**
 * Which of the five GigaAM-Multilingual languages a CTC transcript is in: "ru" | "kk" | "ky" | "uz" | "en".
 *
 * The model emits lowercase characters from ONE shared alphabet (Latin a-z, Russian а-я/ё, and the
 * Kazakh/Kyrgyz letters әғқңөұүһі), so the script and a few language-specific letters decide:
 *   Cyrillic:  any Kazakh-only letter (ә ғ қ ұ һ і) → kk;  ң/ө/ү without those → ky;  else ru.
 *   Latin:     Uzbek markers (o'/g' digraphs, q not followed by u, frequent Uzbek words) → uz;  else en.
 *   Mixed:     the majority script decides.
 * Pure Java (no Android imports) so the JVM unit tests can run it.
 */
public final class LangDetect {
    // Letters that exist in Kazakh but NOT in Kyrgyz or Russian; Kyrgyz shares ң/ө/ү with Kazakh.
    private static final String KK_ONLY = "әғқұһі";
    private static final String KK_KY   = "ңөү";
    // Uzbek Latin: frequent function words / assistant verbs. Compared whole-word, lowercase.
    private static final String[] UZ_WORDS = { "va", "ham", "bu", "uchun", "bilan", "men", "siz", "biz", "yoq", "ha",
            "iltimos", "qil", "och", "yop", "oching", "yoping", "yoqing", "ber", "bering", "qancha",
            "nima", "qayerda", "qanday", "bo'l", "bo'ldi", "kerak", "mumkin", "bugun", "ertaga", "havo",
            "rejim", "rejimi", "rejimni", "keyingi", "oldingi", "ekran", "ekranni", "ovoz", "ovozni", "musiqa", "musiqani",
            "oyna", "oynani", "chiroq", "chiroqni", "eshik", "harorat", "haroratni", "haydovchi", "yo'lovchi", "trek" };

    private static final String[] EN_WORDS = { "the", "a", "an", "to", "on", "off", "in", "of", "my", "me", "it", "is",
            "up", "down", "turn", "open", "close", "set", "please", "and", "for", "with", "what", "how", "where", "when",
            "put", "play", "stop", "next", "back", "more", "less", "this", "that", "at", "by", "can", "you", "i", "make",
            "switch", "start", "call", "show", "go", "get", "some", "all", "lights", "light", "window", "windows", "seat",
            "music", "volume", "air", "temperature", "degrees", "car", "door", "trunk", "home", "song", "louder", "quieter" };

    public static String detect(String text) {
        if (text == null || text.isEmpty()) return "ru";
        int cyr = 0, lat = 0; boolean kkOnly = false, kkKy = false;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (c >= 'a' && c <= 'z') lat++;
            else if ((c >= 'а' && c <= 'я') || c == 'ё') cyr++;
            else if (KK_ONLY.indexOf(c) >= 0) { cyr++; kkOnly = true; }
            else if (KK_KY.indexOf(c) >= 0)   { cyr++; kkKy = true; }
        }
        if (cyr == 0 && lat == 0) return "ru";
        if (cyr >= lat) return kkOnly ? "kk" : (kkKy ? "ky" : "ru");
        String t = text.toLowerCase();
        // Latin script: Uzbek vs English. Uzbek markers: o'/g' digraphs, q not followed by u, the letter x,
        // agglutinative suffixes (-ni -ga -lar -da -dan -ini -si …) and frequent words; English markers:
        // its function words. The side with more evidence wins; a tie with any Uzbek evidence is Uzbek —
        // a car command in Uzbek ("sport rejimi") often has no apostrophe or q at all.
        int uz = 0, en = 0;
        if (t.contains("o'") || t.contains("g'") || t.contains("oʻ") || t.contains("gʻ") || t.contains("o’") || t.contains("g’")) uz += 2;
        for (int i = t.indexOf('q'); i >= 0; i = t.indexOf('q', i + 1))
            if (i + 1 >= t.length() || t.charAt(i + 1) != 'u') { uz += 2; break; }
        if (t.indexOf('x') >= 0) uz++;
        for (String w : t.split("[^a-z']+")) {
            if (w.isEmpty()) continue;
            boolean hit = false;
            for (String u : UZ_WORDS) if (w.equals(u)) { uz += 2; hit = true; break; }
            if (!hit) for (String e : EN_WORDS) if (w.equals(e)) { en += 2; hit = true; break; }
            if (!hit && w.length() >= 4 && w.matches(".*(ni|ga|qa|lar|lari|larni|ini|si|da|dan|dagi|gacha|imni|imiz|iga|imga|ing|ib|di|ti|mi|gi|ngi|i)$")
                && !w.matches(".*(thing|ring|ling|ning|king|sing|ting|ding|ping|wing|ying)$")) uz++;
        }
        return uz >= en && uz > 0 ? "uz" : "en";
    }

    private LangDetect() {}
}
