package net.devemperor.dictate.rewording;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.devemperor.dictate.R;

import java.util.List;

public class PromptsOverviewAdapter extends RecyclerView.Adapter<PromptsOverviewAdapter.RecyclerViewHolder> {

    private final AppCompatActivity activity;
    private final List<PromptModel> data;
    private final AdapterCallback callback;
    private final PromptsDatabaseHelper db;

    public interface AdapterCallback {
        void onItemClicked(Integer position);
    }

    public PromptsOverviewAdapter(AppCompatActivity activity, List<PromptModel> data, PromptsDatabaseHelper db, AdapterCallback callback) {
        this.activity = activity;
        this.data = data;
        this.callback = callback;
        this.db = db;
    }

    @NonNull
    @Override
    public RecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_prompts_overview, parent, false);
        return new RecyclerViewHolder(view);
    }

    public static class RecyclerViewHolder extends RecyclerView.ViewHolder {
        final TextView itemNameTv;
        final TextView itemPromptTv;
        final MaterialButton moveUpBtn;
        final MaterialButton moveDownBtn;
        final MaterialButton deleteBtn;

        public RecyclerViewHolder(View itemView) {
            super(itemView);
            itemNameTv = itemView.findViewById(R.id.item_prompts_overview_name_tv);
            itemPromptTv = itemView.findViewById(R.id.item_prompts_overview_prompt_tv);
            moveUpBtn = itemView.findViewById(R.id.item_prompts_overview_move_up_btn);
            moveDownBtn = itemView.findViewById(R.id.item_prompts_overview_move_down_btn);
            deleteBtn = itemView.findViewById(R.id.item_prompts_overview_delete_btn);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerViewHolder holder, final int position) {
        int currentPosition = holder.getAdapterPosition();
        if(currentPosition == RecyclerView.NO_POSITION) return;

        PromptModel model = data.get(currentPosition);
        holder.itemNameTv.setText(model.getName());
        holder.itemNameTv.setOnClickListener(v -> callback.onItemClicked(currentPosition));
        holder.itemPromptTv.setText(model.getPrompt());
        holder.itemPromptTv.setOnClickListener(v -> callback.onItemClicked(currentPosition));

        holder.moveUpBtn.setVisibility(currentPosition == 0 ? View.GONE : View.VISIBLE);
        holder.moveDownBtn.setVisibility(currentPosition == data.size() - 1 ? View.GONE : View.VISIBLE);

        holder.moveUpBtn.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos <= 0) return;

            PromptModel currentModel = data.get(pos);
            PromptModel prevModel = data.get(pos - 1);

            currentModel.setPos(pos - 1);
            prevModel.setPos(pos);
            db.update(currentModel);
            db.update(prevModel);
            data.set(pos, prevModel);
            data.set(pos - 1, currentModel);

            notifyItemMoved(pos, pos - 1);
            notifyItemChanged(pos);
            notifyItemChanged(pos - 1);
        });

        holder.moveDownBtn.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos >= data.size() - 1) return;

            PromptModel currentModel = data.get(pos);
            PromptModel nextModel = data.get(pos + 1);

            currentModel.setPos(pos + 1);
            nextModel.setPos(pos);
            db.update(currentModel);
            db.update(nextModel);
            data.set(pos, nextModel);
            data.set(pos + 1, currentModel);

            notifyItemMoved(pos, pos + 1);
            notifyItemChanged(pos);
            notifyItemChanged(pos + 1);
        });

        holder.deleteBtn.setOnClickListener(v -> new MaterialAlertDialogBuilder(v.getContext())
                .setTitle(R.string.dictate_delete_prompt)
                .setMessage(R.string.dictate_delete_prompt_message)
                .setPositiveButton(R.string.dictate_yes, (di, i) -> {
                    int pos = holder.getAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;
                    db.delete(model.getId());
                    data.remove(pos);
                    notifyItemRemoved(pos);

                    activity.findViewById(R.id.prompts_overview_no_prompts_tv).setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
                })
                .setNegativeButton(R.string.dictate_no, null)
                .show());
    }

    @Override
    public int getItemCount() {
        return data.size();
    }
}
