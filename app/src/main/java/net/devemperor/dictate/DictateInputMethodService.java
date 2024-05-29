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
import com.theokanning.openai.service.OpenAiService;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DictateInputMethodService extends InputMethodService {

    private final Handler deleteHandler = new Handler();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable deleteRunnable;
    private boolean isDeleting = false;
    private MediaRecorder recorder;
    private ExecutorService thread;
    private File audioFile;
    Vibrator vibrator;
    SharedPreferences sp;
    private boolean vibrationEnabled = true;

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

        settingsButton.setOnClickListener(v -> {
            infoCl.setVisibility(View.GONE);
            openSettingsActivity();
        });

        recordButton.setOnClickListener(v -> {
            if (vibrationEnabled) vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));

            infoCl.setVisibility(View.GONE);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                openSettingsActivity();
            } else if (recordButton.getText().equals(getString(R.string.dictate_record))) {
                startRecording();
            } else if (recordButton.getText().equals(getString(R.string.dictate_send))) {
                stopRecording();
            }
        });

        backspaceButton.setOnClickListener(v -> {
            if (vibrationEnabled) vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));

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
            if (vibrationEnabled) vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));

            switchToPreviousInputMethod();
        });

        spaceButton.setOnClickListener(v -> {
            if (vibrationEnabled) vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));

            InputConnection inputConnection = getCurrentInputConnection();
            if (inputConnection != null) {
                inputConnection.commitText(" ", 1);
            }
        });

        enterButton.setOnClickListener(v -> {
            if (vibrationEnabled) vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));

            InputConnection inputConnection = getCurrentInputConnection();
            if (inputConnection != null) {
                inputConnection.commitText("\n", 1);
            }
        });

        return dictateKeyboardView;
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
    }

    private void stopRecording() {
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;

            recordButton.setText(R.string.dictate_sending);
            recordButton.setEnabled(false);

            OpenAiService service = new OpenAiService(sp.getString("net.devemperor.dictate.api_key", "NO_API_KEY"));

            thread = Executors.newSingleThreadExecutor();
            thread.execute(() -> {
                try {
                    String resultText;
                    if (sp.getBoolean("net.devemperor.dictate.translate", false)) {
                        CreateTranslationRequest request = CreateTranslationRequest.builder().model("whisper-1").build();
                        TranslationResult result = service.createTranslation(request, audioFile);
                        resultText = result.getText();
                    } else {
                        CreateTranscriptionRequest request = CreateTranscriptionRequest.builder().model("whisper-1").build();
                        TranscriptionResult result = service.createTranscription(request, audioFile);
                        resultText = result.getText();
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
