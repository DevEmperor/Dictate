package net.devemperor.dictate;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    OnboardingAdapter onboardingAdapter;
    ViewPager2 onboardingVp;
    TabLayout onboardingTl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        onboardingAdapter = new OnboardingAdapter(this, new int[]{
                R.layout.viewpager_welcome,
                R.layout.viewpager_permissions,
                R.layout.viewpager_api_key
        });

        onboardingVp = findViewById(R.id.onboarding_viewpager);
        onboardingTl = findViewById(R.id.onboarding_tablayout);
        onboardingVp.setAdapter(onboardingAdapter);
        TabLayoutMediator tabLayoutMediator = new TabLayoutMediator(onboardingTl, onboardingVp, true,
                (tab, position) -> { }
        );
        tabLayoutMediator.attach();
    }

    @Override
    public void onResume() {
        super.onResume();
        List<InputMethodInfo> inputMethodsList = ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).getEnabledInputMethodList();
        for (InputMethodInfo inputMethod : inputMethodsList) {
            if (inputMethod.getPackageName().equals(getPackageName())) {
                onboardingAdapter.notifyItemChanged(1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1337) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onboardingAdapter.notifyItemChanged(1);
            }
        }
    }
}