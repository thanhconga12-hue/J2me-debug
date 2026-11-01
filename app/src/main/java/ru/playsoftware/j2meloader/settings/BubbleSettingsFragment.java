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

    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener preferenceListener =
            (preferences, key) -> {
                if (!isAdded() || key == null) return;
                if ("pref_bubble_enable".equals(key)) {
                    if (preferences.getBoolean("pref_bubble_enable", false)) {
                        ru.playsoftware.j2meloader.bubble.BubbleController.show(requireContext());
                    } else {
                        ru.playsoftware.j2meloader.bubble.BubbleController.hide(requireContext());
                    }
                } else if (key.startsWith("pref_bubble_")
                        && !"pref_bubble_screen".equals(key)) {
                    ru.playsoftware.j2meloader.bubble.BubbleController.refreshStyle(requireContext());
                }
            };

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

    @Override
    public void onResume() {
        super.onResume();
        Objects.requireNonNull(getPreferenceManager().getSharedPreferences())
                .registerOnSharedPreferenceChangeListener(preferenceListener);
    }

    @Override
    public void onPause() {
        Objects.requireNonNull(getPreferenceManager().getSharedPreferences())
                .unregisterOnSharedPreferenceChangeListener(preferenceListener);
        super.onPause();
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
