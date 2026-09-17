/*
 * Russian Voice Assistant modification for Changan A06 (C390).
 * Copyright (c) 2026 Tecrow.
 * Licensed under the PolyForm Noncommercial License 1.0.0 — noncommercial use only. See LICENSE.
 * Independent modification — not affiliated with or endorsed by Changan Automobile.
 */
package com.stand.bridge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uzbek (Latin, as GigaAM-Multilingual writes it: lowercase, no punctuation, ASCII apostrophes) →
 * a Russian KEYWORD phrase that {@link Ru2Zh#ru2zh} understands. Not a translation: ru2zh matches
 * Russian stems with contains(), so every Uzbek stem is rewritten to the Russian word that carries
 * the stem ru2zh looks for ("och" → "открой", "oyna" → "окно"), numerals become digits, a few
 * multi-word constructs are rewritten as fixed Russian collocations ru2zh keys on ("old oynani
 * isit" → "обогрев лобового стекла"). Word order is left as is — Uzbek is SOV and ru2zh mostly
 * does not care. Negated imperatives ("ochma") and question words map to the Russian forms that
 * trip ru2zh's guard, so they never actuate. Pure Java: compiled by the JVM unit tests too.
 */
final class Uz2Ru {
    private Uz2Ru() {}

    /** Uzbek phrase → Chinese command (or null = chat). Same contract as Ru2Zh.ru2zh. */
    static String uz2zh(String uz, int speakerDir) {
        String ru = uz2ru(uz);
        if (ru == null || ru.isEmpty()) return null;
        return Ru2Zh.ru2zh(ru, speakerDir);
    }

    static String uz2zh(String uz) { return uz2zh(uz, 1); }

    // ------------------------------------------------------------------ phrase-level rewrites
    // Ordered. Applied to the whole (normalized) Uzbek text BEFORE the word-level dictionary, for
    // constructs whose Russian counterpart is a fixed collocation or whose word order matters.
    private static final String[][] PHRASES = {
        // --- calls: "<who>ga qo'ng'iroq qil" → "позвони <who>" (contact stays Latin, as in the phone book)
        {"^(?:на )?(\\d[\\d\\s+-]*?)\\s*raqam\\S*\\s+(?:qo'ng'iroq|telefon|sim)\\s*\\S*.*$", "набери номер $1"},
        {"^на (\\d[\\d\\s+-]*?) (?:qo'ng'iroq|telefon|sim)\\s*\\S*.*$", "набери номер $1"},
        {"^(\\d[\\d\\s+-]*?)\\s*(?:ga|ka|qa)?\\s+(?:qo'ng'iroq|telefon|sim)\\s*\\S*.*$", "набери номер $1"},
        {"^yana (?:bir marta )?(?:qo'ng'iroq|telefon)\\s*\\S*$", "набери еще раз"},
        {"^(?:qayta|orqaga) qo'ng'iroq qil\\S*$", "перезвони"},
        {"^(\\S+?)(?:ga|ka|qa) (?:qo'ng'iroq|telefon|sim)\\s*\\S*(?: ber)?$", "позвони $1"},
        {"^(\\S+?)(?:ga|ka|qa) chaqir\\S*$", "позвони $1"},
        {"^(\\S+?)(?:ni|ga) (?:kontakt|telefon kitob)\\S* top\\S*$", "найди в контактах $1"},
        {"kontakt\\S* (?:ichidan |dan )?(\\S+?)(?:ni)? (?:top|qidir)\\S*", "найди в контактах $1"},
        {"qo'ng'iroq\\S* (?:bekor qil|to'xtat)\\S*", "отмени вызов"},
        {"qo'ng'iroq\\S* (?:tashla|o'chir|rad et|uz)\\S*", "сбросьотклони"},
        {"qo'ng'iroq\\S* (?:qabul qil|ol)\\S*", "ответь"},
        {"(?:trubka|go'shak)\\S* (?:ol|ko'tar)\\S*", "возьми трубку"},
        {"(?:trubka|go'shak)\\S* (?:qo'y|tashla)\\S*", "положи трубку"},
        {"javob ber\\S*", "ответь"},
        {"javob berma\\S*", "не отвечай"},
        {"rad et\\S*", "отклони"},
        {"qo'ng'iroqlar (?:tarixi|jurnali|ro'yxati)\\S*", "журнал вызовов"},
        {"o'tkazib yuborilgan qo'ng'iroq\\S*", "пропущенные звонки"},
        {"javobsiz qo'ng'iroq\\S*", "пропущенные звонки"},
        {"kontaktlar\\S* sinxron\\S*", "синхронизируй контакты"},
        // --- negation / refusal
        {"(?:ovoz|tovush)\\S* olib tashla\\S*", "выключи звук"},
        {"blokdan chiqar\\S*", "разблокируй"},
        {"qulfdan chiqar\\S*", "разблокируй"},
        {"olib tashla\\S*", "убери"},
        {"nima degani", "что значит"},
        {"nima deganda", "что значит"},
        {"^nima bu", "что такое"},
        {"(\\S+) nima$", "что такое $1"},
        {"^kerak emas$", "не надо"},
        {"^yo'q kerak emas$", "не надо"},
        {"bezovta qilma\\S*", "не беспокоить"},
        {"bezovta qilinmasin", "не беспокоить"},
        // --- media fixed forms (Russian order matters for ru2zh)
        {"tashqi ovoz\\S*", "внешний голос"},
        {"ovoz\\S* (?:o'chir|so'ndir|olib tashla)\\S*", "выключи звук"},
        {"tovush\\S* (?:o'chir|so'ndir)\\S*", "выключи звук"},
        {"ovoz\\S* (?:yoq|qaytar)\\S*", "включи звук"},
        {"tovush\\S* (?:yoq|qaytar)\\S*", "включи звук"},
        {"ovozsiz qil\\S*", "выключи звук"},
        {"jim bo'l\\S*", "замолчи"},
        {"gapirma\\S*", "замолчи"},
        {"^(?:bo'ldi|yetadi|to'xta|bas)$", "хватит"},
        {"(?:bu|hozir|qaysi) qo'shiq\\S*( chalinyapti| ijro etilyapti| yangrayapti)?( bu)?$", "что за песня играет"},
        {"^nima (?:chalinyapti|ijro etilyapti|yangrayapti)$", "что играет"},
        {"^(?:bu|hozir) (?:qanday|qanaqa|qaysi|nima) (?:qo'shiq|musiqa)\\S*( chalinyapti| yangrayapti)?$", "что за песня играет"},
        {"^(?:hozir )?nima (?:chalinyapti|ijro etilyapti|yangrayapti)$", "что играет"},
        {"qo'shiq\\S* (?:matni|so'zlari)\\S*", "текст песни"},
        {"boshidan (?:qo'y|boshla|chal)\\S*", "перемотай в начало"},
        {"boshiga (?:qaytar|o'tkaz|qo'y)\\S*", "перемотай в начало"},
        {"(?:boshidan|qaytadan|yana) (?:chal|ijro et|qo'y)\\S*", "перемотай в начало"},
        {"tasodifiy (?:tartib|ravishda|ijro)\\S*", "случайный порядок"},
        {"tartib (?:bilan|bo'yicha)", "по порядку"},
        {"ketma-?ket", "по порядку"},
        {"bitta qo'shiqni takrorla\\S*", "песню по кругу"},
        {"qo'shiq\\S* takrorla\\S*", "песню по кругу"},
        {"ovoz\\S* haydovchi\\S*", "звук на водителя"},
        {"ovoz maydon\\S* (?:butun|hamma|barcha) salon\\S*", "звуковая сцена на весь салон"},
        {"ovoz maydon\\S*", "звуковая сцена"},
        {"ovoz sifat\\S* (?:yaxshila|oshir|ko'tar|yuqori)\\S*", "качество звука выше"},
        {"ovoz sifat\\S*", "качество звука"},
        {"ovoz\\S* (?:yaxshilash|kuchaytirish)\\S*", "улучшение звука"},
        {"(?:xabarnoma|bildirishnoma|ogohlantirish) (?:ovoz|tovush|signal)\\S* (?:almashtir|o'zgartir)\\S*", "смени звук уведомлений"},
        {"ovoz\\S* almashtir\\S*", "смени голос"},
        {"boshqa ovoz\\S*", "смени голос"},
        {"ayol ovoz\\S*", "женский голос"},
        {"erkak ovoz\\S*", "мужской голос"},
        {"tashqi ovoz\\S*", "внешний голос"},
        {"(?:uyg'otish|uyg'onish|uyg'otuvchi) so'z\\S*", "слово пробуждения"},
        {"uyg'otishsiz rejim\\S*", "режим без пробуждения"},
        {"past tezlik\\S* (?:ovoz|tovush|signal)\\S*", "звук на низкой скорости"},
        {"(?:ijro|chalish|o'ynatish) tezlig\\S*", "скорость воспроизведения"},
        {"(?:ijro|chalish|o'ynatish) (?:ro'yxati|ro'yxat)\\S*", "плейлист"},
        {"pleylist\\S*", "плейлист"},
        {"tinglash tarix\\S*", "история прослушивания"},
        {"eshitish tarix\\S*", "история прослушивания"},
        {"sevimli qo'shiq\\S*", "избранные песни"},
        {"sevimlilar\\S* (?:qo'sh|saqla)\\S*", "в избранное"},
        {"^sevimlilarga$", "в избранное"},
        {"sevimlilar\\S* (?:olib tashla|o'chir)\\S*", "убери из избранного"},
        {"sevimlilar\\S* och\\S*", "избранное открой"},
        {"(?:manba|manbani) (?:onlayn|onlain|internet)\\S*", "источник онлайн"},
        {"(?:onlayn|onlain) musiqa\\S*", "источник онлайн музыка"},
        {"fleshka\\S*", "с флешки"},
        {"yusb\\S*", "юсб"},
        {"(\\d+) (?:soniya|sekund)\\S* (?:oldinga|orqaga)? ?(?:o'tkaz|surish|sur|aylantir)\\S*", "перемотай $1 секунд"},
        {"(?:oldinga|orqaga)? ?(\\d+) (?:soniya|sekund)\\S*( (?:oldinga|orqaga))? ?(?:o'tkaz|sur|aylantir)\\S*", "перемотай $1 секунд"},
        {"(\\d+)(?:-| )?(?:daqiqa|minut)\\S*(?:ga)? (?:o'tkaz|sur|o't)\\S*", "перемотай на $1 минуту"},
        {"oldinga (?:o'tkaz|sur|aylantir)\\S*", "перемотай вперед"},
        {"orqaga (?:o'tkaz|sur|aylantir|qaytar)\\S*", "перемотай назад"},
        {"radio\\S* (\\d+) (?:chastota|to'lqin)\\S*", "радио на частоту $1"},
        {"(\\d+) (?:chastota|to'lqin)\\S*", "частоту $1"},
        {"radio\\S* (\\d+)$", "радио на частоту $1"},
        {"oldingi (?:qo'shiq|trek|stansiya|stantsiya|kanal)\\S*", "предыдущая песня"},
        {"keyingi (?:qo'shiq|trek|stansiya|stantsiya|kanal)\\S*", "следующая песня"},
        {"boshqa (?:qo'shiq|trek)\\S*", "другую песню"},
        {"qo'shiq\\S* (?:almashtir|o'zgartir)\\S*", "смени песню"},
        {"(\\d+)(?:-| )?(?:chi|nchi|inchi) (?:qo'shiq|trek)\\S*", "песню номер $1"},
        {"(\\d+)(?:-| )?raqamli (?:qo'shiq|trek)\\S*", "песню номер $1"},
        {"(?:musiqa|qo'shiq)\\S* (?:yuklab ol|yukla)\\S*", "скачай эту песню"},
        {"(?:musiqa|qo'shiq)\\S* (?:pauza|to'xtat)\\S*", "музыку на паузу"},
        {"(?:musiqa|qo'shiq)\\S* (?:davom ettir|davom et)\\S*", "продолжи музыку"},
        {"musiqa\\S* eshit\\S*", "хочу послушать музыку"},
        {"musiqa\\S* tingla\\S*", "хочу послушать музыку"},
        {"^musiqa$", "музыку"},
        {"^musiqani$", "музыку"},
        {"^qo'shiq(?:ni)? (?:qo'y|yoq|chal)(?:ing|gin|ib ber)?$", "включи песню"},
        {"to'q sariq", "оранжевый"},
        {"havo\\S* (\\S+?)(?:ga|qa|ka) yo'nalt\\S*", "направь воздух в $1"},
        {"davom et\\S* chal\\S*", "играй дальше"},
        {"chal\\S* davom et\\S*", "играй дальше"},
        {"bu yer(?:da)? (?:jazirama|issiq|do'zax)", "пекло тут"},
        {"yoqilg'i quyish lyuk\\S*", "заправочный лючок"},
        {"^biror (?:narsa|qo'shiq) (?:qo'y|yoq|chal)\\S*$", "включи что-нибудь"},
        {"davom ettir\\S*", "продолжай"},
        {"davom et\\S*", "продолжай"},
        // --- climate fixed forms
        {"(?:old|oldingi|lobovoy) (?:oyna|shisha)\\S* (?:isit|muzsizlantir|muz erit)\\S*", "обогрев лобового стекла"},
        {"(?:orqa|orqadagi) (?:oyna|shisha)\\S* (?:isit|muzsizlantir|muz erit)\\S*", "обогрев заднего стекла"},
        {"(?:oyna|shisha)\\S* (?:isit|muzsizlantir|muz erit)\\S*", "обогрев стекла"},
        {"(?:old|oldingi) (?:oyna|shisha)\\S* (?:pufla|shamol)\\S*", "обдув лобового стекла"},
        {"(?:oyna|shisha)\\S*(?:ga)? (?:pufla|shamol)\\S*", "дуй на стекло"},
        {"(?:oyna|shisha)\\S* (?:terla|bug'lan)\\S*", "окна запотели"},
        {"avtomatik muz(?:sizlantirish|ni eritish)\\S*", "автоматическую разморозку"},
        {"muz(?:sizlantirish|ni eritish| eritish)\\S*", "разморозку"},
        {"konditsioner\\S* (?:quritish|qurit)\\S*", "просушку кондиционера"},
        {"(?:qulf ochilganda|ochilganda|blokdan chiqarilganda|ochganda) (?:shamollatish|havo almashtirish)\\S*", "проветривание при разблокировке"},
        {"(?:batareya|akkumulyator)\\S* (?:isit|qizdir|isitish)\\S*", "подогрев батареи"},
        {"(?:salon|kabina|mashina)\\S* (?:tez|tezroq|zudlik bilan|darhol) (?:isit|qizdir)\\S*", "быстро прогрей салон"},
        {"(?:tez|tezroq|zudlik bilan|darhol) (?:salon|kabina|mashina)\\S* (?:isit|qizdir)\\S*", "быстро прогрей салон"},
        {"(?:salon|kabina|mashina)\\S* (?:isit|qizdir)\\S*", "прогрей салон"},
        {"(?:salon|kabina|mashina)\\S* (?:tez|tezroq|zudlik bilan|darhol) sovut\\S*", "быстро охлади салон"},
        {"(?:tez|tezroq|zudlik bilan|darhol) (?:salon|kabina|mashina)\\S* sovut\\S*", "быстро охлади салон"},
        {"(?:salon|kabina|mashina)\\S* sovut\\S*", "охлади салон"},
        {"(?:salon|kabina|mashina)\\S* (?:shamollat|havosini almashtir)\\S*", "проветри салон"},
        {"toza havo\\S*", "свежего воздуха"},
        {"(?:kuchli |qattiq )?shamol (?:esyapti|esayapti|esmoqda|esdi)", "дует сильный ветер"},
        {"ko'cha\\S*", "на улице"},
        {"(?:kuchli|maksimal|zo'r) sovutish\\S*", "сильное охлаждение"},
        {"havo tozalagich\\S*", "очиститель воздуха"},
        {"havo\\S* tozala\\S*", "очисти воздух"},
        {"havo (?:oqimi|oqim)\\S*", "поток воздуха"},
        {"havo\\S* (?:tebranish|tebrat|silkit|chayqal)\\S*", "качание воздуха"},
        {"(?:ichki|ichkі) (?:aylanish|sirkulyatsiya)\\S*", "рециркуляцию"},
        {"tashqi (?:aylanish|sirkulyatsiya|havo olish)\\S*", "внешний забор воздуха"},
        {"tashqi havo\\S* (?:olish|kirit)\\S*", "внешний забор воздуха"},
        {"havo aylanish\\S*", "циркуляцию"},
        {"aylanish\\S* (?:almashtir|o'zgartir|o't)\\S*", "переключи циркуляцию"},
        {"havo aylanishi\\S* (?:almashtir|o'zgartir)\\S*", "переключи циркуляцию"},
        {"harorat\\S* sinxron\\S*", "синхронизируй температуру"},
        {"harorat\\S* (?:bir xil|teng)\\S*", "температуру одинаково"},
        {"harorat\\S* maksimum\\S*", "температуру на максимум"},
        {"harorat\\S* minimum\\S*", "температуру на минимум"},
        {"harorat\\S* (?:eng yuqori|eng baland)\\S*", "температуру на максимум"},
        {"harorat\\S* (?:eng past|eng kam)\\S*", "температуру на минимум"},
        {"(?:yuz|yuzim|bet)\\S* va (?:oyoq|oyog'im)\\S*", "лицо и ноги"},
        {"(?:oyoq|oyog'im)\\S* va (?:yuz|yuzim|bet|tana|badan)\\S*", "лицо и ноги"},
        {"(?:oyna|shisha)\\S* va (?:oyoq|oyog'im)\\S*", "стекло и ноги"},
        {"(?:menga|ustimga|yuzimga) pufla\\S*", "дуй на меня"},
        {"nam(?:lik)? (?:kamaytir|olish|qurit)\\S*", "осушение"},
        {"namlik\\S*", "влажность"},
        {"tejamkor rejim\\S*", "эконом режим"},
        {"(?:issiq|isitish) rejim\\S*", "на обогрев"},
        {"sovutish rejim\\S*", "на охлаждение"},
        {"^dim$", "душно"},
        {"^dim bo'lib ketdi$", "душно"},
        {"^havo dim$", "душно"},
        {"^(?:men(?:ga)? )?issiq(?: bo'lyapti| bo'ldi)?$", "жарко"},
        {"^(?:men(?:ga)? )?sovuq(?: bo'lyapti| bo'ldi)?$", "мне холодно"},
        {"^mashina(?:da|ning ichi)? sovuq$", "холодно в машине"},
        {"^mashina(?:da|ning ichi)? issiq$", "жарко"},
        {"^(?:men )?muzlab (?:ketdim|qoldim)$", "я замерз"},
        {"^(?:men )?muzladim$", "я замерз"},
        {"^(?:men )?terlab ketdim$", "я вспотел"},
        {"^(?:men )?terladim$", "я вспотел"},
        {"^issiq(?:lik)? (?:kerak|xohlayman|istayman)$", "хочу тепла"},
        {"^issiqroq (?:kerak|xohlayman|istayman)$", "хочу тепла"},
        {"^(?:juda )?(?:issiq|jazirama|do'zax)(?: bu yerda| bu yer)?$", "пекло тут"},
        {"^(?:qattiq |juda )?sovuq bu yerda$", "дубак в машине"},
        {"^(?:juda )?sovuq mashinada$", "дубак в машине"},
        {"^qaltirayapman$", "колотун"},
        {"^titrayapman$", "колотун"},
        {"^sovqotdim$", "продрог весь"},
        {"^sovqotib ketdim$", "продрог весь"},
        {"issiqroq qil\\S*", "сделай теплее"},
        {"sovuqroq qil\\S*", "сделай холоднее"},
        {"salqinroq qil\\S*", "сделай прохладнее"},
        {"biroz issiqroq", "теплее"},
        {"biroz sovuqroq", "холоднее"},
        // --- windows / roof
        {"(?:sal|ozgina|biroz|picha|salgina|andak) (?:och|ochib qo'y)\\S*", "приоткрой"},
        {"(?:tirqish|yoriq|yoriqcha)(?:gina|cha)? (?:och|qoldir)\\S*", "приоткрой"},
        {"yomg'ir(?:da|ga)?\\S* (?:oyna|deraza)\\S* (?:yop|avtomatik yop)\\S*", "закрывай окна в дождь"},
        {"yomg'ir\\S* (?:avtomatik )?(?:yopish|yopil)\\S*", "закрытие окон в дождь"},
        {"qo'riqlash(?:da|ga)? (?:oyna|deraza)\\S* yop\\S*", "закрой окна на охране"},
        {"qulflaganda (?:oyna|deraza)\\S* yop\\S*", "закрой окна на охране"},
        {"(?:oyna|deraza)\\S* (?:qulf|blok)\\S*", "блокировку окон"},
        {"(?:oyna|deraza)\\S* (?:blokdan chiqar|qulfdan chiqar|qulfini och)\\S*", "разблокируй стеклоподъемники"},
        {"(?:oyna|deraza)\\S* (?:blokla|qulfla)\\S*", "заблокируй стеклоподъемники"},
        {"(?:oyna|deraza) ko'targich\\S*", "стеклоподъемники"},
        {"(?:oyna|deraza)\\S* pardasi\\S*", "шторки на окнах"},
        {"(?:oyna|deraza)(?:lar|dagi)\\S* parda\\S*", "шторки на окнах"},
        {"quyosh pardasi\\S*", "шторку"},
        {"(?:tom|lyuk)\\S* parda\\S*", "шторку на крыше"},
        {"(?:orqa|orqadagi) parda\\S*", "шторки сзади"},
        {"haydovchi orqasidagi", "за водителем"},
        {"haydovchi orqasida", "за водителем"},
        {"haydovchi ortidagi", "за водителем"},
        {"yo'lovchi orqasidagi", "за пассажиром"},
        {"yo'lovchi ortidagi", "за пассажиром"},
        {"(?:yuk|yukxona) (?:oyna|shisha|derazasi)\\S*", "стекло багажника"},
        {"yukxona(?:ning)? (?:oyna|shisha|deraza)\\S*", "стекло багажника"},
        {"(?:yuk|yukxona)\\S* (?:chiroq|chirog'i|yorug'lig'i)\\S*", "свет в багажнике"},
        {"yukxona(?:ning)? (?:tomi|tom|ustki qismi|yuqori qismi|yuqorisi)\\S*", "крыша багажника"},
        {"(?:yukxona|kuzov)(?:ning)? (?:pastki|quyi) (?:borti|bort|qismi)\\S*", "нижний борт багажника"},
        {"yukxona(?:ning)? (?:qopqog'i|qopqoq)\\S*", "крышку багажника"},
        {"(?:oldingi|old) yukxona\\S*", "передний багажник"},
        {"bolalar qulf\\S*", "детский замок"},
        {"avtomatik bolalar qulf\\S*", "автоматический детский замок"},
        {"(?:orqa )?(?:to'siq|ajratgich|ajratuvchi) (?:oyna|shisha)\\S*", "перегородку"},
        {"(?:zaryad|zaryadlash) (?:qopqog'i|qopqoq|lyuki|lyuk|eshigi|eshikcha|teshigi|porti|port)\\S*", "лючок зарядки"},
        {"(?:zaryad|zaryadlash)(?:ni|ini)? (?:och|yop)\\S*", "$0"},
        {"(?:benzin|yoqilg'i|yonilg'i) (?:baki|bak|qopqog'i|lyuki|lyuk)\\S*", "лючок бензобака"},
        {"(?:bak|bakni|bakcha)(?:ni)? (?:och|yop)\\S*", "$0"},
        {"yaqinlashganda (?:qulf(?:ni)? och|ochil)\\S*", "разблокировку при подходе"},
        {"yaqinlash\\S* (?:avtomatik )?(?:ochilish|ochish)\\S*", "разблокировку при подходе"},
        {"(?:ketganda|uzoqlashganda|chiqib ketganda) (?:yop|qulfla|qulflan)\\S*", "закрывать при уходе"},
        {"(?:mashina|avtomobil)\\S* (?:qo'riq|himoya)\\S*(?:ga)? qo'y\\S*", "поставь машину на охрану"},
        {"(?:mashina|avtomobil)\\S* qulfla\\S*", "запри машину"},
        {"(?:mashina|avtomobil)\\S* (?:yop|qulfl)\\S*", "запри машину"},
        {"(?:mashina|avtomobil)\\S* (?:och|qulfini och|qulfdan chiqar)\\S*", "открой машину"},
        {"(?:eshik|eshiklar)\\S* qulfla\\S*", "запри двери"},
        {"(?:eshik|eshiklar)\\S* (?:qulfini och|qulfdan chiqar|blokdan chiqar)\\S*", "разблокируй двери"},
        {"qulf\\S* yop\\S*", "закрой замок"},
        {"qulf\\S* och\\S*", "открой замок"},
        {"^qulfla$", "запри машину"},
        {"^qulfdan chiqar$", "разблокируй двери"},
        // --- seats
        {"(?:o'rindiq|kreslo)\\S* (?:holat|joylashuv|pozitsiya)\\S* (?:eslab qol|saqla)\\S*", "запомни позицию сиденья"},
        {"(?:o'rindiq|kreslo) (?:holat|joylashuv|pozitsiya)\\S* (?:eslab qol|saqla)\\S*", "запомни позицию сиденья"},
        {"(?:o'rindiq|kreslo)\\S* (?:uxlash|yotish) (?:holati|rejimi|uchun)\\S*", "кресло в положение для сна"},
        {"(?:o'rindiq|kreslo)\\S* (?:yot|yoy|tekisla)\\S*", "разложи кресло"},
        {"(?:o'rindiq|kreslo)\\S* vaznsizlik\\S*", "сиденье в невесомость"},
        {"karavot\\S* (?:yoy|yoz|och)\\S*", "кровать разложи"},
        {"(?:orqa|orqadagi) (?:o'ng|o'ngdagi) (?:o'rindiq|kreslo)\\S*", "заднее правое сиденье"},
        {"(?:orqa|orqadagi) (?:chap|chapdagi) (?:o'rindiq|kreslo)\\S*", "заднее левое сиденье"},
        {"butun tana\\S*", "всего тела"},
        {"butun (?:orqa|bel|umurtqa)\\S*", "всей спины"},
        {"(?:yelka|yelkalar|bo'yin)\\S* massaj\\S*", "массаж плеч"},
        {"massaj\\S* (?:yelka|bo'yin)\\S*", "массаж плеч"},
        {"(?:orqa|orqam|bel|belim)\\S* massaj\\S*", "массаж спины"},
        {"massaj\\S* (?:orqa|orqam|bel|belim)(?:ga)?\\S*", "массаж на спину"},
        {"to'lqin(?:li|simon)? massaj\\S*", "волновой массаж"},
        {"massaj\\S* (?:tur|rejim|usul)\\S* (?:almashtir|o'zgartir)\\S*", "смени режим массажа"},
        {"boshqa massaj\\S*", "другой режим массажа"},
        {"massaj\\S* (?:almashtir|o'zgartir)\\S*", "смени массаж"},
        {"massaj\\S* to'g'rila\\S*", "поправь массаж"},
        {"massaj\\S* (?:maksimum|maksimal)\\S*", "массаж на максимум"},
        {"oyoq (?:tagligini|qo'ygichini|tayanchini)?\\s*(?:uzunroq|uzaytir)\\S*", "подножку длиннее"},
        {"oyoq (?:tagligini|qo'ygichini|tayanchini)?\\s*(?:kaltaroq|qisqaroq|qisqartir)\\S*", "подножку короче"},
        {"oyoq (?:tagligi|qo'ygich|tayanchi|tirgagi|tirgak|tagligini|qo'ygichini|tayanchini)\\S*", "подставку для ног"},
        {"(?:qulay|komfort) (?:o'tirish|kirish|chiqish|kirish-chiqish|kirib chiqish)\\S*", "комфортная посадка"},
        {"vip yo'lovchi\\S*", "вип пассажир"},
        {"yo'lovchi vip\\S*", "вип пассажир"},
        {"(?:o'rindiq|kreslo)\\S* (?:oldinga|oldin)\\S*", "сиденье вперед"},
        {"(?:o'rindiq|kreslo)\\S* (?:orqaga|orqa tomonga)\\S*", "сиденье назад"},
        {"(?:o'rindiq|kreslo)\\S* (?:yaqinroq|yaqin)\\S*", "придвинь кресло поближе"},
        {"(?:suyanchiq|suyanch)\\S* (?:orqaga|orqa tomonga|yotqiz|pastga|tushir)\\S*", "спинку сиденья назад"},
        {"(?:suyanchiq|suyanch)\\S* (?:to'g'ri|tik|tikroq|to'g'riroq)\\S*", "спинку сиденья прямее"},
        {"(?:suyanchiq|suyanch)\\S* ko'tar\\S*", "подними спинку сиденья"},
        {"(?:suyanchiq|suyanch)\\S* (?:yoy|yoz|yotqizib qo'y)\\S*", "разложи кресло"},
        // --- lights
        {"(?:uzoq|uzoqqa) (?:nur|yorug'lik|chiroq|fara)\\S*", "дальний свет"},
        {"(?:yaqin|yaqinga) (?:nur|yorug'lik|chiroq|fara)\\S*", "ближний свет"},
        {"avariya (?:chiroq|chirog'i|signali|signal|yorug'lig'i)\\S*", "аварийку"},
        {"^avariya\\S*$", "аварийку"},
        {"(?:oldingi|old) tuman (?:chiroq|chirog'i|faralar)\\S*", "передние противотуманки"},
        {"(?:orqa|orqadagi) tuman (?:chiroq|chirog'i|faralar)\\S*", "задние противотуманки"},
        {"tuman (?:chiroq|chirog'i|faralar|fara)\\S*", "противотуманки"},
        {"gabarit\\S*", "габариты"},
        {"(?:kunduzgi|kunduz) (?:chiroq|yurish chiroq)\\S*", "дхо"},
        {"(?:orqa|orqadagi) (?:chiroq|fonar)\\S*", "задние фонари"},
        {"(?:yuk|yukxona)\\S* (?:chiroq|fonar)\\S*", "свет в багажнике"},
        {"salon\\S* (?:butun|hamma|barcha) (?:chiroq|yorug'lik)\\S*", "весь салонный свет"},
        {"(?:butun|hamma|barcha) salon (?:chiroq|yorug'lik|yoritish)\\S*", "весь салонный свет"},
        {"salon(?:dagi|ning)? (?:chiroq|yorug'lik|yoritish|yoritgich)\\S*", "салонный свет"},
        {"(?:mashina|avtomobil)(?:dagi|ning ichidagi|ning ichi)? (?:chiroq|yorug'lik|yoritish)\\S*", "свет в автомобиле"},
        {"(?:hamma|barcha) (?:chiroq|lampa)\\S*", "все лампы"},
        {"(?:hamma|har) (?:joyda|yerda) (?:chiroq|yorug'lik)\\S*", "свет везде"},
        {"(?:chiroq|yorug'lik)\\S* (?:hamma|har) (?:joyda|yerda)", "свет везде"},
        {"(?:shift|ship|tom) (?:chiroq|chirog'i|yorug'lig'i)\\S*", "потолочный свет"},
        {"(?:shift|ship|tom)(?:dagi|da)? (?:chiroq|yorug'lik)\\S*", "свет на потолке"},
        {"o'qish (?:chiroq|chirog'i|lampa)\\S*", "лампу чтения"},
        {"plafon\\S*", "плафон"},
        {"(?:atmosfera|fon|dekorativ|ambient|neon|kontur) (?:yoritish|yorug'lik|chiroq|chirog'i|yoritgich)\\S*", "подсветку"},
        {"^(?:yoritish|yoritishni|yoritishning)$", "подсветку"},
        {"(?:yoritish|yoritishni) ((?:yoq|o'chir|yorqinroq|xiraroq|kuchaytir|susaytir|pasaytir|oshir)\\S*)", "подсветку $1"},
        {"(?:yorug'lik|chiroq) (?:shou|shousi)\\S*", "световое шоу"},
        {"musiqa\\S* (?:yorug'lik|chiroq|nur)\\S*", "световое шоу"},
        {"(?:yoritish|podsvetka)\\S* (?:musiqa|ritm|takt)\\S*", "подсветка в такт музыке"},
        {"(?:yoritish|podsvetka)\\S* (?:gradient|jilvalan|tovlan)\\S*", "подсветка с переливами"},
        {"(?:yoritish|podsvetka)\\S* (?:mavzu|tema)\\S*", "тему подсветки"},
        {"(?:yoritish|podsvetka)\\S* (?:effekt|jilo)\\S*", "эффект подсветки"},
        {"(?:yoritish|podsvetka)\\S* (?:rang|rangi)\\S*", "цвет подсветки"},
        {"(?:yoritish|podsvetka)\\S* sozlama\\S*", "настройки подсветки"},
        {"(?:kutib olish|salomlashish|kutib olish) (?:yoritish|chiroq|nur|yorug'lik)\\S*", "приветственную подсветку"},
        {"(?:kuzatish|xayrlashish|kuzatuv) (?:yoritish|chiroq|nur|yorug'lik)\\S*", "прощальную подсветку"},
        {"(?:fara|faralar)\\S* (?:avtomatik|avto)\\S*", "фары в автоматический режим"},
        {"avtomatik (?:fara|faralar)\\S*", "автоматические фары"},
        {"(?:uzoq nur|uzoq yorug'lik)\\S* avtomatik\\S*", "дальний в автоматический режим"},
        {"(?:chiroq|yorug'lik)\\S* avtomatik\\S*", "свет в автоматический"},
        {"(?:fara|faralar)\\S* (?:pastroq|pastga|tushir)\\S*", "фары ниже"},
        {"(?:fara|faralar)\\S* (?:balandroq|yuqoriga|ko'tar)\\S*", "фары выше"},
        // --- mirrors / steering / wipers
        {"(?:ko'zgu|oyna)\\S* (?:avtomatik )?(?:yig'ilish|buklanish|yig'ish)\\S*", "автоскладывание зеркал"},
        {"(?:ko'zgu|ko'zgular)\\S* sozlama\\S*", "настройки зеркал"},
        {"(?:oqim|striming|strim) (?:ko'zgu|oyna)\\S*", "стриминговое зеркало"},
        {"(?:orqa|orqani) ko'rish (?:yordamchi|assistent)\\S*", "ассистент заднего обзора"},
        {"(?:orqa|orqani) ko'rish kamera\\S*", "камера заднего вида"},
        {"orqa kamera\\S*", "камера заднего вида"},
        {"rul\\S* (?:yengil|yengilroq)\\S*", "руль легче"},
        {"rul\\S* (?:og'ir|og'irroq)\\S*", "руль тяжелее"},
        {"rul\\S* (?:avtomatik |avto)?(?:isit|isitish)\\S* (?:avtomatik|avto)\\S*", "автоподогрев руля"},
        {"rul\\S* (?:avtomatik|avto) (?:isit|isitish)\\S*", "автоподогрев руля"},
        {"rul\\S* (?:mahkamla|qotir|fiksatsiya)\\S*", "зафиксируй руль"},
        {"(?:oyna|shisha|old oyna)\\S* (?:yuv|yuvish)\\S*", "помой стекло"},
        {"(?:oyna|shisha)\\S* (?:art|artib|tozalab qo'y)\\S*", "протри стекло"},
        {"(?:oyna|shisha)\\S*(?:dagi|dan)? tomchi\\S* (?:art|olib tashla|tozala)\\S*", "смахни капли со стекла"},
        {"tomchi\\S* (?:art|olib tashla|tozala)\\S*", "смахни капли со стекла"},
        {"(?:yomg'ir|yomgir) (?:sensor|datchik|sezgich)\\S*", "датчик дождя"},
        {"(?:oyna|shisha) tozalagich\\S*", "дворники"},
        {"(?:tozalagich|dvornik|shyotka|cho'tka)\\S* sezgirlig\\S*", "чувствительность дворников"},
        {"(?:tozalagich|dvornik)\\S* (?:xizmat|servis|ta'mirlash|almashtirish) (?:rejim|holat)\\S*", "сервисный режим дворников"},
        {"(?:orqa|orqadagi) (?:tozalagich|dvornik|cho'tka|shyotka)\\S*", "задний дворник"},
        // --- HUD / screens
        {"(?:proyeksiya|proektsiya|proyektsiya|hud|xad)\\S* (?:rang|rangi)\\S*", "цвет проекции"},
        {"(?:proyeksiya|proektsiya|proyektsiya|hud|xad)\\S* (?:rejim|ko'rinish)\\S*", "режим хад"},
        {"(?:proyeksiya|proektsiya|proyektsiya|hud|xad)\\S* sozlama\\S*", "настройки хад"},
        {"(?:proyeksiya|proektsiya|proyektsiya|hud|xad)\\S* (?:burchag|burchak|qiyalig|qiyalik|og'ish)\\S*", "угол проекции"},
        {"(?:ekran|displey)\\S* (?:avtomatik |avto)?yorqinlik\\S* (?:avtomatik|avto)?", "автояркость экрана"},
        {"(?:avtomatik|avto) yorqinlik\\S*", "автояркость экрана"},
        {"ko'z(?:ni)? himoya\\S*", "защиту глаз"},
        {"ko'zni asrash\\S*", "защиту глаз"},
        {"(?:ekran|displey)\\S* (?:tungi|kecha|tun) (?:rejim|holat)\\S*", "экран в ночной режим"},
        {"(?:ekran|displey)\\S* (?:kunduzgi|kun) (?:rejim|holat)\\S*", "экран в дневной режим"},
        {"(?:qorong'i|qora|tungi) (?:mavzu|tema)\\S*", "тёмную тему"},
        {"(?:yorug'|oq|yorqin|kunduzgi) (?:mavzu|tema)\\S*", "светлую тему"},
        {"(?:ekran|displey)\\S* (?:o'chir|so'ndir)\\S* (?:qorong'ilashtir)?", "$0"},
        {"(?:ekran|displey)\\S* (?:so'ndir|qorong'ilashtir|uxlat)\\S*", "погаси экран"},
        {"(?:ekran|displey)\\S* uyg'ot\\S*", "разбуди экран"},
        {"(?:ekran|displey)\\S* (?:aylantir|bur|ag'dar)\\S*", "поверни экран"},
        {"(?:ekran|displey)\\S* (?:menga|men tomonga|haydovchi tomonga|haydovchiga)\\S* (?:aylantir|bur|qarat)\\S*", "поверни экран ко мне"},
        {"(?:ekran|displey)\\S* (?:gorizontal|yotiq)\\S*", "экран горизонтально"},
        {"(?:ekran|displey)\\S* (?:vertikal|tik)\\S*", "экран вертикально"},
        {"(?:ekran|displey)\\S* (?:tozalash|tozalov) (?:rejim|holat)\\S*", "режим очистки экрана"},
        {"(?:rang|rangli) harorat\\S*", "цветовая температура"},
        {"yo'lovchi (?:ekran|displey)\\S* (?:qiyalig|qiyalik|burchag|burchak|og'ish)\\S*", "наклон экрана пассажира"},
        {"yo'lovchi (?:ekran|displey)\\S* (?:egi|eg|qiyalat|og'dir)\\S*", "пассажирский экран наклони"},
        {"yo'lovchi (?:ekran|displey)\\S* (?:pastroq|pastga)\\S*", "экран пассажира ниже"},
        {"yo'lovchi (?:ekran|displey)\\S* (?:balandroq|yuqoriga)\\S*", "наклон экрана пассажира повыше"},
        {"(?:asosiy|bosh) (?:ekran|sahifa|menyu)\\S*", "главный экран"},
        {"ish stoli\\S*", "рабочий стол"},
        {"(?:oldingi|avvalgi) (?:ekran|sahifa)\\S*", "предыдущий экран"},
        {"keyingi sahifa\\S*", "следующая страница"},
        {"(?:varaqla|varaqlab ber|ag'dar)\\S*", "листай"},
        {"shrift\\S* (?:kattaroq|kattalashtir|katta)\\S*", "шрифт крупнее"},
        {"shrift\\S* (?:kichikroq|kichraytir|maydaroq|kichik)\\S*", "шрифт мельче"},
        // --- drive / energy / suspension / modes
        {"(?:haydash|yurish) (?:rejim|uslub)\\S*", "режим вождения"},
        {"(?:standart|oddiy|odatiy) (?:haydash |yurish )?rejim\\S*", "стандартный режим вождения"},
        {"(?:qish|qishki|qor|qorli) (?:rejim|holat)\\S*", "зимний режим"},
        {"yo'lsizlik (?:yordam|yordamchi|assistent)\\S*", "помощь на бездорожье"},
        {"(?:yo'lsizlik|yo'lsiz|off-?road|ofroud) (?:rejim|holat)\\S*", "режим бездорожья"},
        {"(?:tiqilib|botib) qoldim\\S*", "я застрял помоги выбраться"},
        {"(?:loy|qum|qor)(?:dan)? chiqish\\S*", "помощь при выезде из грязи"},
        {"(?:loy|qum|qor)(?:dan)? chiqishga yordam\\S*", "помощь при выезде из грязи"},
        {"joyida (?:burilish|aylanish|buril)\\S*", "разворот на месте"},
        {"tank(?:cha|day|simon)? (?:burilish|aylanish)\\S*", "танковый разворот"},
        {"(?:energiya|quvvat) (?:qaytarish|tiklash|rekuperatsiya)\\S*", "рекуперацию"},
        {"rekuperatsiya\\S*", "рекуперацию"},
        {"(?:zaryad|quvvat)(?:ni)? (?:saqlash|tejash) (?:rejim|holat)\\S*", "режим сохранения заряда"},
        {"(?:elektr|elektrik|faqat elektr) (?:rejim|holat|yurish)\\S*", "электрический режим"},
        {"gibrid (?:rejim|holat)\\S*", "гибридный режим"},
        {"(?:benzin|yoqilg'i) (?:rejim|holat|ustuvor)\\S*", "режим на бензине"},
        {"(?:aqlli|intellektual) (?:energiya )?(?:rejim|holat)\\S*", "умный режим"},
        {"(?:osma|podveska|amortizator)\\S* (?:tekisla|tekislash|tekis qil)\\S*", "выровняй подвеску"},
        {"(?:qattiq|qattiqroq) (?:osma|podveska)\\S*", "жесткая подвеска"},
        {"(?:yumshoq|yumshoqroq) (?:osma|podveska)\\S*", "мягкая подвеска"},
        {"(?:yuk|yuklash|yuk ortish) (?:rejim|holat)\\S*", "режим погрузки"},
        // --- comfort / scenario
        {"(?:kino|film) (?:rejim|holat)\\S*", "режим кино"},
        {"kemping (?:rejim|holat)\\S*", "режим кемпинга"},
        {"(?:oldingi|old) (?:kabina|salon) kemping\\S*", "кемпинг спереди"},
        {"kemping\\S* (?:oldingi|old|old tomon)\\S*", "кемпинг спереди"},
        {"karaoke\\S* kemping\\S*", "кемпинг с караоке"},
        {"kemping\\S* karaoke\\S*", "кемпинг с караоке"},
        {"(?:lager|kemping) (?:qo'riq|himoya|qo'riqlash)\\S*", "охрану лагеря"},
        {"yulduz\\S* (?:ko'r|tomosha|qara)\\S*", "хочу посмотреть на звезды"},
        {"(?:qirolicha|malika) (?:rejim|holat)\\S*", "режим королевы"},
        {"(?:pari|parizod|fairy) (?:rejim|holat)\\S*", "режим феи"},
        {"romantik (?:kutib olish|uchrashuv|rejim)\\S*", "романтическую встречу"},
        {"(?:osma|podveska)\\S* (?:bilan )?(?:salomlash|kutib ol)\\S*", "приветствие подвеской"},
        {"(?:ekran|displey)\\S*(?:da|dagi)? (?:salomlash|kutib ol|salom)\\S*", "экран приветствие"},
        {"(?:yordamchi|assistent)\\S* (?:salomlash|kutib ol|salom)\\S*", "приветствие ассистента"},
        {"bayram(?:ona)? (?:salomlash|tabrik|kutib ol)\\S*", "праздничное приветствие"},
        {"(?:kutib olish|uchrashuv) (?:rejim|holat)\\S*", "режим встречи"},
        {"(?:salomlash|kutib olish|salom berish)\\S*", "приветствие"},
        {"(?:qo'riqlash|qo'riq|soqchi|posbon|signalizatsiya) (?:rejim|holat)\\S*", "режим охраны"},
        {"(?:maxfiy|shaxsiy|privat) (?:rejim|holat)\\S*", "приватный режим"},
        {"(?:uxlash|uyqu) (?:rejim|holat)\\S*", "режим сна"},
        {"yarim soat\\S*", "полчаса"},
        {"(\\d+) (?:daqiqa|minut)\\S* (?:uxla|mizg'i|dam ol)\\S*", "поспать $1 минут"},
        {"(\\d+) (?:daqiqa|minut)(?:dan)? (?:keyin|so'ng) uyg'ot\\S*", "разбуди через $1 минут"},
        {"yana (\\d+) (?:daqiqa|minut)\\S*", "еще $1 минут поспать"},
        {"soat (\\d+)(?:da|ga)? uyg'ot\\S*", "разбуди в $1 часов"},
        {"(\\d+)(?:da|ga) uyg'ot\\S*", "разбуди в $1 часов"},
        {"(?:muzlatgich|sovutgich|xolodilnik)\\S* (?:eshik|eshikcha|eshigi)\\S*", "дверцу холодильника"},
        {"(?:muzlatgich|sovutgich|xolodilnik)\\S* (?:isitish|isit|qizdirish)\\S*", "холодильник на подогрев"},
        {"(?:muzlatgich|sovutgich|xolodilnik)\\S* (?:minus|manfiy) (\\d+)", "холодильник на минус $1"},
        {"(?:kamar|xavfsizlik kamari|remen)\\S* (?:eslatma|ogohlantirish)\\S*", "напоминание о ремнях"},
        {"telefon\\S* (?:eslatma|unutish|qoldirish)\\S*", "напоминание о телефоне"},
        {"tezlik (?:cheklov|chegara)\\S* (?:eslatma|ogohlantirish)\\S*", "напоминание об ограничении скорости"},
        {"(?:eslatma|eslatish)\\S*", "напоминания"},
        {"(?:mavzu|tema)\\S* (?:almashtir|o'zgartir)\\S*", "смени тему"},
        {"boshqa (?:mavzu|tema)\\S*", "смени тему"},
        {"(?:fon rasm|fon rasmi|oboi|orqa fon)\\S* (?:almashtir|o'zgartir)\\S*", "смени обои"},
        {"(?:personaj|qahramon|avatar|obraz)\\S*", "персонажа"},
        {"(?:kengaytirish|kengaytma|kengaytirilgan) (?:rejim|holat)\\S*", "режим расширения"},
        {"(?:yuvish|moyka|mashina yuvish) (?:rejim|holat)\\S*", "режим мойки"},
        {"(?:kutib olish|uchrashuv) (?:rejim|holat)\\S*", "режим встречи"},
        {"(?:tetiklik|bardamlik|uyg'oqlik|tetik) (?:rejim|holat)\\S*", "режим бодрости"},
        {"(?:bosh aylanish|ko'ngil aynish|chayqalish|ukachka)\\S*( (?:rejim|holat)\\S*)?", "режим от укачивания"},
        {"(?:pardoz|makiyaj|bo'yanish) (?:rejim|holat|xona)\\S*", "режим макияжа"},
        {"(?:dam olish|hordiq) (?:rejim|holat|xona)\\S*", "режим отдыха"},
        {"bolalar (?:rejim|holat)\\S*", "детский режим"},
        {"(?:bo'sh|erkin) (?:muloqot|suhbat)\\S*", "свободное общение"},
        {"vidjet\\S*", "виджеты"},
        {"minus bir (?:ekran|sahifa)\\S*", "минус один экран"},
        // --- connectivity / cameras
        {"(?:ulanish|kirish) nuqta\\S*", "точку доступа"},
        {"hotspot\\S*", "точку доступа"},
        {"internet\\S* (?:tarqat|ulash)\\S*", "раздай интернет"},
        {"(?:vayfay|vay-fay|wifi|wi-fi)\\S* (?:tarqat|ulash)\\S*", "раздай вайфай"},
        {"simsiz (?:zaryad|zaryadlash|quvvatlash)\\S*", "беспроводную зарядку"},
        {"(?:zaryad|quvvat)\\S* (?:tugadi|tugab qoldi|o'tirdi|bitdi)", "разрядился"},
        {"telefon\\S* (?:zaryad|zaryadla|quvvatla)\\S*", "заряди телефон"},
        {"(?:rozetka|tashqi quvvat|tashqi zaryad|quvvat berish)\\S*", "розетку"},
        {"(?:surat|rasm)(?:ga)? (?:ol|tushir)\\S*", "сфоткай"},
        {"(?:suratga|rasmga) (?:ol|tushir)\\S*", "сфоткай"},
        {"selfi\\S*", "селфи"},
        {"kamera\\S*(?:dan)? (?:surat|rasm)\\S*", "фото с камеры"},
        {"video(?:ga)? (?:ol|yoz|yozib ol|tushir)\\S*", "сними видео"},
        {"registrator\\S* (?:albom|yozuvlar|yozuvlari)\\S*", "альбом регистратора"},
        {"registrator\\S* (?:skrinshot|kadr|surat)\\S*", "скриншот с регистратора"},
        {"registrator\\S*(?:dan|da)? video\\S* (?:yoz|ol)\\S*", "запиши видео с регистратора"},
        {"registrator\\S* (?:yozish|yozuv)\\S* (?:boshla|yoq)\\S*", "начни запись регистратора"},
        {"registrator\\S* (?:to'xtat|o'chir)\\S*", "останови регистратор"},
        {"(?:aylanma|doiraviy|360|uch yuz oltmish)(?: daraja(?:li)?)? (?:ko'rinish|kamera|obzor)\\S*", "круговой обзор"},
        {"(?:panoramik|panorama) (?:ko'rinish|kamera)\\S*", "панорамный обзор"},
        {"kamera (?:uch yuz oltmish|360)\\S*", "камера триста шестьдесят"},
        {"(?:yuqoridan|tepadan) ko'rinish\\S*", "вид сверху"},
        {"kamera (?:ko'rinish|rakurs|burchag)\\S* (?:almashtir|o'zgartir)\\S*", "переключи вид камеры"},
        {"(?:kamera|360)\\S* o'chir\\S*", "выключи камеру 360"},
        // --- autopilot
        {"(?:adaptiv|moslashuvchan) kruiz\\S*", "адаптивный круиз"},
        {"kruiz\\S* (\\d+)(?:ga)?", "круиз на $1"},
        {"(?:masofa|oraliq)\\S* (\\d+)(?:ga)?", "дистанция $1"},
        {"(?:masofa|oraliq)\\S* (?:kattaroq|ko'proq|uzoqroq|oshir|ko'paytir|uzaytir)\\S*", "дистанцию побольше"},
        {"(?:masofa|oraliq)\\S* (?:kichikroq|kamroq|yaqinroq|kamaytir|qisqartir)\\S*", "сократи дистанцию"},
        {"(?:yo'lak|polosa|qator)\\S* (?:chapga|chap tomonga)\\S*", "перестройся влево"},
        {"(?:yo'lak|polosa|qator)\\S* (?:o'ngga|o'ng tomonga)\\S*", "перестройся вправо"},
        {"chapga (?:o't|o'tib ol|qayta tuzil)\\S*", "перестройся влево"},
        {"o'ngga (?:o't|o'tib ol|qayta tuzil)\\S*", "перестройся вправо"},
        {"(?:yo'lak|polosa|qator)\\S* (?:saqla|ushlab tur|tut)\\S*", "держи полосу"},
        {"(\\d+)(?:-| )?(?:chi|nchi|inchi) (?:yo'lak|polosa|qator)\\S*(?:da|dan)? (?:yur|hayda|bor)\\S*", "езжай по $1-chi полосе"},
        {"(?:oldingi|oldindagi) (?:mashina|avtomobil)\\S* (?:orqasidan|ortidan|ketidan) (?:yur|hayda|bor|ergash)\\S*", "едь за той машиной"},
        {"(?:o'sha|u|anavi|bu) (?:mashina|avtomobil)\\S* (?:orqasidan|ortidan|ketidan) (?:yur|hayda|bor|ergash)\\S*", "едь за той машиной"},
        {"(?:o'sha|u|anavi|bu|oldingi) (?:mashina|avtomobil)\\S* (?:orqasidan|ortidan|ketidan) (?:yurma|haydama|borma|ergashma)\\S*", "не едь за той машиной"},
        {"(?:ergashish|kuzatish|ergashuv)\\S* (?:bekor qil|to'xtat)\\S*", "отмени следование"},
        {"(?:quvib o't|o'zib ket|o'zib o't)\\S*", "обгони"},
        {"tezlik (?:cheklov|chegara)\\S* (?:eslatma|ogohlantirish)\\S*", "напоминание об ограничении скорости"},
        {"(?:parkovka|to'xtash|to'xtash joyi)\\S* (?:eslab qol|saqla|yodda tut)\\S*", "запомни парковку"},
        {"(?:parkovka|to'xtash)\\S* (?:marshrut|yo'l)\\S* (?:o'chir|unut)\\S*", "удали маршрут парковки"},
        {"(?:parkovka|to'xtash)\\S* (?:pauza|to'xtat|kut)\\S*", "пауза парковки"},
        {"(?:parkovka|to'xtash)\\S* davom et\\S*", "продолжи парковку"},
        {"(?:parkovka|to'xtash joyi)(?:dan)? chiq\\S*", "выйди с парковки"},
        {"(?:parkovka|to'xtash joyi)(?:ga)? (?:kir|kirib ol|kiring)\\S*", "заезжай на парковку"},
        {"(?:o'zing|o'zi|avtomatik) (?:to'xta|park)\\S*", "припаркуйся"},
        {"^(?:to'xtab qo'y|park qil|parkovka qil|to'xta)\\S*$", "паркуйся"},
        {"^(?:mashinani|avtomobilni)? ?(?:to'xtatib qo'y|park qil|parkovka qil)\\S*$", "припаркуйся"},
        {"(?:mashina|avtomobil)\\S* (?:chaqir|yaqinga chaqir)\\S*", "подзови машину"},
        {"navigatsiya (?:pilot|yordamchi)\\S*", "навигационный пилот"},
        {"(?:avtomatik|avto) (?:haydash|boshqaruv)\\S*", "автопилот"},
        // --- vehicle info / feedback
        {"(?:qancha|qanday|nechchi) (?:zaryad|quvvat|batareya)\\S*", "сколько заряда"},
        {"(?:zaryad|quvvat|batareya)\\S* (?:qancha|nechchi|qanday|darajasi qanday|holati qanday)\\S*", "сколько заряда"},
        {"(?:zaryad|quvvat|batareya)\\S* (?:ko'rsat|ayt)\\S*", "покажи заряд"},
        {"(?:zaryad|quvvat|batareya)\\S* (?:qoldi|qolgan)\\S*", "сколько заряда осталось"},
        {"(?:batareya|akkumulyator) (?:qancha|necha foiz|necha protsent)\\S*", "на сколько заряжена батарея"},
        {"(?:shina|g'ildirak|balon)\\S* bosim\\S*", "давление в шинах"},
        {"(?:probeg|yurgan masofa|yurilgan masofa|umumiy masofa)\\S*", "пробег"},
        {"salon(?:dagi|da)? havo\\S*", "воздух в салоне"},
        {"havo sifat\\S*", "воздух в салоне"},
        {"shikoyat\\S*", "хочу пожаловаться"},
        {"(?:fikr|taklif) (?:bildir|yubor)\\S*", "хочу пожаловаться"},
        // --- smart home
        {"uy(?:da|dagi|imda|imdagi)\\S* (?:konditsioner|klimat)\\S* (\\d+)", "кондиционер дома на $1"},
        {"(?:konditsioner|klimat)\\S* uy(?:da|dagi)\\S* (\\d+)", "кондиционер дома на $1"},
        {"uy(?:da|dagi|imda|imdagi)\\S* parda\\S*", "дома шторы"},
        {"uy(?:da|dagi|imda|imdagi)\\S*", "дома"},
        {"changyutgich\\S*", "пылесос"},
        {"robot (?:changyutgich|tozalagich)\\S*", "пылесос"},
        {"(?:namlik yutgich|quritgich|nam yutgich)\\S*", "осушитель"},
        // --- UI
        {"(?:sozlama|sozlamalar)\\S*", "настройки"},
        {"(?:galereya|rasmlar|suratlar)\\S*", "галерею"},
        {"(?:ilova|ilovalar) (?:do'kon|market)\\S*", "магазин приложений"},
        {"(?:brauzer|internet)\\S*", "браузер"},
        {"(?:kalendar|taqvim)\\S*", "календарь"},
        {"(?:ssenariy|stsenariy|senariy|ssenariylar)\\S*", "сценарии"},
        {"(?:yordamchi|assistent)\\S*(?:dan)? (?:chiq|yop)\\S*", "выйди ассистент"},
        {"^(?:ket|yo'qol|tinch qo'y|bezovta qilma|jo'na|meni tinch qo'y)\\S*$", "отстань"},
        {"^(?:yopil|o'zingni yop)\\S*$", "закройся"},
        {"^(?:tasdiqlayman|roziman|ha|xo'p|mayli|bo'pti|albatta)$", "да"},
        {"^(?:yo'q|kerakmas|bekor|bekor qil|shart emas)$", "нет"},
        {"^(?:orqaga|qaytish|qayt)$", "назад"},
        {"^(?:keyingisi|keyingi|davom|davomi)$", "дальше"},
        {"^(?:oldingisi|oldingi|avvalgisi)$", "предыдущий"},
        {"^(?:hammasini|barchasini|hamma narsani) yop\\S*$", "закрой все"},
        {"^(?:hammasini|barchasini|hamma narsani) och\\S*$", "открой все"},
        {"(\\d+)(?:-| )?(?:chi|nchi|inchi)(?:sini|si)? (?:tanla|ol)\\S*", "выбери $1-chi"},
        {"(\\d+)(?:-| )?(?:chi|nchi|inchi) (?:sahifa|bet)\\S*", "страница $1-chi"},
        {"(\\d+)(?:-| )?(?:chi|nchi|inchi)(?:sini|si)? (?:o'chir|olib tashla)\\S*", "удали $1-chi"},
        {"(\\d+)(?:-| )?(?:chi|nchi|inchi)(?:siga|sisiga|siga)? (?:qo'ng'iroq|telefon)\\S*( qil\\S*)?", "позвони $1-chi"},
        {"(\\d+)(?:-| )?(?:chi|nchi|inchi)(?:sini|si)? (?:ter|chaqir)\\S*", "набери $1-chi"},
        {"(\\d+)(?:-| )?(?:chi|nchi|inchi)(?:sini|si)? (?:qo'y|yoq|chal)\\S*", "включи $1-chi"},
        {"^(\\d+)(?:-| )?(?:chi|nchi|inchi)(?:si|sini)?$", "$1-chi"},
    };

    // ------------------------------------------------------------------ word-level dictionary
    // Uzbek STEM → Russian word(s). Longest stem that prefixes the token wins; the remainder must be
    // built from Uzbek suffixes (see SUFFIX). Sorted by length at class init.
    private static final String[][] STEMS = {
        {"och", "открой"}, {"ochib", "открой"}, {"ochil", "открой"}, {"yop", "закрой"}, {"yopib", "закрой"},
        {"yopil", "закрой"}, {"yoq", "включи"}, {"yoqib", "включи"}, {"yoqil", "включи"}, {"o'chir", "выключи"},
        {"o'chirib", "выключи"}, {"so'ndir", "погаси"}, {"tushir", "опусти"}, {"tushirib", "опусти"},
        {"pastga", "вниз"}, {"pastla", "опусти"}, {"ko'tar", "подними"}, {"ko'tarib", "подними"},
        {"yuqoriga", "вверх"}, {"tepaga", "вверх"}, {"qo'y", "поставь"}, {"qo'yib", "поставь"}, {"qil", "сделай"},
        {"qilib", "сделай"}, {"o'rnat", "поставь"}, {"sozla", "настрой"}, {"ber", ""}, {"bering", ""},
        {"boshla", "запусти"}, {"ishga tushir", "запусти"}, {"yur", "поехали"}, {"to'xtat", "останови"},
        {"to'xta", "стоп"}, {"bekor qil", "отмени"}, {"bekor", "отмени"}, {"almashtir", "смени переключи"},
        {"o'zgartir", "смени"}, {"o'tkaz", "переключи"}, {"oshir", "повысь"}, {"ko'paytir", "увеличь"},
        {"kuchaytir", "усиль"}, {"uzaytir", "длиннее"}, {"kamaytir", "понизь"}, {"pasaytir", "понизь"},
        {"susaytir", "ослабь"}, {"qisqartir", "короче"}, {"isit", "подогрей"}, {"qizdir", "подогрей"},
        {"isitish", "подогрев"}, {"isitgich", "подогрев"}, {"sovut", "охлади"}, {"sovutish", "охлаждение"},
        {"ko'rsat", "покажи"}, {"ayt", "скажи"}, {"aytib", "расскажи"}, {"tushuntir", "объясни"}, {"top", "найди"},
        {"qidir", "найди"}, {"izla", "найди"}, {"chaqir", "вызови"}, {"tanla", "выбери"}, {"saqla", "сохрани"},
        {"eslab qol", "запомни"}, {"esla", "запомни"}, {"yodda tut", "запомни"}, {"uyg'ot", "разбуди"},
        {"uxla", "поспать"}, {"uxlamoqchiman", "хочу поспать"}, {"mizg'i", "подремать"}, {"dam ol", "поспать"},
        {"yubor", "отправь"}, {"aylantir", "поверни"}, {"bur", "поверни"}, {"ag'dar", "переверни"},
        {"yig'", "сложи"}, {"buk", "сложи"}, {"yoy", "разложи"}, {"yoz", "разложи"}, {"sur", "подвинь"},
        {"tort", "потяни"}, {"art", "протри"}, {"yuv", "помой"}, {"tekisla", "выровняй"}, {"blokla", "заблокируй"},
        {"qulfla", "запри"}, {"chal", "включи"}, {"yondir", "зажги"}, {"ijro et", "включи"}, {"o'ynat", "включи"},
        {"eshit", "послушать"}, {"tingla", "послушать"}, {"ol", "возьми"}, {"pufla", "дуй"}, {"puflayapti", "дует"},
        {"esayapti", "дует"}, {"ur", "дуй"}, {"yo'nalt", "направь"}, {"yo'naltir", "направь"}, {"tarqat", "раздай"},
        {"zaryadla", "заряди"}, {"quvvatla", "заряди"}, {"yukla", "скачай"}, {"yuklab", "скачай"},
        {"takrorla", "повтор"}, {"aralashtir", "перемешай"}, {"pauza", "пауза"}, {"to'g'rila", "поправь"},
        {"bos", "нажми"}, {"muzlat", "заморозь"}, {"quri", "просуши"}, {"qurit", "просуши"},
        {"shamollat", "проветри"}, {"tozala", "очисти"}, {"tozalab", "очисти"}, {"ko'r", "посмотреть"},
        {"tomosha qil", "посмотреть"}, {"qara", "посмотри"}, {"kir", "заезжай"}, {"chiq", "выйди"},
        {"yopish", "закрытие"}, {"ochish", "открытие"}, {"saqlash", "сохранения"}, {"tejash", "сохранения"},
        {"hayda", "едь"}, {"gapir", "говори"}, {"gapirma", "замолчи"}, {"kuyla", "спой"}, {"sevaman", "люблю"},
        {"yoqadi", "нравится"}, {"yoqdi", "нравится"}, {"yoqmadi", "не нравится"}, {"yoqmaydi", "не нравится"},
        {"buzil", "сломался"}, {"buzildi", "сломался"}, {"ishlamayapti", "не работает"},
        {"ishlamaydi", "не работает"}, {"kuyib ketdi", "перегорел"}, {"kuydi", "перегорел"}, {"unutdim", "забыл"},
        {"qoldirdim", "оставил"}, {"yo'qotdim", "потерял"}, {"terlayapti", "запотели"}, {"terlaydi", "потеют"},
        {"bug'lan", "запотели"}, {"o'rgandin", "научился"}, {"o'rgangan", "научился"}, {"qanday", "как "},
        {"qanaqa", "какой"}, {"qaysi", "какой"}, {"nega", "почему"}, {"nima uchun", "почему"}, {"nimaga", "почему"},
        {"nima", "что"}, {"nimadir", "что-то"}, {"qayer", "где "}, {"qayerda", "где "}, {"qayerga", "куда "},
        {"qancha", "сколько"}, {"nechta", "сколько"}, {"necha", "сколько"}, {"qachon", "когда "}, {"kim", "кто "},
        {"kimniki", "чей "}, {"mumkinmi", "можно ли"}, {"kerakmi", "стоит ли"}, {"xavfli", "опасен"},
        {"xavflimi", "опасен ли"}, {"kecha", "вчера"}, {"menda", "у меня"}, {"mening", "у меня"},
        {"menikida", "у меня"}, {"bizda", "у нас"}, {"qo'shni", "у соседа"}, {"qo'shnim", "у соседа"},
        {"haqida", "расскажи про"}, {"gaplash", "поговорим"}, {"suhbat", "поговорим"}, {"maslahat", "посоветуй"},
        {"tavsiya", "порекомендуй"}, {"deb o'ylaysan", "как думаешь"}, {"fikring", "твоё мнение"},
        {"nima deb", "как думаешь"}, {"bilasan", "что ты знаешь"}, {"qila olasan", "что умеешь"},
        {"qo'lingdan", "что умеешь"}, {"narx", "стоит"}, {"narxi", "сколько стоит"}, {"turadi", "стоит"},
        {"tarjima", "переведи"}, {"latifa", "анекдот"}, {"hazil", "анекдот"}, {"ertak", "сказку"},
        {"ob-havo", "погода"}, {"ob havo", "погода"}, {"ismim", "меня зовут"}, {"rahmat", "спасибо"},
        {"salom", "привет"}, {"zerikdim", "мне скучно"}, {"yaxshimi", "как дела"}, {"qalaysan", "как дела"},
        {"vaqt", "времени"}, {"soat necha", "сколько времени"}, {"konditsioner", "кондиционер"},
        {"kondey", "кондей"}, {"klimat", "климат"}, {"harorat", "температуру"}, {"temperatura", "температуру"},
        {"daraja", "градус"}, {"gradus", "градус"}, {"shamol", "обдув"}, {"puflash", "обдув"},
        {"ventilyator", "вентилятор"}, {"ventilator", "вентилятор"}, {"havo", "воздух"}, {"yuz", "лицо"},
        {"bet", "лицо"}, {"yuzim", "лицо"}, {"oyoq", "ноги"}, {"oyog'im", "ноги"}, {"tana", "тело"},
        {"badan", "тело"}, {"ko'krak", "грудь"}, {"aft", "морду"}, {"pechka", "печку"}, {"pech", "печку"},
        {"otoplenie", "отопление"}, {"aromat", "аромат"}, {"aromatizator", "ароматизатор"}, {"hid", "запах"},
        {"xushbo'y", "аромат"}, {"parfyum", "парфюм"}, {"resirkulyatsiya", "рециркуляцию"},
        {"retsirkulyatsiya", "рециркуляцию"}, {"sirkulyatsiya", "циркуляцию"}, {"aylanish", "циркуляцию"},
        {"ionizatsiya", "ионизацию"}, {"salon", "салон"}, {"kabina", "салон"}, {"mashina", "машину"},
        {"avtomobil", "автомобиль"}, {"batareya", "батарею"}, {"akkumulyator", "аккумулятор"}, {"issiq", "тепло"},
        {"issiqroq", "теплее"}, {"sovuq", "холодно"}, {"sovuqroq", "холоднее"}, {"salqin", "прохладно"},
        {"salqinroq", "прохладнее"}, {"kuchli", "сильнее"}, {"kuchliroq", "сильнее"}, {"kuchsiz", "слабее"},
        {"kuchsizroq", "слабее"}, {"sust", "слабее"}, {"sustroq", "слабее"}, {"sekin", "медленнее"},
        {"sekinroq", "медленнее"}, {"tez", "быстро"}, {"tezroq", "быстрее"}, {"zudlik bilan", "срочно"},
        {"darhol", "срочно"}, {"maksimum", "на максимум"}, {"maksimal", "максимальн"}, {"maksimalga", "на максимум"},
        {"minimum", "на минимум"}, {"minimal", "минимальн"}, {"minimalga", "на минимум"}, {"to'liq", "полностью"},
        {"to'la", "на полную"}, {"butunlay", "полностью"}, {"oxirigacha", "на полную"}, {"yarim", "наполовину"},
        {"yarmigacha", "наполовину"}, {"uchdan bir", "на треть"}, {"chorak", "на четверть"}, {"foiz", "процентов"},
        {"protsent", "процентов"}, {"avtomatik", "автоматический"}, {"avto", "авто"}, {"rejim", "режим"},
        {"holat", "режим"}, {"sinxron", "синхронизируй"}, {"oyna", "окно"}, {"oynalar", "окна"}, {"deraza", "окно"},
        {"derazalar", "окна"}, {"shisha", "стекло"}, {"lyuk", "люк"}, {"panorama", "панораму"},
        {"panoram", "панорам"}, {"tom", "крышу"}, {"parda", "шторку"}, {"pardalar", "шторки"}, {"jalyuzi", "шторку"},
        {"eshik", "дверь"}, {"eshiklar", "двери"}, {"qulf", "замок"}, {"blok", "блокировку"},
        {"bloklash", "блокировку"}, {"yukxona", "багажник"}, {"bagajnik", "багажник"}, {"kuzov", "кузов"},
        {"bort", "борт"}, {"kapot", "капот"}, {"qopqoq", "крышку"}, {"qopqog'i", "крышку"}, {"bak", "бак"},
        {"benzin", "бензин"}, {"yoqilg'i", "заправка"}, {"quyish", "заправиться"}, {"quy", "залей"},
        {"yonilg'i", "топливо"}, {"zaryad", "заряд"}, {"zaryadlash", "зарядку"}, {"quvvat", "заряд"},
        {"frunk", "фрунк"}, {"yuqori", "верхний"}, {"ustki", "верхний"}, {"pastki", "нижний"}, {"quyi", "нижний"},
        {"haydovchi", "водителя"}, {"yo'lovchi", "пассажиру"}, {"orqam", "спину"}, {"balandlig", "громкость"},
        {"balandlik", "громкость"}, {"mos", "подходящая"}, {"vino", "вина"}, {"og'ri", "у меня заболела"},
        {"og'rib", "у меня заболела"}, {"og'riyapti", "у меня заболела"}, {"telefonim", "у меня телефон"},
        {"tugadi", "закончился"}, {"esyapti", "дует"}, {"esmoqda", "дует"}, {"qisqaroq", "короче"},
        {"uzunroq", "длиннее"}, {"ko'cha", "на улице"}, {"orqada", "сзади"}, {"oldinda", "спереди"},
        {"o't", "переключись"}, {"o'tib", "переключись"}, {"og'iz", "в рот"}, {"lyukcha", "лючок"},
        {"kontrol", "контроль"}, {"orqa", "заднее"}, {"orqadagi", "заднее"}, {"orqaga", "назад"}, {"ortga", "назад"},
        {"orqasidagi", "сзади"}, {"old", "переднее"}, {"oldingi", "переднее"}, {"oldindagi", "переднее"},
        {"oldinga", "вперед"}, {"oldin", "вперед"}, {"o'ng", "правое"}, {"o'ngdagi", "правое"}, {"o'ngga", "вправо"},
        {"o'ngroq", "правее"}, {"chap", "левое"}, {"chapdagi", "левое"}, {"chapga", "влево"}, {"chaproq", "левее"},
        {"hamma", "все"}, {"barcha", "все"}, {"hammasi", "все"}, {"barchasi", "все"}, {"barchasini", "все"},
        {"hammasini", "все"}, {"o'rta", "среднее"}, {"o'rtadagi", "среднее"}, {"ikkala", "оба"},
        {"ikkalasini", "оба"}, {"har", "везде"}, {"joyda", ""}, {"yerda", ""}, {"ichki", "внутренний"},
        {"tashqi", "внешний"}, {"ichida", "в"}, {"ichidagi", "в"}, {"o'rindiq", "сиденье"},
        {"o'rindiqlar", "сиденья"}, {"kreslo", "кресло"}, {"joy", "место"}, {"shamollatish", "вентиляцию"},
        {"ventilyatsiya", "вентиляцию"}, {"massaj", "массаж"}, {"uqala", "массируй"}, {"bel", "поясницу"},
        {"belim", "поясницу"}, {"suyanchiq", "спинку"}, {"suyanch", "спинку"}, {"yostiq", "подушку"},
        {"yostig'", "подушку"}, {"suyanchig'", "спинку"}, {"yorqinlig'", "яркость"},
        {"avtoyorqinlig'", "автояркость"}, {"avtoyorqinlik", "автояркость"}, {"eshig'", "дверь"},
        {"chirog'", "свет"}, {"vaznsizlik", "невесомость"}, {"gravitatsiya", "гравитацию"}, {"karavot", "кровать"},
        {"pozitsiya", "позицию"}, {"tayanch", "опору"}, {"tirgak", "опору"}, {"avariya", "аварийку"},
        {"chiroq", "свет"}, {"chirog'i", "свет"}, {"chiroqlar", "свет"}, {"chiroqni", "свет"}, {"lampa", "лампу"},
        {"lampochka", "лампочку"}, {"yorug'lik", "свет"}, {"yoritish", "освещение"}, {"yoritgich", "освещение"},
        {"nur", "свет"}, {"fara", "фары"}, {"faralar", "фары"}, {"podsvetka", "подсветку"}, {"ambient", "подсветку"},
        {"neon", "неоновую подсветку"}, {"rang", "цвет"}, {"rangi", "цвет"}, {"ko'k", "синий"}, {"moviy", "синий"},
        {"qizil", "красный"}, {"yashil", "зеленый"}, {"oq", "белый"}, {"sariq", "желтый"},
        {"binafsha", "фиолетовый"}, {"siyohrang", "фиолетовый"}, {"to'q sariq", "оранжевый"},
        {"apelsin", "оранжевый"}, {"pushti", "розовый"}, {"qora", "черный"}, {"yorqin", "ярче"},
        {"yorqinroq", "ярче"}, {"xira", "темнее"}, {"xiraroq", "темнее"}, {"qorong'i", "темнее"},
        {"qorong'iroq", "темнее"}, {"yorqinlik", "яркость"}, {"yorqinlig", "яркость"},
        {"avtoyorqinlig", "автояркость"}, {"effekt", "эффект"}, {"jilo", "эффект"}, {"shou", "шоу"},
        {"gradient", "градиент"}, {"ko'zgu", "зеркало"}, {"ko'zgular", "зеркала"}, {"rul", "руль"},
        {"tozalagich", "дворники"}, {"dvornik", "дворники"}, {"cho'tka", "щетки"}, {"shyotka", "щетки"},
        {"suv", "воду"}, {"sepgich", "омыватель"}, {"sezgirlik", "чувствительность"},
        {"sezgirlig", "чувствительность"}, {"datchik", "датчик"}, {"sensor", "датчик"}, {"yomg'ir", "дождь"},
        {"yengil", "легче"}, {"og'ir", "тяжелый"}, {"og'irroq", "тяжелее"}, {"proyeksiya", "проекцию"},
        {"proektsiya", "проекцию"}, {"proyektsiya", "проекцию"}, {"hud", "хад"}, {"xad", "хад"}, {"ekran", "экран"},
        {"displey", "экран"}, {"shrift", "шрифт"}, {"katta", "больше"}, {"kattaroq", "больше"}, {"kichik", "меньше"},
        {"kichikroq", "меньше"}, {"mayda", "мельче"}, {"maydaroq", "мельче"}, {"burchak", "угол"},
        {"burchag", "угол"}, {"qiyalik", "наклон"}, {"qiyalig", "наклон"}, {"tungi", "ночной"},
        {"kunduzgi", "дневной"}, {"gorizontal", "горизонтально"}, {"vertikal", "вертикально"}, {"balandroq", "выше"},
        {"baland", "выше"}, {"pastroq", "ниже"}, {"past", "ниже"}, {"yuqoriroq", "выше"}, {"ovoz", "громкость"},
        {"tovush", "громкость"}, {"musiqa", "музыку"}, {"muzika", "музыку"}, {"qo'shiq", "песню"}, {"trek", "трек"},
        {"kompozitsiya", "композицию"}, {"radio", "радио"}, {"stansiya", "станцию"}, {"stantsiya", "станцию"},
        {"kanal", "канал"}, {"manba", "источник"}, {"blyutus", "блютус"}, {"bluetooth", "блютус"},
        {"blutuz", "блютус"}, {"usb", "юсб"}, {"matn", "текст"}, {"so'zlar", "текст"}, {"keyingi", "следующий"},
        {"oldingisi", "предыдущий"}, {"boshqa", "другой"}, {"boshqasini", "другой"}, {"yangi", "новый"},
        {"tasodifiy", "случайный"}, {"tartib", "порядок"}, {"soniya", "секунд"}, {"sekund", "секунд"},
        {"daqiqa", "минут"}, {"minut", "минут"}, {"soat", "час"}, {"boshiga", "в начало"}, {"boshidan", "сначала"},
        {"qaytadan", "заново"}, {"karaoke", "караоке"}, {"sifat", "качество"}, {"tezlik", "скорость"},
        {"tezlig", "скорость"}, {"yordamchi", "ассистент"}, {"assistent", "ассистент"}, {"golos", "голос"},
        {"yandeks", "яндекс"}, {"yandex", "яндекс"}, {"vayfay", "вайфай"}, {"vay-fay", "вайфай"}, {"wifi", "вайфай"}, {"wi-fi", "вайфай"},
        {"internet", "интернет"}, {"telefon", "телефон"}, {"raqam", "номер"}, {"kontakt", "контакты"},
        {"kontaktlar", "контакты"}, {"kamera", "камера"}, {"video", "видео"}, {"surat", "фото"}, {"rasm", "фото"},
        {"registrator", "регистратор"}, {"videoregistrator", "регистратор"}, {"albom", "альбом"},
        {"yozuv", "запись"}, {"yozish", "запись"}, {"ko'rinish", "вид"}, {"obzor", "обзор"}, {"sport", "спорт"},
        {"eko", "эко"}, {"ekonom", "эконом"}, {"tejamkor", "эконом"}, {"komfort", "комфорт"},
        {"qulay", "комфортный"}, {"standart", "стандартный"}, {"oddiy", "обычный"}, {"odatiy", "обычный"},
        {"qish", "зимний"}, {"qishki", "зимний"}, {"qor", "снег"}, {"kruiz", "круиз"}, {"avtopilot", "автопилот"},
        {"masofa", "дистанцию"}, {"oraliq", "дистанцию"}, {"yo'lak", "полосу"}, {"polosa", "полосу"},
        {"parkovka", "парковку"}, {"osma", "подвеску"}, {"podveska", "подвеску"}, {"amortizator", "подвеску"},
        {"klirens", "клиренс"}, {"yumshoq", "мягче"}, {"yumshoqroq", "мягче"}, {"qattiq", "жестче"},
        {"qattiqroq", "жестче"}, {"elektr", "электро"}, {"gibrid", "гибрид"}, {"muzlatgich", "холодильник"},
        {"sovutgich", "холодильник"}, {"xolodilnik", "холодильник"}, {"minus", "минус"}, {"manfiy", "минус"},
        {"nol", "ноль"}, {"kino", "кино"}, {"film", "фильм"}, {"kemping", "кемпинг"}, {"lager", "лагерь"},
        {"qo'riqlash", "охрану"}, {"qo'riq", "охрану"}, {"signalizatsiya", "сигнализацию"}, {"maxfiy", "приватный"},
        {"shaxsiy", "приватный"}, {"privat", "приватный"}, {"mavzu", "тему"}, {"tema", "тему"}, {"kamar", "ремень"},
        {"eslatma", "напоминание"}, {"ayol", "женский"}, {"erkak", "мужской"}, {"uy", "дом"}, {"ish", "работу"},
        {"aeroport", "аэропорт"}, {"markaz", "центр"}, {"vokzal", "вокзал"}, {"navigatsiya", "навигация"},
        {"navigator", "навигатор"}, {"xarita", "карту"}, {"marshrut", "маршрут"}, {"tirbandlik", "пробки"},
        {"probka", "пробки"}, {"taksi", "такси"}, {"shoxobcha", "заправку"}, {"zapravka", "заправку"},
        {"to'xtash", "парковку"}, {"kofe", "кофе"}, {"ovqat", "поесть"}, {"hojatxona", "туалет"},
        {"tualet", "туалет"}, {"dorixona", "аптеку"}, {"kasalxona", "больницу"}, {"shifoxona", "больницу"},
        {"supermarket", "супермаркет"}, {"mehmonxona", "отель"}, {"otel", "отель"}, {"moyka", "мойку"},
        {"yaqin", "рядом"}, {"yaqinroq", "придвинь поближе"}, {"yaqindagi", "ближайшая"}, {"uzoq", "далеко"},
        {"uzoqmi", "далеко еще"}, {"ko'p", "долго"}, {"ko'pmi", "долго еще"}, {"qoldi", "осталось"},
        {"yetamiz", "приедем"}, {"boramiz", "едем"}, {"ketamiz", "поехали"}, {"ketdik", "поехали"},
        {"olib bor", "отвези"}, {"yetkaz", "отвези"}, {"kilometr", "километров"}, {"km", "км"},
        {"peshtaxta", "бардачок"}, {"bardachok", "бардачок"}, {"sigaret", "прикури"}, {"audiokitob", "аудиокнигу"},
        {"kitob", "книгу"}, {"ilova", "приложение"}, {"sahifa", "страницу"}, {"sevimli", "избранное"},
        {"sevimlilar", "избранное"}, {"yuk", "погрузка"}, {"bugun", "сегодня"}, {"ertaga", "завтра"},
        {"kun", "день"}, {"plyus", "плюс"}, {"sir", "секрет"}, {"haqiqat", "правду"}, {"yolg'on", "ложь"},
        {"oy", "луна"}, {"ota-ona", "родителям"}, {"ota-onam", "родителям"}, {"keyinroq", "позже"},
        {"ko'z", "взгляд"}, {"layk", "лайк"}, {"belgila", "поставь"}, {"iltimos", "пожалуйста"},
        {"marhamat", "пожалуйста"}, {"menga", ""}, {"meni", "меня"}, {"men", "я"}, {"biz", "мы"}, {"sen", "ты"},
        {"siz", "вы"}, {"u", "он"}, {"bu", "эта"}, {"shu", "эта"}, {"o'sha", "та"}, {"va", "и"}, {"ham", "и"},
        {"bilan", "с"}, {"uchun", "для"}, {"juda", "очень"}, {"biroz", "немного"}, {"ozgina", "немного"},
        {"sal", "немного"}, {"picha", "немного"}, {"yana", "еще"}, {"hozir", "сейчас"}, {"tezda", "быстро"},
        {"kerak", "надо"}, {"xohlayman", "хочу"}, {"istayman", "хочу"}, {"xohlaymiz", "хотим"},
        {"eshiting", "слушай"}, {"qani", "давай"}, {"keling", "давай"}, {"deydi", "говорит"}, {"dedi", "сказал"},
        {"ha", "да"}, {"xo'p", "да"},
    };

    private static final String SUFFIX =
        "(?:lar|ning|niki|ni|ga|ka|qa|da|dan|dagi|im|imiz|ingiz|ing|iz|i|si|miz|m|roq|cha|gina|ib|ip|gan|kan|qan"
        + "|sin|ay|aylik|yin|di|ti|yapti|moqda|masin|mang|ma|mas|may|maydi|mayapti|ish|sh|il|in|ir|dir|tir|gich"
        + "|chi|lik|siz|mi|moqchi|man|san|u|a|o|e|y|n)*";
    private static final Pattern SUFFIX_RE = Pattern.compile("^" + SUFFIX + "$");
    private static final Pattern NEG_RE = Pattern.compile("^(.+?)(ma|mang|masin|maylik|mayman|maymiz|maysiz|maydi|mayapti|mayotir|magan)$");

    private static String[][] stemsSorted;
    private static List<Object[]> phrasesCompiled;

    private static synchronized void init() {
        if (stemsSorted != null) return;
        String[][] s = STEMS.clone();
        Arrays.sort(s, new Comparator<String[]>() {
            public int compare(String[] a, String[] b) { return b[0].length() - a[0].length(); }
        });
        List<Object[]> p = new ArrayList<>();
        for (String[] r : PHRASES) p.add(new Object[]{ Pattern.compile(r[0]), r[1] });
        phrasesCompiled = p;
        stemsSorted = s;
    }

    // Uzbek numerals (units, teens are compositional: "o'n ikki" = 12)
    private static final String[][] NUMS = {
        {"nol", "0"}, {"bir", "1"}, {"ikki", "2"}, {"uch", "3"}, {"to'rt", "4"}, {"besh", "5"}, {"olti", "6"},
        {"yetti", "7"}, {"sakkiz", "8"}, {"to'qqiz", "9"}, {"o'n", "10"}, {"yigirma", "20"}, {"o'ttiz", "30"},
        {"qirq", "40"}, {"ellik", "50"}, {"oltmish", "60"}, {"yetmish", "70"}, {"sakson", "80"}, {"to'qson", "90"},
        {"yuz", "100"},
    };
    private static final String[] ORD = {"", "первый", "второй", "третий", "четвертый", "пятый", "шестой", "седьмой",
        "восьмой", "девятый", "десятый"};

    /** Digits for a numeral token (with an optional Uzbek suffix), -1 if not a numeral. */
    private static int numOf(String w) {
        for (String[] n : NUMS) {
            if (w.equals(n[0])) return Integer.parseInt(n[1]);
            if (w.startsWith(n[0]) && SUFFIX_RE.matcher(w.substring(n[0].length())).matches()
                && !w.startsWith(n[0] + "inchi") && !w.startsWith(n[0] + "nchi")) {
                // "yuz" (100) vs "yuz" (face): a bare "yuz" is a number only next to another numeral —
                // handled by the caller; here plain prefix match
                return Integer.parseInt(n[1]);
            }
        }
        return -1;
    }

    private static int ordOf(String w) {
        for (String[] n : NUMS) {
            if (w.startsWith(n[0] + "inchi") || w.startsWith(n[0] + "nchi")) return Integer.parseInt(n[1]);
        }
        return -1;
    }

    /** Uzbek → Russian keyword phrase. Never null; "" for empty input. */
    static String uz2ru(String t) {
        if (t == null) return "";
        init();
        String s = t.toLowerCase().replace('ʻ', '\'').replace('ʼ', '\'').replace('’', '\'').replace('‘', '\'')
            .replace('`', '\'').replace('´', '\'').replaceAll("(?<=\\d)[.,](?=\\d)", ".").replaceAll("(?<!\\d)[.,]|[.,](?!\\d)|[!?;:]", " ")
            .replaceAll("(?<=[a-z'])-(?=[a-z])", " ").replaceAll("\\s+", " ").trim()
            .replace("uchdan bir", "на треть").replace("to'rtdan bir", "на четверть");
        if (s.isEmpty()) return "";
        // 1) numerals → digits (before phrase rules, which key on \d+). Tens+units combine.
        StringBuilder nb = new StringBuilder();
        String[] toks = s.split(" ");
        int pending = -1;
        for (int i = 0; i < toks.length; i++) {
            String w = toks[i];
            int ord = ordOf(w);
            if (ord > 0) { flushNum(nb, pending); pending = -1; nb.append(ord).append("-chi").append(' '); continue; }
            int v = numOf(w);
            // "yuz" = face unless it follows/precedes a numeral; "bir" alone before a noun = "a/one" is fine as 1
            if (w.startsWith("yuz") && !(pending >= 0) && !(i + 1 < toks.length && numOf(toks[i + 1]) >= 0)
                && (s.contains("pufla") || s.contains("shamol") || s.contains(" ur") || s.contains("yuzim") || s.contains("yuzga va") || s.contains("va yuz"))) v = -1;
            if (w.equals("bir") && i + 1 < toks.length && (toks[i + 1].startsWith("marta") || toks[i + 1].equals("xil")
                || toks[i + 1].startsWith("oz") || toks[i + 1].startsWith("necha"))) v = -1;
            if (v >= 0) {
                if (pending < 0) pending = v;
                else if (pending % 10 == 0 && pending >= 20 && v < 10) pending += v;
                else if (pending == 10 && v < 10) pending += v;
                else if (pending % 100 == 0 && pending >= 100 && v < 100) pending += v;
                else if (v == 100 && pending < 10) pending *= 100;
                else { flushNum(nb, pending); pending = v; }
                // carry the suffix of the numeral token ("ikkiga" → "2 ga") so phrase rules can see it
                String suf = numSuffix(w);
                if (!suf.isEmpty() && !(i + 1 < toks.length && numOf(toks[i + 1]) >= 0)) {
                    flushNum(nb, pending); pending = -1; nb.append(suf).append(' ');
                }
                continue;
            }
            flushNum(nb, pending); pending = -1;
            nb.append(w).append(' ');
        }
        flushNum(nb, pending);
        s = nb.toString().trim().replaceAll("\\s+", " ");
        s = s.replaceAll("(\\d+)-chi", "$1-chi");
        // "N ga" (dative on a numeral) → keep just N; "N daraja(ga)" handled by phrase rules
        s = s.replaceAll("(?<![\\d.])(-?\\d+(?:\\.\\d+)?) ga(?= |$)", "на $1");
        s = s.replaceAll("(\\d+) (?:ta|da|dan|ni)(?= |$)", "$1");
        s = s.replaceAll("(?<![\\d.])1 yarim daraja\\S*", "на полтора градуса");
        s = s.replaceAll("(?<![\\d.])1 yarim(?= |$)", "полтора");
        s = s.replaceAll("(\\d+)-chi (?:pog'ona|pogona|bosqich|daraja)\\S*", "на $1");
        s = s.replaceAll("(\\d+) yarim daraja\\S*", "$1 с половиной градуса");
        s = s.replaceAll("(\\d+) yarim(?= |$)", "$1 с половиной");
        s = s.replaceAll("(\\d+) (?:va|-u|u) (\\d)(?: o'ndan bir| o'ndan| ondan bir)?(?= |$)", "$1.$2");
        s = s.replaceAll("(\\d+) butun (\\d)(?= |$)", "$1.$2");
        s = s.replaceAll("yarim daraja\\S*", "на полградуса");
        s = s.replaceAll("(\\d+) foiz\\S*", "на $1 процентов");
        s = s.replaceAll("(\\d+) daraja(?:ga|gacha)(?= |$)", "на $1 градуса");
        s = s.replaceAll("(\\d+) daraja\\S*", "$1 градуса");
        s = s.replaceAll("(\\d+) (?:soniya|sekund)\\S* (?:oldinga |orqaga )?(?:o'tkaz|sur|aylantir)\\S*", "перемотай $1 секунд");
        s = s.replaceAll("(\\d+)(?:-chi)? daqiqa\\S* (?:o'tkaz|sur)\\S*", "перемотай на $1 минуту");
        s = s.replaceAll("(\\d+) (?:pog'ona|pogona|daraja|bosqich)(?:ga)?(?= |$)", "на $1");
        // 2) phrase rules
        for (Object[] r : phrasesCompiled) {
            Matcher m = ((Pattern) r[0]).matcher(s);
            if (m.find()) {
                String rep = (String) r[1];
                if (rep.equals("$0")) continue;               // keep the Uzbek text for the word pass
                s = m.replaceAll(Matcher.quoteReplacement(rep).replace("\\$1", "$1").replace("\\$2", "$2"));
                s = s.replaceAll("\\s+", " ").trim();
            }
        }
        // 3) word pass
        StringBuilder out = new StringBuilder();
        boolean mediaCtx = s.matches(".*\\b(navigatsiya|navigator|ovoz|tovush|musiqa|qo'shiq|radio|trek|gapir|kanal|stansiya|stantsiya|eshit|tingla|музык|песн|громк|звук|радио|говори)\\S*.*");
        boolean heatCtx  = s.matches(".*\\b(salon|kabina|mashina|konditsioner|klimat|batareya|akkumulyator|pechka|салон|кондиц|климат)\\S*.*");
        boolean wiperCtx = s.matches(".*\\b(tozalagich|dvornik|cho'tka|shyotka|дворник)\\S*.*");
        boolean lightCtx = s.matches(".*\\b(chiroq|chirog|yorug'lik|lampa|свет|лампу)\\S*.*")
            && !s.matches(".*\\b(ekran|displey|podsvetka|yoritish|экран|подсветк)\\S*.*");
        for (String w : s.split(" ")) {
            if (w.isEmpty()) continue;
            if (w.matches("[а-яё0-9.+-]+|[а-яё]+\\S*") || w.matches("\\d[\\d.,+-]*")) { out.append(w).append(' '); continue; }
            if (w.matches("\\d+-chi")) { int i = Integer.parseInt(w.substring(0, w.length() - 4)); out.append(i <= 10 ? ORD[i] : w).append(' '); continue; }
            String ru = mapWord(w, mediaCtx, heatCtx, wiperCtx, lightCtx);
            if (ru == null) { out.append(w).append(' '); continue; }
            if (!ru.isEmpty()) out.append(ru).append(' ');
        }
        return out.toString().trim().replaceAll("\\s+", " ");
    }

    private static void flushNum(StringBuilder nb, int pending) { if (pending >= 0) nb.append(pending).append(' '); }

    private static String numSuffix(String w) {
        for (String[] n : NUMS) if (w.startsWith(n[0]) && w.length() > n[0].length()) return w.substring(n[0].length());
        return "";
    }

    /** One Uzbek token → Russian word(s); null = unknown (kept as is, e.g. a contact name). */
    private static String mapWord(String w, boolean mediaCtx, boolean heatCtx, boolean wiperCtx, boolean lightCtx) {
        // negated imperative: stem + ma/mang… → "не <verb>" (ru2zh's guard catches the common verbs; for
        // the rest the Russian «не» + verb still never forms a command word ru2zh keys on)
        Matcher nm = NEG_RE.matcher(w);
        if (nm.matches() && !w.endsWith("massaj")) {
            String base = nm.group(1);
            String ru = lookup(base);
            String vb = base.endsWith("il") || base.endsWith("in") ? base.substring(0, base.length() - 2) : base;
            if (ru != null && (isVerb(base) || isVerb(vb))) return "не " + negForm(ru);
        }
        String ru = lookup(w);
        if (ru == null) return null;
        // context-sensitive words
        if (w.startsWith("baland") || w.startsWith("yuqoriroq")) return mediaCtx ? "громче" : "выше";
        if (w.startsWith("past") && !w.startsWith("pastki") && !w.startsWith("pastga")) return mediaCtx ? "тише" : "ниже";
        if (w.startsWith("sekin")) return mediaCtx ? "тише" : "медленнее";
        if (w.startsWith("oldingi") || w.equals("oldingisi") || w.startsWith("avvalgi")) return mediaCtx ? "предыдущая" : "переднее";
        if (w.startsWith("keyingi")) return mediaCtx ? "следующая" : "следующий";
        if (w.startsWith("isitgich")) return "обогреватель";
        if (w.startsWith("isitish")) return heatCtx ? "обогрев" : "подогрев";
        if (w.startsWith("isit") || w.startsWith("qizdir")) return heatCtx ? "прогрей" : "подогрей";
        if (w.startsWith("tezroq")) return wiperCtx ? "быстрее" : "быстрее";
        if (mediaCtx && (w.startsWith("oshir") || w.startsWith("ko'paytir") || w.startsWith("kuchaytir"))) return "громче";
        if (mediaCtx && (w.startsWith("pasaytir") || w.startsWith("kamaytir") || w.startsWith("susaytir"))) return "тише";
        if (lightCtx && (w.startsWith("xira") || w.startsWith("qorong'i") || w.startsWith("susaytir"))) return "приглуши";
        return ru;
    }

    private static boolean isVerb(String stem) {
        for (String v : new String[]{"och", "yop", "yoq", "o'chir", "tushir", "ko'tar", "qo'y", "qil", "boshla", "to'xtat",
                "almashtir", "o'zgartir", "oshir", "kamaytir", "pasaytir", "isit", "sovut", "chaqir", "hayda", "yur",
                "ber", "ol", "gapir", "yubor", "aylantir", "yig'", "yoy", "sur", "yuv", "blokla", "qulfla", "chal",
                "eshit", "tingla", "pufla", "uxla", "uyg'ot", "ket", "chiq", "kir", "bos", "top", "saqla", "esla"})
            if (stem.equals(v)) return true;
        return false;
    }

    /** Russian negative imperative for the guard: «не включай/выключай/открывай/закрывай/…». */
    private static String negForm(String ru) {
        switch (ru) {
            case "включи": return "включай";
            case "выключи": return "выключай";
            case "открой": return "открывай";
            case "закрой": return "закрывай";
            case "останови": return "выключай";
            case "едь": return "едь";
            case "поехали": return "езжай";
            default: return "включай " + ru;   // still starts with «не включай» → guarded
        }
    }

    private static String lookup(String w) {
        String r = lookup0(w);
        if (r != null) return r;
        // Uzbek final-consonant alternation before a vowel suffix: eshik→eshigi, chiroq→chirog'i, yostiq→yostig'i
        if (w.contains("g'")) { r = lookup0(w.replace("g'", "q")); if (r != null) return r; }
        int g = w.lastIndexOf('g');
        if (g > 0 && (g + 1 >= w.length() || w.charAt(g + 1) != '\'')) { r = lookup0(w.substring(0, g) + "k" + w.substring(g + 1)); if (r != null) return r; }
        return null;
    }

    private static String lookup0(String w) {
        for (String[] e : stemsSorted) {
            String k = e[0];
            if (w.equals(k)) return e[1];
            if (w.startsWith(k) && SUFFIX_RE.matcher(w.substring(k.length())).matches()) return e[1];
        }
        return null;
    }
}
