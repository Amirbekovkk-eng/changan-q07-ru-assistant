package com.stand.asr;

/** JVM test for LangDetect (the multi build's per-utterance language tag). Run by run_tests.sh. */
public final class LangDetectTest {
    private static int fail = 0, n = 0;

    private static void t(String want, String text) {
        n++;
        String got = LangDetect.detect(text);
        if (!want.equals(got)) { fail++; System.out.println("FAIL [" + text + "] want " + want + " got " + got); }
    }

    public static void main(String[] a) {
        // Russian (v3 vocabulary and the multi model's Russian output)
        t("ru", "включи кондиционер и поставь двадцать два градуса");
        t("ru", "открой окно водителя");
        t("ru", "ёлки палки");
        t("ru", "");
        t("ru", null);
        t("ru", "22");
        // Kazakh — has letters Kyrgyz never uses (і қ ғ ұ ә һ)
        t("kk", "кондиционерді қосып жиырма екі градус қой");
        t("kk", "терезені аш");
        t("kk", "бүгін ауа райы қандай");
        // Kyrgyz — ң ө ү but none of the Kazakh-only letters
        t("ky", "полиция сөөк ал жерде бир күндөй турганын билдирген");
        t("ky", "кондиционерди күйгүз");
        t("ru", "терезени ач");   // no marker letters at all → ru (accepted limitation: goes to the backend as Russian)
        // Uzbek (Latin)
        t("uz", "konditsionerni yoqing va yigirma ikki daraja qo'ying");
        t("uz", "oynani oching");
        t("uz", "bugun havo qanday");
        t("uz", "musiqani qo'y");
        t("uz", "sport rejimi");            // no apostrophe, no q: suffix evidence only
        t("uz", "ekranni aylantir");
        t("uz", "oynani yop");
        t("uz", "konditsionerni yoq");
        t("uz", "hud sozlamalari");
        t("uz", "keyingi trek");
        // English
        t("en", "turn on the air conditioner and set twenty two degrees");
        t("en", "open the driver window");
        t("en", "what is the weather today");
        t("en", "quick question");   // q followed by u stays English
        t("en", "next track");
        t("en", "set the temperature to twenty two");
        t("en", "close all windows");
        // Mixed: majority script wins
        t("ru", "включи yandex навигатор");
        t("en", "play music by кино");
        System.out.println("LangDetect: " + (n - fail) + "/" + n + " passed");
        if (fail > 0) System.exit(1);
    }
}
