package com.btr3.jenkinsutils;

import java.util.concurrent.Callable;


public class JenkinsJobCallable implements Callable<JenkinsJobResult> {

	private JenkinsClient client;
	private String buildName;
	private String buildNumber;
	private long pollingInterval;
	
	public JenkinsJobCallable(JenkinsClient client, String buildName, String buildNumber, long pollingInterval){
		this.client = client;
		this.buildName = buildName;
		this.buildNumber = buildNumber;
		this.pollingInterval = pollingInterval;
	}
	
	@Override
	public JenkinsJobResult call() throws Exception {
		JenkinsJobResult res = new JenkinsJobResult();
		res.setBuildName(buildName);
		res.setBuildNumber(buildNumber);
		
		while(true){
			Thread.sleep(pollingInterval);
			
			String status = client.getJobStatus(buildName, buildNumber);
			if(status != null){
				if(status.equalsIgnoreCase("success")){
					res.setResult(true);
					return res;
				}
				else if(status.equalsIgnoreCase("failure")){
					res.setResult(false);
					return res;
				}
			}
		}
	}

}
