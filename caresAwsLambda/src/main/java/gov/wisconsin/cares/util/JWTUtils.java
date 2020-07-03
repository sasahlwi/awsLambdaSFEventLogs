package gov.wisconsin.cares.util;

import io.jsonwebtoken.Header;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import Decoder.BASE64Decoder;

public class JWTUtils {
	
	public static String SALESFORCE_OAUTH2_URL = null;
	private static String SALESFORCE_JWT_ISSUER = null;
	private static PrivateKey SALESFORCE_SIGNING_KEY = null;
	public static String SALESFORCE_SERVICE_ACCOUNT_USER = null;
	private static long SALESFORCE_JWT_EXPIRATION_SECONDS = 180;
	private static String SALESFORCE_JWT_AUDIENCE = "https://test.salesforce.com";
	public static String SALESFORCE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
	
	static {
		try {
			
			SALESFORCE_JWT_ISSUER = System.getenv("salesforceJWTIssuer");
			SALESFORCE_OAUTH2_URL = (System.getenv("salesforceOauth2Url") + "/services/oauth2/token");
			SALESFORCE_SERVICE_ACCOUNT_USER = System.getenv("salesforceServiceAccountUser");
			SALESFORCE_SIGNING_KEY = readPrivateKey(System.getenv("jwtSigningKey"));
		
		} catch (Exception e) {
			System.out.println("\n Error: Unable to instantiate JWTUtils class");
		}
	}
	
	private JWTUtils() { /* Prevent class from being instantiated */ }
    
    public static String generateSalesforceJWT(String user) throws Exception {
		return generateJwtToken(user, null, SALESFORCE_JWT_ISSUER, SALESFORCE_JWT_AUDIENCE, SALESFORCE_JWT_EXPIRATION_SECONDS, SALESFORCE_SIGNING_KEY);
	}
	
	public static String generateSalesforceJWT(String user, Map<String, Object> claims) throws Exception {
		return generateJwtToken(user, claims, SALESFORCE_JWT_ISSUER, SALESFORCE_JWT_AUDIENCE, SALESFORCE_JWT_EXPIRATION_SECONDS, SALESFORCE_SIGNING_KEY);
	}
	
    
    private static String generateJwtToken(String user, Map<String, Object> claims, String issuer, String audience, long expirationSeconds, PrivateKey privateKey) throws Exception {
		String signedToken = ""; 
		Map<String, Object> jwtHeader = new HashMap<>();
		
	    try {
	    	jwtHeader.put(Header.TYPE, Header.JWT_TYPE);
	    	jwtHeader.put(JwsHeader.ALGORITHM, SignatureAlgorithm.RS256);

	    	long currentTime = System.currentTimeMillis();
	    	
		    if (claims != null) {
		    	signedToken = Jwts.builder()
					.setHeader(jwtHeader)
					.setClaims(claims)
					.setSubject(user)
					.setIssuer(issuer)
					.setAudience(audience)
					.setIssuedAt(new Date(currentTime))
					.setExpiration(new Date(currentTime + TimeUnit.SECONDS.toMillis(expirationSeconds)))
					.signWith(SignatureAlgorithm.RS256, privateKey).compact();
		    } else {
		    	signedToken = Jwts.builder()
					.setHeader(jwtHeader)
					.setIssuer(issuer)
					.setSubject(user)
					.setAudience(audience)
					.setIssuedAt(new Date(currentTime))
					.setExpiration(new Date(currentTime + TimeUnit.SECONDS.toMillis(expirationSeconds)))
					.signWith(SignatureAlgorithm.RS256, privateKey).compact();
		    }
	    } catch(Exception e) {
	    	Exception ex = new Exception(e.getMessage() 
	    			+ "\n Error: Unable to generate jwt token in " 
	    			+ JWTUtils.class.getName() + "::generateJwtToken");
	    	throw ex;
	    }
	    
		return signedToken;		
	}
    
    private static PrivateKey readPrivateKey(String jwtSigningKeyStr) throws Exception {
		PrivateKey privateKey = null;
		String privateKey_string = null;
		try {
			
			jwtSigningKeyStr = jwtSigningKeyStr.replaceAll("\\s", "");
			byte[] keyBytes = jwtSigningKeyStr.getBytes("UTF-8");
			
			privateKey_string = new String(keyBytes, "UTF-8");
			privateKey_string = privateKey_string.replaceAll("(-+BEGIN PRIVATE KEY-+\\r?\\n|-+END PRIVATE KEY-+\\r?\\n?)", "");
			
			BASE64Decoder decoder = new BASE64Decoder();
			keyBytes = decoder.decodeBuffer(privateKey_string);

			PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			privateKey =  keyFactory.generatePrivate(spec);
			
		} catch(Exception e) {
			Exception ex = new Exception(e.getMessage() + 
					"\n Error: Unable to read the private key for jwt token in " + 
					JWTUtils.class.getName() + "::readPrivateKey");
	    	throw ex;
		}
		
		return privateKey;	
	}
}
