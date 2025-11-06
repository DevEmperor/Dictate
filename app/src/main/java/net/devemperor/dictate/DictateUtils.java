package net.devemperor.dictate;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.openai.client.okhttp.OpenAIOkHttpClient;

import java.io.File;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DictateUtils {

    public static final String PROMPT_PUNCTUATION_CAPITALIZATION = "This sentence has capitalization and punctuation.";
    public static final String PROMPT_REWORDING_BE_PRECISE = "Be accurate with your output. Only output exactly what the user has asked for above. Do not add any text before or after the actual output. Output the text in the language of the instruction, unless a different language was explicitly requested.";
    private static final Map<String, String> PROMPT_PUNCTUATION_CAPITALIZATION_BY_LANGUAGE;

    static {
        Map<String, String> prompts = new HashMap<>();
        prompts.put("af", "Hierdie sin het hoofletters en punktuasie.");
        prompts.put("sq", "Kjo fjali ka shkronja të mëdha dhe pikësim.");
        prompts.put("ar", "هذه الجملة تحتوي على أحرف كبيرة وعلامات ترقيم.");
        prompts.put("hy", "Այս նախադասությունը ունի մեծատառեր և կետադրություն։");
        prompts.put("az", "Bu cümlədə böyük hərflər və durğu işarələri var.");
        prompts.put("eu", "Esaldi honek letra larriak eta puntuazioa ditu.");
        prompts.put("be", "Гэты сказ мае вялікія літары і знакі прыпынку.");
        prompts.put("bn", "এই বাক্যে বড় হাতের অক্ষর এবং যতিচিহ্ন রয়েছে।");
        prompts.put("bg", "Това изречение има главни букви и пунктуация.");
        prompts.put("yue-cn", "呢句句子有大寫字母同標點符號。");
        prompts.put("yue-hk", "呢句句子有大寫字母同標點符號。");
        prompts.put("ca", "Aquesta frase té majúscules i puntuació.");
        prompts.put("cs", "Tato věta má velká písmena a interpunkci.");
        prompts.put("da", "Denne sætning har store bogstaver og tegnsætning.");
        prompts.put("nl", "Deze zin heeft hoofdletters en interpunctie.");
        prompts.put("en", PROMPT_PUNCTUATION_CAPITALIZATION);
        prompts.put("et", "Selles lauses on suurtähed ja kirjavahemärgid.");
        prompts.put("fi", "Tässä lauseessa on isot kirjaimet ja välimerkit.");
        prompts.put("fr", "Cette phrase contient des majuscules et de la ponctuation.");
        prompts.put("gl", "Esta frase ten maiúsculas e puntuación.");
        prompts.put("de", "Dieser Satz hat Großbuchstaben und Zeichensetzung.");
        prompts.put("el", "Αυτή η πρόταση έχει κεφαλαία γράμματα και στίξη.");
        prompts.put("he", "במשפט הזה יש אותיות גדולות וסימני פיסוק.");
        prompts.put("hi", "इस वाक्य में बड़े अक्षर और विराम चिह्न हैं।");
        prompts.put("hu", "Ez a mondat nagybetűket és írásjeleket tartalmaz.");
        prompts.put("id", "Kalimat ini memiliki huruf kapital dan tanda baca.");
        prompts.put("it", "Questa frase ha lettere maiuscole e punteggiatura.");
        prompts.put("ja", "この文には大文字と句読点があります。");
        prompts.put("kk", "Бұл сөйлемде бас әріптер мен тыныс белгілері бар.");
        prompts.put("ko", "이 문장에는 대문자와 구두점이 있습니다.");
        prompts.put("lv", "Šim teikumam ir lielie burti un pieturzīmes.");
        prompts.put("lt", "Šiame sakinyje yra didžiosios raidės ir skyrybos ženklai.");
        prompts.put("mk", "Оваа реченица има големи букви и интерпункција.");
        prompts.put("zh-cn", "这句话有大写字母和标点符号。");
        prompts.put("zh-tw", "這句話有大寫字母和標點符號。");
        prompts.put("mr", "या वाक्यात मोठी अक्षरे आणि विरामचिन्हे आहेत.");
        prompts.put("ne", "यो वाक्यमा ठूला अक्षर र विराम चिन्हहरू छन्।");
        prompts.put("nn", "Denne setninga har store bokstavar og teiknsetting.");
        prompts.put("fa", "این جمله دارای حروف بزرگ و علائم نگارشی است.");
        prompts.put("pl", "To zdanie ma wielkie litery i znaki interpunkcyjne.");
        prompts.put("pt", "Esta frase tem letras maiúsculas e pontuação.");
        prompts.put("pa", "ਇਸ ਵਾਕ ਵਿੱਚ ਵੱਡੇ ਅੱਖਰ ਅਤੇ ਵਿਸ਼ਰਾਮ ਚਿੰਨ੍ਹ ਹਨ।");
        prompts.put("ro", "Această propoziție are litere mari și punctuație.");
        prompts.put("ru", "В этом предложении есть заглавные буквы и знаки препинания.");
        prompts.put("sr", "Ова реченица има велика слова и интерпункцију.");
        prompts.put("sk", "Táto veta má veľké písmená a interpunkciu.");
        prompts.put("sl", "Ta poved ima velike črke in ločila.");
        prompts.put("es", "Esta frase tiene mayúsculas y puntuación.");
        prompts.put("sw", "Sentensi hii ina herufi kubwa na alama za uakifishaji.");
        prompts.put("sv", "Denna mening har stora bokstäver och skiljetecken.");
        prompts.put("ta", "இந்த வாக்கியத்தில் பெரிய எழுத்துக்கள் மற்றும் குறியீடுகள் உள்ளன.");
        prompts.put("th", "ประโยคนี้มีตัวพิมพ์ใหญ่และเครื่องหมายวรรคตอน.");
        prompts.put("tr", "Bu cümlede büyük harfler ve noktalama işaretleri var.");
        prompts.put("uk", "У цьому реченні є великі літери та розділові знаки.");
        prompts.put("ur", "اس جملے میں بڑے حروف اور اوقاف موجود ہیں۔");
        prompts.put("vi", "Câu này có chữ hoa và dấu câu.");
        prompts.put("cy", "Mae gan y frawddeg hon lythrennau mawr ac atalnodi.");
        PROMPT_PUNCTUATION_CAPITALIZATION_BY_LANGUAGE = Collections.unmodifiableMap(prompts);
    }

    public static String getPunctuationPromptForLanguage(String languageCode) {
        if (languageCode == null || languageCode.isEmpty() || languageCode.equals("detect")) {
            return PROMPT_PUNCTUATION_CAPITALIZATION;
        }
        String normalized = languageCode.toLowerCase(Locale.ROOT);
        String prompt = PROMPT_PUNCTUATION_CAPITALIZATION_BY_LANGUAGE.get(normalized);
        if (prompt != null) return prompt;

        int separatorIndex = normalized.indexOf('-');
        if (separatorIndex > 0) {
            String baseLanguage = normalized.substring(0, separatorIndex);
            prompt = PROMPT_PUNCTUATION_CAPITALIZATION_BY_LANGUAGE.get(baseLanguage);
            if (prompt != null) return prompt;
        }

        return PROMPT_PUNCTUATION_CAPITALIZATION;
    }

    public static String getAssetLanguageSuffix() {
        Locale overrideLocale = null;
        LocaleListCompat appLocales = AppCompatDelegate.getApplicationLocales();
        if (!appLocales.isEmpty()) {
            overrideLocale = appLocales.get(0);
        }
        String language = overrideLocale != null ? overrideLocale.getLanguage() : Locale.getDefault().getLanguage();
        switch (language) {
            case "de":
                return "de";
            case "es":
                return "es";
            case "pt":
                return "pt";
            default:
                return "en";
        }
    }

    public static void applyApplicationLocale(Context context) {
        SharedPreferences sp = context.getSharedPreferences("net.devemperor.dictate", Context.MODE_PRIVATE);
        String language = sp.getString("net.devemperor.dictate.app_language", "system");
        applyApplicationLocale(language);
    }

    public static void applyApplicationLocale(String language) {
        LocaleListCompat locales;
        if (language == null || language.equals("system")) {
            locales = LocaleListCompat.getEmptyLocaleList();
        } else {
            locales = LocaleListCompat.create(new Locale(language));
        }
        LocaleListCompat current = AppCompatDelegate.getApplicationLocales();
        if (current.equals(locales)) {
            return;
        }
        AppCompatDelegate.setApplicationLocales(locales);
    }

    public static double calcModelCost(String modelName, long audioTime, long inputTokens, long outputTokens) {
        switch (modelName) {
            // OpenAI transcription models
            case "whisper-1":  // whisper-1 and gpt-4o-transcribe cost the same
            case "gpt-4o-transcribe":
                return audioTime * 0.0001f;  // 0.0001 USD per second
            case "gpt-4o-mini-transcribe":
                return audioTime * 0.00005f;

            // OpenAI rewording models
            case "o3-mini":  // o3-mini and o1-mini cost the same
            case "o1-mini":
                return inputTokens * 0.0000011f + outputTokens * 0.0000044f;
            case "gpt-5":
                return inputTokens * 0.00000125f + outputTokens * 0.00001f;
            case "gpt-5-mini":
                return inputTokens * 0.00000025f + outputTokens * 0.000002f;
            case "gpt-4o-mini":
                return inputTokens * 0.00000015f + outputTokens * 0.0000006f;
            case "gpt-4o":
                return inputTokens * 0.0000025f + outputTokens * 0.00001f;
            case "gpt-4-turbo":
                return inputTokens * 0.00001f + outputTokens * 0.00003f;
            case "gpt-4":
                return inputTokens * 0.00003f + outputTokens * 0.00006f;
            case "gpt-3.5-turbo":
                return inputTokens * 0.0000005f + outputTokens * 0.0000015f;

            // Groq transcription models
            case "whisper-large-v3-turbo":
                return audioTime * 0.000011;  // rounded up
            case "whisper-large-v3":
                return audioTime * 0.000031;  // rounded up

            // Groq rewording models
            case "llama-3.1-8b-instant":
                return inputTokens * 0.00000005 + outputTokens * 0.00000008;
            case "llama-3.3-70b-versatile":
                return inputTokens * 0.00000059 + outputTokens * 0.00000079;
            case "meta-llama/llama-guard-4-12b":
                return inputTokens * 0.00000020 + outputTokens * 0.00000020;
            case "openai/gpt-oss-120b":
                return inputTokens * 0.00000015 + outputTokens * 0.00000075;
            case "openai/gpt-oss-20b":
                return inputTokens * 0.00000010 + outputTokens * 0.00000050;

            default:
                return 0;
        }
    }

    public static String translateModelName(String modelName) {
        switch (modelName) {
            // OpenAI transcription models
            case "whisper-1":
                return "Whisper V2";
            case "gpt-4o-transcribe":
                return "GPT-4o transcribe";
            case "gpt-4o-mini-transcribe":
                return "GPT-4o mini transcribe";

            // OpenAI rewording models
            case "o3-mini":
                return "OpenAI o3 mini";
            case "o1-mini":
                return "OpenAI o1 mini";
            case "gpt-5":
                return "GPT-5";
            case "gpt-5-mini":
                return "GPT-5 mini";
            case "gpt-4o-mini":
                return "GPT-4o mini";
            case "gpt-4o":
                return "GPT-4o";
            case "gpt-4-turbo":
                return "GPT-4 Turbo";
            case "gpt-4":
                return "GPT-4";
            case "gpt-3.5-turbo":
                return "GPT-3.5 Turbo";

            // Groq transcription models
            case "whisper-large-v3-turbo":
                return "Whisper Large V3 Turbo";
            case "whisper-large-v3":
                return "Whisper Large V3";

            // Groq rewording models
            case "llama-3.1-8b-instant":
                return "LLaMA 3.1 8B Instant";
            case "llama-3.3-70b-versatile":
                return "LLaMA 3.3 70B Versatile";
            case "meta-llama/llama-guard-4-12b":
                return "LLaMA Guard 4 12B";
            case "openai/gpt-oss-120b":
                return "GPT-OSS 120B";
            case "openai/gpt-oss-20b":
                return "GPT-OSS 20B";

            // For custom models, return the model name as is
            default:
                return modelName;
        }
    }

    public static long getAudioDuration(File file) {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever()) {
            retriever.setDataSource(file.getAbsolutePath());
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            retriever.release();
            if (durationStr != null) {
                return Long.parseLong(durationStr) / 1000; // duration in seconds
            } else {
                return -1;
            }
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean isValidProxy(String proxy) {
        if (proxy == null || proxy.isEmpty()) return false;

        // Regex for general format match (http/socks5, optional user:pass, host, port)
        String regex = "^(?:(socks5|http)://)?(?:(\\w+):(\\w+)@)?([\\w.-]+):(\\d+)$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(proxy);

        if (!matcher.matches()) return false;

        String host = matcher.group(4);

        // If it looks like an IPv4 address (e.g., 192.168.0.1), we check more closely.
        if (host != null && host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")) {
            String[] parts = host.split("\\.");
            if (parts.length != 4) return false;
            for (String part : parts) {
                try {
                    int num = Integer.parseInt(part);
                    if (num < 0 || num > 255) return false;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
        }

        return true;
    }

    public static void applyProxy(OpenAIOkHttpClient.Builder clientBuilder, SharedPreferences sp) {
        String proxyInput = sp.getString("net.devemperor.dictate.proxy_host", "");
        boolean proxyEnabled = sp.getBoolean("net.devemperor.dictate.proxy_enabled", false);

        if (!proxyEnabled || proxyInput.isEmpty()) return;

        Pattern pattern = Pattern.compile("^(?:(socks5|http)://)?(?:(\\w+):(\\w+)@)?([\\w.-]+):(\\d+)$");
        Matcher matcher = pattern.matcher(proxyInput);

        if (matcher.matches()) {
            String type = matcher.group(1); // "socks5" or "http" or null
            String user = matcher.group(2); // optional
            String pass = matcher.group(3); // optional
            String host = matcher.group(4);
            int port = Integer.parseInt(matcher.group(5));

            Proxy.Type proxyType = Proxy.Type.HTTP; // Default
            if ("socks5".equalsIgnoreCase(type)) proxyType = Proxy.Type.SOCKS;

            Proxy proxy = new Proxy(proxyType, new InetSocketAddress(host, port));
            clientBuilder.proxy(proxy);

            if (user != null && pass != null) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, pass.toCharArray());
                    }
                });
            }
        }
    }

    public static String translateLanguageToEmoji(String language) {
        switch (language) {
            case "detect":
                return "✨";
            case "af":
                return "\uD83C\uDDFF\uD83C\uDDE6";
            case "sq":
                return "\uD83C\uDDE6\uD83C\uDDF1";
            case "ar":
                return "\uD83C\uDDF8\uD83C\uDDE6";
            case "hy":
                return "\uD83C\uDDE6\uD83C\uDDF2";
            case "az":
                return "\uD83C\uDDE6\uD83C\uDDFF";
            case "eu":
                return "\uD83C\uDDEA\uD83C\uDDF8";
            case "be":
                return "\uD83C\uDDE7\uD83C\uDDFE";
            case "bn":
                return "\uD83C\uDDE7\uD83C\uDDE9";
            case "bg":
                return "\uD83C\uDDE7\uD83C\uDDEC";
            case "yue-CN":
                return "\uD83C\uDDE8\uD83C\uDDF3";
            case "yue-HK":
                return "\uD83C\uDDED\uD83C\uDDF0";
            case "ca":
                return "\uD83C\uDDE6\uD83C\uDDE9";
            case "cs":
                return "\uD83C\uDDE8\uD83C\uDDFF";
            case "da":
                return "\uD83C\uDDE9\uD83C\uDDF0";
            case "nl":
                return "\uD83C\uDDF3\uD83C\uDDF1";
            case "en":
                return "\uD83C\uDDEC\uD83C\uDDE7";
            case "et":
                return "\uD83C\uDDEA\uD83C\uDDEA";
            case "fi":
                return "\uD83C\uDDEB\uD83C\uDDEE";
            case "fr":
                return "\uD83C\uDDEB\uD83C\uDDF7";
            case "gl":
                return "\uD83C\uDDEA\uD83C\uDDF8";
            case "de":
                return "\uD83C\uDDE9\uD83C\uDDEA";
            case "el":
                return "\uD83C\uDDEC\uD83C\uDDF7";
            case "he":
                return "\uD83C\uDDEE\uD83C\uDDF1";
            case "hi":
                return "\uD83C\uDDEE\uD83C\uDDF3";
            case "hu":
                return "\uD83C\uDDED\uD83C\uDDFA";
            case "id":
                return "\uD83C\uDDEE\uD83C\uDDE9";
            case "it":
                return "\uD83C\uDDEE\uD83C\uDDF9";
            case "ja":
                return "\uD83C\uDDEF\uD83C\uDDF5";
            case "kk":
                return "\uD83C\uDDF0\uD83C\uDDFF";
            case "ko":
                return "\uD83C\uDDF0\uD83C\uDDF7";
            case "lv":
                return "\uD83C\uDDF1\uD83C\uDDFB";
            case "lt":
                return "\uD83C\uDDF1\uD83C\uDDF9";
            case "mk":
                return "\uD83C\uDDF2\uD83C\uDDF0";
            case "zh-CN":
                return "\uD83C\uDDE8\uD83C\uDDF3";
            case "zh-TW":
                return "\uD83C\uDDF9\uD83C\uDDFC";
            case "mr":
                return "\uD83C\uDDEE\uD83C\uDDF3";
            case "ne":
                return "\uD83C\uDDF3\uD83C\uDDF5";
            case "nn":
                return "\uD83C\uDDF3\uD83C\uDDF4";
            case "fa":
                return "\uD83C\uDDEE\uD83C\uDDF7";
            case "pl":
                return "\uD83C\uDDF5\uD83C\uDDF1";
            case "pt":
                return "\uD83C\uDDF5\uD83C\uDDF9";
            case "pa":
                return "\uD83C\uDDEE\uD83C\uDDF3";
            case "ro":
                return "\uD83C\uDDF7\uD83C\uDDF4";
            case "ru":
                return "\uD83C\uDDF7\uD83C\uDDFA";
            case "sr":
                return "\uD83C\uDDF7\uD83C\uDDF8";
            case "sk":
                return "\uD83C\uDDF8\uD83C\uDDF0";
            case "sl":
                return "\uD83C\uDDF8\uD83C\uDDEE";
            case "es":
                return "\uD83C\uDDEA\uD83C\uDDF8";
            case "sw":
                return "\uD83C\uDDF9\uD83C\uDDFF";
            case "sv":
                return "\uD83C\uDDF8\uD83C\uDDEA";
            case "ta":
                return "\uD83C\uDDF1\uD83C\uDDF0";
            case "th":
                return "\uD83C\uDDF9\uD83C\uDDED";
            case "tr":
                return "\uD83C\uDDF9\uD83C\uDDF7";
            case "uk":
                return "\uD83C\uDDFA\uD83C\uDDE6";
            case "ur":
                return "\uD83C\uDDF5\uD83C\uDDF0";
            case "vi":
                return "\uD83C\uDDFB\uD83C\uDDF3";
            case "cy":
                return "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC77\uDB40\uDC6C\uDB40\uDC73\uDB40\uDC7F";
            default:
                return "";
        }
    }

    public static int darkenColor(int color, float amount) {
        float factor = clamp(amount);
        int alpha = Color.alpha(color);
        int red = Math.round(Color.red(color) * (1f - factor));
        int green = Math.round(Color.green(color) * (1f - factor));
        int blue = Math.round(Color.blue(color) * (1f - factor));
        return Color.argb(alpha, red, green, blue);
    }

    private static float clamp(float value) {
        return Math.max((float) 0.0, Math.min((float) 1.0, value));
    }
}
