package net.devemperor.dictate.usage;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import net.devemperor.dictate.DictateUtils;
import net.devemperor.dictate.R;

import java.util.List;

public class UsageAdapter extends RecyclerView.Adapter<UsageAdapter.RecyclerViewHolder> {

    private final AppCompatActivity activity;
    private final List<UsageModel> data;
    private final UsageDatabaseHelper db;

    public UsageAdapter(AppCompatActivity activity, List<UsageModel> data, UsageDatabaseHelper db) {
        this.activity = activity;
        this.data = data;
        this.db = db;
    }

    @NonNull
    @Override
    public RecyclerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_usage, parent, false);
        return new RecyclerViewHolder(view);
    }

    public static class RecyclerViewHolder extends RecyclerView.ViewHolder {
        final TextView itemModelNameTv;
        final TableRow itemInputTokensTr;
        final TableRow itemOutputTokensTr;
        final TableRow itemAudioTimeTr;
        final TextView itemInputTokensValueTv;
        final TextView itemOutputTokensValueTv;
        final TextView itemAudioTimeValueTv;
        final TextView itemTotalCostValueTv;

        public RecyclerViewHolder(View itemView) {
            super(itemView);
            itemModelNameTv = itemView.findViewById(R.id.item_usage_model_name);
            itemInputTokensTr = itemView.findViewById(R.id.item_usage_input_tokens);
            itemOutputTokensTr = itemView.findViewById(R.id.item_usage_output_tokens);
            itemAudioTimeTr = itemView.findViewById(R.id.item_usage_audio_time);
            itemInputTokensValueTv = itemView.findViewById(R.id.item_usage_input_tokens_value);
            itemOutputTokensValueTv = itemView.findViewById(R.id.item_usage_output_tokens_value);
            itemAudioTimeValueTv = itemView.findViewById(R.id.item_usage_audio_time_value);
            itemTotalCostValueTv = itemView.findViewById(R.id.item_usage_total_cost_value);
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull RecyclerViewHolder holder, final int position) {
        UsageModel model = data.get(position);
        String modelProvider = "";
        switch ((int) model.getModelProvider()) {
            case 0:
                modelProvider = activity.getString(R.string.dictate_usage_model_provider_openai); break;
            case 1:
                modelProvider = activity.getString(R.string.dictate_usage_model_provider_groq); break;
            case 2:
                modelProvider = activity.getString(R.string.dictate_usage_model_provider_custom); break;
            default:
                break;
        }

        holder.itemModelNameTv.setText(DictateUtils.translateModelName(model.getModelName()) + " (" + modelProvider + ")");
        holder.itemTotalCostValueTv.setText(activity.getString(R.string.dictate_usage_cost, db.getCost(model.getModelName())));
        if (model.getInputTokens() == 0) {
            holder.itemInputTokensTr.setVisibility(View.GONE);
            holder.itemOutputTokensTr.setVisibility(View.GONE);

            holder.itemAudioTimeValueTv.setText(activity.getString(R.string.dictate_usage_audio_time, model.getAudioTime() / 60, model.getAudioTime() % 60));
        } else {
            holder.itemAudioTimeTr.setVisibility(View.GONE);

            holder.itemInputTokensValueTv.setText(String.valueOf(model.getInputTokens()));
            holder.itemOutputTokensValueTv.setText(String.valueOf(model.getOutputTokens()));
        }
    }

    @Override
    public int getItemCount() {
        return data.size();
    }
}
