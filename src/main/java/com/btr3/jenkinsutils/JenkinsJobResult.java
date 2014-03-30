package com.btr3.jenkinsutils;

public class JenkinsJobResult {

	private Boolean result;
	private String buildName;
	private String buildNumber;
	
	
	public Boolean getResult() {
		return result;
	}
	
	public void setResult(Boolean result) {
		this.result = result;
	}
	
	public String getBuildName() {
		return buildName;
	}
	
	public void setBuildName(String buildName) {
		this.buildName = buildName;
	}
	
	public String getBuildNumber() {
		return buildNumber;
	}
	
	public void setBuildNumber(String buildNumber) {
		this.buildNumber = buildNumber;
	}
	
}
