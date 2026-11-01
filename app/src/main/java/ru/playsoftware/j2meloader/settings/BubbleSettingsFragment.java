package ru.playsoftware.j2meloader.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.Objects;

import ru.playsoftware.j2meloader.R;

public final class BubbleSettingsFragment extends PreferenceFragmentCompat {
    private final ActivityResultLauncher<String[]> imagePicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onImagePicked);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.bubble_preferences, rootKey);
        Preference customIcon = findPreference("pref_bubble_custom_icon");
        if (customIcon != null) {
            customIcon.setOnPreferenceClickListener(preference -> {
                imagePicker.launch(new String[]{"image/png"});
                return true;
            });
            String stored = Objects.requireNonNull(getPreferenceManager().getSharedPreferences())
                    .getString("pref_bubble_custom_icon_uri", null);
            if (stored != null) customIcon.setSummary(stored);
        }
    }

    private void onImagePicked(Uri uri) {
        if (uri == null || !isAdded()) return;
        try {
            requireContext().getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
        }
        Objects.requireNonNull(getPreferenceManager().getSharedPreferences()).edit()
                .putString("pref_bubble_custom_icon_uri", uri.toString())
                .apply();
        Preference customIcon = findPreference("pref_bubble_custom_icon");
        if (customIcon != null) customIcon.setSummary(uri.toString());
        Toast.makeText(requireContext(), R.string.bubble_icon_set, Toast.LENGTH_SHORT).show();
    }
}
