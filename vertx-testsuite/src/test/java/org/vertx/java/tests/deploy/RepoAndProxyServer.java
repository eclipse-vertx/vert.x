package org.vertx.java.tests.deploy;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;

public class RepoAndProxyServer {
	private static final String webroot = "src/test/resources/";
	private final Vertx vertx;

	public RepoAndProxyServer(final String module1, final String module2) {
		vertx = Vertx.newVertx(9091,"localhost");
		vertx.createHttpServer()
				.requestHandler(new Handler<HttpServerRequest>() {
					public void handle(final HttpServerRequest req) {
						System.out.println("HANDLING "+req.uri);
						if (req.uri.equals("/vertx-mods/mods/" + module2
								+ "/mod.zip")) {
							System.out.println("HANDLING repo request");
							req.response.sendFile(webroot + "mod.zip");
						} else if (req.uri.equals("http://vert-x.github.com/vertx-mods/mods/" + module1
								+ "/mod.zip")) {
							System.out.println("HANDLING proxy request "+req.uri.substring("http://vert-x.github.com".length()));
							HttpClient client = vertx.createHttpClient()
									.setHost("vert-x.github.com").setPort(80);
						
							final HttpClientRequest cReq = client.request(
									req.method, req.uri.substring("http://vert-x.github.com".length()),
									new Handler<HttpClientResponse>() {
										public void handle(
												HttpClientResponse cRes) {
											System.out
													.println("Proxying response: "
															+ cRes.statusCode);
											req.response.statusCode = cRes.statusCode;
											req.response.headers().putAll(
													cRes.headers());
											req.response.setChunked(true);
											cRes.dataHandler(new Handler<Buffer>() {
												public void handle(Buffer data) {
													System.out
															.println("Proxying response body:"
																	+ data);
													req.response.write(data);
												}
											});
											cRes.endHandler(new SimpleHandler() {
												public void handle() {
													req.response.end();
												}
											});
										}
									});
							cReq.headers().putAll(req.headers());
							cReq.headers().put("Content-Length", "0");
							cReq.setChunked(true);
							req.dataHandler(new Handler<Buffer>() {
								public void handle(Buffer data) {
									System.out.println("Proxying request body:"
											+ data);
									cReq.write(data);
								}
							});
							req.endHandler(new SimpleHandler() {
								public void handle() {
									cReq.end();
								}
							});

						} else {
							// Clearly in a real server you would check the
							// path for better security!!
							req.response.end("cheers!");
						}
					}
				}).listen(9093, "localhost");
	}

}