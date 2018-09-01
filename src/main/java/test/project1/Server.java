package test.project1;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.List;


public class Server extends AbstractVerticle {

	private MongoClient mongoclient;
	private String COLLECTION = "words";

	@Override
	public void start(Future<Void> fut) throws Exception {


		Router router = Router.router(vertx);

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

		mongoclient = MongoClient.createShared(vertx, config);

		router.post("/analyze").handler(this::analyze);

//		final FindOptions printOptions = new FindOptions().setSort(new JsonObject().put("word",1));
//		mongoclient.findWithOptions(COLLECTION, new JsonObject(),printOptions, results -> {
//			List<JsonObject> objects = results.result();
//
//			for (Object object: objects.toArray()) {
//				System.out.println(object);
////				mongoclient.removeDocument(COLLECTION, (JsonObject) object, res -> {
//
////				});
//			}
//		});
	}



	// handle anything POSTed to /analyze
	private void analyze(RoutingContext context) {
		String text = ((String) context.getBodyAsJson().getValue("text")).toLowerCase();
		final String[] ans = new String[2];

		mongoclient.find(COLLECTION, new JsonObject().put("word", text), res -> {

			if (!res.result().isEmpty()) {// the word exist in the db
				ans[0] = text;
				ans[1] = text;

			} else { // the word dosent exist in DB, look for the closest match

				// calc words value
				int wordVal = 0;
				for (int i = 0; i < text.length(); i++) {
					if (Character.isAlphabetic(text.charAt(i))) { //check edge case
						wordVal += ((int) text.charAt(i) - 96);
					}else{
						context.response().end("Wrong input! please type word without digits\n");
						context.fail(res.cause());
					}
				}

				int finalWordVal = wordVal;
				final FindOptions gtfindOptions = new FindOptions().setSort(new JsonObject().put("value",1)).setLimit(1);
				final FindOptions ltfindOptions = new FindOptions().setSort(new JsonObject().put("value",-1)).setLimit(1);
				JsonObject gtQuery = new JsonObject().put("value", new JsonObject().put("$gte",finalWordVal));
				JsonObject ltQuery = new JsonObject().put("value", new JsonObject().put("$lt", finalWordVal));

				mongoclient.findWithOptions(COLLECTION, gtQuery ,gtfindOptions, rG -> {
					if(!rG.result().isEmpty()){//found biggest value
						System.out.println("found biggest value");
						int disGt = Integer.parseInt(String.valueOf(rG.result().get(0).getValue("value"))) - finalWordVal;

						mongoclient.findWithOptions(COLLECTION,  ltQuery, ltfindOptions, rL ->{
							if(!rL.result().isEmpty()) {//found lowest value to
								System.out.println("found lowest value to");
								int disLt = finalWordVal - Integer.parseInt(String.valueOf(rL.result().get(0).getValue("value")));

								if (disGt < disLt)//biggest value closer
									ans[0] = getWord(rG);
								else//lowest value closer
									ans[0] = getWord(rL);

							}else//only biggest value
								ans[0] = getWord(rG);
							lookForLexical(text,ans, context,finalWordVal);
						});

					}else{
						mongoclient.findWithOptions(COLLECTION, ltQuery, ltfindOptions, rL ->{
							if(!rL.result().isEmpty())
								ans[0] = getWord(rL);
							else
								ans[0] = "null";
							lookForLexical(text,ans,context,finalWordVal);
						});
					}
				});
			}
		});
	}

	private void lookForLexical(String text, String[] ans, RoutingContext context, int finalWordVal) {
		final FindOptions gtRegFindOptions = new FindOptions().setSort(new JsonObject().put("word", 1)).setLimit(1);
		final FindOptions ltRegFindOptionslt = new FindOptions().setSort(new JsonObject().put("word", -1)).setLimit(1);
		JsonObject gtRegQuery = new JsonObject().put("word", new JsonObject().put("$gte", text));
		JsonObject ltRegQuery = new JsonObject().put("word", new JsonObject().put("$lt", text));

		mongoclient.findWithOptions(COLLECTION, gtRegQuery, gtRegFindOptions, gtR -> {
			if (!gtR.result().isEmpty()) {
				String gWord = getWord(gtR);
				mongoclient.findWithOptions(COLLECTION, ltRegQuery, ltRegFindOptionslt, ltR -> {
					if (!ltR.result().isEmpty()) {
						String lWord = getWord(ltR);
						int compareGt = compareStrings(gWord, text);
						int compareLt = compareStrings(text, lWord);
						System.out.println(lWord + "(" + compareLt + "), " + text + "," + gWord + "(" + compareGt + ")");

						if (compareLt < compareGt)
							ans[1] = lWord;
						else
							ans[1] = gWord;

					} else
						ans[1] = gWord;
					context.response().end(new JsonObject().put("value", ans[0]).put("lexical", ans[1]) + "\n");
					insertToDB(text, finalWordVal);

				});

			} else {
				mongoclient.findWithOptions(COLLECTION, ltRegQuery, ltRegFindOptionslt, ltR -> {
					if (!ltR.result().isEmpty())
						ans[1] = getWord(ltR);
					else
						ans[1] = "null";

					context.response().end(new JsonObject().put("value", ans[0]).put("lexical", ans[1]) + "\n");
					insertToDB(text, finalWordVal);

				});
			}
		});
		System.out.println("lexical results" + ans[1]);
	}

	private void insertToDB(String text, int val) {

		mongoclient.insert("words", new JsonObject().put("word", text).put("value", val), r -> {
			if (r.succeeded()) {
				System.out.println("Saved word : " + text + ", with the value:" + val);
			} else {
				r.cause().printStackTrace();
			}
		});
	}

	private String getWord(AsyncResult<List<JsonObject>> h) {
		return h.result().get(0).getValue("word").toString();
	}

	private static int compareStrings(String s1, String s2) {
		int compareSum = 0;
		int c1, c2;
		for(int i = 0; i < s1.length() && i < s2.length(); i++) {
			c1 = (int) s1.charAt(i);
			c2 = (int) s2.charAt(i);
			compareSum += c1 - c2;
		}

		if (s1.length() > s2.length()) {
			for (int i = s2.length(); i < s1.length(); i++) {
				compareSum += ((int) s1.charAt(i) - 96);
			}
			return Math.abs(compareSum);
		}
		if (s1.length() < s2.length()) {
			for (int i = s1.length(); i < s2.length(); i++) {
				compareSum += ((int) s2.charAt(i) - 96);
			}
			return Math.abs(compareSum);
		}

		return Math.abs(compareSum);
	}

}

