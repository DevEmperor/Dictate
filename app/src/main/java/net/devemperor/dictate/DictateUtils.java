package net.devemperor.dictate;

public class DictateUtils {

    public static double calcModelCost(String modelName, long audioTime, long inputTokens, long outputTokens) {
        switch (modelName) {
            case "whisper-1":
                return audioTime * 0.0001f;
            case "gpt-4o-mini":
                return inputTokens * 0.00000015f + outputTokens * 0.0000006f;
            case "gpt-4o":
                return inputTokens * 0.000005f + outputTokens * 0.000015f;
            case "gpt-4-turbo":
                return inputTokens * 0.00001f + outputTokens * 0.00003f;
            case "gpt-4":
                return inputTokens * 0.00003f + outputTokens * 0.00006f;
            case "gpt-3.5-turbo":
                return inputTokens * 0.0000005f + outputTokens * 0.0000015f;
            default:
                return 0;
        }
    }

    public static String translateModelName(String modelName) {
        switch (modelName) {
            case "whisper-1":
                return "Whisper";
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
            default:
                return "Unknown";
        }
    }
}
