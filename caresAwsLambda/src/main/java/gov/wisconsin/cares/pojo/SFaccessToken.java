package gov.wisconsin.cares.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown=true)
public class SFaccessToken {
	
	private String id;
	private String scope;
	private String tokenType;
	private String accesToken;
	private String instanceurl;
	
	public String getId() {
		return id;
	}

	@JsonProperty("id")
	public void setId(String id) {
		this.id = id;
	}

	public String getScope() {
		return scope;
	}

	@JsonProperty("scope")
	public void setScope(String scope) {
		this.scope = scope;
	}

	public String getTokenType() {
		return tokenType;
	}

	@JsonProperty("token_type")
	public void setTokenType(String tokenType) {
		this.tokenType = tokenType;
	}

	public String getAccesToken() {
		return accesToken;
	}

	@JsonProperty("access_token")
	public void setAccesToken(String accesToken) {
		this.accesToken = accesToken;
	}

	public String getInstanceurl() {
		return instanceurl;
	}

	@JsonProperty("instance_url")
	public void setInstanceurl(String instanceurl) {
		this.instanceurl = instanceurl;
	}

	@Override
	public String toString() {
		return "SFaccessToken [id=" + id + ", scope=" + scope + ", tokenType="
				+ tokenType + ", accesToken=" + accesToken + ", instanceurl="
				+ instanceurl + "]";
	}
}
