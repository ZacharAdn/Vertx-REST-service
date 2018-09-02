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


/**
 *
 */
public class Server extends AbstractVerticle {

	private MongoClient mongoclient;
	private String COLLECTION = "words";

	/**
	 *
	 * @param fut
	 * @throws Exception
	 */
	@Override
	public void start(Future<Void> fut) throws Exception {
        Router router = Router.router(vertx);

		// create Http server and listen to 9000 port
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

		// mongo connection config file
		JsonObject config = new JsonObject()
				.put("connection_string", "mongodb://localhost:27017")
				.put("db_name", "words");

		// create mongoDB client
		mongoclient = MongoClient.createShared(vertx, config);

		// routing all users POSTs to /analyze
		router.post("/analyze").handler(this::analyze);
	}


	/**
	 * handle anything POSTed to /analyze
	 * @param context
	 */
	private void analyze(RoutingContext context) {
		// get the input in lower case alphabet
		String text = ((String) context.getBodyAsJson().getValue("text")).toLowerCase();
		final String[] ans = new String[2];

		// look for the input word in the DB
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

				findAnserws(wordVal, text, ans, context);
			}
		});
	}

    /**
     * Find closest words by lexical and value
     * @param finalWordVal input word value
     * @param text input word
     * @param ans answers array
     * @param context RoutingContext
     */
	private void findAnserws(int finalWordVal, String text, String[] ans, RoutingContext context) {
		final FindOptions gtfindOptions = new FindOptions().setSort(new JsonObject().put("value",1)).setLimit(1);
		final FindOptions ltfindOptions = new FindOptions().setSort(new JsonObject().put("value",-1)).setLimit(1);
		JsonObject gtQuery = new JsonObject().put("value", new JsonObject().put("$gte",finalWordVal));
		JsonObject ltQuery = new JsonObject().put("value", new JsonObject().put("$lt", finalWordVal));

		// look for the closest word with biggest value than the input
		mongoclient.findWithOptions(COLLECTION, gtQuery ,gtfindOptions, rG -> {
			if(!rG.result().isEmpty()){//found biggest value

                // calc difference to input word value
				int disGt = Integer.parseInt(String.valueOf(rG.result().get(0).getValue("value"))) - finalWordVal;

				// look for closest word with smallest value than the input
				mongoclient.findWithOptions(COLLECTION,  ltQuery, ltfindOptions, rL ->{

				    if(!rL.result().isEmpty()) {//found lowest value to
						int disLt = finalWordVal - Integer.parseInt(String.valueOf(rL.result().get(0).getValue("value")));

						if (disGt < disLt)//biggest value closer
							ans[0] = getWord(rG);
						else//lowest value closer
							ans[0] = getWord(rL);

					}else//only biggest value found
						ans[0] = getWord(rG);

					lookForLexical(text,ans,finalWordVal, context);
				});

			}else{// not found biggest value

                mongoclient.findWithOptions(COLLECTION, ltQuery, ltfindOptions, rL ->{

                    if(!rL.result().isEmpty())
						ans[0] = getWord(rL);
					else // no found match in the DB
						ans[0] = "null";

					lookForLexical(text,ans,finalWordVal,context);
				});
			}
		});
	}

	/**
	 * Find closest lexical words in the database
	 * @param text input word
	 * @param ans output words
     * @param finalWordVal input word value
	 * @param context RoutingContext
	 */
	private void lookForLexical(String text, String[] ans, int finalWordVal, RoutingContext context) {
		final FindOptions gtRegFindOptions = new FindOptions().setSort(new JsonObject().put("word", 1)).setLimit(1);
		final FindOptions ltRegFindOptionslt = new FindOptions().setSort(new JsonObject().put("word", -1)).setLimit(1);
		JsonObject gtRegQuery = new JsonObject().put("word", new JsonObject().put("$gte", text));
		JsonObject ltRegQuery = new JsonObject().put("word", new JsonObject().put("$lt", text));

		// look for the alphabetic closest word above the input
		mongoclient.findWithOptions(COLLECTION, gtRegQuery, gtRegFindOptions, gtR -> {

		    if (!gtR.result().isEmpty()) {
				String gWord = getWord(gtR);

				// look for the alphabetic closest word below the input
				mongoclient.findWithOptions(COLLECTION, ltRegQuery, ltRegFindOptionslt, ltR -> {

				    if (!ltR.result().isEmpty()) {
						String lWord = getWord(ltR);
						System.out.println(lWord + "(" + compareStrings(text, lWord) + "), " + text + ", " + gWord + "(" + compareStrings(gWord, text) + ")");

						// compare between the words differences
						if (compareStrings(text, lWord) < compareStrings(gWord, text))
							ans[1] = lWord;
						else
							ans[1] = gWord;

					} else // its no word below
						ans[1] = gWord;

					// send response to the client
					context.response().end(new JsonObject().put("value", ans[0]).put("lexical", ans[1]) + "\n");
					insertToDB(text, finalWordVal);
				});

			} else { // its no word above

				mongoclient.findWithOptions(COLLECTION, ltRegQuery, ltRegFindOptionslt, ltR -> {

					if (!ltR.result().isEmpty())
						ans[1] = getWord(ltR);
					else// match found match in the DB
						ans[1] = "null";

					// send response to the client
					context.response().end(new JsonObject().put("value", ans[0]).put("lexical", ans[1]) + "\n");
					insertToDB(text, finalWordVal);

				});
			}
		});
	}

	/**
	 * insert the input word to the database
	 * @param text input word
	 * @param val input word value
	 */
	private void insertToDB(String text, int val) {

		mongoclient.insert("words", new JsonObject().put("word", text).put("value", val), r -> {
			if (r.succeeded()) {
				System.out.println("Saved word : " + text + ", with the value:" + val);
			} else {
				r.cause().printStackTrace();
			}
		});
	}

	/**
	 * get word from mongocient find results
	 * @param h
	 * @return word
	 */
	private String getWord(AsyncResult<List<JsonObject>> h) {
		return h.result().get(0).getValue("word").toString();
	}

	/**
	 * Compare between two Strings and get the alphabetic difference between them
	 * @param s1 String 1
	 * @param s2 String 2
	 * @return abs of the compare sum
	 */
	private static int compareStrings(String s1, String s2) {
		int compareSum = 0;
		int c1, c2;

		// calc the difference between chars
		for(int i = 0; i < s1.length() && i < s2.length(); i++) {
			c1 = (int) s1.charAt(i);
			c2 = (int) s2.charAt(i);
			compareSum += c1 - c2;
		}

		// in case that one word longer than other
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

