package com.example.travel2;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.List;

public class StepHistoryAdapter extends ArrayAdapter<String> {
    private final Context context;
    private final List<String> items;
    private final OnDeleteClickListener deleteListener;

    public interface OnDeleteClickListener {
        void onDeleteClick(String item);
    }

    public StepHistoryAdapter(Context context, List<String> items, OnDeleteClickListener listener) {
        super(context, R.layout.item_step_history, items);
        this.context = context;
        this.items = items;
        this.deleteListener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_step_history, parent, false);
        }

        TextView textView = convertView.findViewById(R.id.historyText);
        ImageButton deleteButton = convertView.findViewById(R.id.deleteButton);

        String item = items.get(position);
        textView.setText(item);

        deleteButton.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDeleteClick(item);
            }
        });

        return convertView;
    }
} 