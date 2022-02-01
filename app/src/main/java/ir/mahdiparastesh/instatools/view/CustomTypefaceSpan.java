package ir.mahdiparastesh.instatools.view;

import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.TypefaceSpan;

public class CustomTypefaceSpan extends TypefaceSpan {
    private final Typeface newType;
    private final float textSize;
    private final int textColour;

    public CustomTypefaceSpan(String family, Typeface type, float size, int colour) {
        super(family);
        newType = type;
        textSize = size;
        textColour = colour;
    }

    private static void applyCustomTypeFace(Paint paint, Typeface tf, float ts, int tc) {
        int oldStyle;
        Typeface old = paint.getTypeface();
        if (old == null) oldStyle = 0;
        else oldStyle = old.getStyle();

        int fake = oldStyle & ~tf.getStyle();
        if ((fake & Typeface.BOLD) != 0)
            paint.setFakeBoldText(true);

        if ((fake & Typeface.ITALIC) != 0)
            paint.setTextSkewX(-0.25f);

        paint.setTextSize(ts);
        paint.setColor(tc);
        paint.setTypeface(tf);
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        applyCustomTypeFace(ds, newType, textSize, textColour);
    }

    @Override
    public void updateMeasureState(TextPaint paint) {
        applyCustomTypeFace(paint, newType, textSize, textColour);
    }
}
