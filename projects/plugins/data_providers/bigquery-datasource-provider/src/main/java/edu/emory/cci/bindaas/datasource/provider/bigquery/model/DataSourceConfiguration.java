package edu.emory.cci.bindaas.datasource.provider.bigquery.model;

import com.google.gson.annotations.Expose;

public class DataSourceConfiguration {

	@Expose private String url;


	public void validate() throws Exception
	{
		if(url == null) throw new Exception("DataSourceConfiguration: url not set");


	}

	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}



}
