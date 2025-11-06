package net.devemperor.dictate.rewording;

import android.animation.TimeInterpolator;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import net.devemperor.dictate.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PromptsKeyboardAdapter extends RecyclerView.Adapter<PromptsKeyboardAdapter.RecyclerViewHolder> {

    private static final float PRESSED_SCALE = 0.92f;
    private static final long PRESS_ANIM_DURATION = 80L;
    private static final TimeInterpolator PRESS_INTERPOLATOR = new DecelerateInterpolator();

    private final SharedPreferences sp;
    private final List<PromptModel> data;
    private final AdapterCallback callback;
    private final List<Integer> queuedPromptOrder = new ArrayList<>();
    private boolean disableNonSelectionPrompts = false;
    private MaterialButton selectAllButton;
    private boolean selectAllActive = false;

    public interface AdapterCallback {
        void onItemClicked(Integer position);
        void onItemLongClicked(Integer position);
    }

    public PromptsKeyboardAdapter(SharedPreferences sp, List<PromptModel> data, AdapterCallback callback) {
        this.sp = sp;
        this.data = data;
        this.callback = callback;
    }

    public void setQueuedPromptOrder(List<Integer> queuedPromptIds) {
        queuedPromptOrder.clear();
        queuedPromptOrder.addAll(queuedPromptIds);
        notifyDataSetChanged();
    }

    public void setDisableNonSelectionPrompts(boolean disable) {
        if (disableNonSelectionPrompts == disable) return;
        disableNonSelectionPrompts = disable;
        notifyDataSetChanged();
    }

    public void setSelectAllActive(boolean active) {
        if (selectAllActive == active) return;
        selectAllActive = active;
        if (selectAllButton != null) {
            updateSelectAllButtonIcon();
        }
    }

    @NonNull
    @Override
    public RecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_prompts_keyboard, parent, false);
        return new RecyclerViewHolder(view);
    }

    public static class RecyclerViewHolder extends RecyclerView.ViewHolder {
        final MaterialButton promptBtn;

        public RecyclerViewHolder(View itemView) {
            super(itemView);
            promptBtn = itemView.findViewById(R.id.prompts_keyboard_btn);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerViewHolder holder, final int position) {
        holder.promptBtn.animate().cancel();
        holder.promptBtn.setScaleX(1f);
        holder.promptBtn.setScaleY(1f);
        PromptModel model = data.get(position);
        if (holder.promptBtn == selectAllButton && model.getId() != -3) {
            selectAllButton = null;
        }
        if (model.getId() == -1) {
            holder.promptBtn.setText("");
            holder.promptBtn.setForeground(AppCompatResources.getDrawable(holder.promptBtn.getContext(), R.drawable.ic_baseline_auto_awesome_18));
            holder.promptBtn.setIcon(null);
            holder.promptBtn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        } else if (model.getId() == -3) {
            holder.promptBtn.setText("");
            holder.promptBtn.setIcon(null);
            holder.promptBtn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
            selectAllButton = holder.promptBtn;
            updateSelectAllButtonIcon();
        } else if (model.getId() == -2) {
            holder.promptBtn.setText("");
            holder.promptBtn.setForeground(AppCompatResources.getDrawable(holder.promptBtn.getContext(), R.drawable.ic_baseline_add_24));
            holder.promptBtn.setIcon(null);
            holder.promptBtn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        } else {
            int queueIndex = queuedPromptOrder.indexOf(model.getId());
            if (queueIndex >= 0) {
                holder.promptBtn.setText(String.format(Locale.getDefault(), "%s (%d)", model.getName(), queueIndex + 1));
            } else {
                holder.promptBtn.setText(model.getName());
            }
            holder.promptBtn.setForeground(null);
        }
        boolean shouldDisable = disableNonSelectionPrompts && model.getId() >= 0 && !model.requiresSelection();
        holder.promptBtn.setEnabled(!shouldDisable);
        holder.promptBtn.setAlpha(shouldDisable ? 0.5f : 1f);
        if (model.getId() >= 0) {
            holder.promptBtn.setIcon(queuedPromptOrder.contains(model.getId())
                    ? AppCompatResources.getDrawable(holder.promptBtn.getContext(), R.drawable.ic_baseline_check_circle_outline_24)
                    : null);
            holder.promptBtn.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_END);
        } else {
            holder.promptBtn.setIcon(null);
        }
        holder.promptBtn.setOnClickListener(v -> callback.onItemClicked(position));
        holder.promptBtn.setOnLongClickListener(v -> {
            callback.onItemLongClicked(position);
            return true;
        });
        holder.promptBtn.setBackgroundColor(sp.getInt("net.devemperor.dictate.accent_color", -14700810));
        if (sp.getBoolean("net.devemperor.dictate.animations", true)) {
            holder.promptBtn.setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.animate()
                                .scaleX(PRESSED_SCALE)
                                .scaleY(PRESSED_SCALE)
                                .setDuration(PRESS_ANIM_DURATION)
                                .setInterpolator(PRESS_INTERPOLATOR)
                                .start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(PRESS_ANIM_DURATION)
                                .setInterpolator(PRESS_INTERPOLATOR)
                                .start();
                        break;
                }
                return false;
            });
        } else {
            holder.promptBtn.setOnTouchListener(null);
            holder.promptBtn.setScaleX(1f);
            holder.promptBtn.setScaleY(1f);
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    private void updateSelectAllButtonIcon() {
        if (selectAllButton == null) return;
        selectAllButton.setForeground(AppCompatResources.getDrawable(
                selectAllButton.getContext(),
                selectAllActive ? R.drawable.ic_baseline_deselect_24 : R.drawable.ic_baseline_select_all_24));
    }

}
