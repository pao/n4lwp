/*
Copyright (c) 2012, Patrick O'Leary
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met: 

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer. 
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution. 

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.greentaperacing.olearyp.n4lwp;

import java.util.Random;

import org.andengine.engine.camera.Camera;
import org.andengine.engine.handler.timer.ITimerCallback;
import org.andengine.engine.handler.timer.TimerHandler;
import org.andengine.engine.options.EngineOptions;
import org.andengine.engine.options.ScreenOrientation;
import org.andengine.engine.options.resolutionpolicy.RatioResolutionPolicy;
import org.andengine.entity.scene.Scene;
import org.andengine.entity.scene.background.Background;
import org.andengine.entity.sprite.Sprite;
import org.andengine.entity.sprite.batch.SpriteGroup;
import org.andengine.extension.ui.livewallpaper.BaseLiveWallpaperService;
import org.andengine.opengl.texture.TextureOptions;
import org.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlas;
import org.andengine.opengl.texture.region.TextureRegion;
import org.andengine.opengl.texture.region.TextureRegionFactory;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.preference.PreferenceManager;

public class N4WallpaperService extends BaseLiveWallpaperService implements SensorEventListener {


	private SensorManager mSensorManager = null;
	private Sensor mRotation = null;
	private Sensor mLight = null;
	private final float illumMax = 700f;
	private float[] rotation = {0.0f, 0.0f, 0.0f};
	private float illum = illumMax;

	private final float[] dotAngles = new float[20];
	private Bitmap dotImg = null;

	private SharedPreferences prefs = null;

	private final int CAMERA_WIDTH = 480;
	private final int CAMERA_HEIGHT = 720;
	private final int CELL_SIZE = 64;

	private BitmapTextureAtlas texAtlas;
	private TextureRegion dotRegion;
	protected boolean settings_changed;
	private Scene scene;
	private SpriteGroup[] dotBatches;
	private Sprite[][] dotGrid;

	@Override
	public EngineOptions onCreateEngineOptions() {
		return new EngineOptions(true, ScreenOrientation.LANDSCAPE_FIXED, new RatioResolutionPolicy(CAMERA_WIDTH, CAMERA_HEIGHT), new Camera(0,0,CAMERA_WIDTH, CAMERA_HEIGHT));
	}

	@Override
	public void onCreateResources(
			OnCreateResourcesCallback pOnCreateResourcesCallback)
					throws Exception {

		for(int ii = 0; ii < dotAngles.length; ii++) {
			dotAngles[ii] = (float) (ii * Math.PI / dotAngles.length);
		}

		prefs = PreferenceManager.getDefaultSharedPreferences(N4WallpaperService.this);
		dotImg = makeDot(0);

		texAtlas = new BitmapTextureAtlas(getTextureManager(), CELL_SIZE, CELL_SIZE, TextureOptions.BILINEAR_PREMULTIPLYALPHA);
		BitmapTextureAtlasSource source = new BitmapTextureAtlasSource(dotImg);
		texAtlas.addTextureAtlasSource(source, 0, 0);
		texAtlas.load();
		dotRegion = (TextureRegion) TextureRegionFactory.createFromSource(texAtlas, source, 0, 0);

		pOnCreateResourcesCallback.onCreateResourcesFinished();
	}

	@Override
	public void onCreateScene(OnCreateSceneCallback pOnCreateSceneCallback)
			throws Exception {
		scene = new Scene();
		scene.setBackground(new Background(colorFromInt(prefs.getInt("color_bg", Color.BLACK))));
		pOnCreateSceneCallback.onCreateSceneFinished(scene);
	}

	@Override
	public void onPopulateScene(Scene pScene,
			OnPopulateSceneCallback pOnPopulateSceneCallback) throws Exception {

		mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
		mRotation = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
		mLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

		mSensorManager.registerListener(this, mRotation, SensorManager.SENSOR_DELAY_NORMAL);
		if(mLight != null) {
			mSensorManager.registerListener(this, mLight, SensorManager.SENSOR_DELAY_NORMAL);
		}

		prefs.registerOnSharedPreferenceChangeListener(prefListener);

		dotSetup();
		
		pOnPopulateSceneCallback.onPopulateSceneFinished();
	}

	private void dotSetup() {
		org.andengine.util.color.Color color_fg = colorFromInt(prefs.getInt("color_fg", Color.WHITE));

		dotBatches = new SpriteGroup[dotAngles.length];
		for(int ii = 0; ii < dotBatches.length; ii++) {
			dotBatches[ii] = new SpriteGroup(texAtlas, 500, getVertexBufferObjectManager());
			dotBatches[ii].setBlendingEnabled(true);
			dotBatches[ii].setBlendFunction(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
			dotBatches[ii].setColor(color_fg);
		}

		int minScreenDim = Math.min(CAMERA_WIDTH, CAMERA_HEIGHT);
		float gridSize  = ((float) minScreenDim)/Float.valueOf(prefs.getString("dot_num_across", "40"));
		final Random rng = new Random(0);
		dotGrid = new Sprite[(int) Math.ceil(CAMERA_WIDTH/gridSize)][(int) Math.ceil(CAMERA_HEIGHT/gridSize)];
		for(int ii = 0; ii*gridSize < CAMERA_WIDTH; ii++) {
			for(int jj = 0; jj*gridSize < CAMERA_HEIGHT; jj++) {
				int orientation = rng.nextInt(dotAngles.length);
				dotGrid[ii][jj] = new Sprite(ii*gridSize, jj*gridSize, gridSize, gridSize, dotRegion, getVertexBufferObjectManager());
				dotGrid[ii][jj].setRotation(dotAngles[orientation]*180/(float) Math.PI);
				dotGrid[ii][jj].setColor(color_fg);
				dotGrid[ii][jj].setUserData(Integer.valueOf(orientation));
//				dotGrid[ii][jj].setBlendingEnabled(true);
//				dotGrid[ii][jj].setBlendFunction(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
//				scene.attachChild(dotGrid[ii][jj]);
				dotBatches[orientation].attachChild(dotGrid[ii][jj]);
			}
		}

		for(int ii = 0; ii < dotBatches.length; ii++) {
			scene.attachChild(dotBatches[ii]);
		}		

	}

	@Override
	public void onDestroy() {
		mSensorManager.unregisterListener(this);
		super.onDestroy();
	}

	private SharedPreferences.OnSharedPreferenceChangeListener prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {

		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
				String key) {
			scene.getBackground().setColor(colorFromInt(prefs.getInt("color_bg", Color.BLACK)));
			dotImg = makeDot(0);
			BitmapTextureAtlasSource source = new BitmapTextureAtlasSource(dotImg);
			texAtlas.addTextureAtlasSource(source, 0, 0);
			texAtlas.load();
			scene.detachChildren();
			scene.clearEntityModifiers();
			scene.clearTouchAreas();
			scene.clearUpdateHandlers();
			dotSetup();
		}
	};

	private void draw() {
		float[] R_wd = new float[16];
		float[] R_dw = new float[16];
		SensorManager.getRotationMatrixFromVector(R_wd, rotation);
		Matrix.transposeM(R_dw, 0, R_wd, 0);

		// Compute vector normal to screen in world coordinates
		final float[] n_d = {0.0f, 0.0f, 1.0f, 1.0f};
		float[] n_w = new float[4];
		Matrix.multiplyMV(n_w, 0, R_dw, 0, n_d, 0);

		// cos(theta) = n_w[2]
		double theta = Math.acos(n_w[2]);

		// Compute screen up vector in world coordinates
		final float[] u_d = {0.0f, 1.0f, 0.0f, 1.0f};
		float[] u_w = new float[4];
		Matrix.multiplyMV(u_w, 0, R_dw, 0, u_d, 0);

		// Compute projection of world up vector onto screen, expressed in world
		float[] proj_wu_d_w = {-n_w[2]*n_w[0], -n_w[2]*n_w[1], 1.0f-n_w[2]*n_w[2], 1.0f};

		// cos(psi) = dot(u_w, proj_wu_w)/norm(proj_wu_w)
		double psi = Math.acos((u_w[0]*proj_wu_d_w[0] + u_w[1]*proj_wu_d_w[1] + u_w[2]*proj_wu_d_w[2])/norm(proj_wu_d_w));

		// We need to give psi a sign, which we can do by checking the projection in display coordinates
		float[] proj_wu_d_d = new float[4];
		Matrix.multiplyMV(proj_wu_d_d, 0, R_wd, 0, proj_wu_d_w, 0);
		if(proj_wu_d_d[0] < 0) {
			psi = -psi;
		}

		float illum_adj = 1.0f;
		if(prefs.getBoolean("luminance_enabled", true) && illum < illumMax) {
			illum_adj = (illum + 100)/(illumMax + 100);
		}

		for(int ii = 0; ii < dotGrid.length; ii++) {
			for(int jj = 0; jj < dotGrid[ii].length; jj++) {
				dotGrid[ii][jj].setAlpha((float) (Math.abs(Math.sin(theta*2.0) * Math.sin(psi - dotAngles[(Integer) dotGrid[ii][jj].getUserData()])) * illum_adj));
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// pass
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if(event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
			rotation  = event.values;
		} else if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
			illum = event.values[0];
		}
		if(isGameRunning()) {
			draw();
		}
	}

	private Bitmap makeDot(float angle) {
		final float dotSize = ((float) CELL_SIZE)*(Float.valueOf(prefs.getString("dot_fill_pct", "80")))/100.0f;

		Bitmap b = Bitmap.createBitmap(CELL_SIZE, CELL_SIZE, Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(b);
		Paint p = new Paint();
		p.setColor(Color.WHITE);
		p.setAntiAlias(true);

		c.drawCircle(CELL_SIZE/2.0f, CELL_SIZE/2.0f, dotSize/2.0f, p);

		if(prefs.getBoolean("dot_draw_lines", true)) {
			p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
			p.setStrokeWidth(dotSize/8);

			android.graphics.Matrix R_if = new android.graphics.Matrix();
			R_if.setRotate(angle*180f/(float) Math.PI, CELL_SIZE/2.0f, CELL_SIZE/2.0f);
			float[] lineEnds = {
					CELL_SIZE/2.0f, 0,
					CELL_SIZE/2.0f, CELL_SIZE,
					CELL_SIZE/2.0f-dotSize/4.0f, 0,
					CELL_SIZE/2.0f-dotSize/4.0f, CELL_SIZE,
					CELL_SIZE/2.0f+dotSize/4.0f, 0,
					CELL_SIZE/2.0f+dotSize/4.0f, CELL_SIZE,
			};
			R_if.mapPoints(lineEnds);
			c.drawLines(lineEnds, p);
		}
		return b;
	}

	private float norm(float[] vector) {
		return (float) Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1]
				+ vector[2] * vector[2]);
	}

	private org.andengine.util.color.Color colorFromInt(int color) {
		return new org.andengine.util.color.Color(Color.red(color)/255f, Color.green(color)/255f, Color.blue(color)/255f);
	}

}
