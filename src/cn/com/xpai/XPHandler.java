package cn.com.xpai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Toast;
import cn.com.xpai.core.AHandler;
import cn.com.xpai.core.Manager;

public class XPHandler extends AHandler {

	private static final String TAG = "XPHandler";
	public static final int MSG_SHOW_CONNECTION_DIALOG = 0x10002;
	public static final int ERR_NOT_REGISTER_DIALOG_FACTORY = 0x10003;
	public static final int ERR_CONNECTION_TIMEOUT = 0x10005;
	public static final int SUCCESS_AUTH = 0x10013;

	public static final int EXIT_APP = 0x10021;
	public static final int APP_PREPARE_RECORD = 0x10022;
	public static final int APP_START_RECORD = 0x10023;
	public static final int APP_STOP_RECORD = 0x10024;

	public static final int NEED_LOGIN = 0x10031;
	public static final int SHOW_MESSAGE = 0x10032;
	public static final int MSG_CONNECTED = 0x10033;
	public static final int TAKE_PICTURE_WITH_FLASH_ON = 0x10034;
	
	public boolean isRetryRecord = false;
	public boolean isStartRecord = false;
	boolean isConnected = false;

	private static XPHandler instance = null;

	private static Context mContext = null;
	
	static final String MSG_CONTENT = "msg_content";
	
	private static String OPEN_FLASH = "open_flash";
	private static String CLOSE_FLASH = "close_flash";
	private static String TAKE_PICTURE = "take_picture";
	private static String FOCUS = "focus";
	private static String ZOOM_IN = "zoom_in";
	private static String ZOOM_OUT = "zoom_out";
	private static String DRAW_RECT = "draw_rect";
	private static String SHOW_MSG = "show_msg";
	private static String DIRECT = "direct";
	
	private int currentZoomLevel = 1, maxZoomLevel = 0;
	
	private Camera camera;

	public static void register(Context context) {
		mContext = context;
		getInstance();
	}

	public static XPHandler getInstance() {
		if (instance == null) {
			instance = new XPHandler();
		}
		return instance;
	}

	public Context getContext() {
		return mContext;
	}

	public void handleMessage(Message msg) {
		switch (msg.what) {
		case MSG_SHOW_CONNECTION_DIALOG:
			DialogFactory.getInstance(DialogFactory.CONNECTION_DIALOG).show();
			break;
		case ERR_NOT_REGISTER_DIALOG_FACTORY:
			DialogFactory.register(mContext);
			break;
		case ERR_CONNECTION_TIMEOUT:
			Toast.makeText(mContext, "Connection timeout!", Toast.LENGTH_SHORT)
					.show();
			DialogFactory.getInstance(DialogFactory.CONNECTION_FAILED_DIALOG,
					(String) msg.obj).show();
			break;
		case EXIT_APP:
			break;
		case NEED_LOGIN:
			Toast.makeText(mContext, "need login!", Toast.LENGTH_SHORT)
			.show();
			DialogFactory.getInstance(DialogFactory.LOGIN_DIALOG).show();
			break;
		case SHOW_MESSAGE:
			Bundle bdl = msg.getData();
			String text = bdl.getString("msg_content");
			Toast.makeText(mContext, text, Toast.LENGTH_LONG).show();
			break;
		case TAKE_PICTURE_WITH_FLASH_ON:
			takePicture();
			break;
		default:
			break;
		}
		super.handleMessage(msg);
	}

	//收到服务器的握手信息，在此方法中发出认证请求
	@Override
	public boolean onHandshake() {
		Log.d(TAG, "onHandshake");
		Message msg = new Message();
		msg.what = NEED_LOGIN;
		XPHandler.getInstance().sendMessage(msg);
		return true;
	}

	public void exitApp() {
		if (isRetryRecord || isStartRecord) {
			Manager.stopRecord();
		}
	}

	//用户认证结果通知
	@Override
	public boolean onAuthResponse(boolean auth_result) {
		if (auth_result) {
			Log.i(TAG, "Login ok");
			XPAndroid.isAuthSuccess = true;
			Message msg = new Message();
			msg.what = SHOW_MESSAGE;
			Bundle bdl = msg.getData();
			bdl.putString(MSG_CONTENT, "登录成功, 请按MENU键继续操作!");
			msg.setData(bdl);
			XPHandler.getInstance().sendMessage(msg);
		} else {
			Log.i(TAG, "Login failed");
			XPAndroid.isAuthSuccess = false;
			Message msg = new Message();
			msg.what = NEED_LOGIN;
			XPHandler.getInstance().sendMessage(msg);
		}
		return true;
	}

	//连接成功通知
	@Override
	public boolean onConnected() {
		Log.i(TAG, "Connect to server successfully");
		isConnected = true;
		Message msg = new Message();
		msg.what = MainHandler.MSG_NETWORK_CONNECTED;
		XPAndroid.mainHandler.sendMessage(msg);
		return true;
	}

