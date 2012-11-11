package com.greentaperacing.olearyp.n4lwp;

import java.lang.reflect.Array;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

public class N4WallpaperService extends WallpaperService {

	@Override
	public Engine onCreateEngine() {
		return new N4WallpaperEngine();
	}

	private class N4WallpaperEngine extends Engine implements SensorEventListener {
//		private boolean mVisible = false;
//		private final Handler mHandler = new Handler();
//		private final Runnable mUpdateDisplay = new Runnable() {
//			@Override
//			public void run() {
//				draw();
//			}
//		};

		private final SensorManager mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
		private final Sensor mRotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
		private float[] rotation = {0.0f, 0.0f, 0.0f};
		
		private final Bitmap[] dots = {
				BitmapFactory.decodeResource(getResources(),R.drawable.dot1),
				BitmapFactory.decodeResource(getResources(),R.drawable.dot2),
				BitmapFactory.decodeResource(getResources(),R.drawable.dot3),
				BitmapFactory.decodeResource(getResources(),R.drawable.dot4),
		};
		
		@Override
		public void onVisibilityChanged(boolean visible) {
//			mVisible = visible;
			if (visible) {
				mSensorManager.registerListener(this, mRotation, SensorManager.SENSOR_DELAY_NORMAL);
				draw();
			} else {
				mSensorManager.unregisterListener(this);
//				mHandler.removeCallbacks(mUpdateDisplay);
			}
		}

		@Override
		public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
			draw();
		}

		@Override
		public void onSurfaceDestroyed(SurfaceHolder holder) {
			super.onSurfaceDestroyed(holder);
//			mVisible = false;
//			mHandler.removeCallbacks(mUpdateDisplay);
		}

		@Override
		public void onDestroy() {
			super.onDestroy();
//			mVisible = false;
			mSensorManager.unregisterListener(this);
//			mHandler.removeCallbacks(mUpdateDisplay);
		}

		private void draw() {
			SurfaceHolder holder = getSurfaceHolder();
			Canvas c = null;

			try {
				c = holder.lockCanvas();
				if (c != null) {
					float[] q = new float[4]; 
					SensorManager.getQuaternionFromVector(q, rotation);
					int intensity = Math.round(255*q[1]);
					
					c.drawColor(Color.BLACK);
					Paint p = new Paint();
					p.setColor(Color.WHITE);
					p.setAntiAlias(true);

					int hMax = c.getHeight();
					int wMax = c.getWidth();
					int r = 10;
					long n = 0;
					for(int ii = 0; ii*2*r < wMax; ii++) {
						for(int jj = 0; jj*2*r < hMax; jj++) {
							p.setAlpha((int) ((intensity*n)%255)/2);
//							c.drawCircle(ii*2*r, jj*2*r, r-2, p);
							c.drawBitmap(dots[(int) (n%Array.getLength(dots))], ii*2*r, jj*2*r, p);
							// Numerical Recipes LCG
							n = (1664525L*n + 1013904223L) % 4294967296L;
						}
					}
				}
			} finally {
				if (c != null)
					holder.unlockCanvasAndPost(c);
			}
//			mHandler.removeCallbacks(mUpdateDisplay);
//			if (mVisible) {
//				mHandler.postDelayed(mUpdateDisplay, 100);
//			}
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			// pass
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			rotation  = event.values;
			draw();
		}
	}
}
