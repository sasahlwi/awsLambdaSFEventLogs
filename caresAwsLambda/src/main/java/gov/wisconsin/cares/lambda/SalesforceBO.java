package gov.wisconsin.cares.lambda;

import gov.wisconsin.cares.pojo.SFEventLogFile;
import gov.wisconsin.cares.pojo.SFaccessToken;
import gov.wisconsin.cares.util.JWTUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.databind.ObjectMapper;


public class SalesforceBO {
	
	private LambdaLogger logger;
	private HttpClient httpclient;
	private SFaccessToken sfAccessToken;
	private String queryEndpoint = "/services/data/~/query/?q=";
	public static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'00:00:00'Z'");
	static final String EVNT_LOG_FILES_QUERY = "SELECT Id, EventType, LogDate, LogFileLength, LogFile FROM EventLogFile Where LogDate >= ";

	public SalesforceBO(Context context) throws Exception{
		this.logger = context.getLogger();
		this.httpclient = HttpClientBuilder.create().build();
		this.setAPIVersionInQueryEndpoint();
	}
	
	private String getAccessToken(){
		String token = "";
		if(sfAccessToken != null) token = sfAccessToken.getAccesToken();
		return token;
	}
	
	private String encodeValue(String value) throws Exception{
	    return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
	}
	
	/**
	 * Read the Salesforce api version from environment variable and set it in the query endpoint. 
	 * @throws Exception
	 */
	private void setAPIVersionInQueryEndpoint() throws Exception{
		
		try{
			String apiVersion = System.getenv("salesforceAPIversion");
			if(apiVersion != null && !apiVersion.trim().equals("")) {
				
				apiVersion = apiVersion.trim();
				if(apiVersion.toLowerCase().startsWith("v")){
					apiVersion = apiVersion.substring(1, apiVersion.length());
				}
				
				double version = Double.parseDouble(apiVersion);
				DecimalFormat df = new DecimalFormat("##.0");
				
				apiVersion = "v" + df.format(version);
				queryEndpoint = queryEndpoint.replace("~", apiVersion);
			}else {
				throw new Exception("Missing environment variable 'salesforceAPIversion'. Please set to E.G. 48.0");
			}
		}catch(Exception e){
			Exception ex = new Exception(e.getMessage() 
	    			+ "\n Error: Setting environment variable 'salesforceAPIversion' in " 
	    			+ SalesforceBO.class.getName() + "::setAPIVersionInQueryEndpoint");
	    	throw ex;
		}
	}
	
	/**
	 * This will make a call to the Salesforce Rest api to get data for all event log files 
	 * with log date equal to or after the water-mark time-stamp
	 * @param wtrMrkTimestampStr
	 * @return
	 * @throws Exception
	 */
	public List<SFEventLogFile> getLogFiles(String wtrMrkTimestampStr) throws Exception{
		HttpGet request = null;
		HttpResponse response = null;
		List<SFEventLogFile> sfLogFileList = new ArrayList<>();
		try{
			
			request = new HttpGet(sfAccessToken.getInstanceurl() + queryEndpoint + encodeValue(EVNT_LOG_FILES_QUERY + wtrMrkTimestampStr));
			request.addHeader("Authorization", "Bearer " + this.getAccessToken());
			request.addHeader("Accept", "application/json");
			
			response = httpclient.execute(request);
			int status = response.getStatusLine().getStatusCode();
			if(status == HttpStatus.SC_OK){
				
	            sfLogFileList = this.parseEventLogFilesJson(this.getResponseStr(response));
	            
			}else {
				throw new Exception("Status code:" + status);
			}
		}catch(Exception e){

			if(response != null) logger.log("\n Response: " + response);
			Exception ex = new Exception(e.getMessage() 
	    			+ "\n Error: Unsuccessful API call to get Salesforce event logs in " 
	    			+ SalesforceBO.class.getName() + "::getLogFiles");
	    	throw ex;
	    	
		}finally {
			if(request != null) request.releaseConnection();
		}
		return sfLogFileList;
	}
	
	/**
	 * Get specific event log file from Salesforce based on the passed in query.
	 * @param logFileQuery
	 * @return An Inputstream containing data for the .csv event log file.
	 * @throws Exception
	 */
	public InputStream getEventLogFile(String logFileQuery) throws Exception{
		HttpGet request = null;
		HttpResponse response = null;
		InputStream in = null;
		try{
			
			request = new HttpGet(sfAccessToken.getInstanceurl() + logFileQuery);
			request.addHeader("Authorization", "Bearer " + this.getAccessToken());
			
			response = httpclient.execute(request);
			int status = response.getStatusLine().getStatusCode();
			if(status == HttpStatus.SC_OK){
				
				in = response.getEntity().getContent();
				
			}else {
				throw new Exception("Status code:" + status);
			}
		}catch(Exception e){
			
			if(response != null) logger.log("\n Response: " + response);
			Exception ex = new Exception(e.getMessage() 
	    			+ "\n Error: Unsuccessful API call to get Salesforce event log .csv file in " 
	    			+ SalesforceBO.class.getName() + "::getEventLogFile");
	    	throw ex;
		}
		return in;
	}
	
