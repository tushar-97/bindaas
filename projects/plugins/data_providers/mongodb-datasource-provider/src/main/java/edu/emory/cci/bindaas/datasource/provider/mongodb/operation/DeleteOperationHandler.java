package edu.emory.cci.bindaas.datasource.provider.mongodb.operation;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.WriteResult;
import com.mongodb.util.JSON;

import edu.emory.cci.bindaas.datasource.provider.mongodb.MongoDBProvider;
import edu.emory.cci.bindaas.datasource.provider.mongodb.model.OutputFormatProps;
import edu.emory.cci.bindaas.datasource.provider.mongodb.outputformat.OutputFormatRegistry;
import edu.emory.cci.bindaas.framework.model.ProviderException;
import edu.emory.cci.bindaas.framework.model.QueryResult;
import edu.emory.cci.bindaas.framework.util.GSONUtil;
import edu.emory.cci.bindaas.framework.util.StandardMimeType;

import static edu.emory.cci.bindaas.datasource.provider.mongodb.MongoDBProvider.getRulesMap;

public class DeleteOperationHandler implements IOperationHandler{

	private Log log = LogFactory.getLog(getClass());
	@Override
	public QueryResult handleOperation(DBCollection collection,
			OutputFormatProps outputFormatProps, JsonObject operationArguments , OutputFormatRegistry registry, Boolean enableAuthorization, String userRole )
			throws ProviderException {
	
		DeleteOperationDescriptor operationDescriptor = GSONUtil.getGSONInstance().fromJson(operationArguments, DeleteOperationDescriptor.class);
		validateArguments(operationDescriptor);
		DBObject query = (DBObject) JSON.parse(operationDescriptor.query.toString());
		DBCursor cursor = collection.find(query);

		try {
			if(enableAuthorization){
				List<String> ids = new ArrayList<String>(cursor.count());

				for (DBObject o : cursor) {
					ids.add(o.get("_id").toString());
				}

				if(!getRulesMap().containsKey(userRole)) {
					throw new Exception("No authorization rules specified for you");
				}

				List<String> list = getRulesMap().get(userRole);

				if(!list.containsAll(ids)){
					throw new Exception("Not authorized. Check query/role again");
				}
			}


			WriteResult writeResult = collection.remove(query);
			QueryResult queryResult = new QueryResult();
			queryResult.setData(new ByteArrayInputStream( String.format("{ \"rowsAffected\" : %s ,  \"operation\" : \"delete\" , \"query\" : %s }", writeResult.getN() + "" , operationDescriptor.query.toString() ).getBytes()));
			queryResult.setMimeType(StandardMimeType.JSON.toString());
			return queryResult;
		}
		catch(Exception e)
		{
			log.error(e);
			throw new ProviderException(MongoDBProvider.class.getName() , MongoDBProvider.VERSION ,e);
		}
	}
	
	
	private  void validateArguments(DeleteOperationDescriptor operationDescriptor) throws ProviderException
	{
		try {
			check(operationDescriptor!=null ,"Invalid query. Arguments incompatible with [DeleteOperationDescriptor]");
			check(operationDescriptor.query!=null ,"Invalid query. DeleteOperationDescriptor missing parameter [query]");
		
			
		} catch (Exception e) {
			log.error(e);
			throw new ProviderException(MongoDBProvider.class.getName() , MongoDBProvider.VERSION ,e);
		}
		
	}
	
	private static void check(boolean condition , String message) throws Exception
	{
		if(!condition) throw new Exception(message);
	}

	
	
	public static class DeleteOperationDescriptor {
		@Expose public JsonObject query;
		
		public JsonObject getQuery() {
			return query;
		}
		public void setQuery(JsonObject query) {
			this.query = query;
		}
		
	}

}
