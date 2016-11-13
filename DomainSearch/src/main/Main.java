package main;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;

import org.apache.commons.io.IOUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class Main {
	private static Properties properties = new Properties();
	
	private static String searchUrl = "https://ie.godaddy.com/domainsapi/v1/search/exact?q={0}.com&key=dpp_search&pc=&ptl=";
	private static final boolean removeUnnecessaryCharacters = false;
	
	private static String authorisation;
	private static String inputFile;
	private static String outputFile;
	
	private static int updateFrequency;
	private static double maxPrice;
	
	private static boolean printEachWord = false; 
	
	public Main() {
		InputStream configFile = null;

		try {
			configFile = new FileInputStream("domainsearch.properties");
			properties.load(configFile);
			
			authorisation = properties.getProperty("authorisation");
			inputFile = properties.getProperty("inputfile");
			outputFile = properties.getProperty("outputfile");
			updateFrequency = Integer.parseInt(properties.getProperty("updatefrequency"));
			maxPrice = Integer.parseInt(properties.getProperty("maxprice"));
			printEachWord = Boolean.valueOf(properties.getProperty("printEachWord"));
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			if (configFile != null) {
				try {
					configFile.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private static String getResponseData (HttpsURLConnection connection) throws Exception {
		InputStream is = connection.getInputStream();
		byte[] bytes = IOUtils.toByteArray(is);
		return new String(bytes, "UTF-8");
	}
	
	private static HttpsURLConnection createConnection (URL url) throws Exception {
		HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
		connection.setRequestProperty("Authorization", authorisation);
		connection.setRequestMethod("GET");
		
		return connection;
	}
	
	private static Double getPrice(JSONObject obj) {
		Double currentPrice = 0d;
		
		JSONArray products = (JSONArray) obj.get("Products");
		
		if (products.size() > 0) {
			JSONObject firstProduct = (JSONObject) products.get(0);
			JSONObject princeInfo = (JSONObject) firstProduct.get("PriceInfo");
			currentPrice = (Double) princeInfo.get("CurrentPrice");
		}
		
		return currentPrice;
	}
	
	private static DomainInfo getDomainInfo (String keyword) throws Exception {
		if (removeUnnecessaryCharacters) {
			keyword = keyword.replaceAll("\\d","").replaceAll("\\s","").replaceAll("\"", "").replaceAll("\\.", "").replaceAll("'", "").toLowerCase();
		}
		
		if (printEachWord) {
			System.out.println(keyword);
		}
		
		DomainInfo domainInfo = new DomainInfo();
		
		String replacedUrl = searchUrl.replace("{0}", keyword);
		URL url = new URL(replacedUrl);
		
		HttpsURLConnection connection = createConnection(url);
		connection.connect();
		
		if (connection.getResponseCode() == HttpsURLConnection.HTTP_OK) {
			String responseData = getResponseData(connection);
			
			JSONParser parser = new JSONParser();
			
			try {
				JSONObject obj = (JSONObject) parser.parse(responseData);
				JSONObject exactMatchDomain = (JSONObject) obj.get("ExactMatchDomain");
				domainInfo.available = (Boolean) exactMatchDomain.get("IsPurchasable");
				domainInfo.price = exactMatchDomain.get("PriceDisplay") != null ? (Double) exactMatchDomain.get("Price") : getPrice(obj);
			} catch (Exception e) {}
		} else {
			System.out.println("Response code : " + connection.getResponseCode());
		}
		
		return domainInfo;
	}
	
	public static void main (String[] args) {
		String word = null;
		
		int ctr = 0;
		
		try {
			BufferedReader br = new BufferedReader(new FileReader(inputFile));
			BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile));
			
			while((word = br.readLine()) != null) {
				DomainInfo domainInfo = getDomainInfo(word);
				
				if (domainInfo.available && domainInfo.price < maxPrice) {
					String output = word + ", Available: " + domainInfo.available + ", Price: " + domainInfo.price;
					System.err.println(output);
					bw.write(output);
				} else if (ctr % updateFrequency == 0) {
					System.out.println("Progress - ctr: " + ctr + ", word : " + word + ", Available: " + domainInfo.available + ", Price: " + domainInfo.price);
				}	
				
				ctr++;
			}
			
			br.close();
			bw.close();
		} catch (Exception e) {
			System.out.println(word);
			e.printStackTrace();
		}
	}
}
