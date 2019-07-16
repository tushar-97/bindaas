package edu.emory.cci.bindaas.datasource.provider.mongodb;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.util.JSON;

import edu.emory.cci.bindaas.datasource.provider.mongodb.bundle.Activator;
import edu.emory.cci.bindaas.datasource.provider.mongodb.model.DataSourceConfiguration;
import edu.emory.cci.bindaas.datasource.provider.mongodb.model.SubmitEndpointProperties;
import edu.emory.cci.bindaas.datasource.provider.mongodb.model.SubmitEndpointProperties.InputType;
import edu.emory.cci.bindaas.framework.api.IDeleteHandler;
import edu.emory.cci.bindaas.framework.api.IProvider;
import edu.emory.cci.bindaas.framework.api.IQueryHandler;
import edu.emory.cci.bindaas.framework.api.ISubmitHandler;
import edu.emory.cci.bindaas.framework.model.Profile;
import edu.emory.cci.bindaas.framework.model.ProviderException;
import edu.emory.cci.bindaas.framework.model.SubmitEndpoint;
import edu.emory.cci.bindaas.framework.util.DocumentationUtil;
import edu.emory.cci.bindaas.framework.util.GSONUtil;

public class MongoDBProvider implements IProvider{

	public final static int VERSION = 1;
	private IQueryHandler queryHandler;
	private IDeleteHandler deleteHandler;
	private ISubmitHandler submitHandler;
	private Log log = LogFactory.getLog(getClass());
	private static final String DOCUMENTATION_RESOURCES_LOCATION = "META-INF/documentation";
	private JsonObject documentation;
	private static Map<String, List<String>> rulesMap;
	
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
				doInitialize(profile,configuration);
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
			throw new ProviderException(MongoDBProvider.class.getName() , MongoDBProvider.VERSION ,e);
		}
		
	}

	private void initializeRulesCache(DBCollection collection, DataSourceConfiguration configuration) throws IllegalStateException{

		rulesMap = new HashMap<String, List<String>>();

		if(configuration.isAuthorization()){

			try {

				JsonObject jsonObject = GSONUtil.getJsonParser().parse(
						configuration.getAuthorizationRules()).getAsJsonObject();

				for(Map.Entry<String,JsonElement> entry : jsonObject.entrySet()){
					String role = entry.getKey();
					JsonObject rules = GSONUtil.getJsonParser().parse(
							entry.getValue().getAsString()).getAsJsonObject();
					DBObject query = (DBObject) JSON.parse(rules.toString());
					BasicDBObject fields = new BasicDBObject("_id", "1");
					DBCursor cursor = collection.find(query,fields);

					List<String> ids = new ArrayList<String>(cursor.count());

					for (DBObject o : cursor) {
						ids.add(o.get("_id").toString());
					}

					if(rulesMap.containsKey(role)) {
						rulesMap.get(role).addAll(ids);
					}
					else {
						rulesMap.put(role,ids);
					}

					log.info("List is::"+rulesMap.toString());
				}
			}
			catch (IllegalStateException e) {
				log.error(e);
				throw new IllegalStateException("Authorization Rules are not in valid JSON format");
			}

		}

	}

	public static Map<String, List<String>> getRulesMap(){
		return rulesMap;
	}

	private void doInitialize(Profile profile, DataSourceConfiguration configuration) throws Exception{
		
		if(configuration.isInitialize())
		{
			// create mongo endpoint
			SubmitEndpoint mongo = new SubmitEndpoint();
			mongo.setName("json");
			mongo.setCreatedBy(profile.getCreatedBy());
			SubmitEndpointProperties seprops = new SubmitEndpointProperties();
			seprops.setInputType(InputType.JSON);
			mongo.setProperties(GSONUtil.getGSONInstance().toJsonTree(seprops).getAsJsonObject());
			mongo = submitHandler.validateAndInitializeSubmitEndpoint(mongo);

			// create mongo file endpoint
			SubmitEndpoint mongoFile = new SubmitEndpoint();
			mongoFile.setName("jsonFile");
			mongoFile.setCreatedBy(profile.getCreatedBy());
			seprops = new SubmitEndpointProperties();
			seprops.setInputType(InputType.JSON_FILE);
			mongoFile.setProperties(GSONUtil.getGSONInstance().toJsonTree(seprops).getAsJsonObject());
			mongoFile = submitHandler.validateAndInitializeSubmitEndpoint(mongoFile);

			// create csv endpoint
			SubmitEndpoint csv = new SubmitEndpoint();
			csv.setName("csv");
			csv.setCreatedBy(profile.getCreatedBy());
			seprops = new SubmitEndpointProperties();
			seprops.setInputType(InputType.CSV);
			csv.setProperties(GSONUtil.getGSONInstance().toJsonTree(seprops).getAsJsonObject());
			csv = submitHandler.validateAndInitializeSubmitEndpoint(csv);
			
			// create csv file endpoint
			
			SubmitEndpoint csvFile = new SubmitEndpoint();
			csvFile.setName("csvFile");
			csvFile.setCreatedBy(profile.getCreatedBy());
			seprops = new SubmitEndpointProperties();
			seprops.setInputType(InputType.CSV_FILE);
			csvFile.setProperties(GSONUtil.getGSONInstance().toJsonTree(seprops).getAsJsonObject());
			csvFile = submitHandler.validateAndInitializeSubmitEndpoint(csvFile);
			
			profile.getSubmitEndpoints().put(mongo.getName(), mongo);
			profile.getSubmitEndpoints().put(mongoFile.getName(), mongoFile);
			profile.getSubmitEndpoints().put(csv.getName(), csv);
			profile.getSubmitEndpoints().put(csvFile.getName(), csvFile);
		}
		
	}
	private void connect(DataSourceConfiguration configuration) throws Exception
	{
		MongoClient mongo = null;
		try {
			if(configuration.getUsername() == null && configuration.getPassword() == null){
				mongo = new MongoClient(new ServerAddress(configuration.getHost(),configuration.getPort()));
			}
			else if(configuration.getUsername().isEmpty() && configuration.getPassword().isEmpty()){
				mongo = new MongoClient(new ServerAddress(configuration.getHost(),configuration.getPort()));
			}
			else{
				MongoCredential credential = MongoCredential.createCredential(
						configuration.getUsername(),
						configuration.getAuthenticationDb(),
						configuration.getPassword().toCharArray()
				);
				mongo = new MongoClient(new ServerAddress(configuration.getHost(),configuration.getPort()), Arrays.asList(credential));
			}

			DB db = mongo.getDB(configuration.getDb());
			DBCollection collection = db.getCollection(configuration.getCollection());
			collection.count(); // run a simple command to check connectivity
			initializeRulesCache(collection, configuration);
		} catch (Exception e) {
			log.error(e);
			throw e;
		}
		finally{
			if(mongo!=null)
			{
				mongo.close();
			}
		}
		
	}
	
	@Override
	public JsonObject getDataSourceSchema() {
		// TODO later
		return new JsonObject();
	}

}
