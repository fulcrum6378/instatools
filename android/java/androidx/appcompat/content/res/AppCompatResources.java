package androidx.appcompat.content.res;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class AppCompatResources {

    private AppCompatResources() {
    }

    public static ColorStateList getColorStateList(@NonNull Context context, @ColorRes int resId) {
        return context.getResources().getColorStateList(resId, context.getTheme());
    }

    public static @Nullable Drawable getDrawable(@NonNull Context context, @DrawableRes int resId) {
        return context.getResources().getDrawable(resId, context.getTheme());
    }
}
