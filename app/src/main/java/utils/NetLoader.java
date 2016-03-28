package utils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

import org.apache.http.message.BasicNameValuePair;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.FormEncodingBuilder;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.MultipartBuilder;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

public class NetLoader {
	private static final MediaType MEDIA_TYPE_PNG = MediaType
			.parse("image/png");
	public static final MediaType JSON = MediaType
			.parse("application/json; charset=utf-8");

	private static NetLoader mInstance;
	/**
	 * 多次网络请求的类型 这里先默认先进先出，在初始化的时候可以进行重置
	 */
	private Type type = Type.FIFO;
	/**
	 * 线程池
	 */
	private ExecutorService mThreadPool;
	/**
	 * 设置默认线程数为1
	 */
	private static final int DEFAULT_THREAD_COUNT = 1;
	/**
	 * 后台轮询线程
	 */
	private Thread mPoolThread;
	private LinkedList<Runnable> mTaskQueue;
	/**
	 * 创建ui线程的handler
	 */
	private Handler UIhandler;
	/**
	 * 创建子线程的handler
	 */
	private Handler childHandler;
	/**
	 * 这里使用的是okhttp
	 * 
	 */
	private OkHttpClient client;
	private Map<String, NetResponseListener> listeners;
	/**
	 * 定义管理子线程中handler的信量化
	 */
	private Semaphore mSemaphoreChildHander = new Semaphore(0);;
	/**
	 * 定义管理线程池的信量化
	 */
	private Semaphore mSemaphoreThreadPool;

	/**
	 * 一下为uihandler的所用的常量
	 */
	private static final int UI_GET_FAILED = 0X100;
	private static final int UI_POST_FAILED = 0X101;
	private static final int UI_POST_FILE_FAILED = 0X102;
	private static final int UI_POST_PARAS_FILE_FAILED = 0X103;
	private static final int UI_POST_COOKIE_FAILE = 0x104;

	private static final int UI_GET_SUCCED = 0x110;
	private static final int UI_POST_SUCCED = 0X111;
	private static final int UI_POST_FILE_SUCCED = 0X112;
	private static final int UI_POST_PARAS_FILE_SUCCED = 0X113;
	private static final int UI_POST_COOKIE_SUCCED = 0X115;

	private static final int CHILD_MES = 0x114;

	/**
	 * 以下为具体代码
	 * ********************************************************************/
	public enum Type {
		FIFO, LIFO
	}

	public interface NetResponseListener {
		void success(String resultString);

		void fail(String failString, Exception e);
	}

	private NetLoader(int threadCount, Type type) {
		init(threadCount, type);
	}

