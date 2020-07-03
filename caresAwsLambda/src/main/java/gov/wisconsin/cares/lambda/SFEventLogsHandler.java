package gov.wisconsin.cares.lambda;

import gov.wisconsin.cares.pojo.SFEventLogFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;

/**
 * Read Event Log files from Salesforce and store them in S3 bucket
 * @author andersx
 * The Water-mark time-stamp used in API calls, should be in this format 
 * 2020-06-29T00:00:00Z (yyyy-MM-dd'T'00:00:00'Z')
 * This is used to get only the event log files created after or equal to this time-stamp.
 *
 */
public class SFEventLogsHandler implements RequestHandler<ScheduledEvent, String> {
	
	@Override
	public String handleRequest(ScheduledEvent event, Context context) {
		
		String response = "200 OK";
        LambdaLogger logger = context.getLogger();
        String environment = System.getenv("ENV");
        
        try {
        	
        	logger.log("\n Environment is: " + environment);
        	AmazonS3 s3Client = AmazonS3ClientBuilder.standard()                  
        			 .withCredentials(DefaultAWSCredentialsProviderChain.getInstance())
        			 .build();
        	
        	// Get the Salesforce access token
        	SalesforceBO sfBO = new SalesforceBO(context);
        	sfBO.getSalesforceAccessToken();
        	
        	// Get and download the event logs to S3 destination bucket, based on the water-mark time-stamp.
        	List<SFEventLogFile> eventLogsList = sfBO.getLogFiles(this.readWatermarkTimestampForEventLogs(s3Client));
        	this.storeAllLogFilesInS3(eventLogsList, s3Client, sfBO, logger);
        	
        	// Update the water-mark time-stamp after successful completion
        	this.updateWatermarkTimestamp(s3Client);
        	
        }catch (Exception e) {
        	EmailManager.sendFailureNotificationEmail(environment);
            response = "Error";
            logger.log("\n Error: Unable to move Salesforce event logs to S3 bucket");
            logger.log("\n Function Name: " + context.getFunctionName() + "\n");
            e.printStackTrace();
        }
        logger.log("\n RESPONSE: " + response + "\n");
		return response;
	}
	
	/**
	 * This will loop through all our event log files and download them 
	 * from Salesforce one by one to S3 destination bucket.
	 * @param eventLogsList
	 * @param s3Client
	 * @param sfBO
	 * @param logger
	 * @throws Exception
	 */
	private void storeAllLogFilesInS3(List<SFEventLogFile> eventLogsList, AmazonS3 s3Client, SalesforceBO sfBO, LambdaLogger logger) throws Exception{
		
		if(!eventLogsList.isEmpty()){
    		
    		int count = 0;
    		logger.log("\n Number of event logs: " + eventLogsList.size());
    		for(SFEventLogFile eventLog: eventLogsList){
    			this.storeLogFileInS3(eventLog, s3Client, sfBO);
    			count++;
    		}
    		logger.log("\n Number of event logs downloaded: " + count);
    	}
	}
	
