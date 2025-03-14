package net.devemperor.dictate.settings;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import net.devemperor.dictate.R;

import java.util.HashMap;
import java.util.List;

public class HowToAdapter extends BaseExpandableListAdapter {

    private final Context context;
    private final List<String> listDataHeader;
    private final HashMap<String, List<HowToItem>> listChildData;

    public HowToAdapter(Context context, List<String> listDataHeader, HashMap<String, List<HowToItem>> listChildData) {
        this.context = context;
        this.listDataHeader = listDataHeader;
        this.listChildData = listChildData;
    }

    @Override
    public int getGroupCount() {
        return listDataHeader.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return listChildData.get(listDataHeader.get(groupPosition)).size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return listDataHeader.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return listChildData.get(listDataHeader.get(groupPosition)).get(childPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        String headerTitle = (String) getGroup(groupPosition);
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.how_to_item_header, null);
        }

        TextView textView = convertView.findViewById(R.id.how_to_item_header_tv);
        textView.setText(headerTitle);
        return convertView;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        HowToItem item = (HowToItem) getChild(groupPosition, childPosition);
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.how_to_item_body, null);
        }

        TextView textView = convertView.findViewById(R.id.how_to_item_body_tv);
        ImageView imageView = convertView.findViewById(R.id.how_to_item_body_iv);

        textView.setText(item.getText());
        imageView.setImageResource(item.getImageResId());

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return false;
    }
}
