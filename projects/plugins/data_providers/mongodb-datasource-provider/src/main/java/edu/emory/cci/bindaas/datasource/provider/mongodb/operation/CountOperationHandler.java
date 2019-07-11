package edu.emory.cci.bindaas.datasource.provider.mongodb.operation;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.Expose;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;

import edu.emory.cci.bindaas.datasource.provider.mongodb.MongoDBProvider;
import edu.emory.cci.bindaas.datasource.provider.mongodb.model.OutputFormatProps;
import edu.emory.cci.bindaas.datasource.provider.mongodb.outputformat.OutputFormatRegistry;
import edu.emory.cci.bindaas.framework.model.ProviderException;
import edu.emory.cci.bindaas.framework.model.QueryResult;
import edu.emory.cci.bindaas.framework.util.GSONUtil;
import edu.emory.cci.bindaas.framework.util.StandardMimeType;

public class CountOperationHandler implements IOperationHandler {

	private Log log = LogFactory.getLog(getClass());
	
	@Override
	public QueryResult handleOperation(DBCollection collection,
			OutputFormatProps outputFormatProps, JsonObject operationArguments , OutputFormatRegistry registry, Boolean enableAuthorization)
			throws ProviderException {
		CountOperationDescriptor operationDescriptor = GSONUtil.getGSONInstance().fromJson(operationArguments, CountOperationDescriptor.class);
		validateArguments(operationDescriptor);
		
			try{
				DBObject query = (DBObject) JSON.parse(operationDescriptor.query.toString());

				DBCursor cursor = collection.find(query);

				if(enableAuthorization){
					List<String> ids = new ArrayList<String>(cursor.count());

					for (DBObject o : cursor) {
						ids.add(o.get("_id").toString());
					}

					List<String> list = new ArrayList<String>();
					list.add("2");
					list.add("4");
					list.add("5d2619a8975a79640cefe6e2");
					list.add("5d272dcb011328f5b529e4b4");

					if(!list.containsAll(ids)){
						throw new Exception("Not authorized. Check query/role again");
					}
				}

				long count = collection.count(query);
				JsonObject result = new JsonObject();
				result.add("query", operationDescriptor.query);
				result.add("count", new JsonPrimitive(count));
				
				QueryResult queryResult = new QueryResult();
				queryResult.setMimeType(StandardMimeType.JSON.toString());
				queryResult.setData(new ByteArrayInputStream(result.toString().getBytes()));
				return queryResult;
				
			}
			catch(Exception e)
			{
				log.error(e);
				throw new ProviderException(MongoDBProvider.class.getName() , MongoDBProvider.VERSION ,e);
			}
	}
	
	private  void validateArguments(CountOperationDescriptor operationDescriptor) throws ProviderException
	{
		try {
			check(operationDescriptor!=null ,"Invalid query. Arguments cannot as [CountOperationDescriptor]");
			check(operationDescriptor.query!=null ,"Invalid query. CountOperationDescriptor missing parameter [query]");
		} catch (Exception e) {
			log.error(e);
			throw new ProviderException(MongoDBProvider.class.getName() , MongoDBProvider.VERSION ,e);
		}
		
	}
	
	private static void check(boolean condition , String message) throws Exception
	{
		if(!condition) throw new Exception(message);
	}

	public static class CountOperationDescriptor {

		@Expose public JsonObject query;
		
		
		public JsonObject getQuery() {
			return query;
		}
		public void setQuery(JsonObject query) {
			this.query = query;
		}
		
	}

}