	//收到网络异常通知
	@Override
	public boolean onConnectFail(int error_no) {
		Log.i(TAG, "connection lost");
		if (isConnected) {
			Message msg = new Message();
			msg.what = MainHandler.MSG_NETWORK_DISCONNECT;
			msg.arg1 = error_no;
			XPAndroid.mainHandler.sendMessage(msg);
		} else {
			//建立连接失败，显示对话框让用户确认连接参数
			Message msg = new Message();
			msg.what = ERR_CONNECTION_TIMEOUT;
			XPHandler.getInstance().sendMessage(msg );
		}
		return true;
	}
	
	//收到服务器为当前录制的直播视频分配的ID
	@Override
	public boolean onStreamIdNotify(String stream_id) {
		Log.i(TAG, "Get Stream ID: " + stream_id);
		Message msg = new Message();
		msg.what = SHOW_MESSAGE;
		Bundle bdl = new Bundle();
		bdl.putString(MSG_CONTENT, "Got Stream ID:" + stream_id);
		msg.setData(bdl);
		XPHandler.getInstance().sendMessage(msg);
		return true;
	}

	//收到文件上传开始通知
	@Override
	public boolean onUploadFileStart(String file_name) {
		Log.i(TAG, "onUploadFileStart: " + file_name);
		Message msg = new Message();
		msg.what = SHOW_MESSAGE;
		Bundle bdl = new Bundle();
		bdl.putString(MSG_CONTENT, "Start uploading file:" + file_name);
		msg.setData(bdl);
		XPHandler.getInstance().sendMessage(msg);
		return true;
	}
	
	//收到文件上传结束通知
	@Override
	public boolean onUploadFileEnd(String file_id, String file_name) {
		Log.i(TAG, "onUploadFileEnd: " + file_id + " " + file_name);
		Message msg = new Message();
		msg.what = SHOW_MESSAGE;
		Bundle bdl = new Bundle();
		bdl.putString(MSG_CONTENT, "Finished uploading file:" + file_id);
		msg.setData(bdl);
		XPHandler.getInstance().sendMessage(msg);
		return true;
	}

	//收到照片文件生成通知
	@Override
	public boolean onTakePicture(String file_name) {
		Message msg = new Message();
		msg.what = SHOW_MESSAGE;
		Bundle bdl = new Bundle();
		if (null == file_name) {
			Log.i(TAG, "take picture fail" );
			bdl.putString(MSG_CONTENT, "Take picture failed");
		} else {
			Log.i(TAG, "take picture ok: " + file_name);
			bdl.putString(MSG_CONTENT, "Take picture: " + file_name);
			switchFlashLight(false);
		}
		msg.setData(bdl);
		XPHandler.getInstance().sendMessage(msg);
		XPAndroid.lastPictureFileName = file_name;
		return true;
	}
	
