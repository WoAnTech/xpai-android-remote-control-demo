package cn.com.xpai;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.cnnt.player.Player;
import org.cnnt.player.Surface;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.OrientationEventListener;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;
import cn.com.xpai.core.Manager;
import cn.com.xpai.security.utils.SignatureUtils;

@TargetApi(Build.VERSION_CODES.ECLAIR)
public class XPAndroid extends Activity implements SensorEventListener{
	/** Called when the activity is first created. */
	private SurfaceView mPreview = null;
	private static String TAG = "XPAndroid";
	private static XPAndroid instance = null;

	private static Menu menu = null;

	static String lastPictureFileName = null;

	static MainHandler mainHandler;
	private Timer timer;
	static boolean isAuthSuccess = false;
	private Player player;
	private String fileName;
	private Surface surface = null;
	private static final int START_PLAY = 0x30001;
	private boolean isPlaying = false;
	private String lastPlayUrl;
	private SensorManager sensorManager;
	public static float currentLight;

	static Menu getMenu() {
		return menu;
	}
	
	private OrientationEventListener orientationListener;

	public final static int MENU_UPLOAD_PICTURE = 20004;
	public final static int MENU_UPLOAD_VF_WHOLE = 20013;
	public final static int MENU_UPLOAD_VF = 20014;
	public final static int MENU_TRANSCODER = 20015;
	public static XPAndroid getInstance() {
		return instance;
	}
	
