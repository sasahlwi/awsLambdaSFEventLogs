package gov.wisconsin.cares.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown=true)
public class SFEventLogFile {
	
	private String id;
	private String eventType;
	private String logFile;
	private String logDate;
	private long logFileLength;

	public String getId() {
		return id;
	}
	
	@JsonProperty("Id")
	public void setId(String id) {
		this.id = id;
	}
	
	public String getEventType() {
		return eventType;
	}

	@JsonProperty("EventType")
	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	public String getLogFile() {
		return logFile;
	}

	@JsonProperty("LogFile")
	public void setLogFile(String logFile) {
		this.logFile = logFile;
	}
	
	public String getLogDate(){
		return logDate;
	}
	
	@JsonProperty("LogDate")
	public void setLogDate(String logDate) {
		this.logDate = logDate;
	}

	public long getLogFileLength() {
		return logFileLength;
	}

	@JsonProperty("LogFileLength")
	public void setLogFileLength(long logFileLength) {
		this.logFileLength = logFileLength;
	}

	@Override
	public String toString() {
		return "SFEventLogFile [id=" + id + ", eventType=" + eventType
				+ ", logFile=" + logFile + ", logDate=" + logDate
				+ ", logFileLength=" + logFileLength + "]";
	}
}
