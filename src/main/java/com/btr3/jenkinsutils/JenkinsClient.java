package com.btr3.jenkinsutils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.FutureTask;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class JenkinsClient {

	private static Logger logger = LogManager.getLogger(JenkinsClient.class);
	private static long DEFAULT_POLLING_INTERVAL = 30000;
	
	private String host;
	private String port;
	private String path;
	private String user;
	private String password;

	public JenkinsClient(String host, String port, String path, String user,
			String password) {
		this.host = host;
		this.port = port;
		this.path = path;
		this.user = user;
		this.password = password;
	}

	private String makeHttpRequest(String url, String body) throws IOException {
		// This usage of HttpClient based on example:
		// http://hc.apache.org/httpcomponents-client-ga/httpclient/examples/org/apache/http/examples/client/ClientAuthentication.java
		// Basic auth usage based on second example:
		// http://hc.apache.org/httpcomponents-client-ga/httpclient/examples/org/apache/http/examples/client/ClientPreemptiveBasicAuthentication.java

		// Content to be returned
		String ret = null;
		
		// Register handler for Basic Auth on Jenkins server
		HttpHost targetHost = new HttpHost(host, Integer.parseInt(port), "http");
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(new AuthScope(targetHost.getHostName(),
				targetHost.getPort()), new UsernamePasswordCredentials(user,
				password));
		CloseableHttpClient httpclient = HttpClients.custom()
				.setDefaultCredentialsProvider(credsProvider).build();
		// Create AuthCache instance
		AuthCache authCache = new BasicAuthCache();
		// Generate BASIC scheme object and add it to the local
		// auth cache
		BasicScheme basicAuth = new BasicScheme();
		authCache.put(targetHost, basicAuth);

		// Add AuthCache to the execution context
		HttpClientContext localContext = HttpClientContext.create();
		localContext.setAuthCache(authCache);

		try {
			HttpPost httppost = new HttpPost(url);

			// Add payload, if available
			if (body != null) {
				List<NameValuePair> nvps = new ArrayList<NameValuePair>();
				nvps.add(new BasicNameValuePair("json", body));
				httppost.setEntity(new UrlEncodedFormEntity(nvps));
				logger.debug(httppost.getEntity().toString());
			}

			System.out.println("executing request" + httppost.getRequestLine());
			CloseableHttpResponse response = httpclient.execute(targetHost,
					httppost, localContext);
			try {
				HttpEntity entity = response.getEntity();
				System.out.println("----------------------------------------");
				System.out.println(response.getStatusLine());
				System.out.println(response.getStatusLine().getStatusCode());
				if (entity != null) {
					System.out.println("Response content length: "
							+ entity.getContentLength());
				}
				
				if((response.getStatusLine().getStatusCode() == 404) || (response.getStatusLine().getStatusCode() == 500)){
					ret = null;
				}
				
				else{
					ret = EntityUtils.toString(entity);					
				}
				
				EntityUtils.consume(entity);
			} finally {
				response.close();
			}
		} finally {
			httpclient.close();
		}
		
		return ret;
	}
	
	public String getJobStatus(String jobName, String buildNumber) {
		String buildUrl = "http://" + host + ":" + port + "/" + path + "/job/"
				+ jobName + "/build/" + buildNumber + "/api/json";
		
		try {
			String buildStatusJson = this.makeHttpRequest(buildUrl, null);
			JsonElement buildElement = new JsonParser().parse(buildStatusJson);
			JsonObject buildObject = buildElement.getAsJsonObject();
			return buildObject.getAsJsonPrimitive("result").toString();

		} catch (IOException e) {
			// We can safely ignore this exception as we will retry later
			e.printStackTrace();
			return null;
		}
	}

	public FutureTask<JenkinsJobResult> submitJob(String jobName, Map<String, String> params, long pollingInterval)
			throws IOException {
		String payload = null;

		if (params != null) {
			// Build the Jenkins payload
			payload = "{\"parameter\": [";
			boolean comma = false;
			for (Entry<String, String> pair : params.entrySet()) {
				if (comma) {
					payload += ",";
				} else {
					comma = true;
				}
				payload += "{\"name\": \"" + pair.getKey()
						+ "\", \"value\": \"" + pair.getValue() + "\"}";
			}

			payload += "], \"\": \"\"}";
			logger.debug(payload);
		}

		String urlBuild = "http://" + host + ":" + port + "/" + path + "/job/"
				+ jobName + "/build";

		/*
		 * Get the next build number for the job
		 * For this to be accurate, we have to guarantee that this client
		 * is the only client kicking off builds for this job. This is not
		 * guaranteed here and has to be managed externally
		 */
		
		String jobUrl = "http://" + host + ":" + port + "/" + path + "/job/"
				+ jobName + "/api/json";
		
		String buildJson = this.makeHttpRequest(jobUrl, null);
		JsonElement buildElement = new JsonParser().parse(buildJson);
		JsonObject buildObject = buildElement.getAsJsonObject();
		String buildNumber = buildObject.getAsJsonPrimitive("nextBuildNumber").toString();
		
		/*
		 * Kick off a build of the job
		 */
		
		this.makeHttpRequest(urlBuild, payload);
		
		/*
		 * Create and return a FutureTask for waiting until job is complete
		 */
		
		JenkinsJobCallable jobCallable = new JenkinsJobCallable(this, jobName, buildNumber, pollingInterval);
		return new FutureTask<JenkinsJobResult>(jobCallable);
	}
	
	public void submitJob(String jobName, Map<String, String> params) throws IOException{
		String urlBuild = "http://" + host + ":" + port + "/" + path + "/job/"
				+ jobName + "/build";
		
		String payload = null;

		if (params != null) {
			// Build the Jenkins payload
			payload = "{\"parameter\": [";
			boolean comma = false;
			for (Entry<String, String> pair : params.entrySet()) {
				if (comma) {
					payload += ",";
				} else {
					comma = true;
				}
				payload += "{\"name\": \"" + pair.getKey()
						+ "\", \"value\": \"" + pair.getValue() + "\"}";
			}

			payload += "], \"\": \"\"}";
			logger.debug(payload);
		}
		
		
		this.makeHttpRequest(urlBuild, payload);
	}
	
	
}
