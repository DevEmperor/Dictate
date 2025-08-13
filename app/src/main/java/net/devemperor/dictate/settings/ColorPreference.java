package net.devemperor.dictate.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.Spannable;
import android.text.SpannableString;
import android.util.AttributeSet;

import androidx.preference.Preference;

import com.flask.colorpicker.ColorPickerView;
import com.flask.colorpicker.builder.ColorPickerDialogBuilder;

import net.devemperor.dictate.R;

public class ColorPreference extends Preference {

    private int colorValue = 0xFF29B6F6;

    public ColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {

        ColorPickerDialogBuilder.with(getContext())
                .setTitle(getContext().getString(R.string.dictate_settings_accent_color))
                .initialColor(colorValue)
                .wheelType(ColorPickerView.WHEEL_TYPE.FLOWER)
                .density(9)
                .setPositiveButton(getContext().getString(R.string.dictate_okay), (dialog, selectedColor, allColors) -> {
                    if (callChangeListener(selectedColor)) {
                        colorValue = selectedColor;
                        persistInt(colorValue);
                        updateSummary();
                        notifyChanged();
                    }
                })
                .setNegativeButton(getContext().getString(R.string.dictate_cancel), (dialog, which) -> {})
                .build()
                .show();
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        colorValue = getPersistedInt(defaultValue != null ? (int) defaultValue : 0xFF29B6F6);
        updateSummary();
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInteger(index, 0xFF29B6F6);
    }

    private void updateSummary() {
        String summaryText = getContext().getString(R.string.dictate_settings_accent_color_summary);
        String highlight = getContext().getString(R.string.dictate_settings_accent_color_word);

        int start = summaryText.indexOf(highlight);
        if (start == -1) {
            setSummary(summaryText);
            return;
        }
        int end = start + highlight.length();

        SpannableString spannableSummary = new SpannableString(summaryText);
        spannableSummary.setSpan(
                new android.text.style.ForegroundColorSpan(colorValue),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        setSummary("Temporary summary");  // Temporarily set a placeholder summary to ensure the view is updated
        setSummary(spannableSummary);
    }
}