	//收到服务器传来的文本消息
	@Override
	public boolean onRecvTextMessage(String from, String what) {
		String text = String.format("Recv text msg from %s: %s", from, what);
		Log.i(TAG, text);
		try {
			JSONObject jobj = new JSONObject(what);
			String cmd = jobj.getString("cmd");
			if (OPEN_FLASH.equals(cmd)) {
				switchFlashLight(true);
			} else if (CLOSE_FLASH.equals(cmd)) {
				switchFlashLight(false);
			} else if (TAKE_PICTURE.equals(cmd)) {
				if (XPAndroid.currentLight <= 20) {//该亮度阈值可根据需要进行设定
					switchFlashLight(true);
					getInstance().sendEmptyMessageDelayed(TAKE_PICTURE_WITH_FLASH_ON,
							1500);
				} else {
					takePicture();
				}
			} else if (FOCUS.equals(cmd)) {
				JSONArray jArray = jobj.getJSONArray("params");
				double x = jArray.getDouble(0);
				double y = jArray.getDouble(1);
				double width = jArray.getDouble(2);
				double height = jArray.getDouble(3);
				RectDraw rectDraw = (RectDraw) (((XPAndroid)mContext).findViewById(R.id.rect_view));
				rectDraw.setVisibility(View.VISIBLE);
				rectDraw.destroyDrawingCache();
				rectDraw.drawRect(x, y, width, height, true);
			} else if (ZOOM_IN.equals(cmd)) {
				JSONArray jArray = jobj.getJSONArray("params");
				int zoomStep = jArray.getInt(0);
				zoom(true, zoomStep);
			} else if (ZOOM_OUT.equals(cmd)) {
				JSONArray jArray = jobj.getJSONArray("params");
				int zoomStep = jArray.getInt(0);
				zoom(false, zoomStep);
			} else if (DRAW_RECT.equals(cmd)){
				JSONArray jArray = jobj.getJSONArray("params");
				double x = jArray.getDouble(0);
				double y = jArray.getDouble(1);
				double width = jArray.getDouble(2);
				double height = jArray.getDouble(3);
				RectDraw rectDraw = (RectDraw) (((XPAndroid)mContext).findViewById(R.id.rect_view));
				rectDraw.setVisibility(View.VISIBLE);
				rectDraw.destroyDrawingCache();
				rectDraw.drawRect(x, y, width, height, false);
			} else if (SHOW_MSG.equals(cmd)) {
				JSONArray jArray = jobj.getJSONArray("params");
				String msgStr = jArray.getString(0);
				Message msg = getInstance().obtainMessage();
				msg.what = SHOW_MESSAGE;
				Bundle bdl = msg.getData();
				bdl.putString(MSG_CONTENT, msgStr);
				msg.setData(bdl);
				XPHandler.getInstance().sendMessage(msg);
			} else if (DIRECT.equals(cmd)) {
				JSONArray jArray = jobj.getJSONArray("params");
				String direct = jArray.getString(0);
				RectDraw rectDraw = (RectDraw) (((XPAndroid)mContext).findViewById(R.id.rect_view));
				rectDraw.setVisibility(View.VISIBLE);
				rectDraw.destroyDrawingCache();
				rectDraw.drawDirect(direct);
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Log.e(TAG, "cmd json parse error!");
		}
		
		return true;
	}
	
	//收到录制停止通知
	@Override
	public boolean onRecordFinished(long data_size, int duration) {
		String text = String.format("Finished Record %s, %s", data_size, duration);
		Log.i(TAG, text);
		Message msg = new Message();
		msg.what = SHOW_MESSAGE;
		Bundle bdl = new Bundle();
		bdl.putString(MSG_CONTENT, text);
		msg.setData(bdl);
		XPHandler.getInstance().sendMessage(msg);
		return true;
	}
	
	//收到视频上传完毕通知
	@Override
	public boolean onStreamUploaded(String stream_id) {
		String text = String.format("Finished uploading stream %s", stream_id);
		Log.i(TAG, text);
		Message msg = new Message();
		msg.what = SHOW_MESSAGE;
		Bundle bdl = new Bundle();
		bdl.putString(MSG_CONTENT, text);
		msg.setData(bdl);
		XPHandler.getInstance().sendMessage(msg);
		return true;
	}
	
	//收到本地视频文件名及路径的通知
	@Override
	public boolean onLocalFilename(String fname) {
		String text = String.format("Video File name:%s", fname);
		Log.i(TAG, text);
		Message msg = new Message();
		msg.what = SHOW_MESSAGE;
		Bundle bdl = new Bundle();
		bdl.putString(MSG_CONTENT, text);
		msg.setData(bdl);
		XPHandler.getInstance().sendMessage(msg);
		return true;
	}
	
	/*
	 * 续传或上传离线视频文件解析开始通知
	 * 续传或上传离线文件分为两个步骤， 一个步骤是对视频文件的解析，另一个步骤是向服务器传送数据
	 * 这里是解析步骤开始的回调
	 * @param duration 视频文件时长
	 */
	public boolean onParseVideoFileStart(int duration, int file_size) {
		String text = String.format("parse Video File start, duration: %d, file size: %d"
				,duration, file_size);
		Log.i(TAG, text);
		Message msg = new Message();
		msg.what = SHOW_MESSAGE;
		Bundle bdl = new Bundle();
		bdl.putString(MSG_CONTENT, text);
		msg.setData(bdl);
		XPHandler.getInstance().sendMessage(msg);
		return true;
	}
	
	
	/*
	 * 续传或上传离线视频文件解析结束通知
	 * 当此回调发生时不代表视频已经全部上传完毕，
	 * 视频上传完毕的条件是：收到此回调后，并且Manager.getCacheRemaining() 为零才是全部上传完成
	 * @audio_pkt_cnt 音频包数目
	 * @video_pkt_cnt 视频包数目
	 * @data_size 数据总量(byte为单位)
	 * 
	 */
	public boolean onParseVideoFileEnd(int audio_pkt_cnt, int video_pkt_cnt, int data_size) {
		String text = String.format("parse Video File end,  audio pkt: %d, video pkt: %d data_size: %d", 
				audio_pkt_cnt, video_pkt_cnt, data_size);
		Log.i(TAG, text);
		Message msg = new Message();
		msg.what = SHOW_MESSAGE;
		Bundle bdl = new Bundle();
		bdl.putString(MSG_CONTENT, text);
		msg.setData(bdl);
		XPHandler.getInstance().sendMessage(msg);
		return true;
	}
	
	/*
	 * 续传或上传离线文件解析进度更新
	 * @param processed_data_size 已经完成处理的数据量
	 * @param file_size 文件大小
	 */
	
	int last_progress = 0;
	public boolean onParseVideoFileUpdate(int processed_data_size, int file_size) {
		//这是计算进度的简单算法, 注意计算进度时要考虑到发送cache中未发送的数据量
		float fp = ((float)(processed_data_size)) / (file_size + Manager.getCacheRemaining());
		int progress = (int) (fp * 100);
		if (last_progress == progress) {
			return true; //避免打印过多重复的日志信息
		}
		last_progress = progress;
		String text = String.format("uploading progress: %d",  progress);
		Log.d(TAG, text);
		return true;
	}
	
	//收到本地视频文件名及路径的通知
	@Override
	public boolean onRecvAudioMessage(int data_size, String fname) {
		String text = String.format("Recv Audio Message, size: %d, name: %s",
				data_size, fname);
		Log.i(TAG, text);
		Message msg = new Message();
		msg.what = SHOW_MESSAGE;
		Bundle bdl = new Bundle();
		bdl.putString(MSG_CONTENT, text);
		msg.setData(bdl);
		XPHandler.getInstance().sendMessage(msg);
		return true;
	}
	
	private void switchFlashLight(boolean isOn) {
		if (!Manager.isPreviewing()) {
			Message msg = new Message();
			msg.what = SHOW_MESSAGE;
			Bundle bdl = new Bundle();
			bdl.putString(MSG_CONTENT, "请打开预览！");
			msg.setData(bdl);
			XPHandler.getInstance().sendMessage(msg);
			return;
		}
		camera = Manager.getCamera();
		if (camera == null)
			return;
		Parameters parameters = camera.getParameters();
		String flashModel = parameters.getFlashMode();
		if (flashModel == null)
			return;
		if (flashModel.equals(Parameters.FLASH_MODE_TORCH)) {
			if (!isOn)
			flashModel = Parameters.FLASH_MODE_OFF;
		} else {
			if (isOn)
			flashModel = Parameters.FLASH_MODE_TORCH;
		}
		parameters.setFlashMode(flashModel);
		camera.setParameters(parameters);
	}
	
	private void takePicture() {
		Camera.Size picSize = null;
		if (Manager.isPreviewing()) {
			if (null == picSize) {
				List <Camera.Size> size_list = Manager.getSupportedPictureSizes();
				if (null != size_list) {
					if(!size_list.isEmpty()) {
						Camera.Size firstSize = size_list.get(0);
						Camera.Size lastSize = size_list.get(size_list.size() - 1);
						if (firstSize.width > lastSize.width) {
							picSize = firstSize;
						} else {
							picSize = lastSize;
						}
					}
				} else {
					Log.w(TAG, "Can't supported take picture");
				}
			}
			if (null != picSize) {
				Log.i(TAG, "picSize width-->" + picSize.width + "picHeight-->" + picSize.height);
				Manager.takePicture("/sdcard/xpai", picSize.width, picSize.height);
			}
		} else {
			Message msg = new Message();
			msg.what = SHOW_MESSAGE;
			Bundle bdl = new Bundle();
			bdl.putString(MSG_CONTENT, "请打开预览！");
			msg.setData(bdl);
			XPHandler.getInstance().sendMessage(msg);
			Log.w(TAG, "Camera is not previewing, can't take picture");
		}
	}
	
	private void zoom(boolean isZoomIn, int zoomStep) {
		Message msg = new Message();
		msg.what = SHOW_MESSAGE;
		Bundle bdl = new Bundle();
		if (Manager.isPreviewing() && Manager.isZoomSupported()) {
			maxZoomLevel = Manager.getMaxZoomLevel();
			
			if (isZoomIn) {
				currentZoomLevel += zoomStep;
				if(currentZoomLevel <= maxZoomLevel) {
        				Manager.setZoom(currentZoomLevel, true);
        			} else {
        				currentZoomLevel = maxZoomLevel;
        				bdl.putString(MSG_CONTENT, "画面已放至最大！");
        				msg.setData(bdl);
        				XPHandler.getInstance().sendMessage(msg);
        				Log.e(TAG, "can't zoomIn!");
        			}
			} else {
				currentZoomLevel -= zoomStep;
				if(currentZoomLevel >= 0) {
        				Manager.setZoom(currentZoomLevel, true);
        			} else {
        				currentZoomLevel = 0;
        				bdl.putString(MSG_CONTENT, "画面已缩至最小！");
        				msg.setData(bdl);
        				XPHandler.getInstance().sendMessage(msg);
        				Log.e(TAG, "can't zoomOut!");
        			}
			}
		} else {
			bdl.putString(MSG_CONTENT, "请打开预览！");
			msg.setData(bdl);
			XPHandler.getInstance().sendMessage(msg);
			Log.w(TAG, "can't zoomIn or zoomOut");
		}
	}
}
