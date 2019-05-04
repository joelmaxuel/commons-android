package org.mtransit.android.commons.api;

import java.util.Objects;

import android.annotation.TargetApi;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

@TargetApi(Build.VERSION_CODES.KITKAT)
public class KitKatSupport extends JellyBeanSupportMR2 {

	private static final String LOG_TAG = KitKatSupport.class.getSimpleName();

	@NonNull
	@Override
	public String getLogTag() {
		return LOG_TAG;
	}

	@SuppressWarnings("WeakerAccess")
	public KitKatSupport() {
		super();
	}

	@Override
	public boolean isCharacterAlphabetic(int codePoint) {
		return Character.isAlphabetic(codePoint);
	}

	@NonNull
	@Override
	public <T> T requireNonNull(@Nullable T obj, @NonNull String message) {
		return Objects.requireNonNull(obj, message);
	}
}
