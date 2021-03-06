package org.mtransit.android.commons;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.ArrayMap;

public final class ColorUtils implements MTLog.Loggable {

	private static final String LOG_TAG = ColorUtils.class.getSimpleName();

	@Override
	public String getLogTag() {
		return LOG_TAG;
	}

	private static final String NUMBER_SIGN = "#";

	private static ArrayMap<String, Integer> colorMap = new ArrayMap<>();

	@ColorInt
	public static int parseColor(@NonNull String color) {
		try {
			if (!color.startsWith(NUMBER_SIGN)) {
				color = NUMBER_SIGN + color;
			}
			Integer colorResId = colorMap.get(color);
			if (colorResId == null) {
				colorResId = Color.parseColor(color);
				colorMap.put(color, colorResId);
			}
			return colorResId;
		} catch (Exception e) {
			MTLog.w(LOG_TAG, e, "Error while parsing color '%s'!", color);
			return Color.BLACK;
		}
	}

	private static final String TO_RGB = "#%06X";

	public static String toRGBColor(int colorInt) {
		return String.format(TO_RGB, 0xFFFFFF & colorInt);
	}

	public static float extractHue(@ColorInt int colorInt) {
		float[] hsv = new float[3];
		Color.colorToHSV(colorInt, hsv);
		return hsv[0];
	}

	public static float extractSaturation(@ColorInt int colorInt) {
		float[] hsv = new float[3];
		Color.colorToHSV(colorInt, hsv);
		return hsv[1];
	}

	public static float extractValue(@ColorInt int colorInt) {
		float[] hsv = new float[3];
		Color.colorToHSV(colorInt, hsv);
		return hsv[2];
	}

	@NonNull
	public static Paint getNewPaintColorFilter(@ColorInt int colorInt) {
		Paint paint = new Paint();
		paint.setColorFilter(new PorterDuffColorFilter(colorInt, PorterDuff.Mode.MULTIPLY));
		return paint;
	}

	@Nullable
	public static Bitmap colorizeBitmapResource(@Nullable Context context, @ColorInt int markerColor, int bitmapResId) {
		if (context == null) {
			return null;
		}
		return colorizeBitmap(markerColor, BitmapFactory.decodeResource(context.getResources(), bitmapResId));
	}

	public static Bitmap colorizeBitmap(@ColorInt int markerColor, @NonNull Bitmap bitmap) {
		try {
			Bitmap obm = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
			Canvas canvas = new Canvas(obm);
			canvas.drawBitmap(bitmap, 0f, 0f, getNewPaintColorFilter(markerColor));
			return obm;
		} catch (Exception e) {
			MTLog.w(LOG_TAG, e, "Error while colorizing bitmap!");
			return bitmap;
		}
	}

	private static int textColorPrimary = -1;

	public static int getTextColorPrimary(@NonNull Context context) {
		if (textColorPrimary < 0) {
			textColorPrimary = ThemeUtils.resolveColorAttribute(context, android.R.attr.textColorPrimary);
		}
		return textColorPrimary;
	}

	private static int textColorSecondary = -1;

	public static int getTextColorSecondary(@NonNull Context context) {
		if (textColorSecondary < 0) {
			textColorSecondary = ThemeUtils.resolveColorAttribute(context, android.R.attr.textColorSecondary);
		}
		return textColorSecondary;
	}

	private static int textColorTertiary = -1;

	public static int getTextColorTertiary(@NonNull Context context) {
		if (textColorTertiary < 0) {
			textColorTertiary = ThemeUtils.resolveColorAttribute(context, android.R.attr.textColorTertiary);
		}
		return textColorTertiary;
	}

	public static int[] getColorScheme(@NonNull Context context, int... attrIds) {
		if (attrIds == null || attrIds.length == 0) {
			return new int[0];
		}
		int[] colorScheme = new int[attrIds.length];
		for (int i = 0; i < attrIds.length; i++) {
			colorScheme[i] = ThemeUtils.resolveColorAttribute(context, attrIds[i]);
		}
		return colorScheme;
	}

	public static int getDarkerColor(int color1, int color2) {
		return color1 < color2 ? color1 : color2;
	}

	public static int getLighterColor(int color1, int color2) {
		return color1 > color2 ? color1 : color2;
	}

	@ColorInt
	public static int blendColors(int color1, int color2, float ratio) {
		float inverseRation = 1f - ratio;
		float r = (Color.red(color1) * ratio) + (Color.red(color2) * inverseRation);
		float g = (Color.green(color1) * ratio) + (Color.green(color2) * inverseRation);
		float b = (Color.blue(color1) * ratio) + (Color.blue(color2) * inverseRation);
		return Color.rgb((int) r, (int) g, (int) b);
	}
}
