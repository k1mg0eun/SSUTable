package com.g1j2.ssutable.ui;

import androidx.fragment.app.Fragment;

import com.g1j2.ssutable.App;

/** 보이는 동안 0.25초마다 다시 그리는 화면. */
public abstract class LiveFragment extends Fragment {
    private final Runnable tick = () -> {
        if (isAdded() && !isHidden() && getView() != null) render();
    };

    @Override
    public void onStart() {
        super.onStart();
        App.get().addListener(tick);
        render();
    }

    @Override
    public void onStop() {
        super.onStop();
        App.get().removeListener(tick);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && getView() != null) render();
    }

    protected MainActivity main() {
        return (MainActivity) requireActivity();
    }

    protected abstract void render();
}
