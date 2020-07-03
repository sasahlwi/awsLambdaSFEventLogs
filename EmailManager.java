package gov.wisconsin.cares.lambda;

import java.util.Properties;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * Send email notifications
 * Note: username and password are unique for each region / host
 * @author andersx
 *
 */
public class EmailManager {
	
	private final static int SMTP_PORT = 587;  /*** 25, 587, or 2587 ***/
    static String messageBody = String.join(
    	    System.getProperty("line.separator"),
    	    "<h3>Download of Salesforce event logs to S3 failed</h3>",
    	    "<p>Download of Salesforce event log files to S3 storage failed in AWS Lambda function getSFEventLogs-~</p>"

    	);
    
	public static void sendFailureNotificationEmail(String env) {
		
		messageBody = messageBody.replace("~", env);
		final String HOST           = System.getenv("smtpHost");
		final String SMTP_USERNAME  = System.getenv("smtpUsername");
		final String SMTP_PASSWORD  = System.getenv("smtpPassword");
		final String FROM_ADDRESS   = System.getenv("smtpFromEmailAdr");
		final String[] TO_ADDRESSES = System.getenv("smtpToEmailAdr").split(",");
		
		// Create a Properties object to contain connection configuration information.
    	Properties props = System.getProperties();
    	props.put("mail.smtp.auth", "true");
    	props.put("mail.smtp.port", SMTP_PORT); 
    	props.put("mail.transport.protocol", "smtp");
    	props.put("mail.smtp.starttls.enable", "true");

        // Create a Session object to represent a mail session with the specified properties. 
    	Session session = Session.getDefaultInstance(props);
    	Transport transport = null;
    	
    	try {
    		
    		// Set to addresses
    		Address[] toAdrAry = null;
            if(TO_ADDRESSES != null){
            	
                toAdrAry = new InternetAddress[TO_ADDRESSES.length];
                for (int j = 0; j < toAdrAry.length; j++){
                    if(TO_ADDRESSES[j] != null){
                        toAdrAry[j] = new InternetAddress(TO_ADDRESSES[j]);
                    }
                }
            }
    		
	    	// Create a message with the specified information. 
	        MimeMessage msg = new MimeMessage(session);
	        msg.setFrom(new InternetAddress(FROM_ADDRESS));
	        msg.addRecipients(Message.RecipientType.TO, toAdrAry);
	        msg.setSubject(System.getenv("ENV") + " AWS-Salesforce Event Logs Notification");
	        msg.setContent(messageBody,"text/html");
	           
	        // Create a transport.
	        transport = session.getTransport();
	       
	        // Send the email
	        System.out.println(" Sending email notification...");
            transport.connect(HOST, SMTP_USERNAME, SMTP_PASSWORD);
            transport.sendMessage(msg, msg.getAllRecipients());
            System.out.println(" Email sent!");
        
    	}catch(Exception e) {
    		System.out.println("\n Error: Failed to send failure email notification.");
    		System.out.println("\n Host:" + HOST);
    	}finally {
    		try {
    			transport.close();
    		}catch(Exception e) {
    			e.printStackTrace();
    		}
    	}
	}
}

