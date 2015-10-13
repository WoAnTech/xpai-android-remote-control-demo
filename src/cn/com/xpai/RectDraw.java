package cn.com.xpai;

import java.util.ArrayList;
import java.util.List;

import cn.com.xpai.core.Manager;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class RectDraw extends SurfaceView implements SurfaceHolder.Callback {

	private static final String TAG = "RectDraw";
	protected SurfaceHolder sh;
	private int mWidth;
	private int mHeight;
	private Context mContext;
	private Paint clearPaint = null;
	
	private Handler handler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			clearLastDraw();
		}
	};

	public RectDraw(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
		mContext = context;
		sh = getHolder();
		sh.addCallback(this);
		sh.setFormat(PixelFormat.TRANSPARENT);
		setZOrderOnTop(true);
		clearPaint = new Paint();
		clearPaint.setXfermode(new PorterDuffXfermode(Mode.CLEAR));
		setWillNotDraw(false);
	}

	public void surfaceChanged(SurfaceHolder arg0, int arg1, int w, int h) {
		// TODO Auto-generated method stub
		mWidth = w;
		mHeight = h;
	}

	public void surfaceCreated(SurfaceHolder arg0) {
		// TODO Auto-generated method stub

	}

	public void surfaceDestroyed(SurfaceHolder arg0) {
		// TODO Auto-generated method stub

	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	public void drawRect(double x, double y, double width, double height, boolean isFocusArea) {
		clearLastDraw();
		Canvas canvas = sh.lockCanvas();
		canvas.drawPaint(clearPaint);
		canvas.drawColor(Color.TRANSPARENT);
		Paint p = new Paint();
		p.setAntiAlias(true);
		if (isFocusArea) {
			p.setColor(Color.GREEN);
			p.setStyle(Style.STROKE);
		} else {
			p.setColor(Color.RED);
			p.setStyle(Style.STROKE);
		}
		Display display = ((XPAndroid) mContext).getWindowManager()
				.getDefaultDisplay();
		int screenWidth = display.getWidth();
		int screenHeight = display.getHeight();
		int t_x = (int) (x * screenWidth);
		int t_y = (int) (y * screenHeight);
		Rect rect = new Rect();
		rect.left = t_x;
		rect.top = t_y;
		rect.right = t_x + (int) (width * screenWidth);
		rect.bottom = t_y + (int) (height * screenHeight);
		Log.i(TAG, "touch rect:" + rect.top + "," + rect.left + ","
				+ rect.bottom + "," + rect.right);
		canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom, p);
		sh.unlockCanvasAndPost(canvas);
		handler.removeMessages(0);
		if (isFocusArea) {
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
				return;
			}
			Rect focusRect = getFocusRect(t_x, t_y);
			Camera.Area area = new Camera.Area(focusRect, 10);
            List<Camera.Area> list = new ArrayList<Camera.Area>();
            list.add(area);
            Manager.setMeteringAreas(list);
            handler.sendEmptyMessageDelayed(0, 1200);
		} else {
			handler.sendEmptyMessageDelayed(0, 5000);
		}
	}

	public void clearLastDraw() {
		try {
			Canvas canvas = sh.lockCanvas();
			canvas.drawPaint(clearPaint);
			sh.unlockCanvasAndPost(canvas);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private Rect getFocusRect(double x, double y) {
		int t_x = (int) (-1000 + x );
        int t_y = (int) (-1000 + y );
        Rect rect = new Rect();
        rect.left = t_x - 150;
        rect.top =  t_y - 150;
        rect.right = t_x + 150;
        rect.bottom =  t_y + 150;
        rect.left = rect.left < -1000 ? -1000 : rect.left;
        rect.top = rect.top < -1000 ? -1000 : rect.top;
        rect.right = rect.right > 1000 ? 1000 : rect.right;
        rect.bottom = rect.bottom > 1000 ? 1000 : rect.bottom;
        Log.i(TAG, "touch x:" + x + " y:" + y);
        Log.i(TAG, "touch rect:" + rect.top + "," + rect.left +  "," + rect.bottom + "," + rect.right);
        return rect;
	}
	
	public void drawDirect(String direct) {
		float x = 0;
		float y = 0;
		int picId = 0;
		Display display = ((XPAndroid) mContext).getWindowManager()
				.getDefaultDisplay();
		int screenWidth = display.getWidth();
		int screenHeight = display.getHeight();
		if ("left".equals(direct)) {
			picId = R.drawable.left;
			x = screenWidth/6 ;
			y = screenHeight/2;
		} else if ("right".equals(direct)) {
			picId = R.drawable.right;
			x = screenWidth - screenWidth/6;
			y = screenHeight/2;
		} else if ("up".equals(direct)) {
			picId = R.drawable.top;
			x = screenWidth/2;
			y = screenHeight/4;
		} else {
			picId = R.drawable.down;
			x = screenWidth/2;
			y = screenHeight - screenHeight/4;
		}
		drawBitmap(picId, x, y);
	}
	
	private void drawBitmap(int picId, float argX, float argY) {
		clearLastDraw();
		Bitmap bitmap = BitmapFactory.decodeResource(getResources(), picId);
		float x = argX - bitmap.getWidth()/2;
		float y = argY - bitmap.getHeight()/2;
		Paint p = new Paint();
		Canvas canvas = getHolder().lockCanvas();
		canvas.drawPaint(clearPaint);
		canvas.drawBitmap(bitmap, x, y,p);
		getHolder().unlockCanvasAndPost(canvas);
		handler.removeMessages(0);
		handler.sendEmptyMessageDelayed(0, 5000);
	}

}
