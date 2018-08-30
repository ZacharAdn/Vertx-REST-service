package test.project1;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;


public class Server extends AbstractVerticle {

	private Router router;
	MongoClient client;

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
	}



	// handle anything POSTed to /analyze
	private void analyze(RoutingContext context) {
		JsonObject body = context.getBodyAsJson();

		client.save("words", body, res -> {
			if (res.succeeded()) {
				String id = res.result();

				String postedText = body.getString("text");

				//print on server
				System.out.println("Saved word : " + postedText + ", with the ID:" + id);

				String Value = "";
				String lexical = "";

				//print to the client
				context.response().end("You POSTed JSON which contains a text attribute with the value: " + postedText + "\n");
			} else {
				res.cause().printStackTrace();
			}
		});
	}
}