	/**
	 * Parse the returned JSON for all the event log files and convert into list of event log objects.
	 * @param responseJsonStr
	 * @return List of all event log file objects returned from Salesforce.
	 * @throws Exception
	 */
	private List<SFEventLogFile> parseEventLogFilesJson(String responseJsonStr) throws Exception {
		
		List<SFEventLogFile> sfLogFileList = new ArrayList<>();
		if(responseJsonStr != null && !responseJsonStr.trim().equals("")) {
			
			try{
				
		        JSONObject jsonObj = new JSONObject(responseJsonStr); 
		   		int count = jsonObj.getInt("totalSize");
		   		if(count > 0){
		   			JSONArray records = jsonObj.getJSONArray("records");
		   			ObjectMapper mapper = new ObjectMapper();
		   			SFEventLogFile[] sfEventLogArr = mapper.readValue(records.toString(), SFEventLogFile[].class);
		   			sfLogFileList = Arrays.asList(sfEventLogArr);
		   		}
		   		
			}catch(Exception e){
				Exception ex = new Exception(e.getMessage() 
		    			+ "\n Error: Unable to parse event log files JSON response in " 
		    			+ SalesforceBO.class.getName() + "::parseEventLogFilesJson");
		    	throw ex;
			}
        }
		return sfLogFileList;
	}
	
	/**
	 * Get the access token from Salesforce and set it into our AccessToken object.
	 * @return Access token needed for making Salesforce api calls
	 * @throws Exception
	 */
	public String getSalesforceAccessToken() throws Exception {
		String result = "";
		HttpPost httpPost = null;
		try {
			
			String jwt = JWTUtils.generateSalesforceJWT(JWTUtils.SALESFORCE_SERVICE_ACCOUNT_USER);
	        httpPost = new HttpPost(JWTUtils.SALESFORCE_OAUTH2_URL);
	        httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
			
			List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
	        nameValuePairs.add(new BasicNameValuePair("grant_type", JWTUtils.SALESFORCE_GRANT_TYPE));
	        nameValuePairs.add(new BasicNameValuePair("assertion", jwt));
	        httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
			
	        HttpResponse response = httpclient.execute(httpPost);
	        int statusCode = response.getStatusLine().getStatusCode();
	        if(statusCode == HttpStatus.SC_OK){
	        	
	        	ObjectMapper mapper = new ObjectMapper();
	        	sfAccessToken = mapper.readValue(this.getResponseStr(response), SFaccessToken.class);
	        	result = sfAccessToken.getAccesToken();
	        	
	        }else {
	        	throw new Exception("\n Status: " + statusCode + "\n Response: " + this.getResponseStr(response));
	        }
			
		}catch(Exception e){
        	Exception ex = new Exception(e.getMessage() 
	    			+ "\n Error getting Saleforce access token in " 
	    			+ SalesforceBO.class.getName() + "::getSalesforceAccessToken");
	    	throw ex;
		}finally{
	        if(httpPost != null) httpPost.releaseConnection();
		}
		return result;
	}
	
	/**
	 * Get the content of an HttpResponse and return it as a string.
	 * @param response
	 * @return HttpResponse content as a string.
	 * @throws Exception
	 */
	public String getResponseStr(HttpResponse response) throws Exception{
		String result = "";
		if(response != null){
			StringBuilder sb = new StringBuilder();
	        try (BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
	        	String line = "";
	        	while ((line = rd.readLine()) != null) {
	        		sb.append(line);
	        	}
	        	result = sb.toString();
	        }catch(IOException e) {
	        	Exception ex = new Exception(e.getMessage() 
		    			+ "\n Error reading response content in " 
		    			+ SalesforceBO.class.getName() + "::getResponseStr");
		    	throw ex;
	        }
		}
		return result;
	}
	
	/**
	 * Helper method to read the water-mark time-stamp from the passed in S3Object.
	 * @param s3Object
	 * @return The water-mark time-stamp.
	 * @throws Exception
	 */
	public static String readWtrMrkTS(S3Object s3Object) throws Exception{
		String result = LocalDateTime.now().minusDays(1).format(formatter);
		if(s3Object != null){
			StringBuilder sb = new StringBuilder();
	        try (BufferedReader rd = new BufferedReader(new InputStreamReader(s3Object.getObjectContent()))) {
	        	String line = "";
	        	while ((line = rd.readLine()) != null) {
	        		sb.append(line);
	        	}
	        	result = sb.toString();
	        }catch(IOException e) {
	        	Exception ex = new Exception(e.getMessage() 
		    			+ "\n Error reading watermark timestamp in "
		    			+ SalesforceBO.class.getName() + "::readWtrMrkTS");
		    	throw ex;
	        }
		}
		return result;
	}
}

