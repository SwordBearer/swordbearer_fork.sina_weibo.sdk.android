package com.weibo.sdk.android;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnKeyListener;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.weibo.sdk.android.demo.R;
import com.weibo.sdk.android.util.Utility;

/**
 * 用来显示用户认证界面的dialog，封装了一个webview，通过redirect地址中的参数来获取accesstoken
 */
public class WeiboDialog extends Dialog implements OnKeyListener {

	static FrameLayout.LayoutParams FILL = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.FILL_PARENT,
			ViewGroup.LayoutParams.FILL_PARENT);
	private String mUrl;
	private WeiboAuthListener mListener;
	private LinearLayout mProgressContainer;
	private WebView mWebView;
	private RelativeLayout webViewContainer;
	private RelativeLayout mContent;

	private final static String TAG = "Weibo-WebView";
	private static int theme = android.R.style.Theme_Translucent_NoTitleBar;

	public WeiboDialog(Context context, String url, WeiboAuthListener listener) {
		super(context, theme);
		mUrl = url;
		mListener = listener;

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		mContent = new RelativeLayout(getContext());
		setUpWebView();

		addContentView(mContent, new LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.FILL_PARENT));

		this.setOnKeyListener(this);
	}

	private void setUpWebView() {
		webViewContainer = new RelativeLayout(getContext());
		mWebView = new WebView(getContext());
		mWebView.setVerticalScrollBarEnabled(false);
		mWebView.setHorizontalScrollBarEnabled(false);
		mWebView.getSettings().setJavaScriptEnabled(true);
		mWebView.setWebViewClient(new WeiboDialog.WeiboWebViewClient());
		mWebView.loadUrl(mUrl);
		mWebView.setLayoutParams(FILL);
		mWebView.setVisibility(View.INVISIBLE);

		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
				LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);

		RelativeLayout.LayoutParams lp0 = new RelativeLayout.LayoutParams(
				LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);

		mContent.setBackgroundColor(Color.TRANSPARENT);
		webViewContainer.setBackgroundResource(R.drawable.weibosdk_dialog_bg);
		webViewContainer.addView(mWebView, lp0);
		webViewContainer.setGravity(Gravity.CENTER);

		mContent.addView(webViewContainer, lp);

		mProgressContainer = new LinearLayout(getContext());
		ProgressBar progressBar = new ProgressBar(getContext());
		TextView tvHint = new TextView(getContext());
		tvHint.setText("正在加载...");
		mProgressContainer.setOrientation(LinearLayout.VERTICAL);
		LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT);
		mProgressContainer.setGravity(Gravity.CENTER);
		mProgressContainer.addView(progressBar, params);
		mProgressContainer.addView(tvHint, params);
		mContent.addView(mProgressContainer, FILL);
	}

	private class WeiboWebViewClient extends WebViewClient {
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			Log.d(TAG, "Redirect URL: " + url);
			if (url.startsWith("sms:")) { // 针对webview里的短信注册流程，需要在此单独处理sms协议
				Intent sendIntent = new Intent(Intent.ACTION_VIEW);
				sendIntent.putExtra("address", url.replace("sms:", ""));
				sendIntent.setType("vnd.android-dir/mms-sms");
				WeiboDialog.this.getContext().startActivity(sendIntent);
				return true;
			}
			return super.shouldOverrideUrlLoading(view, url);
		}

		@Override
		public void onReceivedError(WebView view, int errorCode,
				String description, String failingUrl) {
			super.onReceivedError(view, errorCode, description, failingUrl);
			mListener.onError(new WeiboDialogError(description, errorCode,
					failingUrl));
			WeiboDialog.this.dismiss();
		}

		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			if (url.startsWith(Weibo.redirecturl)) {
				handleRedirectUrl(view, url);
				view.stopLoading();
				WeiboDialog.this.dismiss();
				return;
			}
			super.onPageStarted(view, url, favicon);
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			Log.d(TAG, "onPageFinished URL: " + url);
			super.onPageFinished(view, url);
			if (mProgressContainer.getVisibility() == View.VISIBLE) {
				mContent.removeView(mProgressContainer);
			}
			mWebView.setVisibility(View.VISIBLE);
		}

		@SuppressLint("Override")
		public void onReceivedSslError(WebView view, SslErrorHandler handler,
				SslError error) {
			handler.proceed();
		}
	}

	private void handleRedirectUrl(WebView view, String url) {
		Bundle values = Utility.parseUrl(url);
		String error = values.getString("error");
		String error_code = values.getString("error_code");

		if (error == null && error_code == null) {
			mListener.onComplete(values);
		} else if (error.equals("access_denied")) {
			// 用户或授权服务器拒绝授予数据访问权限
			mListener.onCancel();
		} else {
			if (error_code == null) {
				mListener.onWeiboException(new WeiboException(error, 0));
			} else {
				mListener.onWeiboException(new WeiboException(error, Integer
						.parseInt(error_code)));
			}
		}
	}

	@Override
	public boolean onKey(DialogInterface arg0, int arg1, KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
			try {
				mListener.onCancel();
				if (null != mWebView) {
					mWebView.stopLoading();
					mWebView.destroy();
					this.dismiss();
				}
			} catch (Exception e) {
			}
		}
		return false;
	}
}