	/**
	 * 初始化方法
	 * 
	 * @param threadCount
	 * @param type
	 */
	private void init(int threadCount, Type type) {
		mSemaphoreThreadPool = new Semaphore(threadCount);
		mPoolThread = new Thread(new Runnable() {

			@Override
			public void run() {
				// TODO Auto-generated method stub
				Looper.prepare();
				childHandler = new Handler() {
					public void handleMessage(Message msg) {
						mThreadPool.execute(getTask());
						try {
							mSemaphoreThreadPool.acquire();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

					}

				};
				mSemaphoreChildHander.release();// 在此通知handler初始化完毕
				Looper.loop();

			}
		});
		mPoolThread.start();
		mThreadPool = Executors.newFixedThreadPool(threadCount);
		mTaskQueue = new LinkedList<Runnable>();
		listeners = new HashMap<String, NetResponseListener>();
		this.type = type;
		client = new OkHttpClient();
		UIhandler = new Handler() {
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case UI_GET_FAILED:// GET网络数据加载失败
					failData(msg);
					break;
				case UI_POST_FILE_FAILED:// post上传文件失败
					failData(msg);
					break;
				case UI_POST_FAILED:// post请求失败
					failData(msg);
					break;
				case UI_POST_PARAS_FILE_FAILED:// post上传文件和参数失败
					failData(msg);
					break;
				case UI_POST_COOKIE_FAILE:
					failData(msg);
					break;
				case UI_GET_SUCCED:// get请求加载成功
					sucData(msg);
					break;
				case UI_POST_FILE_SUCCED:// 上传文件成功
					sucData(msg);
					break;
				case UI_POST_PARAS_FILE_SUCCED:// 上传文件以及携带参数成功
					sucData(msg);
					break;
				case UI_POST_SUCCED:// post请求成功
					sucData(msg);
					break;
				case UI_POST_COOKIE_SUCCED:
					sucData(msg);
					break;

				default:
					break;
				}

			};
		};

	}

	/****************************************************************/
	/**
	 * 数据成功的回调
	 * 
	 * @param message
	 */
	private void sucData(Message message) {
		if (message != null) {
			SucHolder holder = (SucHolder) message.obj;
			String url = holder.url;
			String result = holder.result;
			if (listeners.containsKey(url)) {
				NetResponseListener listener = listeners.get(url);
				listener.success(result);
			}
			listeners.remove(url);
		}

	}

	private void failData(Message message) {
		if (message != null) {
			FailHolder holder = (FailHolder) message.obj;
			String url = holder.url;
			String failString = holder.failString;
			Exception e = holder.exception;
			if (listeners.containsKey(url)) {
				NetResponseListener listener = listeners.get(url);
				listener.fail(failString, e);

			}
			listeners.remove(url);
		}
	}

	/*********************************************************************/

	/**
	 * 采用懒加载
	 * 
	 * @return
	 */
	public static NetLoader getInstance() {
		if (mInstance == null) {
			synchronized (NetLoader.class) {
				if (mInstance == null) {
					mInstance = new NetLoader(DEFAULT_THREAD_COUNT, Type.LIFO);
				}
			}
		}
		return mInstance;
	}

	/**
	 * 可以用此方法现在application中初始化
	 * 
	 * @param threadCount
	 * @param type
	 * @return
	 */
	public static NetLoader getInstance(int threadCount, Type type) {
		if (mInstance == null) {
			synchronized (NetLoader.class) {
				if (mInstance == null) {
					mInstance = new NetLoader(threadCount, type);
				}
			}
		}
		return mInstance;
	}

	/**
	 * 加载get请求
	 * 
	 * @param url
	 */
	public void loadGetData(final String url, NetResponseListener listener) {
		listeners.put(url, listener);
		addTask(new Runnable() {
			public void run() {

				Request request = new Request.Builder().url(url).build();
				client.newCall(request).enqueue(new Callback() {

					@Override
					public void onResponse(Response arg0) throws IOException {
						// TODO Auto-generated method stub
						String result = getResponseString(arg0);
						Message message = getSucMessage(url, result,
								UI_GET_SUCCED);
						UIhandler.sendMessage(message);
					}

					@Override
					public void onFailure(Request arg0, IOException arg1) {
						String failString = getFailedString(arg0);
						Message message = getFailMessage(failString, arg1,
								UI_GET_SUCCED);
						UIhandler.sendMessage(message);

					}
				});
				mSemaphoreThreadPool.release();
			}
		});

	}
	/**
	 * 加载普通post请求
	 */
	public void loadOrdinaryPostData(final String url,
			NetResponseListener listener, final List<BasicNameValuePair> list) {
		listeners.put(url, listener);
		addTask(new Runnable() {

			@Override
			public void run() {
				FormEncodingBuilder builder = new FormEncodingBuilder();
				if (list != null && list.size() > 0) {
					for (int i = 0; i < list.size(); i++) {
						BasicNameValuePair pairs = list.get(i);
						builder.add(pairs.getName(), pairs.getValue());
					}
				}
				if (builder != null) {
					Request request = new Request.Builder().url(url)
							.post(builder.build()).build();
					client.newCall(request).enqueue(new Callback() {

						@Override
						public void onResponse(Response arg0)
								throws IOException {
							String result = getResponseString(arg0);
							Message message = getSucMessage(url, result,
									UI_POST_SUCCED);
							UIhandler.sendMessage(message);
						}

						@Override
						public void onFailure(Request arg0, IOException arg1) {
							String failString = getFailedString(arg0);
							Message message = getFailMessage(failString, arg1,
									UI_POST_FAILED);
							UIhandler.sendMessage(message);
						}
					});
				}
				mSemaphoreThreadPool.release();
			}
		});
	}

	/**
	 * 上传文件的post请求
	 */
	public void loadPostFilesData(final String url, final String[] filekeys,
			final String[] filePaths, NetResponseListener listener) {
		listeners.put(url, listener);
		addTask(new Runnable() {

			@Override
			public void run() {
				MultipartBuilder builder = new MultipartBuilder();
				builder.type(MultipartBuilder.FORM);
				if (filePaths.length > 0 && filekeys.length > 0
						&& (filekeys.length == filePaths.length)) {
					File[] files = new File[filePaths.length];
					for (int i = 0; i < filePaths.length; i++) {
						files[i] = new File(filePaths[i]);
						builder.addFormDataPart(filekeys[i], filePaths[i],
								RequestBody.create(MEDIA_TYPE_PNG, files[i]));
					}

				}
				if (builder != null) {
					Request request = new Request.Builder().url(url)
							.post(builder.build()).build();
					client.newCall(request).enqueue(new Callback() {

						@Override
						public void onResponse(Response arg0)
								throws IOException {
							String result = getResponseString(arg0);
							Message message = getSucMessage(url, result,
									UI_POST_FILE_SUCCED);
							UIhandler.sendMessage(message);
						}

						@Override
						public void onFailure(Request arg0, IOException arg1) {
							String failString = getFailedString(arg0);
							Message message = getFailMessage(failString, arg1,
									UI_POST_FILE_FAILED);
							UIhandler.sendMessage(message);
						}
					});
				}

				mSemaphoreThreadPool.release();
			}
		});
	}

	/**
	 * 上传文件的post请求以及携带参数
	 */
	public void loadPostParamsWithFiles(final String url,
			final String[] filekeys, final String[] filePaths,
			NetResponseListener listener, final List<BasicNameValuePair> list) {
		listeners.put(url, listener);
		addTask(new Runnable() {

			@Override
			public void run() {
				MultipartBuilder builder = new MultipartBuilder();
				builder.type(MultipartBuilder.FORM);
				if (list != null && list.size() > 0) {
					for (int i = 0; i < list.size(); i++) {
						BasicNameValuePair pairs = list.get(i);
						builder.addFormDataPart(pairs.getName(),
								pairs.getValue());
					}

				}

				if (filePaths.length > 0 && filekeys.length > 0
						&& (filekeys.length == filePaths.length)) {
					File[] files = new File[filePaths.length];
					for (int i = 0; i < filePaths.length; i++) {
						files[i] = new File(filePaths[i]);
						builder.addFormDataPart(filekeys[i], filePaths[i],
								RequestBody.create(MEDIA_TYPE_PNG, files[i]));
					}

				}
				if (builder != null) {
					Request request = new Request.Builder().url(url)
							.post(builder.build()).build();
					client.newCall(request).enqueue(new Callback() {

						@Override
						public void onResponse(Response arg0)
								throws IOException {
							String result = getResponseString(arg0);
							Message message = getSucMessage(url, result,
									UI_POST_PARAS_FILE_SUCCED);
							UIhandler.sendMessage(message);
						}

						@Override
						public void onFailure(Request arg0, IOException arg1) {
							String failString = getFailedString(arg0);
							Message message = getFailMessage(failString, arg1,
									UI_POST_PARAS_FILE_FAILED);
							UIhandler.sendMessage(message);
						}
					});
				}

				mSemaphoreThreadPool.release();
			}
		});
	}

	/**
	 * post上传携带cookie
	 * 
	 * @param url
	 * @param json
	 * @param cookiekey
	 * @param cookie
	 * @param listener
	 */
	public void loadPostDataWithCokkie(final String url, final String json,
			final String cookiekey, final String cookie,
			NetResponseListener listener) {
		listeners.put(url, listener);
		addTask(new Runnable() {

			@Override
			public void run() {
				RequestBody body = RequestBody.create(JSON, json);
				Request request = new Request.Builder()
						.header(cookiekey, cookie).url(url).post(body).build();
				client.newCall(request).enqueue(new Callback() {

					@Override
					public void onResponse(Response arg0) throws IOException {
						// TODO Auto-generated method stub
						String result = getResponseString(arg0);
						Message message = getSucMessage(url, result,
								UI_POST_COOKIE_SUCCED);
						UIhandler.sendMessage(message);

					}

					@Override
					public void onFailure(Request arg0, IOException arg1) {
						// TODO Auto-generated method stub
						String failString = getFailedString(arg0);
						Message message = getFailMessage(failString, arg1,
								UI_POST_COOKIE_FAILE);
						UIhandler.sendMessage(message);
					}
				});
				mSemaphoreThreadPool.release();

			}
		});
	}

	/**
	 * 添加任务到队列中
	 * 
	 * @param runnable
	 *            加同步锁以防线程并发
	 */
	private synchronized void addTask(Runnable runnable) {
		mTaskQueue.add(runnable);
		if (childHandler == null) {// 如果子线程的中handler为空 那么等待handler初始化
			try {
				mSemaphoreChildHander.acquire();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		childHandler.sendEmptyMessage(CHILD_MES);

	}

	/**
	 * 从任务中取出任务
	 * 
	 * @return
	 */
	private Runnable getTask() {
		if (type == Type.FIFO) {
			return mTaskQueue.removeFirst();
		} else if (type == Type.LIFO) {
			return mTaskQueue.removeLast();
		}
		return null;
	};

	/**
	 * 将返回的response转成String
	 * 
	 * @param response
	 * @return
	 * @throws IOException
	 */
	private String getResponseString(Response response) throws IOException {
		if (response != null) {
			return response.body().string();
		}
		return null;
	}

	/**
	 * 将失败的request转化成String
	 * 
	 * @param request
	 * @return
	 */
	private String getFailedString(Request request) {
		if (request != null) {
			return request.body().toString();
		}
		return null;

	}

	/**
	 * 得到成功的message
	 * 
	 * @param result
	 * @param code
	 * @return
	 */
	private Message getSucMessage(String url, String result, int code) {
		if (result != null && code != -1) {
			Message message = Message.obtain();
			message.obj = getSucHolder(url, result);
			message.what = code;
			return message;
		}
		return null;

	}

	/**
	 * 得到失败的message
	 * 
	 * @param failString
	 * @param e
	 * @param code
	 * @return
	 */
	private Message getFailMessage(String failString, IOException e, int code) {
		if (failString != null && e != null && code != -1) {
			Message message = Message.obtain();
			message.what = code;
			message.obj = getFailHolder(failString, e);
			return message;
		}
		return null;
	}

	/**
	 * 得到失败的holder对象
	 * 
	 * @param failString
	 * @param e
	 * @return
	 */
	private FailHolder getFailHolder(String failString, IOException e) {
		if (failString != null && e != null) {
			FailHolder holder = new FailHolder();
			holder.exception = e;
			holder.failString = failString;
			return holder;
		}
		return null;

	}

	/**
	 * 得到成功的holder对象
	 * 
	 * @param url
	 * @param result
	 * @return
	 */
	private SucHolder getSucHolder(String url, String result) {
		if (url != null && result != null) {
			SucHolder holder = new SucHolder();
			holder.result = result;
			holder.url = url;
			return holder;
		}
		return null;
	}

	/**
	 * 失败缓存
	 * 
	 * @author nanchaodong
	 * 
	 */
	private class FailHolder {
		String failString;
		Exception exception;
		String url;
	}

	/**
	 * 成功缓存
	 * 
	 * @author nanchaodong
	 * 
	 */
	private class SucHolder {
		String url;
		String result;
	}

}
