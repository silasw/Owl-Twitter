package com.github.silasw.owltwitter;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Scanner;

import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.TwitterApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.owlplatform.worldmodel.Attribute;
import com.owlplatform.worldmodel.client.ClientWorldConnection;
import com.owlplatform.worldmodel.client.StepResponse;
import com.owlplatform.worldmodel.client.WorldState;
import com.owlplatform.worldmodel.types.BooleanConverter;

public class TwitterApp {
	/**
	 * Logger for this class.
	 */
	private static final Logger log = LoggerFactory.getLogger(TwitterApp.class);
	/**
	 * URL for updating Twitter status.
	 */
	private static final String PROTECTED_RESOURCE_URL = "https://api.twitter.com/1/statuses/update.json";
	private static final String API_KEY = "UWlRURSVQgN8vnYb5B8yg";
/**
 * Reads a text file named config.txt where each line is in the format objecturi,attributename,true/false
 * The true or false is optional. If it says true, the app will tweet when the given attribute is true, and 
 * likewise for false. If nothing is written, it will tweet upon any change in status of that attribute.
 * 
 * Only works with boolean attributes.
 * 
 * The first time this program is run, it will ask for authorization by a Twitter account.
 * You may change accounts by deleting twitterauth.txt.
 * 
 * @param args World Model Host, World Model Client Port
 */
	public static void main(String[] args) {
		// Check if there are enough arguments
		if (args.length != 2) {
			System.out.println("I need 2 things: <World Model Host> <World Model Port>");
			return;
		}
		// Check if twitterauth.txt exists. If it does not, do authorization procedure.
		File f = new File("twitterauth.txt");
		if (!f.exists()){
			authorize();
		}
		else
			log.info("twitterauth.txt found.");
		
		// Read config file
		FileInputStream confstream = null;
		LinkedList<AttributeWatch> attwatches = new LinkedList<AttributeWatch>();
		String line;
		try{
		confstream = new FileInputStream("config.txt");
		DataInputStream in = new DataInputStream(confstream);
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		while((line = br.readLine()) != null){
			String[] lineparts = line.split(",");
			// The first part is the URI, the second is the attribute name, and the third is either true, false or nothing.
			// (If the last part is none of those, it will be treated 
			if(lineparts.length==2)
				attwatches.add(new AttributeWatch(lineparts[0], lineparts[1],""));
			else
				attwatches.add(new AttributeWatch(lineparts[0], lineparts[1], lineparts[2]));
		}
		br.close();
		}catch(Exception e){
			log.error("Error: " + e);
		}
		// Connect to the world model as a client.
		ClientWorldConnection wmc = new ClientWorldConnection();
		wmc.setHost(args[0]);
		wmc.setPort(Integer.parseInt(args[1]));
		wmc.connect(6000);
		log.info("Connected to world model.");
		do{	
			// If it isn't connected...
			if (!wmc.isConnected()) {
				System.err.println("Couldn't connect to the world model!  Check your connection parameters.");
				try {
					Thread.sleep(3000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				break;
			}
			// Begin streaming request
			// Each line in the config file has its own request
			long now = System.currentTimeMillis();
			long oneSecond = 1000l;
			log.info("Requesting from " + new Date(now) + " every " + oneSecond + " ms.");
			LinkedList<StepResponse> responses = new LinkedList<StepResponse>();
			for(AttributeWatch attw : attwatches){
				responses.add(wmc.getStreamRequest(attw.uri, now, oneSecond, attw.attname));
				log.info("Requesting from " + new Date(now) + " every " + oneSecond + " ms.");
				log.info("Identifier: "+attw.uri+" Attribute: " + attw.attname);
			}
			//Does not tweet on the first pass through the loop.
			boolean firstpass = true;
			mainloop:
			while(wmc.isConnected()){
				ListIterator<AttributeWatch> paramtracker = attwatches.listIterator();
				for(StepResponse response : responses) {
					AttributeWatch current = paramtracker.next();
					WorldState state = null;
					// If the response has an error, return to the outer loop and try to reconnect.
					if(response.isError()){
						log.error("Error reading response.",response.getError());
						break mainloop;
					}
					// Receive response. If response is not ready, skip to the next response.
					if(response.hasNext()){
						try {
						state = response.next();
						}catch(Exception e){
							System.err.println("Error occured during request: " + e);
							e.printStackTrace();
							break;
						}
					}
					else
						continue;
					// Get data from response, prioritizing more recent updates.
					Collection<String> identifiers = state.getIdentifiers();
					if (identifiers != null) {
						for (String uri : identifiers) {
							log.debug("Received update from URI: " + uri);
							Collection<Attribute> attribs = state.getState(uri);
							long timestamp = 0;
							Attribute attfinal = null;
							for (Attribute att : attribs) {
								if(att.getCreationDate()>timestamp){
									timestamp = att.getCreationDate();
									attfinal = att;
								}
								log.info(uri);
								log.info(Boolean.toString(BooleanConverter.get().decode(attfinal.getData())));
								// Recognize what type of change the app should check for.
								if (current.param==1){
									if(BooleanConverter.get().decode(attfinal.getData()) && !current.prev)
										tweet(uri+" is "+att.getAttributeName());
								}
								else if (current.param==-1){
									if(!BooleanConverter.get().decode(attfinal.getData()) && current.prev)
										tweet(uri+" is not "+att.getAttributeName());
								}
								else{
									if(!firstpass && (BooleanConverter.get().decode(attfinal.getData()) != current.prev))
										tweet(uri+" "+att.getAttributeName()+" status is "+BooleanConverter.get().decode(attfinal.getData()));
								}
								// Store previous value found, so known data is not tweeted again.
								current.prev = BooleanConverter.get().decode(attfinal.getData());
							}
		
							
						}
						
					}
				}
				try {
					Thread.sleep(oneSecond);
				} catch (InterruptedException e) {
					log.error("InterruptedException: " + e.getMessage());
					e.printStackTrace();
				}
				firstpass = false;
			}
		}while(wmc.isConnected());
		wmc.disconnect();
	}
	private static String getSecret(){
		FileInputStream sstream = null;
		String secret = "";
		try{
			  sstream = new FileInputStream("secretkey.txt");
			  DataInputStream in = new DataInputStream(sstream);
			  BufferedReader br = new BufferedReader(new InputStreamReader(in));
			  secret = br.readLine();
			  br.close();
			  sstream.close();
		}catch (Exception e){
			  log.error("Exception when reading secret key file: "+e);
		}
		return secret;
	}
	/**
	 * Asks the user to authorize the Twitter account.
	 * 
	 * The API key for the request is hard-coded. The API secret is read from a text file, secretkey.txt.
	 *
	 * @return boolean indicating success
	 */
	private static boolean authorize(){
		OAuthService service = new ServiceBuilder()
        .provider(TwitterApi.class)
        .apiKey(API_KEY)
        .apiSecret(getSecret())
        .build();
		
		Scanner in = new Scanner(System.in);

	    // Obtain the Request Token
	    Token requestToken = service.getRequestToken();
	    // Manual Authorization
	    System.out.println("Visit this URL and authorize this application:");
	    System.out.println(service.getAuthorizationUrl(requestToken));
	    System.out.println("And paste the verifier here");
	    System.out.print(">>");
	    Verifier verifier = new Verifier(in.nextLine());

	    // Trade the Request Token and Verifier for the Access Token
	    log.debug("Trading the Request Token for an Access Token...");
	    Token accessToken = service.getAccessToken(requestToken, verifier);
	    log.debug("Access token:" + accessToken + " )");
		try{
			  // Create file 
			  FileWriter fstream = new FileWriter("twitterauth.txt");
			  BufferedWriter out = new BufferedWriter(fstream);
			  out.write(accessToken.getToken());
			  out.write("\n");
			  out.write(accessToken.getSecret());
			  //Close the output stream
			  out.close();
		  }catch (Exception e){//Catch exception if any
		  log.error("Error: " + e.getMessage());
		  return false;
		  }
		return true;
	}
	/**
	 * Updates twitter status with the current date and time and the given content.
	 * @param content
	 * @return boolean representing success
	 */
	private static boolean tweet(String content){
		String token = "";
		String tokensecret = "";
		FileInputStream fstream = null;
		try{
			  fstream = new FileInputStream("twitterauth.txt");
		}catch (FileNotFoundException e){
			  authorize();
		}
			  // Get the object of DataInputStream
		try{
			  DataInputStream in = new DataInputStream(fstream);
			  BufferedReader br = new BufferedReader(new InputStreamReader(in));
			  token = br.readLine();
			  tokensecret = br.readLine();
			  if(token==null || tokensecret==null){
				  log.error("twitterauth.txt incorrectly formatted");
				  return false;
			  }
			  //Close the input stream
			  in.close();
		}catch (Exception e){//Catch exception if any
			  log.error("Error: " + e.getMessage() + "\nTry deleting twitterauth.txt and restarting the application.");
		}
		OAuthService service = new ServiceBuilder()
        .provider(TwitterApi.class)
        .apiKey(API_KEY)
        .apiSecret(getSecret())
        .build();
	    // Makes a request to Twitter to update status
	    OAuthRequest request = new OAuthRequest(Verb.POST, PROTECTED_RESOURCE_URL);
	    String tweetcontent = new Date(System.currentTimeMillis()).toString() + " " + content;
	    log.info("Tweeting: "+tweetcontent);
	    request.addBodyParameter("status",tweetcontent);
	    Token accessToken = new Token(token, tokensecret);
	    service.signRequest(accessToken, request);
	    Response response = request.send();
	    // Log the response
	    log.debug(response.getBody());
		return true;
	}
}
