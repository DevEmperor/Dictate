package net.devemperor.dictate.core;

import static com.theokanning.openai.service.OpenAiService.defaultClient;
import static com.theokanning.openai.service.OpenAiService.defaultObjectMapper;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.theokanning.openai.audio.CreateTranscriptionRequest;
import com.theokanning.openai.audio.TranscriptionResult;
import com.theokanning.openai.client.OpenAiApi;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

import net.devemperor.dictate.BuildConfig;
import net.devemperor.dictate.rewording.PromptModel;
import net.devemperor.dictate.rewording.PromptsDatabaseHelper;
import net.devemperor.dictate.rewording.PromptsKeyboardAdapter;
import net.devemperor.dictate.rewording.PromptsOverviewActivity;
import net.devemperor.dictate.settings.DictateSettingsActivity;
import net.devemperor.dictate.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class DictateInputMethodService extends InputMethodService {

    private Handler mainHandler;
    private Handler deleteHandler;
    private Handler recordTimeHandler;
    private Runnable deleteRunnable;
    private Runnable recordTimeRunnable;

    private long elapsedTime;
    private boolean isDeleting = false;
    private long startDeleteTime = 0;
    private int currentDeleteDelay = 50;
    private boolean isRecording = false;
    private boolean isPaused = false;
    private boolean vibrationEnabled = true;
    private String translationPrompt = "";

    private MediaRecorder recorder;
    private ExecutorService speechApiThread;
    private ExecutorService rewordingApiThread;
    private File audioFile;
    private Vibrator vibrator;
    private SharedPreferences sp;
    private AudioManager am;
    private AudioFocusRequest audioFocusRequest;

    private ConstraintLayout dictateKeyboardView;
    private MaterialButton settingsButton;
    private MaterialButton recordButton;
    private MaterialButton resendButton;
    private MaterialButton backspaceButton;
    private MaterialButton switchButton;
    private MaterialButton trashButton;
    private MaterialButton spaceButton;
    private MaterialButton pauseButton;
    private MaterialButton enterButton;
    private ConstraintLayout infoCl;
    private TextView infoTv;
    private Button infoYesButton;
    private Button infoNoButton;
    private LinearLayout promptsLl;
    private RecyclerView promptsRv;
    private MaterialButton selectAllButton;
    private TextView noPromptsTv;

    PromptsDatabaseHelper promptsDb;
    PromptsKeyboardAdapter promptsAdapter;

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
        promptsDb = new PromptsDatabaseHelper(this);
        vibrationEnabled = sp.getBoolean("net.devemperor.dictate.vibration", true);

        dictateKeyboardView = (ConstraintLayout) LayoutInflater.from(context).inflate(R.layout.activity_dictate_keyboard_view, null);

        settingsButton = dictateKeyboardView.findViewById(R.id.settings_btn);
        recordButton = dictateKeyboardView.findViewById(R.id.record_btn);
        resendButton = dictateKeyboardView.findViewById(R.id.resend_btn);
        backspaceButton = dictateKeyboardView.findViewById(R.id.backspace_btn);
        switchButton = dictateKeyboardView.findViewById(R.id.switch_btn);
        trashButton = dictateKeyboardView.findViewById(R.id.trash_btn);
        spaceButton = dictateKeyboardView.findViewById(R.id.space_btn);
        pauseButton = dictateKeyboardView.findViewById(R.id.pause_btn);
        enterButton = dictateKeyboardView.findViewById(R.id.enter_btn);

        infoCl = dictateKeyboardView.findViewById(R.id.info_cl);
        infoTv = dictateKeyboardView.findViewById(R.id.info_tv);
        infoYesButton = dictateKeyboardView.findViewById(R.id.info_yes_btn);
        infoNoButton = dictateKeyboardView.findViewById(R.id.info_no_btn);

        promptsLl = dictateKeyboardView.findViewById(R.id.prompts_keyboard_ll);
        promptsRv = dictateKeyboardView.findViewById(R.id.prompts_keyboard_rv);
        selectAllButton = dictateKeyboardView.findViewById(R.id.select_all_btn);
        noPromptsTv = dictateKeyboardView.findViewById(R.id.prompts_keyboard_no_prompts_tv);

        promptsRv.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open("dictate_translation_prompt.txt")));
            translationPrompt = reader.readLine();
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();  //TODO firebase crashlytics
        }

        recordTimeRunnable = new Runnable() {
            @Override
            public void run() {
                elapsedTime += 100;
                recordButton.setText(getString(R.string.dictate_send,
                        String.format(Locale.getDefault(), "%02d:%02d", (int) (elapsedTime / 60000), (int) (elapsedTime / 1000) % 60)));
                recordTimeHandler.postDelayed(this, 100);
            }
        };

        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(focusChange -> {
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                        if (isRecording) pauseButton.performClick();
                    }
                })
                .build();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) switchButton.setEnabled(false);

        settingsButton.setOnClickListener(v -> {
            if (isRecording) trashButton.performClick();
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

        resendButton.setOnClickListener(v -> {
            vibrate();
            startApiRequest();
        });

        backspaceButton.setOnClickListener(v -> {
            vibrate();
            deleteOneCharacter();
        });

        backspaceButton.setOnLongClickListener(v -> {
            isDeleting = true;
            startDeleteTime = System.currentTimeMillis();
            currentDeleteDelay = 50;
            deleteRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isDeleting) {
                        deleteOneCharacter();
                        long diff = System.currentTimeMillis() - startDeleteTime;
                        if (diff > 1500 && currentDeleteDelay == 50) {
                            vibrate();
                            currentDeleteDelay = 25;
                        } else if (diff > 3000 && currentDeleteDelay == 25) {
                            vibrate();
                            currentDeleteDelay = 10;
                        } else if (diff > 5000 && currentDeleteDelay == 10) {
                            vibrate();
                            currentDeleteDelay = 5;
                        }
                        deleteHandler.postDelayed(this, currentDeleteDelay);
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

        switchButton.setOnLongClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
            return true;
        });

        trashButton.setOnClickListener(v -> {
            vibrate();
            if (recorder != null) {
                recorder.stop();
                recorder.release();
                recorder = null;

                if (recordTimeRunnable != null) {
                    recordTimeHandler.removeCallbacks(recordTimeRunnable);
                }
            }
            am.abandonAudioFocusRequest(audioFocusRequest);

            isRecording = false;
            isPaused = false;
            recordButton.setText(R.string.dictate_record);
            recordButton.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_mic_24));
            recordButton.setEnabled(true);
            pauseButton.setVisibility(View.GONE);
            pauseButton.setForeground(AppCompatResources.getDrawable(context, R.drawable.ic_baseline_pause_24));
            trashButton.setVisibility(View.GONE);
        });

        spaceButton.setOnClickListener(v -> {
            vibrate();

            InputConnection inputConnection = getCurrentInputConnection();
            if (inputConnection != null) {
                inputConnection.commitText(" ", 1);
            }
        });

        pauseButton.setOnClickListener(v -> {
            vibrate();
            if (recorder != null) {
                if (isPaused) {
                    am.requestAudioFocus(audioFocusRequest);
                    recorder.resume();
                    recordTimeHandler.post(recordTimeRunnable);
                    pauseButton.setForeground(AppCompatResources.getDrawable(context, R.drawable.ic_baseline_pause_24));
                    isPaused = false;
                } else {
                    am.abandonAudioFocusRequest(audioFocusRequest);
                    recorder.pause();
                    recordTimeHandler.removeCallbacks(recordTimeRunnable);
                    pauseButton.setForeground(AppCompatResources.getDrawable(context, R.drawable.ic_baseline_mic_24));
                    isPaused = true;
                }
            }
        });

        enterButton.setOnClickListener(v -> {
            vibrate();

            InputConnection inputConnection = getCurrentInputConnection();
            if (inputConnection != null) {
                inputConnection.commitText("\n", 1);
            }
        });

        selectAllButton.setOnClickListener(v -> {
            vibrate();

            InputConnection inputConnection = getCurrentInputConnection();
            if (inputConnection != null) {
                ExtractedText extractedText = inputConnection.getExtractedText(new ExtractedTextRequest(), 0);

                if (inputConnection.getSelectedText(0) == null && extractedText.text.length() > 0) {
                    inputConnection.performContextMenuAction(android.R.id.selectAll);
                    selectAllButton.setForeground(AppCompatResources.getDrawable(context, R.drawable.ic_baseline_deselect_24));
                } else {
                    inputConnection.clearMetaKeyStates(0);
                    if (extractedText == null || extractedText.text == null) {
                        inputConnection.setSelection(0, 0);
                    } else {
                        inputConnection.setSelection(extractedText.text.length(), extractedText.text.length());
                    }
                    selectAllButton.setForeground(AppCompatResources.getDrawable(context, R.drawable.ic_baseline_select_all_24));
                }
            }
        });

        noPromptsTv.setOnClickListener(v -> {
            vibrate();
            Intent intent = new Intent(this, PromptsOverviewActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        return dictateKeyboardView;
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);

        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;

            if (recordTimeRunnable != null) {
                recordTimeHandler.removeCallbacks(recordTimeRunnable);
            }
        }
        pauseButton.setVisibility(View.GONE);
        trashButton.setVisibility(View.GONE);
        resendButton.setVisibility(View.GONE);
        infoCl.setVisibility(View.GONE);
        isRecording = false;
        isPaused = false;
        am.abandonAudioFocusRequest(audioFocusRequest);
        recordButton.setText(R.string.dictate_record);
        recordButton.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_mic_24));
        recordButton.setEnabled(true);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);

        if (sp.getBoolean("net.devemperor.dictate.rewording_enabled", true)) {
            promptsLl.setVisibility(View.VISIBLE);

            List<PromptModel> data;
            if (getCurrentInputConnection().getSelectedText(0) == null) {
                data = promptsDb.getAll(false);
            } else {
                data = promptsDb.getAll(true);
            }
            noPromptsTv.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);

            promptsAdapter = new PromptsKeyboardAdapter(data, position -> {
                PromptModel model = data.get(position);

                promptsAdapter.removeAllExcept(position);
                promptsRv.getChildAt(0).findViewById(R.id.prompts_keyboard_pb).setVisibility(View.VISIBLE);
                promptsRv.getChildAt(0).findViewById(R.id.prompts_keyboard_btn).setEnabled(false);
                infoCl.setVisibility(View.GONE);

                String apiKey = sp.getString("net.devemperor.dictate.api_key", "NO_API_KEY");
                Retrofit retrofit = new Retrofit.Builder()
                        .baseUrl("https://api.openai.com/")
                        .client(defaultClient(apiKey.replaceAll("[^ -~]", ""), Duration.ofSeconds(120)).newBuilder().build())
                        .addConverterFactory(JacksonConverterFactory.create(defaultObjectMapper()))
                        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                        .build();
                OpenAiService service = new OpenAiService(retrofit.create(OpenAiApi.class));

                rewordingApiThread = Executors.newSingleThreadExecutor();
                rewordingApiThread.execute(() -> {
                    try {
                        String prompt = model.getPrompt();
                        if (getCurrentInputConnection().getSelectedText(0) != null) {
                            prompt += "\n\n" + getCurrentInputConnection().getSelectedText(0).toString();
                        }

                        ChatCompletionRequest request = ChatCompletionRequest.builder()
                                .model("gpt-4o-mini")
                                .messages(Collections.singletonList(new ChatMessage(ChatMessageRole.USER.value(), prompt)))
                                .build();
                        ChatCompletionResult rewordedResult = service.createChatCompletion(request);
                        String rewordedText = rewordedResult.getChoices().get(0).getMessage().getContent();

                        sp.edit().putLong("net.devemperor.dictate.translation_input_tokens",
                                sp.getLong("net.devemperor.dictate.translation_input_tokens", 0) + rewordedResult.getUsage().getPromptTokens()).apply();
                        sp.edit().putLong("net.devemperor.dictate.translation_output_tokens",
                                sp.getLong("net.devemperor.dictate.translation_output_tokens", 0) + rewordedResult.getUsage().getCompletionTokens()).apply();

                        InputConnection inputConnection = getCurrentInputConnection();
                        if (inputConnection != null) {
                            inputConnection.commitText(rewordedText, 1);
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
                        promptsRv.getChildAt(0).findViewById(R.id.prompts_keyboard_pb).setVisibility(View.GONE);
                        promptsRv.getChildAt(0).findViewById(R.id.prompts_keyboard_btn).setEnabled(true);

                        List<PromptModel> newData = promptsDb.getAll(false);
                        promptsAdapter.getData().clear();
                        promptsAdapter.getData().addAll(newData);
                        promptsAdapter.notifyDataSetChanged();
                        noPromptsTv.setVisibility(newData.isEmpty() ? View.VISIBLE : View.GONE);
                    });
                });
            });
            promptsRv.setAdapter(promptsAdapter);
        } else {
            promptsLl.setVisibility(View.GONE);
        }

        if (sp.getInt("net.devemperor.dictate.last_version_code", 0) < BuildConfig.VERSION_CODE) {
            showInfo("update");
        } else if (sp.getFloat("net.devemperor.dictate.total_duration", 0.0f) > 180 && !sp.getBoolean("net.devemperor.dictate.flag_has_rated_in_playstore", false)) {
            showInfo("rate");
        } else if (sp.getFloat("net.devemperor.dictate.total_duration", 0.0f) > 600 && !sp.getBoolean("net.devemperor.dictate.flag_has_donated", false)) {
            showInfo("donate");
        }

        if (sp.getBoolean("net.devemperor.dictate.instant_recording", false)) {
            recordButton.performClick();
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onUpdateSelection (int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);

        if (sp.getBoolean("net.devemperor.dictate.rewording_enabled", true)) {
            List<PromptModel> data;
            if (getCurrentInputConnection().getSelectedText(0) == null) {
                data = promptsDb.getAll(false);
                selectAllButton.setForeground(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_select_all_24));
            } else {
                data = promptsDb.getAll(true);
                selectAllButton.setForeground(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_deselect_24));
            }

            promptsAdapter.getData().clear();
            promptsAdapter.getData().addAll(data);
            promptsAdapter.notifyDataSetChanged();
            noPromptsTv.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
        }
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

        am.requestAudioFocus(audioFocusRequest);

        try {
            recorder.prepare();
            recorder.start();
        } catch (IOException e) {
            e.printStackTrace();  //TODO firebase crashlytics
        }

        recordButton.setText(R.string.dictate_send);
        recordButton.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_baseline_send_24));
        pauseButton.setVisibility(View.VISIBLE);
        trashButton.setVisibility(View.VISIBLE);
        resendButton.setVisibility(View.GONE);
        isRecording = true;

        elapsedTime = 0;
        recordTimeHandler.post(recordTimeRunnable);
    }

    private void stopRecording() {
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;

            if (recordTimeRunnable != null) {
                recordTimeHandler.removeCallbacks(recordTimeRunnable);
            }

            startApiRequest();
        }
    }

    private void startApiRequest() {
        recordButton.setText(R.string.dictate_sending);
        recordButton.setEnabled(false);
        pauseButton.setVisibility(View.GONE);
        trashButton.setVisibility(View.GONE);
        resendButton.setVisibility(View.GONE);
        infoCl.setVisibility(View.GONE);
        isRecording = false;
        isPaused = false;

        am.abandonAudioFocusRequest(audioFocusRequest);

        String customApiHost = sp.getString("net.devemperor.dictate.custom_api_host", getString(R.string.dictate_custom_host_hint));
        String apiKey = sp.getString("net.devemperor.dictate.api_key", "NO_API_KEY");
        String language = sp.getString("net.devemperor.dictate.input_language", "detect");
        String prompt = sp.getString("net.devemperor.dictate.prompt", "NO_PROMPT");
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(sp.getBoolean("net.devemperor.dictate.custom_api_host_enabled", false) ? customApiHost : "https://api.openai.com/")
                .client(defaultClient(apiKey.replaceAll("[^ -~]", ""), Duration.ofSeconds(120)).newBuilder().build())
                .addConverterFactory(JacksonConverterFactory.create(defaultObjectMapper()))
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build();
        OpenAiService service = new OpenAiService(retrofit.create(OpenAiApi.class));

        speechApiThread = Executors.newSingleThreadExecutor();
        speechApiThread.execute(() -> {
            try {
                CreateTranscriptionRequest request = CreateTranscriptionRequest.builder()
                        .model("whisper-1")
                        .responseFormat("verbose_json")
                        .language(!language.equals("detect") ? language : null)
                        .prompt(!prompt.equals("NO_PROMPT") ? prompt : null)
                        .build();
                TranscriptionResult result = service.createTranscription(request, audioFile);
                String resultText = result.getText();
                double duration = result.getDuration();
                sp.edit().putFloat("net.devemperor.dictate.total_duration", sp.getFloat("net.devemperor.dictate.total_duration", 0) + (float) duration).apply();

                if (sp.getBoolean("net.devemperor.dictate.translate", false)) {
                    mainHandler.post(() -> recordButton.setText(R.string.dictate_translating));
                    ChatMessage message = new ChatMessage(ChatMessageRole.USER.value(),
                            String.format(translationPrompt, sp.getString("net.devemperor.dictate.translation_language", "English"), resultText));
                    ChatCompletionRequest translationRequest = ChatCompletionRequest.builder().model("gpt-4o-mini").messages(Collections.singletonList(message)).build();
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
                    resendButton.setVisibility(View.VISIBLE);
                    if (Objects.requireNonNull(e.getMessage()).contains("SocketTimeoutException")) {
                        showInfo("timeout");
                    } else if (e.getMessage().contains("API key")) {
                        showInfo("invalid_api_key");
                    } else if (e.getMessage().contains("quota")) {
                        showInfo("quota_exceeded");
                    } else if (sp.getBoolean("net.devemperor.dictate.custom_api_host_enabled", false)) {
                        if (e.getMessage().contains("ConnectException")) {
                            showInfo("internet_error");
                        } else {
                            showInfo("unknown_host");
                        }
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

    private void showInfo(String type) {
        infoCl.setVisibility(View.VISIBLE);
        infoNoButton.setVisibility(View.VISIBLE);
        infoTv.setTextColor(getResources().getColor(R.color.dictate_red, getTheme()));
        switch (type) {
            case "update":
                infoTv.setTextColor(getResources().getColor(R.color.dictate_green, getTheme()));
                infoTv.setText(R.string.dictate_update_installed_msg);
                infoYesButton.setVisibility(View.VISIBLE);
                infoYesButton.setOnClickListener(v -> {
                    openSettingsActivity();
                    infoCl.setVisibility(View.GONE);
                });
                infoNoButton.setOnClickListener(v -> {
                    sp.edit().putInt("net.devemperor.dictate.last_version_code", BuildConfig.VERSION_CODE).apply();
                    infoCl.setVisibility(View.GONE);
                });
                break;
            case "rate":
                infoTv.setTextColor(getResources().getColor(R.color.dictate_green, getTheme()));
                infoTv.setText(R.string.dictate_rate_app_msg);
                infoYesButton.setVisibility(View.VISIBLE);
                infoYesButton.setOnClickListener(v -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=net.devemperor.dictate"));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(browserIntent);
                    sp.edit().putBoolean("net.devemperor.dictate.flag_has_rated_in_playstore", true).apply();
                    infoCl.setVisibility(View.GONE);
                });
                infoNoButton.setOnClickListener(v -> {
                    sp.edit().putBoolean("net.devemperor.dictate.flag_has_rated_in_playstore", true).apply();
                    infoCl.setVisibility(View.GONE);
                });
                break;
            case "donate":
                infoTv.setTextColor(getResources().getColor(R.color.dictate_green, getTheme()));
                infoTv.setText(R.string.dictate_donate_msg);
                infoYesButton.setVisibility(View.VISIBLE);
                infoYesButton.setOnClickListener(v -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://paypal.me/DevEmperor"));
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(browserIntent);
                    sp.edit().putBoolean("net.devemperor.dictate.flag_has_donated", true).apply();
                    infoCl.setVisibility(View.GONE);
                });
                infoNoButton.setOnClickListener(v -> {
                    sp.edit().putBoolean("net.devemperor.dictate.flag_has_donated", true).apply();
                    infoCl.setVisibility(View.GONE);
                });
                break;
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
            case "unknown_host":
                infoTv.setText(R.string.dictate_invalid_custom_host_msg);
                infoYesButton.setVisibility(View.VISIBLE);
                infoYesButton.setOnClickListener(v -> {
                    openSettingsActivity();
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