	/**
	 * This will download the passed in event log file from Salesforce and store it in S3 destination bucket.
	 * A destination folder with the log file date will be created in the destination bucket, 
	 * and the .csv log file will be down-loaded to that folder.
	 * @param eventLog
	 * @param s3Client
	 * @param sfBO
	 * @throws Exception
	 */
	private void storeLogFileInS3(SFEventLogFile eventLog, AmazonS3 s3Client, SalesforceBO sfBO) throws Exception{
		
		InputStream in = null;
		try{
			
			// Determine folder location based on Log Date
			String destFolder = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
			if(eventLog.getLogDate() != null && !eventLog.getLogDate().trim().equals("")) {
				destFolder = eventLog.getLogDate().substring(0, 10);
			}
			
			// Build the destination key name and get the file size.
			String destFolderKeyName = destFolder + File.separator + (eventLog.getEventType() + ".csv");
			long fileSize = getFileSize(sfBO.getEventLogFile(eventLog.getLogFile()));
			
			// Set meta-data and store it in S3 destination folder
	        ObjectMetadata meta = new ObjectMetadata();
	        meta.setContentLength(fileSize);
	        in = sfBO.getEventLogFile(eventLog.getLogFile());
	        s3Client.putObject(System.getenv("salesforceLogFileDstBkt"), destFolderKeyName, in, meta);
	        
		}catch(Exception e){
			Exception ex = new Exception(e.getMessage() 
	    			+ "\n Error storing .csv event log file in S3. "
	    			+ " Event log file::" + eventLog + " Class::"
	    			+ SFEventLogsHandler.class.getName() + "::storeLogFileInS3");
	    	throw ex;
		}finally {
			try{
				if(in != null) in.close();
			}catch(IOException e){
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Update the water-mark time stamp when the process has successfully ended.
	 * We will update the water-mark time-stamp to the current time - 1 day. 
	 * This is because an event log file is first created when an event occurs, 
	 * but won't be available for download before 24 hours later. So by subtracting 1 day when updating the water-mark, 
	 * will ensure that we will get all the log files.
	 * @param s3Client
	 * @throws Exception 
	 */
	private void updateWatermarkTimestamp(AmazonS3 s3Client) throws Exception{
		String wtrMrkFileBkt  = "";
		String wtrMrkFileName = "";
		try {
			
			if(Boolean.valueOf(System.getenv("updateWtrMrkTimestamp"))){
		 		wtrMrkFileBkt  = System.getenv("salesforceEventLogFilesWatermarkBkt");
				wtrMrkFileName = System.getenv("watermarkFile");
				
				// Update water-mark with current time stamp value.
				String updatedWtrMrk = LocalDateTime.now().minusDays(1).format(SalesforceBO.formatter);
				s3Client.putObject(wtrMrkFileBkt, wtrMrkFileName, updatedWtrMrk);
			}
			
		}catch(Exception e){
			Exception ex = new Exception(e.getMessage() 
	    			+ "\n Error updating watermark timestamp file:" + wtrMrkFileName + " in S3 bucket:" + wtrMrkFileBkt + ", "
	    			+ SFEventLogsHandler.class.getName() + "::updateWatermarkTimestamp");
	    	throw ex;
		}
	}
	
	/**
	 * Read the water-mark from file in S3 bucket location. If the water-mark time-stamp file does not exist, 
	 * then we will create it and default time-stamp to 2020-01-01 in order to get all logs available after this date.
	 * @param s3Client
	 * @return The water-mark time-stamp value
	 * @throws Exception 
	 */
	private String readWatermarkTimestampForEventLogs(AmazonS3 s3Client) throws Exception{
		String wtrMrkStr      = "";
		String wtrMrkFileBkt  = "";
		String wtrMrkFileName = "";
		try {
				
			wtrMrkFileBkt  = System.getenv("salesforceEventLogFilesWatermarkBkt");
			wtrMrkFileName = System.getenv("watermarkFile");
			
			// Create the file if it does not exist and write water-mark date to file
			if(!s3Client.doesObjectExist(wtrMrkFileBkt, wtrMrkFileName)){
				
				wtrMrkStr = "2020-01-01T00:00:00Z";
				s3Client.putObject(wtrMrkFileBkt, wtrMrkFileName, wtrMrkStr);
				
			}else {
				
				// Read the water-mark time stamp from the file.
				S3Object s3Object = s3Client.getObject(wtrMrkFileBkt, wtrMrkFileName);
				wtrMrkStr = SalesforceBO.readWtrMrkTS(s3Object);
			}
			
		}catch(Exception e){
			Exception ex = new Exception(e.getMessage() 
	    			+ "\n Error reading watermark timestamp file:" + wtrMrkFileName + " in S3 bucket:" + wtrMrkFileBkt + ", "
	    			+ SFEventLogsHandler.class.getName() + "::readWatermarkTimestampForEventLogs");
	    	throw ex;
		}
		return wtrMrkStr;
	}
	
	/**
	 * 
	 * @param in
	 * @return The size of the file from the InputStream
	 * @throws Exception
	 */
	private long getFileSize(InputStream in) throws Exception {
		
		long size = 0;
		if(in != null) {
			try {
				int nRead = 0;
				byte[] byteArr = new byte[1024 * 1024];
				while((nRead = in.read(byteArr, 0, byteArr.length)) != -1) {
					size += nRead;
				}
			}catch(Exception e) {
				Exception ex = new Exception(e.getMessage() 
		    			+ "\n Error reading the size of .csv event log file " 
		    			+ SFEventLogsHandler.class.getName() + "::getFileSize");
		    	throw ex;
			}finally {
				try{
					if(in != null) in.close();
				}catch(IOException e){
					e.printStackTrace();
				}
			}
		}
		
		return size;
	}
}
