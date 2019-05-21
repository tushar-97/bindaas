package edu.emory.cci.bindaas.datasource.provider.bigquery;


import edu.emory.cci.bindaas.datasource.provider.bigquery.model.DataSourceConfiguration;
import edu.emory.cci.bindaas.framework.api.IDeleteHandler;
import edu.emory.cci.bindaas.framework.api.IQueryHandler;
import edu.emory.cci.bindaas.framework.api.ISubmitHandler;
import edu.emory.cci.bindaas.framework.model.Profile;
import edu.emory.cci.bindaas.datasource.provider.bigquery.bundle.Activator;
import edu.emory.cci.bindaas.framework.model.ProviderException;
import edu.emory.cci.bindaas.framework.util.DocumentationUtil;
import edu.emory.cci.bindaas.framework.api.IProvider;
import edu.emory.cci.bindaas.framework.util.GSONUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.gson.JsonObject;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.bigquery.Dataset;
import java.io.File;
import java.io.FileInputStream;

import java.util.Dictionary;
import java.util.Hashtable;

public class BigQueryProvider implements IProvider {
	public final static int VERSION = 1;
	private IQueryHandler queryHandler;
	private IDeleteHandler deleteHandler;
	private ISubmitHandler submitHandler;
	private Log log = LogFactory.getLog(getClass());
	private static final String DOCUMENTATION_RESOURCES_LOCATION = "META-INF/documentation";
	private JsonObject documentation;

	public void init() {
		Dictionary<String, Object> props = new Hashtable<String, Object>();
		props.put("class", getClass().getName());
		Activator.getContext().registerService(IProvider.class.getName(), this, props);

		// initialize documentation object

		documentation = DocumentationUtil.getProviderDocumentation(Activator.getContext(), DOCUMENTATION_RESOURCES_LOCATION);
	}
	public void setQueryHandler(IQueryHandler queryHandler) {
		this.queryHandler = queryHandler;
	}

	public void setDeleteHandler(IDeleteHandler deleteHandler) {
		this.deleteHandler = deleteHandler;
	}

	public void setSubmitHandler(ISubmitHandler submitHandler) {
		this.submitHandler = submitHandler;
	}

	@Override
	public String getId() {
		return getClass().getName();
	}

	@Override
	public int getVersion() {

		return VERSION;
	}

	@Override
	public JsonObject getDocumentation() {

		return documentation;
	}

	@Override
	public IQueryHandler getQueryHandler() {

		return queryHandler;
	}

	@Override
	public ISubmitHandler getSubmitHandler() {

		return submitHandler;
	}

	@Override
	public IDeleteHandler getDeleteHandler() {

		return deleteHandler;
	}

	@Override
	public Profile validateAndInitializeProfile(Profile profile)
			throws ProviderException {
		try {

			JsonObject dataSource = profile.getDataSource();
			if(dataSource!=null)
			{
				DataSourceConfiguration configuration = GSONUtil.getGSONInstance().fromJson(dataSource, DataSourceConfiguration.class);
				connect(configuration);
				return profile;
			}
			else
			{
				throw new Exception("dataSource not specified");
			}
		}
		catch(Exception e)
		{
			log.error(e);
			throw new ProviderException(BigQueryProvider.class.getName() , BigQueryProvider.VERSION ,e);
		}
	}

	private void connect(DataSourceConfiguration configuration) throws Exception
	{
		System.out.println(configuration.getUrl());
		GoogleCredentials credentials=null;
		File credentialsPath = new File(configuration.getUrl());
		FileInputStream serviceAccountStream = new FileInputStream(credentialsPath);

		//Data provider disappears if uncommented
		/**
		 try {
			credentials = ServiceAccountCredentials.fromStream(serviceAccountStream);
		}
		catch (Exception e){
			System.out.println(e);
		}
		**/

		// Instantiate a client.
		BigQuery bigquery =
				BigQueryOptions.newBuilder().setCredentials(credentials).build().getService();

		// Use the client.
		System.out.println("Datasets:");
		for (Dataset dataset : bigquery.listDatasets().iterateAll()) {
			System.out.printf("%s%n", dataset.getDatasetId().getDataset());
		}
	}

	@Override
	public JsonObject getDataSourceSchema() {
		// TODO later
		return new JsonObject();
	}
}


