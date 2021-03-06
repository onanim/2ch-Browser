package com.vortexwolf.dvach.services.domain;

import java.nio.charset.Charset;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HTTP;

import android.content.res.Resources;
import android.net.Uri;

import com.vortexwolf.dvach.R;
import com.vortexwolf.dvach.common.Constants;
import com.vortexwolf.dvach.common.library.ExtendedHttpClient;
import com.vortexwolf.dvach.common.library.MyLog;
import com.vortexwolf.dvach.common.utils.StringUtils;
import com.vortexwolf.dvach.common.utils.UriUtils;
import com.vortexwolf.dvach.exceptions.SendPostException;
import com.vortexwolf.dvach.interfaces.IPostSender;
import com.vortexwolf.dvach.models.domain.PostEntity;
import com.vortexwolf.dvach.models.domain.PostFields;

public class PostSender implements IPostSender {
	private static final String TAG = "PostSender";
	private final DefaultHttpClient mHttpClient;
	private final Resources mResources;
	private final HttpStringReader mHttpStringReader;
	private final PostResponseParser mResponseParser;
	
	public PostSender(DefaultHttpClient client, Resources resources){
		//this.mHttpClient = client;
		this.mHttpClient = new ExtendedHttpClient(); // похоже, что передаваемые клиент не работает, лучше создать новый
		this.mResources = resources;
		this.mResponseParser = new PostResponseParser();
		this.mHttpStringReader = new HttpStringReader(this.mHttpClient);
	}
	
	@Override
	public String sendPost(String boardName, String threadNumber, PostFields fields, PostEntity entity) throws SendPostException {
		
		if(boardName == null || threadNumber == null || fields == null || entity == null){
			throw new SendPostException(mResources.getString(R.string.error_incorrect_argument));
		}

		String uri = Constants.DVACH_URL + boardName + "/wakaba.pl";
		
		// 1 - 'ро' на кириллице, 2 - 'р' на кириллице,  3 - 'о' на кириллице, 4 - все латинскими буквами, 
		String[] possibleTasks = new String[] { "роst", "рost", "pоst", "post",  };
		int statusCode = 502; // Возвращается при неправильном значении task=post, часто меняется, поэтому неизвестно какой будет на данный момент
		HttpResponse response = null;
			
		for(int i = 0; i < possibleTasks.length && statusCode == 502; i++){
			HttpPost httpPost = new HttpPost(uri);
			response = executeHttpPost(httpPost, threadNumber, possibleTasks[i], fields, entity);
			//Проверяем код ответа
			statusCode = response.getStatusLine().getStatusCode();
			
			if(statusCode == 502) {
				httpPost.abort();
			}
			
			MyLog.v(TAG, response.getStatusLine());
        }
		// Вернуть ссылку на тред после успешной отправки и редиректа
		if(statusCode == 302 || statusCode == 303){
			Header header = response.getFirstHeader("Location");
			if(header != null){
				return header.getValue();
			}
		}
		else if(statusCode != 200) {
	    	throw new SendPostException(statusCode + " - "+response.getStatusLine().getReasonPhrase());
	    }
	    
		//Проверяю 200-response на наличие html-разметки с ошибкой
	    String responseText = this.mHttpStringReader.fromResponse(response);
		// Вызываю только для выброса exception
	    this.mResponseParser.isPostSuccessful(responseText);
	    
	    return null;
	}
	
	private HttpResponse executeHttpPost(HttpPost httpPost, String threadNumber, String task, PostFields fields, PostEntity entity) throws SendPostException{
		//Редирект-коды я обработаю самостоятельно путем парсинга и возврата заголовка Location
		HttpClientParams.setRedirecting(httpPost.getParams(), false);
		//Настраиваем заголовки
		httpPost.setHeader(HTTP.USER_AGENT, Constants.USER_AGENT_STRING);
		httpPost.setHeader("Accept", "application/json,text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
	    httpPost.setHeader("Accept-Language", "ru,ru-ru;q=0.8,en-us;q=0.5,en;q=0.3");
	    httpPost.setHeader("Accept-Encoding", "gzip, deflate");
	    httpPost.setHeader("Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7");

        HttpResponse response = null;
        try
        {
        	Charset utf = Constants.UTF8_CHARSET;
    	    //Заполняем параметры для отправки сообщения
        	MultipartEntity multipartEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);  
            multipartEntity.addPart("task", new StringBody(task, utf));
            multipartEntity.addPart("parent", new StringBody(threadNumber, utf));
            multipartEntity.addPart(fields.getCaptchaKey(), new StringBody(StringUtils.emptyIfNull(entity.getCaptchaKey()), utf));
            multipartEntity.addPart(fields.getCaptcha(), new StringBody(StringUtils.emptyIfNull(entity.getCaptchaAnswer()), utf));
            multipartEntity.addPart(fields.getComment(), new StringBody(StringUtils.emptyIfNull(entity.getComment()), utf));
            if(entity.isSage()){
            	multipartEntity.addPart(fields.getEmail(), new StringBody(Constants.SAGE_EMAIL, utf));
            }
            if(entity.getAttachment() != null){
            	multipartEntity.addPart(fields.getFile(), new FileBody(entity.getAttachment()));
            }
            if(entity.getSubject() != null){
            	multipartEntity.addPart(fields.getSubject(), new StringBody(entity.getSubject(), utf));
            }
            if(!StringUtils.isEmpty(entity.getName())){
            	multipartEntity.addPart(fields.getName(), new StringBody(entity.getName(), utf));
            }
            // Only for /po and /test
            if(entity.getPolitics() != null){
            	multipartEntity.addPart("anon_icon", new StringBody(entity.getPolitics(), utf));
            }

	        httpPost.setEntity(multipartEntity);
	        response = this.mHttpClient.execute(httpPost);  
	    }	        
        catch (Exception e) {
        	MyLog.e(TAG, e);
        	throw new SendPostException(mResources.getString(R.string.error_send_post));
        }
        
        return response;
	}
}
