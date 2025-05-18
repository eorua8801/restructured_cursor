package camp.visual.android.sdk.sample.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

public class OverlayCursorView extends View {

    private final Paint circlePaint;
    private final Paint progressPaint;
    private final Paint textPaint;

    private float x = -100;
    private float y = -100;
    private float progressAngle = 0f;
    private String cursorText = "●";
    private boolean showTextAbove = false; // 텍스트 위치 제어 변수 추가

    // 커서 반지름 (약 7.5dp)
    private final float radius;

    public OverlayCursorView(Context context) {
        this(context, null);
    }

    public OverlayCursorView(Context context, AttributeSet attrs) {
        super(context, attrs);

        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(Color.rgb(0x84, 0x5e, 0xc2));  // 보라색 (#845EC2)
        circlePaint.setStyle(Paint.Style.FILL);

        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setColor(Color.GREEN);
        progressPaint.setStyle(Paint.Style.FILL);
        progressPaint.setAlpha(150);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.rgb(0x84, 0x5e, 0xc2));  // 보라색 (#845EC2)로 변경
        textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16, getResources().getDisplayMetrics()));
        textPaint.setTextAlign(Paint.Align.CENTER);

        radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 7.5f, getResources().getDisplayMetrics());
    }

    public void updatePosition(float gazeX, float gazeY) {
        this.x = gazeX;
        this.y = gazeY;
        postInvalidate();
    }

    public void setCursorText(String text) {
        this.cursorText = text;
        postInvalidate();
    }

    // 텍스트 위치 설정 메서드 추가 (true=위, false=아래)
    public void setTextPosition(boolean above) {
        this.showTextAbove = above;
        postInvalidate();
    }

    public void setProgress(float progress) {
        this.progressAngle = 360 * progress;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 보라색 시선 커서 원
        canvas.drawCircle(x, y, radius, circlePaint);

        // 클릭 진행률 원형 게이지
        if (progressAngle > 0) {
            RectF oval = new RectF(x - radius, y - radius, x + radius, y + radius);
            canvas.drawArc(oval, -90, progressAngle, true, progressPaint);
        }

        // 커서 텍스트가 있으면 표시 (예: 스크롤 방향 등)
        if (!cursorText.equals("●")) {
            float textY;

            // 텍스트 위치 결정 (위/아래)
            if (showTextAbove) {
                textY = y - radius * 2.5f; // 텍스트를 커서 위에 표시
            } else {
                textY = y + radius * 2.5f; // 텍스트를 커서 아래에 표시
            }

            canvas.drawText(cursorText, x, textY, textPaint);
        }
    }
}