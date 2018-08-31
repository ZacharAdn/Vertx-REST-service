package test.project1;

import com.mongodb.client.model.Filters;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;

import java.util.Comparator;
import java.util.List;


public class Server extends AbstractVerticle {

	private Router router;
	private MongoClient client;
	private String COLLECTION = "words";

	@Override
	public void start(Future<Void> fut) throws Exception {


		router = Router.router(vertx);

		vertx.createHttpServer().requestHandler(router::accept)
				.listen(
						config().getInteger("http.port", 9000),
						result -> {
							if (result.succeeded()) {
								fut.complete();
								System.out.println("Http server completed..");
							} else {
								fut.fail(result.cause());
								System.out.println("Http server failed..");
							}
						});

		router.route().handler(BodyHandler.create());

		JsonObject config = new JsonObject()
				.put("connection_string", "mongodb://localhost:27017")
				.put("db_name", "words");

		client = MongoClient.createShared(vertx, config);

		router.post("/analyze").handler(this::analyze);


		client.find(COLLECTION, new JsonObject(), results -> {
			List<JsonObject> objects = results.result();

			for (Object object: objects.toArray()) {
				System.out.println(object);
//				client.removeDocument(COLLECTION, (JsonObject) object, res -> {

//				});
			}
		});
	}



	// handle anything POSTed to /analyze
	private void analyze(RoutingContext context) {
		JsonObject record = new JsonObject();
		JsonObject body = context.getBodyAsJson();
		String text = (String) body.getValue("text");

		System.out.println("text:" + text);


		client.find(COLLECTION, new JsonObject().put("word", text), res -> {
			JsonObject ans = new JsonObject();

			if (!res.result().isEmpty()) {// the word exist in the db

				System.out.println("result: " + res.result());
				ans.put("value", text).put("lexical", text);
				context.response().end(ans.toString()+"\n");

			} else { // the word dosent exist in DB, look for the closest match

				// calc words value
				int wordVal = 0;
				for (int i = 0; i < text.length(); i++) {
					if (Character.isAlphabetic(text.charAt(i))) {
						wordVal += ((int) Character.toUpperCase(text.charAt(i)) - 64);
					}else{
						context.response().end("Wrong input! please type word without digits\n");
						// need to throw exception!
					}
				}
				System.out.println("word value: " + wordVal);

				// create Json object to insert the db
				record.put("word", text).put("value", wordVal);

				// query for value that greater than input words val
				Bson gt = Filters.gte("value", wordVal);
				BsonDocument bsonDocument = gt.toBsonDocument(BsonDocument.class, com.mongodb.async.client.MongoClients.getDefaultCodecRegistry() );
				JsonObject queryG = new JsonObject(bsonDocument.toJson());

				// query for value that lower than input words val
				Bson lt = Filters.lt("value", wordVal);
				BsonDocument bsonDocumentl = lt.toBsonDocument(BsonDocument.class, com.mongodb.async.client.MongoClients.getDefaultCodecRegistry());
				JsonObject queryL = new JsonObject(bsonDocumentl.toJson());

				int finalWordVal = wordVal;
				client.find(COLLECTION,  queryG, rG ->{
					if(!rG.result().isEmpty()){

						rG.result().sort(new Comparator<JsonObject>() {
							@Override
							public int compare(JsonObject l, JsonObject r) {
								int lf = Integer.parseInt(l.getValue("value").toString());
								int ri = Integer.parseInt(r.getValue("value").toString());
								return lf < ri ? -1 : lf > ri ? 1 : 0;
							}
						});

						System.out.println("found biggest value");
						System.out.println(rG.result());

						int disGt = Integer.parseInt(String.valueOf(rG.result().get(0).getValue("value"))) - finalWordVal;


						client.find(COLLECTION,  queryL, rL ->{
							if(!rL.result().isEmpty()) {
								rL.result().sort(new Comparator<JsonObject>() {
									@Override
									public int compare(JsonObject l, JsonObject r) {
										int lf = Integer.parseInt(l.getValue("value").toString());
										int ri = Integer.parseInt(r.getValue("value").toString());
										return lf > ri ? -1 : lf < ri ? 1 : 0;
									}
								});
								System.out.println("found lowest value to");
								System.out.println(rL.result());

								int disLt = finalWordVal - Integer.parseInt(String.valueOf(rL.result().get(0).getValue("value")));
								if (disGt < disLt){
									ans.put("value", rG.result().get(0).getValue("word"));
									context.response().end(ans.toString()+"\n");
									System.out.println("biggest value closer");
								}else{
									ans.put("value", rL.result().get(0).getValue("word"));
									context.response().end(ans.toString()+"\n");
									System.out.println("lowest value closer");
								}
							}else{
								System.out.println("only biggest value");
								ans.put("value", rG.result().get(0).getValue("word"));
								context.response().end(ans.toString()+"\n");
							}
						});

					}else{
						client.find(COLLECTION,  queryL, rL ->{
							if(!rL.result().isEmpty()) {
								rL.result().sort(new Comparator<JsonObject>() {
									@Override
									public int compare(JsonObject l, JsonObject r) {
										int lf = Integer.parseInt(l.getValue("value").toString());
										int ri = Integer.parseInt(r.getValue("value").toString());
										return lf > ri ? -1 : lf < ri ? 1 : 0;
									}
								});

								ans.put("value", rL.result().get(0).getValue("word"));
								context.response().end(ans.toString()+"\n");
								System.out.println("only lowest value");
								System.out.println(rL);
							}else{
								ans.put("value", "null");
								context.response().end(ans.toString()+"\n");
								System.out.println("the is no match!" + ans.toString());
							}
						});
					}
				});

				// insert the new word to the DB
				client.insert("words", record, r -> {
					if (r.succeeded()) {
						//print on server
						System.out.println("Saved word : " + record.getString("word") + ", with the value:" + record.getInteger("value"));
					} else {
						r.cause().printStackTrace();
					}
				});
			}
			// look for the closest by lexical

		});
	}

}

