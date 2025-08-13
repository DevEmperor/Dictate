package net.devemperor.dictate.rewording;

import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import net.devemperor.dictate.R;

import java.util.List;

public class PromptsKeyboardAdapter extends RecyclerView.Adapter<PromptsKeyboardAdapter.RecyclerViewHolder> {

    private final SharedPreferences sp;
    private final List<PromptModel> data;
    private final AdapterCallback callback;

    public interface AdapterCallback {
        void onItemClicked(Integer position);
    }

    public PromptsKeyboardAdapter(SharedPreferences sp, List<PromptModel> data, AdapterCallback callback) {
        this.sp = sp;
        this.data = data;
        this.callback = callback;
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
        PromptModel model = data.get(position);
        if (model.getId() == -1) {
            holder.promptBtn.setText("");
            holder.promptBtn.setForeground(AppCompatResources.getDrawable(holder.promptBtn.getContext(), R.drawable.ic_baseline_auto_awesome_18));
        } else if (model.getId() == -2) {
            holder.promptBtn.setText("");
            holder.promptBtn.setForeground(AppCompatResources.getDrawable(holder.promptBtn.getContext(), R.drawable.ic_baseline_add_24));
        } else {
            holder.promptBtn.setText(model.getName());
            holder.promptBtn.setForeground(null);
        }
        holder.promptBtn.setOnClickListener(v -> callback.onItemClicked(position));
        holder.promptBtn.setBackgroundColor(sp.getInt("net.devemperor.dictate.accent_color", -14700810));
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public List<PromptModel> getData() {
        return data;
    }
}