	private Handler playerHandler = new Handler() {
		public void handleMessage(Message msg) {
			if (!Thread.currentThread().isInterrupted()) {
				switch (msg.what) {
				case Player.MSG_OPEN_OK:
					//Toast.makeText(getApplication(), "读取视频文件 " + fileName + " 成功!", Toast.LENGTH_LONG).show();
					break;
				case Player.MSG_OPEN_ERROR:
					isPlaying = false;
					//Toast.makeText(getApplication(), "读取视频文件 " + fileName + " 失败!", Toast.LENGTH_LONG).show();
					break;
				case Player.MSG_PROGRESS_UPDATE:
					break;
				case Player.MSG_PLAYER_STOPPED:
					isPlaying = false;
					//Toast.makeText(getApplication(), "播放结束", Toast.LENGTH_LONG).show();
					break;
				case Player.MSG_READ_ERROR:
					isPlaying = false;
					//Toast.makeText(getApplication(), "读取数据错误", Toast.LENGTH_LONG).show();
					/*下面代码不是必须的而是为了演示在直播过程中网络发生错误时，自动重连*/
					break;
				case START_PLAY:
					if (player != null) {
						player.onDestroy();
					}
					if (!isPlaying) {
						if(fileName != null && fileName.equals(lastPlayUrl)) {
							Log.i(TAG, "the playUrl is the same!");
							return;
						}
						lastPlayUrl = fileName;
						newPlayer();
					}
					isPlaying = true;
					break;
				}
			}
			super.handleMessage(msg);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Hide the window title.
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);
		instance = this;
		timer = new Timer();
		DialogFactory.register(this);
		XPHandler.register(this);
		Config.load(this);
		if (0 != Manager.init(this, XPHandler.getInstance())) {
			Log.e(TAG, "init core manager failed");
		}
		//以下强制明文认证
		//Manager.forceAuthenMode(Manager.HANDSHAKE_CLEAR_PASSWORD);
		Manager.setVideoFpsRange(20, 20);
		List<Manager.Resolution> res_list = Manager.getSupportedVideoResolutions();
		if (null != res_list && res_list.size() > 0) {
			if (0 == Config.videoWidth || 0 == Config.videoHeight) {
				//使用第一个可用分辨率作为默认分辨率
				Manager.Resolution res = res_list.get(0);
				Config.videoWidth = res.width;
				Config.videoHeight = res.height;
				Config.videoBitRate = res.width;
			}
		} else {
			Log.e(TAG, "cannto get supported resolutions");
		}
		Manager.setVideoResolution(Config.videoWidth, Config.videoHeight);
		
		List<Camera.Size> pic_size_list = Manager.getSupportedPictureSizes();
		if (pic_size_list != null) {
			Iterator<Camera.Size> it = pic_size_list.iterator();
			while(it.hasNext()) {
				Camera.Size size = it.next();
				Log.i(TAG, String.format("support picuture size %dx%d", size.width, size.height));
			}
		}
		/*设置网络自适应 true为开启 false为关闭*/
		Manager.setNetWorkingAdaptive(Config.isOpenNetWorkingAdaptive);
		Manager.setAudioRecorderParams(Config.audioEncoderType, Config.channel, Config.audioSampleRate, Config.audioBitRate);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		Message msg = new Message();
		msg.what = XPHandler.MSG_SHOW_CONNECTION_DIALOG;
		XPHandler.getInstance().sendMessage(msg);
		setContentView(R.layout.main);
		mPreview = (SurfaceView) findViewById(R.id.preview_view);
		mPreview.setZOrderMediaOverlay(false);
		mPreview.setZOrderOnTop(false);
		mainHandler = new MainHandler(this);
		
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				if (player != null && isPlaying)
					return;
				if (isAuthSuccess) {
					getTaskList();
				}
			}
		}, 1*1000, 1000*1);
		
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
				                    , SensorManager.SENSOR_DELAY_GAME);

		/* 竖屏拍摄模式，请将注释打开
		Manager.updateOrientation(90);
		if (!Manager.forcePortrait(true)) {
			Log.w(TAG, "force portrait record fail");
		}
		*/
	}
	
	private void getTaskList () {
		String uri="/api/20140928/task_list";
		if(Config.serviceKey == null || "".equals(Config.serviceKey)) {
			return;
		}
		String result = aws_by_getMethod(uri, Config.serviceCode, Config.serviceKey, null);
		if (result == null) {
			return;
		}
		try {
			JSONObject jobj = new JSONObject(result);
			int ret = jobj.getInt("ret");
			if (ret == 0) {
				String opaque = "";
				String httpLiveUrl = "";
				JSONObject opaqueJObj = null;
				JSONArray taskListArray = jobj.getJSONArray("task_list");
				String fName = "";
				for (int i=0;i<taskListArray.length();i++) {
					JSONObject taskObj = taskListArray.getJSONObject(i);
					JSONArray outputsArray = taskObj.getJSONArray("outputs");
					for (int j=0;j<outputsArray.length();j++) {
						JSONObject outputJObj = outputsArray.getJSONObject(j);
						String format = outputJObj.getString("format");
						if ("flv".equals(format)) {
							fName = outputJObj.getString("file_name");
							break;
						}
					}
					opaque = URLDecoder.decode(taskObj.getString("opaque"), "UTF-8");
					try {
						opaqueJObj = new JSONObject(opaque);
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						continue;
					}
					String userName = opaqueJObj.getString("player_user");
					if (Config.userName.equals(userName)) {
						httpLiveUrl = taskObj.getString("http_live_url");
						fileName = httpLiveUrl + fName;
						playerHandler.sendEmptyMessage(START_PLAY);
					}
				}
			} else {
				Log.e(TAG, "Error msg:" + jobj.getString("msg"));
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Log.e(TAG, "get task_list parse json error!");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void newPlayer() {
		Log.v(TAG, "play url: " + fileName);
		player = new Player(getApplication(), playerHandler, fileName, new String[]{"-live"});
		surface = new Surface(getApplication(), player);
	
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
				LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		params.gravity = Gravity.CENTER;
		surface.setLayoutParams(params);
		FrameLayout frameContainer = (FrameLayout) findViewById(R.id.framecontainer);
		frameContainer.addView(surface);
	}
	
	private String aws_by_getMethod(String uri, String code,
			String secret_key, String queryString) {
		String xvs_signature = null;
		BufferedReader reader = null;
		String result = null;
		try {
			String timeStamp = SignatureUtils.getTimeStamp();
			String url_str = "http://c.zhiboyun.com" + uri + "?"
					+ "service_code=" + code;
			xvs_signature = SignatureUtils.getSignature(secret_key, uri, code,
					queryString, timeStamp);
			HttpClient client = new DefaultHttpClient();
			HttpGet request = new HttpGet();
			request.setHeader("xvs-timestamp", timeStamp);
			request.setHeader("xvs-signature", xvs_signature);
			request.setURI(new URI(url_str));
			HttpResponse response = client.execute(request);
			reader = new BufferedReader(new InputStreamReader(response
					.getEntity().getContent()));

			StringBuffer strBuffer = new StringBuffer("");
			String line = null;
			while ((line = reader.readLine()) != null) {
				strBuffer.append(line);
			}
			result = strBuffer.toString();
		} catch (Exception e) {
			e.printStackTrace();
			Log.e(TAG, "get task_list error");
		}

		return result;
	}

	/* Creates the menu items */
	@Override
	public boolean onCreateOptionsMenu(Menu m) {
		menu = m;
		menu.add(0, MENU_UPLOAD_PICTURE, 0, "上传照片");
		menu.add(0, MENU_UPLOAD_VF_WHOLE, 0, "上传离线录制的文件");
		menu.add(0, MENU_UPLOAD_VF, 0, "续传视频文件");
		menu.add(0, MENU_TRANSCODER, 0, "测试视频变换");
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu m) {
		super.onPrepareOptionsMenu(m);
		if (Manager.RecordStatus.IDLE != Manager.getRecordStatus()) {
			m.findItem(MENU_UPLOAD_PICTURE).setEnabled(false);
			m.findItem(MENU_UPLOAD_VF).setEnabled(false);
			m.findItem(MENU_UPLOAD_VF_WHOLE).setEnabled(false);
		} else {
			m.findItem(MENU_UPLOAD_PICTURE).setEnabled(true);
			m.findItem(MENU_UPLOAD_VF).setEnabled(true);
			m.findItem(MENU_UPLOAD_VF_WHOLE).setEnabled(true);
		}
		if (Manager.isConnected() && 
				Manager.RecordStatus.IDLE != Manager.getRecordStatus()) {
			m.findItem(MENU_UPLOAD_PICTURE).setEnabled(false);
			m.findItem(MENU_UPLOAD_VF).setEnabled(false);
		}

		if (!Manager.isConnected()) {
			m.findItem(MENU_UPLOAD_PICTURE).setEnabled(false);
			m.findItem(MENU_UPLOAD_VF).setEnabled(false);
		}

		if (lastPictureFileName == null) {
			m.findItem(MENU_UPLOAD_PICTURE).setEnabled(false);
		}
		
		return true;
	}

	/* Handles item selections */
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_UPLOAD_PICTURE:
			if (null != lastPictureFileName) {
				Manager.uploadFile(lastPictureFileName);
				Log.v(TAG, "upload file name:" + lastPictureFileName);
			} else {
				Message msg = new Message();
				msg.what = XPHandler.SHOW_MESSAGE;
				Bundle bdl = new Bundle();
				bdl.putString(XPHandler.MSG_CONTENT, "未找到最近拍摄的照片!");
			}
			return true;
		case MENU_UPLOAD_VF_WHOLE:
			Intent intent = new Intent(this, FileChooser.class);
			startActivityForResult(intent, 0);
			return true;
		case MENU_UPLOAD_VF:
			intent = new Intent(this, FileChooser.class);
			startActivityForResult(intent, 1);
			return true;
		case MENU_TRANSCODER:
			intent = new Intent(this, FileChooser.class);
			intent .putExtra("Flag", "transcode");
			startActivity(intent);
			return true;
		}
		return false;
	}

	protected void onDestroy() {
		Log.i(TAG, "mini app destroy");
		XPHandler.getInstance().exitApp();
		Manager.deInit();
		if (timer != null) {
			timer.cancel();
		}
		if (player != null) {
			player.onDestroy();
		}
		sensorManager.unregisterListener(this);
		super.onDestroy();
		System.exit(0);
	}

	/* 覆盖 onActivityResult() */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent i) {
		switch (resultCode) {
		case RESULT_OK:
			/* 取得来自Activity2的数据，并显示于画面上 */
			Bundle b = i.getExtras();
			String file_name = b.getString("file_name");
			Log.i(TAG, "Get file name:" + file_name);
			if(!Manager.isConnected()) {
				Toast.makeText(this, "上传离线视频文件,请先连接视频服务器!", Toast.LENGTH_LONG).show();
				return;
			}
			// Manager.uploadVideoFile(..., false)
			// 第二个参数为 false代表新上传一个文件, 服务器总是将上传的数据存为一个新的视频文件
			// 第二个参数为 true 代表续传
			if (!Manager.uploadVideoFile(file_name, requestCode == 1)) {
				// todo 错误处理
				Log.w(TAG, "Upload file failed.");
			}
			break;
		default:
			break;
		}
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		// TODO Auto-generated method stub
		float[] values = event.values;
		currentLight = values[0];
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}

}
