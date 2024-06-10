package net.devemperor.dictate;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.theokanning.openai.audio.CreateTranscriptionRequest;
import com.theokanning.openai.audio.CreateTranslationRequest;
import com.theokanning.openai.audio.TranscriptionResult;
import com.theokanning.openai.audio.TranslationResult;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DictateInputMethodService extends InputMethodService {

    private Handler mainHandler;
    private Handler deleteHandler;
    private Handler recordTimeHandler;
    private Runnable deleteRunnable;
    private Runnable recordTimeRunnable;

    private long startTime;
    private boolean isDeleting = false;
    private boolean isRecording = false;
    private boolean vibrationEnabled = true;

    private MediaRecorder recorder;
    private ExecutorService apiThread;
    private File audioFile;
    private Vibrator vibrator;
    private SharedPreferences sp;

    private ConstraintLayout dictateKeyboardView;
    private MaterialButton settingsButton;
    private MaterialButton recordButton;
    private MaterialButton backspaceButton;
    private MaterialButton switchButton;
    private MaterialButton spaceButton;
    private MaterialButton enterButton;

    private ConstraintLayout infoCl;
    private TextView infoTv;
    private Button infoYesButton;
    private Button infoNoButton;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateInputView() {
        Context context = new ContextThemeWrapper(this, R.style.Theme_Dictate);

        mainHandler = new Handler(Looper.getMainLooper());
        deleteHandler = new Handler();
        recordTimeHandler = new Handler(Looper.getMainLooper());

        audioFile = new File(getFilesDir(), "audio.mp3");
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        sp = getSharedPreferences("net.devemperor.dictate", MODE_PRIVATE);
        vibrationEnabled = sp.getBoolean("net.devemperor.dictate.vibration", true);

        dictateKeyboardView = (ConstraintLayout) LayoutInflater.from(context).inflate(R.layout.activity_dictate_keyboard_view, null);

        settingsButton = dictateKeyboardView.findViewById(R.id.settings_btn);
        recordButton = dictateKeyboardView.findViewById(R.id.record_btn);
        backspaceButton = dictateKeyboardView.findViewById(R.id.backspace_btn);
        switchButton = dictateKeyboardView.findViewById(R.id.switch_btn);
        spaceButton = dictateKeyboardView.findViewById(R.id.space_btn);
        enterButton = dictateKeyboardView.findViewById(R.id.enter_btn);

        infoCl = dictateKeyboardView.findViewById(R.id.info_cl);
        infoTv = dictateKeyboardView.findViewById(R.id.info_tv);
        infoYesButton = dictateKeyboardView.findViewById(R.id.info_yes_btn);
        infoNoButton = dictateKeyboardView.findViewById(R.id.info_no_btn);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) switchButton.setEnabled(false);

        settingsButton.setOnClickListener(v -> {
            infoCl.setVisibility(View.GONE);
            openSettingsActivity();
        });

        recordButton.setOnClickListener(v -> {
            vibrate();

            infoCl.setVisibility(View.GONE);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                openSettingsActivity();
            } else if (!isRecording) {
                startRecording();
            } else {
                stopRecording();
            }
        });

        backspaceButton.setOnClickListener(v -> {
            vibrate();
            deleteOneCharacter();
        });

        backspaceButton.setOnLongClickListener(v -> {
            isDeleting = true;
            deleteRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isDeleting) {
                        deleteOneCharacter();
                        deleteHandler.postDelayed(this, 50);
                    }
                }
            };
            deleteHandler.post(deleteRunnable);
            return true;
        });

        backspaceButton.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                isDeleting = false;
                deleteHandler.removeCallbacks(deleteRunnable);
            }
            return false;
        });

        switchButton.setOnClickListener(v -> {
            vibrate();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchToPreviousInputMethod();
            }
        });

        spaceButton.setOnClickListener(v -> {
            vibrate();

            InputConnection inputConnection = getCurrentInputConnection();
            if (inputConnection != null) {
                inputConnection.commitText(" ", 1);
            }
        });

        enterButton.setOnClickListener(v -> {
            vibrate();

            InputConnection inputConnection = getCurrentInputConnection();
            if (inputConnection != null) {
                inputConnection.commitText("\n", 1);
            }
        });

        return dictateKeyboardView;
    }

    private void vibrate() {
        if (vibrationEnabled) if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
        } else {
            vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
        }
    }

    private void openSettingsActivity() {
        Intent intent = new Intent(this, DictateSettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void startRecording() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setAudioEncodingBitRate(64000);
        recorder.setAudioSamplingRate(44100);
        recorder.setOutputFile(audioFile);

        try {
            recorder.prepare();
            recorder.start();
        } catch (IOException e) {
            e.printStackTrace();  //TODO firebase crashlytics
        }

        recordButton.setText(R.string.dictate_send);
        recordButton.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_send_24));
        isRecording = true;

        startTime = System.currentTimeMillis();
        recordTimeRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsedTime = System.currentTimeMillis() - startTime;
                recordButton.setText(getString(R.string.dictate_send,
                        String.format(Locale.getDefault(), "%02d:%02d", (int) (elapsedTime / 60000), (int) (elapsedTime / 1000) % 60)));
                recordTimeHandler.postDelayed(this, 1000);
            }
        };
        recordTimeHandler.post(recordTimeRunnable);
    }

    private void stopRecording() {
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;

            if (recordTimeRunnable != null) {
                recordTimeHandler.removeCallbacks(recordTimeRunnable);
                recordTimeRunnable = null;
            }

            recordButton.setText(R.string.dictate_sending);
            recordButton.setEnabled(false);
            isRecording = false;

            OpenAiService service = new OpenAiService(sp.getString("net.devemperor.dictate.api_key", "NO_API_KEY"));

            apiThread = Executors.newSingleThreadExecutor();
            apiThread.execute(() -> {
                try {
                    String resultText;
                    double duration;
                    if (sp.getBoolean("net.devemperor.dictate.translate", false)) {
                        CreateTranslationRequest request = CreateTranslationRequest.builder().model("whisper-1").responseFormat("verbose_json").build();
                        TranslationResult result = service.createTranslation(request, audioFile);
                        resultText = result.getText();
                        duration = result.getDuration();
                    } else {
                        CreateTranscriptionRequest request = CreateTranscriptionRequest.builder().model("whisper-1").responseFormat("verbose_json").build();
                        TranscriptionResult result = service.createTranscription(request, audioFile);
                        resultText = result.getText();
                        duration = result.getDuration();
                    }
                    sp.edit().putFloat("net.devemperor.dictate.total_duration", sp.getFloat("net.devemperor.dictate.total_duration", 0) + (float) duration).apply();

                    if (sp.getBoolean("net.devemperor.dictate.translate", false)) {
                        mainHandler.post(() -> recordButton.setText(R.string.dictate_translating));
                        ChatMessage message = new ChatMessage(ChatMessageRole.USER.value(), String.format("I want you to act as a %1$s translator. " +
                                "I will speak to you in any language and you will detect the language, translate it and answer in the %1$s version of my text. " +
                                "I want you to only reply the correction, the improvements and nothing else, do not write explanations. " +
                                "My first sentence is \"%2$s\"", sp.getString("net.devemperor.dictate.translation_language", "English"), resultText));
                        ChatCompletionRequest translationRequest = ChatCompletionRequest.builder().model("gpt-4o").messages(Collections.singletonList(message)).build();
                        ChatCompletionResult translationResult = service.createChatCompletion(translationRequest);
                        resultText = translationResult.getChoices().get(0).getMessage().getContent();
                        sp.edit().putLong("net.devemperor.dictate.translation_input_tokens",
                                sp.getLong("net.devemperor.dictate.translation_input_tokens", 0) + translationResult.getUsage().getPromptTokens()).apply();
                        sp.edit().putLong("net.devemperor.dictate.translation_output_tokens",
                                sp.getLong("net.devemperor.dictate.translation_output_tokens", 0) + translationResult.getUsage().getCompletionTokens()).apply();
                    }

                    InputConnection inputConnection = getCurrentInputConnection();
                    if (inputConnection != null) {
                        if (sp.getBoolean("net.devemperor.dictate.instant_output", false)) {
                            inputConnection.commitText(resultText, 1);
                        } else {
                            for (int i = 0; i < resultText.length(); i++) {
                                char character = resultText.charAt(i);
                                mainHandler.postDelayed(() -> inputConnection.commitText(String.valueOf(character), 1), i * 20L);
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    e.printStackTrace();  //TODO firebase crashlytics
                    if (vibrationEnabled) vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));

                    mainHandler.post(() -> {
                        if (Objects.requireNonNull(e.getMessage()).contains("SocketTimeoutException")) {
                            showInfo("timeout");
                        } else if (e.getMessage().contains("API key")) {
                            showInfo("invalid_api_key");
                        } else if (e.getMessage().contains("quota")) {
                            showInfo("quota_exceeded");
                        } else {
                            showInfo("internet_error");
                        }
                    });
                }

                mainHandler.post(() -> {
                    recordButton.setText(R.string.dictate_record);
                    recordButton.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_mic_24));
                    recordButton.setEnabled(true);
                });
            });
        }
    }

    private void showInfo(String type) {
        infoCl.setVisibility(View.VISIBLE);
        infoNoButton.setVisibility(View.VISIBLE);
        switch (type) {
            case "timeout":
                infoTv.setText(R.string.dictate_timeout_msg);
                infoYesButton.setVisibility(View.GONE);
                infoNoButton.setOnClickListener(v -> infoCl.setVisibility(View.GONE));
                break;
            case "invalid_api_key":
                infoTv.setText(R.string.dictate_invalid_api_key_msg);
                infoYesButton.setVisibility(View.VISIBLE);
                infoYesButton.setOnClickListener(v -> {
                    openSettingsActivity();
                    infoCl.setVisibility(View.GONE);
                });
                infoNoButton.setOnClickListener(v -> infoCl.setVisibility(View.GONE));
                break;
            case "quota_exceeded":
                infoTv.setText(R.string.dictate_quota_exceeded_msg);
                infoYesButton.setVisibility(View.VISIBLE);
                infoYesButton.setOnClickListener(v -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.openai.com/settings/organization/billing/overview"));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(browserIntent);
                    infoCl.setVisibility(View.GONE);
                });
                infoNoButton.setOnClickListener(v -> infoCl.setVisibility(View.GONE));
                break;
            case "internet_error":
                infoTv.setText(R.string.dictate_internet_error_msg);
                infoYesButton.setVisibility(View.GONE);
                infoNoButton.setOnClickListener(v -> infoCl.setVisibility(View.GONE));
                break;
        }
    }

    private void deleteOneCharacter() {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null) {
            CharSequence selectedText = inputConnection.getSelectedText(0);

            if (selectedText != null) {
                inputConnection.commitText("", 1);
            } else {
                inputConnection.deleteSurroundingText(1, 0);
            }
        }
    }

}
