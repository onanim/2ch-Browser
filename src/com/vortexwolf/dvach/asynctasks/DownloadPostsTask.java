package com.vortexwolf.dvach.asynctasks;

import android.os.AsyncTask;
import android.view.Window;

import com.vortexwolf.dvach.exceptions.JsonApiReaderException;
import com.vortexwolf.dvach.interfaces.ICancellable;
import com.vortexwolf.dvach.interfaces.IJsonApiReader;
import com.vortexwolf.dvach.interfaces.IPostsListView;
import com.vortexwolf.dvach.interfaces.IProgressChangeListener;
import com.vortexwolf.dvach.models.domain.PostsList;

public class DownloadPostsTask extends AsyncTask<String, Long, Boolean> implements IProgressChangeListener, ICancellable {
	
	static final String TAG = "DownloadPostsTask";
	
	private final IJsonApiReader mJsonReader;
	private final IPostsListView mView;
	private final String mThreadNumber;
	private final String mBoard;
	private final boolean mIsPartialLoading;
	
	private String mLoadAfterPost = null;
	private PostsList mPostsList = null;
	private String mUserError = null;

	// Progress bar
	private long mContentLength = 0;
	
	public DownloadPostsTask(IPostsListView view, String board, String threadNumber, IJsonApiReader jsonReader, boolean isPartialLoading) {
		this.mJsonReader = jsonReader;
		this.mView = view;
		this.mThreadNumber = threadNumber;
		this.mBoard = board;
		this.mIsPartialLoading = isPartialLoading;
	}
	
	@Override
	protected Boolean doInBackground(String... params) {
		
		if(params.length > 0){
			this.mLoadAfterPost = params[0];
		}
		else {
			this.mLoadAfterPost = null;
		}
		
		//Читаем по ссылке json-объект со списком постов
		try{
			this.mPostsList = this.mJsonReader.readPostsList(mBoard, mThreadNumber, mLoadAfterPost, this, this);
		}
		catch(JsonApiReaderException e){
			this.mUserError = e.getMessage();
		}
		
    	return mPostsList != null;
	}
	
	@Override
	public void onPreExecute() {
		if(this.mIsPartialLoading){
			this.mView.showUpdateLoading();
		}
		else{
			this.mView.showLoadingScreen();
		}
		
		if (mContentLength == -1){
			this.mView.setWindowProgress(Window.PROGRESS_INDETERMINATE_ON);
		}
		else{
			this.mView.setWindowProgress(0);
		}
	}
	
	@Override
	public void onPostExecute(Boolean success) {
		// Прячем все индикаторы загрузки
		this.onFinished();
		
		// Обновляем список или отображаем ошибку
		if(success && this.mPostsList != null){
			if(this.mIsPartialLoading){
				this.mView.updateData(this.mLoadAfterPost, this.mPostsList);
			}
			else{
				this.mView.setData(this.mPostsList);
			}
		}
		else if(!success) {
			if(this.mIsPartialLoading){
				this.mView.showUpdateError(this.mUserError);
			}
			else {
				this.mView.showError(this.mUserError);
			}
		}
	}

	private void onFinished(){
		if(this.mIsPartialLoading){
			this.mView.hideUpdateLoading();
		}
		else{
			this.mView.hideLoadingScreen();
		}
		
		if (mContentLength == -1){
			this.mView.setWindowProgress(Window.PROGRESS_INDETERMINATE_OFF);
		}
		//Hide progress anyway
		this.mView.setWindowProgress(10000);
	}

	@Override
	public void onProgressUpdate(Long... progress) {
		// 0-9999 is ok, 10000 means it's finished
		if(mContentLength > 0){
			int relativeProgress = progress[0].intValue() * 9999 / (int) mContentLength;
			this.mView.setWindowProgress(relativeProgress);
		}
	}

	@Override
	public void progressChanged(long oldValue, long newValue) {
		if(this.isCancelled()) return;
		
		publishProgress(newValue);
	}
	
	@Override
	public void setContentLength(long value) {
		this.mContentLength = value;
	}
}
