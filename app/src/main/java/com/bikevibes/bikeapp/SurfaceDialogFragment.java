package com.bikevibes.bikeapp;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

public class SurfaceDialogFragment extends DialogFragment {
    final static String[] items = {"Pavement", "Dirt", "Gravel"};

    public interface SurfaceDialogListener {
        void onSelect(DialogFragment dialog, String surface);
    }

    SurfaceDialogListener listener;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.surface_dialog_title)
                .setItems(R.array.surfaces, (dialog, which) -> listener.onSelect(SurfaceDialogFragment.this, items[which]));
        // Create the AlertDialog object and return it
        return builder.create();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            listener = (SurfaceDialogListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement SurfaceDialogListener");
        }
    }


}